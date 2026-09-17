#!/usr/bin/env python3
"""Run the packaged fixture in a separate official Forge Windows client installation.

Uses only the installed release profile and checksum-verified official libraries
and assets. Never reads launcher accounts or changes the normal game directory.
The explicit offline test identity is for singleplayer tests, not online login.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]


def digest(path, algorithm='sha256'):
    return hashlib.new(algorithm, path.read_bytes()).hexdigest()


def verified_file(spec, destination, caches=()):
    if destination.is_file() and digest(destination, 'sha1') == spec['sha1']:
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    for cache in caches:
        if cache.is_file() and digest(cache, 'sha1') == spec['sha1']:
            shutil.copyfile(cache, destination)
            return
    if not spec.get('url'):
        raise ValueError(f'Missing installer-produced file: {destination}')
    request = urllib.request.Request(spec['url'], headers={'User-Agent': 'TACZ local port verification'})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    if hashlib.sha1(data).hexdigest() != spec['sha1'] or len(data) != spec['size']:
        raise ValueError(f'Official download checksum/size mismatch: {spec["url"]}')
    destination.write_bytes(data)


def allowed(rules):
    decision = not rules
    for rule in rules:
        system = rule.get('os', {})
        if any(rule.get('features', {}).values()):
            continue  # No demo, quick-play or other launcher feature flags.
        if system.get('name', 'windows') != 'windows':
            continue
        if 'arch' in system and system['arch'] not in ('x86_64', 'amd64'):
            continue
        if 'version' in system and not re.search(system['version'], platform.version()):
            continue
        if 'versionRange' in system:
            raise ValueError('Unhandled OS versionRange in required launch arguments')
        decision = rule['action'] == 'allow'
    return decision


def flatten(arguments, variables):
    result = []
    for entry in arguments:
        if isinstance(entry, dict):
            if not allowed(entry.get('rules', [])):
                continue
            entry = entry['value']
        for value in entry if isinstance(entry, list) else [entry]:
            result.append(re.sub(r'\$\{([^}]+)\}', lambda match: variables[match[1]], value))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--installation', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--backend', choices=['OPENGL', 'VULKAN'], default='OPENGL')
    parser.add_argument('--clean-world', help='Load an existing isolated world with only TACZ installed, then request normal window closure')
    parser.add_argument('--evidence-prefix', required=True, type=Path)
    args = parser.parse_args()
    if os.name != 'nt' or platform.machine().lower() not in ('amd64', 'x86_64'):
        parser.error('This harness is validated only for Windows x64')
    installation = args.installation.resolve()
    if args.clean_world and (Path(args.clean_world).name != args.clean_world
                            or not (installation / 'saves' / args.clean_world / 'level.dat').is_file()):
        parser.error('--clean-world must name an existing world inside this test installation')
    normal_game = Path(os.environ['APPDATA']) / '.minecraft'
    if installation == normal_game.resolve() or normal_game.resolve() in installation.parents:
        parser.error('Use a separate test installation, never the normal game directory')
    profile_path = installation / 'versions/26.2-forge-65.1.0/26.2-forge-65.1.0.json'
    vanilla_path = installation / 'versions/26.2/26.2.json'
    forge = json.loads(profile_path.read_text())
    vanilla = json.loads(vanilla_path.read_text())
    if forge['inheritsFrom'] != '26.2' or vanilla['javaVersion']['majorVersion'] != 25:
        raise ValueError('Unexpected installed profile or target Java version')
    # Forge overrides the inherited version of the same group/artifact/classifier.
    libraries = {}
    for lib in vanilla['libraries'] + forge['libraries']:
        if allowed(lib.get('rules', [])):
            coordinate = lib['name'].split(':')
            libraries[tuple(coordinate[:2] + coordinate[3:])] = lib['downloads']['artifact']
    paths = []
    for spec in libraries.values():
        path = installation / 'libraries' / spec['path']
        verified_file(spec, path, [normal_game / 'libraries' / spec['path']])
        paths.append(path)
    client = installation / 'versions/26.2/26.2.jar'
    verified_file(vanilla['downloads']['client'], client)
    paths.append(client)
    print(f'Verified {len(libraries)} installed libraries and vanilla client', flush=True)
    assets = installation / 'assets'
    index_path = assets / 'indexes' / (vanilla['assetIndex']['id'] + '.json')
    verified_file(vanilla['assetIndex'], index_path, [normal_game / 'assets/indexes' / index_path.name])
    index = json.loads(index_path.read_text())

    def asset(entry):
        hash_value = entry['hash']
        relative = f'objects/{hash_value[:2]}/{hash_value}'
        spec = {'sha1': hash_value, 'size': entry['size'],
                'url': f'https://resources.download.minecraft.net/{hash_value[:2]}/{hash_value}'}
        verified_file(spec, assets / relative, [normal_game / 'assets' / relative])
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        list(executor.map(asset, index['objects'].values()))
    print(f'Verified {len(index["objects"])} asset entries', flush=True)
    mods = installation / 'mods'
    mods.mkdir(exist_ok=True)
    candidate = ROOT / 'build/libs/tacz-26.2-1.1.8-hotfix-port.1.jar'
    fixture = ROOT / 'tools/porting/runtime-fixture/build/libs/tacz-full-runtime-fixture-1.0.jar'
    expected = {candidate.name, fixture.name}
    if any(path.name not in expected for path in mods.glob('*.jar')):
        raise ValueError('Unexpected mod in isolated installed-client directory')
    if args.clean_world:
        (mods / fixture.name).unlink(missing_ok=True)  # Only our copied test mod, never the source JAR.
    for path in (candidate,) if args.clean_world else (candidate, fixture):
        shutil.copyfile(path, mods / path.name)
    natives = installation / 'natives'
    natives.mkdir(exist_ok=True)
    variables = {
        'auth_player_name': 'TaczFixture', 'version_name': forge['id'],
        'game_directory': installation.as_posix(), 'assets_root': assets.as_posix(),
        'assets_index_name': vanilla['assetIndex']['id'],
        'auth_uuid': uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:TaczFixture').digest(), version=3).hex,
        'auth_access_token': '0', 'clientid': '0', 'auth_xuid': '0', 'version_type': 'release',
        'natives_directory': natives.as_posix(), 'launcher_name': 'tacz-installed-fixture',
        'launcher_version': '1', 'classpath': os.pathsep.join(path.as_posix() for path in paths),
    }
    command_args = ['-Xmx4G', '-Deventbus.api.strictRuntimeChecks=true', '-Dmixin.debug.countInjections=true']
    if not args.clean_world:
        command_args += ['-Dtacz.fixture.gameplay=true', '-Dtacz.fixture.environment=official installed Forge client']
    command_args += flatten(vanilla['arguments']['jvm'] + forge['arguments']['jvm'], variables)
    command_args += [forge['mainClass']]
    command_args += flatten(vanilla['arguments']['game'] + forge['arguments']['game'], variables)
    command_args += ['--width', '854', '--height', '480', '--graphicsBackend', args.backend]
    if args.clean_world:
        command_args += ['--quickPlaySingleplayer', args.clean_world]
    argfile = installation / 'fixture-launch.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in command_args), encoding='utf-8')
    prefix = args.evidence_prefix.resolve()
    prefix.parent.mkdir(parents=True, exist_ok=True)
    fixture_inputs = [path for path in (ROOT / 'tools/porting/runtime-fixture/src').rglob('*') if path.is_file()]
    fixture_inputs += [ROOT / 'tools/porting/runtime-fixture' / name for name in ('build.gradle', 'settings.gradle', 'gradle.properties')]
    report = {'scope': ('official installed Forge release client; only TACZ in mods; existing-world load and ordinary window closure'
                        if args.clean_world else 'official installed Forge release client with full TACZ JAR plus packaged test mod'),
              'backend_requested': args.backend, 'command': [str(args.java.resolve()), '@' + str(argfile)],
              'arguments': command_args, 'offline_test_identity': True,
              'installed_mods_sha256': {path.name: digest(path) for path in mods.glob('*.jar')},
              'input_sha256': {str(path): digest(path) for path in
                [Path(__file__), profile_path, vanilla_path, index_path, candidate, argfile]
                + ([] if args.clean_world else [fixture] + fixture_inputs)},
              'classpath_sha256': {str(path): digest(path) for path in paths},
              'asset_entries_verified': len(index['objects']), 'result': 'failed', 'forced_stop': False}
    prefix.with_name(prefix.name + '-inputs.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    result_path = installation / 'runtime-fixture-result.json'
    result_path.unlink(missing_ok=True)
    try:
        with prefix.with_suffix('.log').open('wb') as log:
            started = time.monotonic()
            process = subprocess.Popen(report['command'], cwd=installation, stdout=log, stderr=subprocess.STDOUT)
            try:
                if args.clean_world:
                    deadline = time.monotonic() + 120
                    joined = None
                    while process.poll() is None and time.monotonic() < deadline:
                        content = prefix.with_suffix('.log').read_text(encoding='utf-8', errors='replace')
                        if 'TaczFixture joined the game' in content:
                            if joined is None:
                                joined = time.monotonic()
                            if time.monotonic() - joined >= 10:
                                break
                        time.sleep(0.25)
                    if joined is None or time.monotonic() - joined < 10 or process.poll() is not None:
                        raise AssertionError('Clean client did not remain in the saved world for ten seconds')
                    # Close only the JVM this harness owns, using its ordinary window
                    # close request. Minecraft performs its own disconnect/save/shutdown.
                    close = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command',
                                            f'(Get-Process -Id {process.pid}).CloseMainWindow()'],
                                           capture_output=True, text=True, timeout=10)
                    report['normal_window_close_requested'] = close.returncode == 0 and close.stdout.strip() == 'True'
                    if not report['normal_window_close_requested']:
                        raise AssertionError('Could not request ordinary close of the owned client window')
                report['exit_code'] = process.wait(timeout=180)
            except Exception:
                if process.poll() is None:
                    report['forced_stop'] = True
                    process.kill()
                    report['exit_code'] = process.wait(timeout=15)
                raise
            report['process_elapsed_seconds'] = round(time.monotonic() - started, 3)
        if args.clean_world:
            content = prefix.with_suffix('.log').read_text(encoding='utf-8', errors='replace')
            backend = re.search(r'Using graphics backend (\w+),', content)
            stop = content.rfind('Stopping server')
            if (report['exit_code'] != 0 or stop < 0 or 'All dimensions are saved' not in content[stop:]
                    or not backend or backend[1].upper() != args.backend):
                raise AssertionError('Clean client failed backend, normal process exit or post-stop save checks')
            if 'TACZ_RUNTIME_RESULT' in content or report['installed_mods_sha256'].keys() != {candidate.name}:
                raise AssertionError('Unexpected fixture or extra mod in clean client')
            report['world_joined'] = args.clean_world
            report['result'] = 'passed'
            return
        if result_path.exists():
            report['fixture_result'] = json.loads(result_path.read_text())
        result = report.get('fixture_result', {})
        if report['exit_code'] != 0 or result.get('status') != 'passed' or result.get('assertions') != 66:
            raise AssertionError('Installed client failed process exit or gameplay fixture checks')
        if result.get('backend', '').upper() != args.backend:
            raise AssertionError('Installed client used the wrong backend')
        report['result'] = 'passed'
    except Exception as error:
        report['failure'] = str(error)
        raise
    finally:
        log = prefix.with_suffix('.log')
        if log.exists():
            report['log_sha256'] = digest(log)
        prefix.with_name(prefix.name + '-result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({key: report.get(key) for key in ('result', 'exit_code', 'forced_stop', 'failure')}, indent=2))


if __name__ == '__main__':
    main()
