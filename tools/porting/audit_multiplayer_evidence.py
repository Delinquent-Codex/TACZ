"""Verify captured multiplayer input/artifact/log hashes, not gameplay assertions.

Requires the preserved disposable installations. Native options.txt changes at
shutdown; its prelaunch hash is retained but is not a post-run equality check.
Historical source/controller snapshots must match their recorded bytes exactly.
"""
import argparse
from functools import lru_cache
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/porting/evidence'


@lru_cache(maxsize=None)
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def verify(path, expected):
    if not path.is_file() or digest(path) != expected:
        raise AssertionError('Missing or changed evidence input: ' + str(path))


def verify_visual_images(images, root=ROOT):
    """Verify repository-relative PNG evidence without allowing paths outside evidence."""
    if not isinstance(images, dict):
        raise AssertionError('Visual evidence images must be a path-to-SHA256 mapping')
    evidence = (root / 'docs/porting/evidence').resolve()
    for name, expected in images.items():
        if not isinstance(name, str):
            raise AssertionError('Visual evidence image path must be a string')
        relative = Path(name)
        if (relative.is_absolute() or relative.drive or '..' in relative.parts
                or relative.parts[:3] != ('docs', 'porting', 'evidence')
                or relative.suffix != '.png'):
            raise AssertionError('Visual evidence PNG must be under docs/porting/evidence: ' + name)
        path = (root / relative).resolve()
        if not path.is_relative_to(evidence):
            raise AssertionError('Visual evidence PNG escapes docs/porting/evidence: ' + name)
        if not isinstance(expected, str) or not path.is_file() or digest(path) != expected:
            raise AssertionError('Missing or changed visual evidence PNG: ' + name)
    return len(images)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--first', required=True, type=int)
    parser.add_argument('--last', required=True, type=int)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--installations', type=Path, default=ROOT.parent / 'TACZ-port-reference/installations')
    parser.add_argument('--tested-inputs', type=Path, default=EVIDENCE / 'tested-inputs.json')
    args = parser.parse_args()
    if args.first < 1 or args.last < args.first:
        parser.error('Require 1 <= first <= last')
    inputs = read(args.tested_inputs)['input_sha256']
    for name, expected in inputs.items():
        verify(ROOT / name, expected)
    archives = {digest(path): str(path.relative_to(ROOT)) for pattern in ('*-harness-*.py', '*.java', 'server-trace-config-*.json')
                for path in EVIDENCE.glob(pattern)}

    def resolve(name, expected, mods=None):
        path = Path(name)
        if path.is_file() and digest(path) == expected:
            return str(path)
        if expected in archives:
            return archives[expected]
        if mods is not None and (mods / path.name).is_file() and digest(mods / path.name) == expected:
            return str(mods / path.name)
        raise AssertionError('Missing exact current/historical input: ' + name)

    report = {'scope': __doc__, 'root_inputs_verified': len(inputs), 'tested_inputs': str(args.tested_inputs),
              'audit_tool_sha256': digest(Path(__file__)), 'runs': {}}
    for number in range(args.first, args.last + 1):
        run = 'multiplayer-' + str(number)
        game = args.installations / run
        data = read(EVIDENCE / (run + '-result.json'))
        verify(EVIDENCE / (run + '-server.log'), data['server_log_sha256'])
        for name, expected in data['server_libraries_sha256'].items():
            verify(game / 'server' / name, expected)
        verify(game / 'server/forge-26.2-65.1.0-shim.jar', data['server_shim_sha256'])
        verify(game / 'server/mods/tacz-26.2-1.1.8-hotfix-port.1.jar', data['candidate_sha256'])
        for name, expected in data.get('server_mods_sha256', {}).items():
            verify(game / 'server/mods' / name, expected)
        if 'server_trace_sha256' in data:
            verify(EVIDENCE / (run + '-server-trace.jsonl'), data['server_trace_sha256'])
        entry = {'result': data['result'], 'checks': len(data['checks']), 'failure': data.get('failure'),
                 'elapsed_seconds': data['elapsed_seconds'], 'server_exit': data['server_exit_code'],
                 'forced_stop': data['forced_stop'], 'server_log_hash_matches': True,
                 'server_libraries_verified': len(data['server_libraries_sha256']),
                 'controller_inputs': {name: resolve(name, expected, game / 'server/mods') for name, expected in data['input_sha256'].items()},
                 'clients': {}}
        if 'visual_scenario' in data:
            visual = data['visual_scenario']
            if not isinstance(visual, dict):
                raise AssertionError(run + ' visual_scenario must be a mapping')
            if 'images' in visual:
                entry['visual_images_verified'] = verify_visual_images(visual['images'])
        for role in data['client_commands']:
            client = read(EVIDENCE / f'{run}-{role}-result.json')
            before = read(EVIDENCE / f'{run}-{role}-inputs.json')
            verify(EVIDENCE / f'{run}-{role}.log', client['log_sha256'])
            resolved = {name: resolve(name, expected, game / role / 'mods')
                        for name, expected in before['input_sha256'].items() if Path(name).name != 'options.txt'}
            for name, expected in before['installed_mods_sha256'].items():
                verify(game / role / 'mods' / name, expected)
            for name, expected in before['classpath_sha256'].items():
                verify(Path(name), expected)
            entry['clients'][role] = {'result': client['result'], 'exit': client['exit_code'],
                'forced_stop': client['forced_stop'], 'log_hash_matches': True,
                'inputs': resolved, 'installed_mods_verified': len(before['installed_mods_sha256']),
                'classpath_files_verified': len(before['classpath_sha256']),
                'options_note': 'Prelaunch hash retained; native shutdown rewrite excluded from equality.'}
        report['runs'][str(number)] = entry
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Verified', len(inputs), 'root inputs and', len(report['runs']), 'captured multiplayer runs')


if __name__ == '__main__':
    main()
