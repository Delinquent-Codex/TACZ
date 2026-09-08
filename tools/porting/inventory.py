#!/usr/bin/env python3
"""Rebuild the immutable source inventory and current file disposition (stdlib only).

Usage: python tools/porting/inventory.py --baseline ../TACZ-port-reference/baseline-1.20.1
The baseline is a clean checkout of SOURCE_REVISION, never the changing target tree.
Generated inventories are evidence of coverage, not evidence of runtime parity.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile

SOURCE_REVISION = "b43eb84c38e9768d8e73c8b14f0b845669704b38"
PACK = "src/main/resources/assets/tacz/custom/tacz_default_gun/"
ROOT = Path(__file__).resolve().parents[2]


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args]).decode("utf-8")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_json(data):
    # Gson accepts comments in shipped definitions. Preserve quoted // and /*.
    text = data.decode("utf-8-sig")
    text = re.sub(r'("(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/)',
                  lambda m: m[0] if m[0].startswith('"') else " ", text)
    return json.loads(text)


def archive_entries(data, prefix, depth=0):
    rows = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in sorted(archive.infolist(), key=lambda x: x.filename):
            if entry.is_dir():
                continue
            blob = archive.read(entry)
            name = prefix + "!/" + entry.filename
            rows.append({"path": name, "bytes": len(blob), "sha256": digest(blob)})
            if entry.filename.endswith((".jar", ".zip")) and depth < 4:
                rows.extend(archive_entries(blob, name, depth + 1))
    return rows


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    args = parser.parse_args()
    baseline = args.baseline.resolve()
    if git(baseline, "rev-parse", "HEAD").strip() != SOURCE_REVISION:
        parser.error("Baseline HEAD does not match SOURCE_REVISION")
    if git(baseline, "status", "--porcelain", "--untracked-files=no").strip():
        parser.error("Baseline has tracked modifications")
    tree = {entry.split("\t", 1)[1]: entry.split("\t", 1)[0].split()[2]
            for entry in git(baseline, "ls-tree", "-rz", SOURCE_REVISION).split("\0") if entry}
    paths = sorted(tree)
    files, archives, definitions, languages, registrations, fields, messages = [], [], [], [], [], [], []
    parse_errors = []
    for name in paths:
        blob = (baseline / name).read_bytes()
        # Hash canonical Git contents rather than checkout-specific CRLF changes.
        git_oid = tree[name]
        target = ROOT / name
        disposition = "retained" if target.exists() and target.read_bytes() == blob else "modified" if target.exists() else "UNACCOUNTED missing file"
        files.append({"path": name, "git_blob": git_oid, "checkout_sha256": digest(blob),
                      "bytes": len(blob), "disposition": disposition, "target": name if target.exists() else None})
        if name.endswith((".jar", ".zip")):
            archives.extend(archive_entries(blob, name))
        if name.endswith(".java"):
            source = blob.decode("utf-8-sig")
            for number, line in enumerate(source.splitlines(), 1):
                for match in re.finditer(r'\b([A-Z_]+)\.register\("([^"]+)"', line):
                    registrations.append({"registry_variable": match[1], "id": "tacz:" + match[2], "source": f"{name}:{number}"})
                for match in re.finditer(r'\b(?:put|get|contains)(?:String|Int|Float|Double|Long|Boolean|Byte|Short|Compound|List)?\("([^"]+)"', line):
                    fields.append({"key": match[1], "source": f"{name}:{number}", "classification": "persistence candidate; inspect owner"})
            if "/network/message/" in name:
                messages.append({"class": Path(name).stem, "source": name,
                                 "buffer_operations": re.findall(r'\b\w+\.(?:read|write)[A-Z]\w*\([^;]*', source),
                                 "direction": "see NetworkHandler registration", "status": "not started"})
        if name.endswith(".json"):
            try:
                obj = read_json(blob)
            except (ValueError, UnicodeError) as exc:
                parse_errors.append({"path": name, "error": str(exc)})
                continue
            if "/lang/" in name and isinstance(obj, dict):
                languages.append({"path": name, "locale": Path(name).stem, "keys": sorted(obj)})
            if name.startswith(PACK):
                parts = name[len(PACK):].split("/")
                if len(parts) > 3 and parts[0] in ("assets", "data"):
                    kind = "/".join(parts[2:4]) if parts[2] in ("index", "data", "display", "recipes", "tacz_tags") else parts[2]
                    start = 4 if parts[2] in ("index", "data", "display", "recipes", "tacz_tags") else 3
                    identifier = parts[1] + ":" + "/".join(parts[start:]).removesuffix(".json")
                    full_kind = parts[0] + "/" + kind
                    record = {"kind": full_kind, "id": identifier, "source": name}
                    # Preserve every asset by source path/hash without duplicating millions
                    # of geometry/keyframe lines already tracked in the source repository.
                    if full_kind in ("assets/geo_models", "assets/animations", "assets/player_animator", "assets/lang"):
                        record["source_sha256"] = digest(blob)
                        record["root_keys"] = sorted(obj) if isinstance(obj, dict) else []
                    else:
                        record["definition"] = obj
                    definitions.append(record)
    out = ROOT / "docs/porting/inventory"
    summary = {"source_revision": SOURCE_REVISION, "tracked_files": len(files),
               "java_files": sum(x["path"].endswith(".java") for x in files),
               "resource_files": sum("/resources/" in x["path"] for x in files),
               "default_pack_files": sum(x["path"].startswith(PACK) for x in files),
               "archive_members_including_nested": len(archives),
               "definition_counts": dict(sorted(Counter(x["kind"] for x in definitions).items())),
               "locales": sorted(set(x["locale"] for x in languages)), "json_parse_errors": parse_errors}
    write_json(out / "summary.json", summary)
    for filename, value in [("files.json", files), ("archive-members.json", archives),
                            ("definitions.json", definitions), ("languages.json", languages),
                            ("registrations.json", registrations), ("persistence-candidates.json", fields),
                            ("messages.json", messages)]:
        write_json(out / filename, value)
    # Preserve reviewed ledger decisions across inventory regeneration.
    evidence = ROOT / "docs/porting/parity-overrides.json"
    overrides = json.loads(evidence.read_text(encoding="utf-8")) if evidence.exists() else {}
    rows = []
    for item in files:
        name = item["path"]
        if name.endswith(".java"):
            rows.append(("source:" + name, name, "Preserve the complete responsibilities and public contracts of this source file."))
        elif "/resources/" in name:
            rows.append(("resource:" + name, name, "Preserve discovery, contents, references, and runtime presentation of this resource."))
        elif name.endswith((".jar", ".zip")):
            rows.append(("library:" + name, name, "Provide a compatible dependency with complete behavior and runtime closure."))
    for item in definitions:
        rows.append((item["kind"] + "/" + item["id"], item["source"], "Preserve this definition and its resolved data, assets, and behavior."))
    for item in registrations:
        rows.append(("registry:" + item["registry_variable"] + "/" + item["id"], item["source"], "Register the same identifier and preserve its behavior."))
    lines = ["# Feature and content parity", "", "Generated by `tools/porting/inventory.py`; reviewed decisions live in `parity-overrides.json`.",
             "This exhaustive file/content coverage is an audit starting point. Source-derived expectations are not observed gameplay; individual behavioral scenarios still require the subsystem audit.",
             "See [status](status.md), [audit areas](audit-areas.md), and [inventory summary](inventory/summary.json). No baseline feature is removed from scope by a build or dependency failure.", "",
             "| Feature or content ID | Source evidence | Expected behavior | Target implementation | Validation evidence | Status or blocker |", "| --- | --- | --- | --- | --- | --- |"]
    for key, source, expected in rows:
        state = overrides.get(key, {})
        cells = [key, source, state.get("expected", expected), state.get("implementation", "Baseline retained; migration pending"),
                 state.get("validation", "Source inventory only"), state.get("status", "not started")]
        lines.append("| " + " | ".join(str(x).replace("|", "\\|").replace("\n", " ") for x in cells) + " |")
    (out.parent / "feature-parity.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "json_parse_errors"}, indent=2))
    print(f"JSON parse errors requiring audit: {len(parse_errors)}")


if __name__ == "__main__":
    main()
