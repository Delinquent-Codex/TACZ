"""Compare a locally built baseline JAR with the published reference by member bytes."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument("built", type=Path)
parser.add_argument("published", type=Path)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()

def manifest(path):
    with ZipFile(path) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in sorted(archive.namelist()) if not name.endswith("/")}

left, right = manifest(args.built), manifest(args.published)
result = {
    "source_revision": "b43eb84c38e9768d8e73c8b14f0b845669704b38",
    "published_url": "https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/8141310",
    "built": {"filename": args.built.name, "sha256": hashlib.file_digest(args.built.open("rb"), "sha256").hexdigest(), "members": len(left)},
    "published": {"filename": args.published.name, "sha256": hashlib.file_digest(args.published.open("rb"), "sha256").hexdigest(), "members": len(right)},
    "identical_members": sorted(name for name in left.keys() & right.keys() if left[name] == right[name]),
    "different_members": [{"name": name, "built_sha256": left[name], "published_sha256": right[name]}
                          for name in sorted(left.keys() & right.keys()) if left[name] != right[name]],
    "built_only": sorted(left.keys() - right.keys()),
    "published_only": sorted(right.keys() - left.keys()),
}
args.out.parent.mkdir(parents=True, exist_ok=True)
args.out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: len(value) if isinstance(value, list) else value for key, value in result.items()}, indent=2))
