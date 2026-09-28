"""Exercise external directory and ZIP recipe discovery on a live dedicated server."""

import hashlib
import json
from pathlib import Path
import shutil
import zipfile


def run_external_pack_scenarios(server, check, command, state, wait, report):
    pack_root = (server / 'tacz').resolve()
    check(pack_root.is_dir(), 'dedicated server exported its default gun-pack directory')
    folder = pack_root / 'porting_directory_probe'
    archive = pack_root / 'porting_zip_probe.zip'
    check(not folder.exists() and not archive.exists(), 'external probe paths start absent')

    def recipe():
        return (json.dumps({
            'materials': [{'item': {'item': 'minecraft:copper_ingot'}, 'count': 1}],
            'result': {'type': 'ammo', 'group': 'ifp_rifle_cartridges',
                       'id': 'tacz:762x39', 'count': 1},
            'type': 'tacz:gun_smith_table_crafting',
        }, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')

    meta = b'{"namespace":"portprobe"}\n'
    folder_recipe = 'data/portprobe/recipe/ammo/from_directory.json'
    zip_recipe = 'data/portprobe/recipe/ammo/from_zip.json'
    directory_id, zip_id = 'portprobe:ammo/from_directory', 'portprobe:ammo/from_zip'

    def synced(expected):
        values = {role: state(role) for role in ('shooter', 'observer')}
        return values if all(value['recipes'] == 173 + len(expected)
                             and set(value['recipe_ids']) >= expected
                             and not ({directory_id, zip_id} - expected) & set(value['recipe_ids'])
                             for value in values.values()) else None

    def reload_and_check(expected, stage):
        check('reload' in command('tacz reload').lower(), stage + ' reload command accepted')
        values = wait(lambda: synced(expected), stage + ' recipe sync', timeout=90)
        for role, value in values.items():
            check(value['recipes'] == 173 + len(expected), role + ' ' + stage + ' exact recipe count')
            check(set(value['recipe_ids']) & {directory_id, zip_id} == expected,
                  role + ' ' + stage + ' exact external recipe IDs')
        report['external_pack_scenario']['stages'].append({
            'stage': stage, 'expected_ids': sorted(expected),
            'client_recipe_counts': {role: value['recipes'] for role, value in values.items()}})

    report['external_pack_scenario'] = {'stages': [], 'content_sha256': {}}
    check(synced(set()) is not None, 'both clients start without external recipe IDs')
    folder_path = folder / folder_recipe
    folder_path.parent.mkdir(parents=True)
    (folder / 'gunpack.meta.json').write_bytes(meta)
    folder_path.write_bytes(recipe())
    report['external_pack_scenario']['content_sha256']['directory_recipe'] = hashlib.sha256(folder_path.read_bytes()).hexdigest()
    reload_and_check({directory_id}, 'directory added')

    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as output:
        output.writestr('gunpack.meta.json', meta)
        output.writestr(zip_recipe, recipe())
    report['external_pack_scenario']['content_sha256']['zip'] = hashlib.sha256(archive.read_bytes()).hexdigest()
    reload_and_check({directory_id, zip_id}, 'ZIP added')
    reload_and_check({directory_id, zip_id}, 'both packs repeated reload')

    # This is the exact disposable path created above, below the named server root.
    check(folder.resolve().is_relative_to(pack_root), 'probe directory remains in disposable server')
    shutil.rmtree(folder)
    reload_and_check({zip_id}, 'directory removed')
    # FilePackResources keeps the selected ZIP open on Windows until the server
    # releases the pack. The controller checks archive deletion after shutdown.
