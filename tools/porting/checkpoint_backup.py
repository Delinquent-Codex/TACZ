"""Preserve port-owned artifacts and mutable test state before removing local caches.

Minecraft/Forge libraries, downloaded game assets, JDKs, native libraries and
exported default packs are reproducible dependencies. They are excluded. Every
available installed TACZ/fixture binary is retained once, keyed by SHA-256.
Already-removed historical installations are recorded as unavailable.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


REPO = Path(__file__).resolve().parents[2]
EVIDENCE = REPO / 'docs/porting/evidence'


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def git(*args, cwd=REPO):
    return subprocess.check_output(['git', *args], cwd=cwd, encoding='utf-8').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--last-run', type=int, default=171)
    args = parser.parse_args()
    reference, output = args.reference.resolve(), args.output.resolve()
    if output.exists():
        parser.error('--output must be a new directory')
    output.mkdir(parents=True)
    installations = reference / 'installations'
    binaries = {}
    installed_mods = {}
    missing = {}

    def binary(path, expected=None, label=None):
        actual = digest(path)
        if expected is not None and actual != expected:
            raise AssertionError('Recorded binary changed: ' + str(path))
        entry = binaries.setdefault(actual, {'source': path, 'size': path.stat().st_size,
                                             'names': set(), 'origins': set(), 'labels': set()})
        entry['names'].add(path.name)
        origin = ('reference/' + path.relative_to(reference).as_posix()
                  if path.is_relative_to(reference) else 'project/' + path.relative_to(REPO).as_posix())
        entry['origins'].add(origin)
        if label:
            entry['labels'].add(label)
        return actual

    for folder in (REPO / 'build/libs', REPO / 'tools/porting/runtime-fixture/build/libs',
                   REPO / 'tools/porting/server-trace-fixture/build/libs',
                   reference / 'baseline-1.20.1/build/libs',
                   installations / 'client-26.2/mods', installations / 'server-26.2/mods'):
        for path in sorted(folder.glob('*.jar')):
            binary(path, label='available build/template output')

    def recorded_binary(path, expected, label):
        if path.is_file():
            return binary(path, expected, label)
        origin = 'reference/' + path.relative_to(reference).as_posix()
        if expected in binaries:
            binaries[expected]['origins'].add(origin + ' (restorable from matching available copy)')
        else:
            missing.setdefault(expected, set()).add(origin)
        return expected

    for number in range(1, args.last_run + 1):
        run = 'multiplayer-' + str(number)
        result = read(EVIDENCE / (run + '-result.json'))
        server = installations / run / 'server/mods'
        candidate = server / 'tacz-26.2-1.1.8-hotfix-port.1.jar'
        actual = recorded_binary(candidate, result['candidate_sha256'], 'historical installed candidate')
        installed_mods[run + '/server/mods/' + candidate.name] = actual
        for name, expected in result.get('server_mods_sha256', {}).items():
            installed_mods[run + '/server/mods/' + name] = recorded_binary(server / name, expected, 'installed mod')
        for role in ('shooter', 'observer'):
            inputs = EVIDENCE / f'{run}-{role}-inputs.json'
            if inputs.is_file():
                for name, expected in read(inputs)['installed_mods_sha256'].items():
                    path = installations / run / role / 'mods' / name
                    installed_mods[run + '/' + role + '/mods/' + name] = recorded_binary(path, expected, 'installed mod')

    binary_manifest = {'scope': 'Unique TACZ and test-fixture binaries, not game dependencies',
                       'installed_mods': installed_mods, 'binaries': {},
                       'already_missing_original_artifacts': {sha: sorted(paths) for sha, paths in missing.items()
                                                              if sha not in binaries}}
    with zipfile.ZipFile(output / 'tacz-port-binaries.zip', 'x', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as archive:
        for sha, entry in sorted(binaries.items()):
            name = 'sha256/' + sha + '.jar'
            archive.write(entry['source'], name)
            binary_manifest['binaries'][sha] = {
                'archive_path': name, 'bytes': entry['size'],
                'names': sorted(entry['names']), 'origins': sorted(entry['origins']),
                'labels': sorted(entry['labels']),
            }
        archive.writestr('binary-manifest.json', json.dumps(binary_manifest, indent=2) + '\n')
    print('Preserved', len(binaries), 'unique TACZ/fixture/build binaries', flush=True)

    records = {}
    excluded = []
    recovery = output / 'tacz-port-runtime-state.zip'
    with zipfile.ZipFile(recovery, 'x', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as archive:
        def add(path, name):
            if path.is_symlink():
                raise AssertionError('Unexpected symlink in mutable recovery data: ' + str(path))
            if name in records:
                raise AssertionError('Duplicate recovery entry: ' + name)
            raw = path.read_bytes()
            original = hashlib.sha256(raw).hexdigest()
            sanitized = False
            if path.name == 'server.properties':
                text = raw.decode('utf-8')
                text, changes = re.subn(r'(?m)^rcon\.password=.*$', 'rcon.password=CHANGE_ME_ON_RESTORE', text)
                raw = text.encode('utf-8')
                sanitized = bool(changes)
            archive.writestr(name, raw)
            records[name] = {'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)}
            if sanitized:
                records[name].update({'sanitized': 'RCON password replaced', 'original_sha256': original})

        def tree(root, prefix):
            if not root.is_dir():
                return
            for base, dirs, files in os.walk(root, followlinks=False):
                directory = Path(base)
                relative_directory = directory.relative_to(root).parts
                profile_root = not relative_directory or (len(relative_directory) == 1
                    and directory.name in ('shooter', 'observer', 'server', 'client', 'client_a', 'client_b'))
                dirs[:] = sorted(name for name in dirs if name not in {
                    'tacz_default_gun', '.gradle', '__pycache__'
                } and not (profile_root and name in ('libraries', 'assets', 'natives', 'mods'))
                    and not (directory / name).is_symlink()
                    and not (directory / name).is_junction())
                for filename in sorted(files):
                    path = directory / filename
                    relative = path.relative_to(root)
                    if 'versions' in relative.parts and path.suffix == '.jar':
                        continue
                    if path.suffix in ('.jar', '.dll', '.so', '.dylib'):
                        continue
                    if path.name == 'launcher_accounts.json':
                        continue
                    add(path, prefix + '/' + relative.as_posix())

        for installation in sorted(installations.iterdir(), key=lambda path: path.name):
            if installation.is_dir() and (installation.name in ('client-26.2', 'server-26.2')
                                           or re.fullmatch(r'multiplayer-\d+', installation.name)):
                tree(installation, 'reference/installations/' + installation.name)
        tree(REPO / 'run', 'project/run')
        tree(REPO / 'logs', 'project/logs')
        tree(REPO / 'tools/porting/runtime-fixture/run', 'project/tools/porting/runtime-fixture/run')
        tree(REPO / 'tools/porting/scope-fixture/run', 'project/tools/porting/scope-fixture/run')
        tree(reference / 'logs', 'reference/logs')
        for path in sorted(reference.iterdir()):
            if path.is_file() and path.suffix in ('.json', '.txt', '.log'):
                add(path, 'reference/' + path.name)
        for path in sorted((reference / 'downloads').iterdir()):
            if path.is_file() and path.suffix in ('.json', '.xml', '.pom'):
                add(path, 'reference/downloads/' + path.name)
        excluded = [
            'JDK installations/downloads and Gradle caches: reinstall pinned toolchain',
            'Minecraft/Forge libraries, assets, native files and game JARs: recreate official profiles',
            'Exported tacz_default_gun copies: regenerate from the matching archived TACZ JAR',
            'Derived compiled classes/reports and extracted dependency sources: rebuild/redownload',
            'Version-controlled source/evidence: preserved in Git and the checkpoint Git bundle',
            'RCON passwords: replaced in archived server.properties files; launcher accounts omitted',
        ]
        archive.writestr('runtime-state-manifest.json', json.dumps({
            'scope': 'Mutable local test worlds, controls, configuration, logs and metadata',
            'files': records, 'excluded_reproducible_or_sensitive_data': excluded,
        }, indent=2) + '\n')
    print('Preserved', len(records), 'mutable runtime/reference files', flush=True)

    for path in sorted((REPO / 'build/libs').glob('*.jar')):
        shutil.copy2(path, output / path.name)
    prompt = reference.parent / 'TACZ-26.2-Full-Port-Prompt.md'
    shutil.copy2(prompt, REPO / 'docs/porting/full-port-prompt.md')
    assets = {path.name: {'bytes': path.stat().st_size, 'sha256': digest(path)}
              for path in sorted(output.iterdir()) if path.is_file()}
    if any(entry['bytes'] >= 2 * 1024 ** 3 for entry in assets.values()):
        raise AssertionError('An archive exceeds the GitHub release asset limit; split before upload')
    summary = {
        'date': '2026-09-29', 'purpose': 'User-requested GitHub checkpoint before deleting local folders',
        'port_status': 'Incomplete; pending third-person probe is syntax-checked, not runtime-tested',
        'source_parent': git('rev-parse', 'HEAD'),
        'baseline_commit': git('rev-parse', 'HEAD', cwd=reference / 'baseline-1.20.1'),
        'runs_with_preserved_result_reports': list(range(1, args.last_run + 1)),
        'runs_with_available_raw_installations': [number for number in range(1, args.last_run + 1)
                                                 if (installations / ('multiplayer-' + str(number))).is_dir()],
        'unique_binary_count': len(binaries), 'mutable_file_count': len(records),
        'installed_mod_path_count': len(installed_mods),
        'already_missing_binary_versions': len(binary_manifest['already_missing_original_artifacts']),
        'archives': assets, 'excluded_reproducible_or_sensitive_data': excluded,
        'candidate_sha256': digest(output / 'tacz-26.2-1.1.8-hotfix-port.1.jar'),
    }
    write(REPO / 'docs/porting/checkpoint-backup-manifest.json', summary)
    print(json.dumps({'archive_sizes': {name: value['bytes'] for name, value in assets.items()}}, indent=2), flush=True)


if __name__ == '__main__':
    main()
