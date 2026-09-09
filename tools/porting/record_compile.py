#!/usr/bin/env python3
"""Record a completed target compile log and the current, unchanged build inputs."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("log", type=Path)
parser.add_argument("--command", required=True)
args = parser.parse_args()
log = args.log.read_text(encoding="utf-8", errors="replace")
if "BUILD FAILED" not in log and "BUILD SUCCESSFUL" not in log:
    parser.error("The log is not complete")
pattern = re.compile(r"^" + re.escape(str(ROOT)) + r"[\\/](src[\\/]main[\\/]java[\\/].*?):(\d+): error: (.*)$", re.M)
errors = [{"file": path.replace("\\", "/"), "line": int(line), "error": error}
          for path, line, error in pattern.findall(log)]
counts = Counter(row["file"].split("/com/tacz/guns/", 1)[-1].split("/")[0] for row in errors)
evidence = ROOT / "docs/porting/evidence"
evidence.mkdir(exist_ok=True)
shutil.copyfile(args.log, evidence / args.log.name)
inputs = {}
for file in sorted(ROOT.rglob("*")):
    if not file.is_file():
        continue
    name = file.relative_to(ROOT).as_posix()
    if name.startswith(("src/", "gradle/", "libs/")) or name in {"build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat"}:
        inputs[name] = hashlib.sha256(file.read_bytes()).hexdigest()
manifest = {"baseline_revision": "b43eb84c38e9768d8e73c8b14f0b845669704b38", "command": args.command,
            "log": args.log.name, "diagnostics": len(errors), "by_subsystem": dict(sorted(counts.items())),
            "focused_results": [line for line in log.splitlines() if "assertions" in line and ("passed:" in line or "PASS:" in line)],
            "input_sha256": inputs}
(evidence / "tested-inputs.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
(ROOT / "docs/porting/inventory/compile-errors.json").write_text(json.dumps(errors, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: value for key, value in manifest.items() if key != "input_sha256"}, indent=2))
print(f"Hashed {len(inputs)} inputs")
