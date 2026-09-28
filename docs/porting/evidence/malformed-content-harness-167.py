"""Exercise a malformed recipe inside an accepted external gun pack."""

import hashlib
import json
import shutil


def run_malformed_content_scenarios(server, server_log, check, command, state, wait, report):
    pack_root = (server / 'tacz').resolve()
    check(pack_root.is_dir(), 'dedicated server has a gun-pack directory')
    folder = pack_root / 'porting_malformed_content_probe'
    check(not folder.exists(), 'malformed-content probe path starts absent')

    valid_id = 'portprobe:ammo/content_valid'
    malformed_id = 'portprobe:ammo/content_malformed'
    recipe_dir = folder / 'data' / 'portprobe' / 'recipe' / 'ammo'
    malformed_path = recipe_dir / 'content_malformed.json'
    scenario = report['malformed_content_scenario'] = {'input_sha256': {}, 'stages': []}

    def record(name, contents):
        scenario['input_sha256'][name] = hashlib.sha256(contents).hexdigest()
        return contents

    def recipe(count):
        return (json.dumps({
            'materials': [{'item': {'item': 'minecraft:copper_ingot'}, 'count': 1}],
            'result': {'type': 'ammo', 'group': 'ifp_rifle_cartridges',
                       'id': 'tacz:762x39', 'count': count},
            'type': 'tacz:gun_smith_table_crafting',
        }, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')

    def clients_at(expected):
        values = {role: state(role) for role in ('shooter', 'observer')}
        return values if all(value['recipes'] == 173 + len(expected)
                             and set(value['recipe_ids']) & {valid_id, malformed_id} == expected
                             for value in values.values()) else None

    def reload_and_check(expected, stage, expected_pack_count):
        before = len(server_log.read_text(encoding='utf-8', errors='replace'))
        check('reload' in command('tacz reload').lower(), stage + ' reload command accepted')
        wait(lambda: f'Found {expected_pack_count} possible gunpack(s)' in
             server_log.read_text(encoding='utf-8', errors='replace')[before:],
             stage + ' pack discovery', timeout=90)
        values = wait(lambda: clients_at(expected), stage + ' recipe sync', timeout=90)
        for role, value in values.items():
            check(value['recipes'] == 173 + len(expected), role + ' ' + stage + ' exact recipe count')
            check(set(value['recipe_ids']) & {valid_id, malformed_id} == expected,
                  role + ' ' + stage + ' exact probe recipe IDs')
        scenario['stages'].append({
            'stage': stage, 'expected_ids': sorted(expected),
            'client_recipe_counts': {role: value['recipes'] for role, value in values.items()}})
        return before

    check(clients_at(set()) is not None, 'both clients start with 173 default recipes and no probe IDs')
    recipe_dir.mkdir(parents=True)
    metadata = record('metadata', b'{"namespace":"portprobe"}\n')
    valid = record('valid_recipe', recipe(1))
    malformed = record('malformed_recipe', recipe(0))
    (folder / 'gunpack.meta.json').write_bytes(metadata)
    (recipe_dir / 'content_valid.json').write_bytes(valid)
    malformed_path.write_bytes(malformed)

    before = reload_and_check({valid_id}, 'valid and malformed recipes in accepted pack', 2)
    log = wait(lambda: server_log.read_text(encoding='utf-8', errors='replace')[before:]
               if malformed_id in server_log.read_text(encoding='utf-8', errors='replace')[before:]
               else None, 'malformed recipe named in server log', timeout=90)
    check('Result count must be positive' in log,
          'server reports the TACZ result-count validation error')

    repaired = record('repaired_recipe', recipe(1))
    malformed_path.write_bytes(repaired)
    reload_and_check({valid_id, malformed_id}, 'malformed recipe repaired', 2)

    check(folder.resolve().is_relative_to(pack_root), 'probe directory remains in disposable server')
    shutil.rmtree(folder)
    reload_and_check(set(), 'accepted pack removed', 1)
