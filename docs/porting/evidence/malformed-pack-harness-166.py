"""Reject malformed external packs, then prove a valid pack still reloads."""

import hashlib
import json
import shutil
import time
import zipfile


def run_malformed_pack_scenarios(server, server_log, check, command, state, wait, report):
    pack_root = (server / 'tacz').resolve()
    check(pack_root.is_dir(), 'dedicated server has a gun-pack directory')
    names = ('porting_bad_meta', 'porting_missing_dependency',
             'porting_missing_meta.zip', 'porting_corrupt.zip', 'porting_recovery')
    paths = {name: pack_root / name for name in names}
    check(all(not path.exists() for path in paths.values()), 'malformed probe paths start absent')
    recipe_bytes = (json.dumps({
        'materials': [{'item': {'item': 'minecraft:copper_ingot'}, 'count': 1}],
        'result': {'type': 'ammo', 'group': 'ifp_rifle_cartridges',
                   'id': 'tacz:762x39', 'count': 1},
        'type': 'tacz:gun_smith_table_crafting',
    }, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')
    recipe_path = 'data/portprobe/recipe/ammo/recovery.json'
    recipe_id = 'portprobe:ammo/recovery'
    scenario = report['malformed_pack_scenario'] = {'input_sha256': {}, 'stages': []}

    def record(name, contents):
        scenario['input_sha256'][name] = hashlib.sha256(contents).hexdigest()

    def recipe_ids(role):
        value = state(role)
        return value['recipes'], set(value['recipe_ids'])

    check(all(recipe_ids(role)[0] == 173 and recipe_id not in recipe_ids(role)[1]
              for role in ('shooter', 'observer')), 'both clients start with default recipes')

    bad_meta = paths['porting_bad_meta']
    bad_meta.mkdir()
    malformed = b'{"namespace":\n'
    (bad_meta / 'gunpack.meta.json').write_bytes(malformed)
    record('invalid_json_metadata', malformed)
    (bad_meta / recipe_path).parent.mkdir(parents=True)
    (bad_meta / recipe_path).write_bytes(recipe_bytes)

    dependency = paths['porting_missing_dependency']
    dependency.mkdir()
    missing_dependency = b'{"namespace":"portprobe","dependencies":{"porting_absent_mod":"[1.0,)"}}\n'
    (dependency / 'gunpack.meta.json').write_bytes(missing_dependency)
    record('unmet_dependency_metadata', missing_dependency)
    (dependency / recipe_path).parent.mkdir(parents=True)
    (dependency / recipe_path).write_bytes(recipe_bytes)

    missing_meta = paths['porting_missing_meta.zip']
    with zipfile.ZipFile(missing_meta, 'w', compression=zipfile.ZIP_DEFLATED) as output:
        output.writestr(recipe_path, recipe_bytes)
    record('zip_without_metadata', missing_meta.read_bytes())
    corrupt = paths['porting_corrupt.zip']
    corrupt.write_bytes(b'not a ZIP archive\n')
    record('corrupt_zip', corrupt.read_bytes())

    before = len(server_log.read_text(encoding='utf-8', errors='replace'))
    command('tacz reload')

    def rejected_scan():
        log = server_log.read_text(encoding='utf-8', errors='replace')[before:]
        markers = ('Failed to read info json: gunpack.meta.json',
                   'Mod version mismatch: gunpack.meta.json',
                   'No gunpack.meta.json found',
                   'porting_corrupt.zip',
                   'Found 1 possible gunpack(s)')
        return log if all(marker in log for marker in markers) else None

    rejected = wait(rejected_scan, 'all malformed pack rejections and one default pack', timeout=90)
    check('porting_missing_meta.zip' in rejected, 'missing-metadata ZIP named in rejection log')
    check('porting_corrupt.zip' in rejected, 'corrupt ZIP named in rejection log')
    time.sleep(1)
    for role in ('shooter', 'observer'):
        count, ids = recipe_ids(role)
        check(count == 173 and recipe_id not in ids, role + ' rejected packs add no recipes')
    scenario['stages'].append({'stage': 'four invalid packs rejected', 'client_recipes': 173})

    recovery = paths['porting_recovery']
    recovery.mkdir()
    valid_meta = b'{"namespace":"portprobe"}\n'
    (recovery / 'gunpack.meta.json').write_bytes(valid_meta)
    (recovery / recipe_path).parent.mkdir(parents=True)
    (recovery / recipe_path).write_bytes(recipe_bytes)
    record('recovery_metadata', valid_meta)
    record('recovery_recipe', recipe_bytes)
    before = len(server_log.read_text(encoding='utf-8', errors='replace'))
    command('tacz reload')
    wait(lambda: 'Found 2 possible gunpack(s)' in
         server_log.read_text(encoding='utf-8', errors='replace')[before:], 'valid recovery pack discovery')
    wait(lambda: all(recipe_ids(role)[0] == 174 and recipe_id in recipe_ids(role)[1]
                     for role in ('shooter', 'observer')), 'recovery recipe synchronization', timeout=90)
    for role in ('shooter', 'observer'):
        count, ids = recipe_ids(role)
        check(count == 174 and recipe_id in ids, role + ' valid pack recovers after malformed neighbors')
    scenario['stages'].append({'stage': 'valid pack loaded beside four invalid packs', 'client_recipes': 174})

    for name in names:
        path = paths[name]
        check(path.resolve().is_relative_to(pack_root), name + ' remains inside disposable server')
        if path.is_dir():
            shutil.rmtree(path)
        else:
            path.unlink()
    before = len(server_log.read_text(encoding='utf-8', errors='replace'))
    command('tacz reload')
    wait(lambda: 'Found 1 possible gunpack(s)' in
         server_log.read_text(encoding='utf-8', errors='replace')[before:], 'default pack rediscovery')
    wait(lambda: all(recipe_ids(role)[0] == 173 and recipe_id not in recipe_ids(role)[1]
                     for role in ('shooter', 'observer')), 'recipe removal after recovery pack deletion', timeout=90)
    for role in ('shooter', 'observer'):
        count, ids = recipe_ids(role)
        check(count == 173 and recipe_id not in ids, role + ' default recipe set restored')
    scenario['stages'].append({'stage': 'all probe packs removed', 'client_recipes': 173})
