"""Cross-check selected server shoot results, projectile identities and client counts.

This analyzes captured diagnostic runs; it does not relabel a failed run as passed
or establish a baseline-runtime comparison.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/porting/evidence'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', nargs='+', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = {'scope': __doc__, 'analysis_tool_sha256': digest(Path(__file__)), 'runs': {}}
    for number in args.runs:
        prefix = 'multiplayer-' + str(number)
        path = EVIDENCE / (prefix + '-server-trace.jsonl')
        game = json.loads((EVIDENCE / (prefix + '-result.json')).read_text(encoding='utf-8'))
        assert digest(path) == game['server_trace_sha256'], prefix + ' trace hash mismatch'
        rows = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines()]
        shots = [row for row in rows if row['kind'] == 'shoot_result']
        joins = [row for row in rows if row['kind'] == 'projectile_join']
        leaves = [row for row in rows if row['kind'] == 'projectile_leave']
        heat = game['firing_scenarios']['minigun_heat_lock']['prediction']
        accepted = [row for row in shots if row.get('gun') == 'tacz:minigun' and row['result'] == 'SUCCESS']
        natural_rejections = [row for row in shots if row.get('gun') == 'tacz:minigun'
                              and row['result'] != 'SUCCESS'
                              and row['request_timestamp'] not in (-10_000, 1_000_000_000)]
        bullets = [row for row in joins if row['gun'] == 'tacz:minigun']
        assert len(accepted) == heat['authoritative_projectiles'] + 1, prefix + ' server accepted minigun count'
        assert len(bullets) == len(accepted), prefix + ' server minigun bullet identity count'
        assert heat['remote_trigger_events'] == heat['authoritative_projectiles'], prefix + ' remote trigger count'
        assert heat['successful_client_calls'] == heat['authoritative_projectiles'] + len(natural_rejections), prefix + ' local prediction/rejection count'
        if 'invalid_timestamp_-10000' in game['firing_scenarios']:
            prior = [row for row in shots if row['request_timestamp'] == -10_000]
            assert len(prior) == 1 and prior[0]['result'] == 'COOL_DOWN', prefix + ' negative timestamp result'
        if 'invalid_timestamp_1000000000' in game['firing_scenarios']:
            future = [row for row in shots if row['request_timestamp'] == 1_000_000_000]
            assert len(future) == 1 and future[0]['result'] == 'NETWORK_FAIL', prefix + ' future timestamp result'
        result_counts = Counter(row['result'] for row in shots)
        join_ids = [row['uuid'] for row in joins]
        report['runs'][str(number)] = {
            'gameplay_result': game['result'], 'gameplay_failure': game.get('failure'),
            'checks': len(game['checks']), 'server_result_counts': dict(sorted(result_counts.items())),
            'minigun_client_requests': heat['successful_client_calls'],
            'minigun_server_accepted': len(accepted), 'minigun_server_bullets': len(bullets),
            'minigun_natural_rejections': dict(sorted(Counter(row['result'] for row in natural_rejections).items())),
            'server_projectile_joins': len(joins), 'server_unique_projectiles': len(set(join_ids)),
            'server_projectile_leaves': len(leaves),
            'server_leaves_without_removal_reason': sum(row['removal_reason'] == 'null' for row in leaves),
            'trace_sha256': digest(path),
        }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Checked server/client counts for', len(report['runs']), 'diagnostic runs')


if __name__ == '__main__':
    main()
