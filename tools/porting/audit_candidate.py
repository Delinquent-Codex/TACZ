#!/usr/bin/env python3
"""Inspect the actual core JAR and retained optional sources; not runtime parity."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    artifact = ROOT / 'build/libs/tacz-26.2-1.1.8-hotfix-port.1.jar'
    sources = artifact.with_name(artifact.stem + '-sources.jar')
    retained = []
    blocked_prefixes = []
    with zipfile.ZipFile(sources) as archive:
        for source_set in ('kubejsCompat', 'playerAnimatorCompat'):
            for kind in ('java', 'resources'):
                directory = ROOT / 'src' / source_set / kind
                for path in sorted(directory.rglob('*')):
                    if not path.is_file():
                        continue
                    relative = path.relative_to(directory).as_posix()
                    name = f'optional/{source_set}/{relative}'
                    if archive.read(name) != path.read_bytes():
                        raise ValueError(f'Optional source differs in source archive: {name}')
                    retained.append(name)
                    if kind == 'java':
                        blocked_prefixes.append(relative.removesuffix('.java'))
    with zipfile.ZipFile(artifact) as archive:
        names = archive.namelist()
        nested = json.loads(archive.read('META-INF/jarjar/metadata.json'))
        blocked = [name for name in names if any(name == prefix + '.class' or name.startswith(prefix + '$')
                   for prefix in blocked_prefixes)]
        if blocked or 'kubejs.plugins.txt' in names:
            raise ValueError('Unavailable optional adapter/discovery found in core artifact')
        # ForgeGradle 7 configures source-set outputs here, not Gradle's default.
        compiled = ROOT / 'build/sourceSets/main'
        classes = sorted(compiled.rglob('*.class'))
        if not classes:
            raise ValueError(f'No compiled production classes in {compiled}; build first')
        for path in classes:
            name = path.relative_to(compiled).as_posix()
            if archive.read(name) != path.read_bytes():
                raise ValueError(f'Compiled main class differs in artifact: {name}')
        result = {
            'scope': 'Core artifact/source archive inspection only; see separate build and runtime evidence',
            'path': artifact.as_posix(), 'sha256': digest(artifact.read_bytes()),
            'bytes': artifact.stat().st_size, 'entries': len(names),
            'main_classes': sum(name.startswith('com/tacz/') and name.endswith('.class') for name in names),
            'compiled_main_classes_byte_identical': len(classes),
            'nested': nested, 'notices': [name for name in names if name.startswith('META-INF/licenses/')],
            'kubejs_plugin_descriptor_present': False, 'blocked_adapter_classes_present': blocked,
            'nested_sha256': {jar['path']: digest(archive.read(jar['path'])) for jar in nested['jars']},
            'retained_optional_source_entries': retained,
            'source_archive_sha256': digest(sources.read_bytes()), 'audit_script_sha256': digest(Path(__file__).read_bytes()),
        }
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: result[key] for key in ('sha256', 'bytes', 'entries', 'main_classes', 'compiled_main_classes_byte_identical')}, indent=2))


if __name__ == '__main__':
    main()
