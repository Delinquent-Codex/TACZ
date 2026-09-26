"""Replace shipped AK-47 stocks through the native refit screen and check modifiers."""

import math


FIRST = 'tacz:stock_ak12'
SECOND = 'tacz:oem_stock_light'
AMMO = 'tacz:762x39'


def run_stock_refit_scenarios(check, command, client, state, wait, server_number, report):
    result = report['stock_refit_scenario'] = {}
    roles = ('shooter', 'observer')

    def player(value):
        return value['players'].get('TaczShooter', {})

    def near(actual, expected):
        return isinstance(actual, (int, float)) and math.isclose(actual, expected, abs_tol=0.0001)

    def both(magazine, stock, label):
        def ready():
            values = {role: state(role) for role in roles}
            return values if all(player(value).get('gun') == 'tacz:ak47'
                                 and player(value).get('magazine') == magazine
                                 and player(value).get('chamber') is True
                                 and player(value).get('stock') == stock
                                 for value in values.values()) else None
        values = wait(ready, label, timeout=20)
        for role, value in values.items():
            check(player(value)['magazine'] == magazine and player(value).get('stock') == stock,
                  f'{label}: {role} sees expected stock and {magazine} rounds')
        return values

    def server_gun(magazine, stock):
        value = command('data get entity TaczShooter Inventory[{Slot:0b}]')
        check((FIRST in value) is (stock == FIRST) and (SECOND in value) is (stock == SECOND)
              and f'GunCurrentAmmoCount: {magazine}' in value and 'HasBulletInBarrel: 1b' in value,
              f'server gun stores only expected stock with magazine{magazine} and chambered round')
        server_number('Inventory[{Slot:9b}].count', 58)
        return value

    def open_refit():
        client('shooter', 'focus_window')
        wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
             'shooter window has native game-input focus', timeout=10)
        check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
              'native key opens AK-47 refit screen')
        return wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
                    and 'STOCK' in value['refit']['allowed_types']
                    and 'ads_time' in value['refit'] else None,
                    'AK-47 refit screen exposes STOCK and current modifier cache', timeout=10)

    def select_stock():
        check(client('shooter', 'refit_type_click', type='STOCK')['snapshot']['refit']['type'] == 'STOCK',
              'native STOCK button selects compatible attachment type')
        return wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1 else None,
                    'stock inventory controls appear after transform', timeout=10)

    def close_refit():
        check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
              'native key closes stock refit screen')

    def modified(stock, ads, pitch, yaw, inventory, label):
        value = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('stock') == stock
                     and near(value['refit'].get('ads_time'), ads)
                     and near(value['refit'].get('recoil_pitch'), pitch)
                     and near(value['refit'].get('recoil_yaw'), yaw)
                     and all(value['inventory_attachments'].get(item, 0) == count
                             for item, count in inventory.items()) else None,
                     label + ' modifier and inventory sync', timeout=15)
        check(near(value['refit']['ads_time'], ads) and near(value['refit']['recoil_pitch'], pitch)
              and near(value['refit']['recoil_yaw'], yaw), label + ' shipped ADS/recoil modifiers evaluated')
        check(value['refit']['unload_buttons'] == 1, label + ' native unload control visible')
        return value

    initial = both(29, None, 'unmodified AK-47')
    check(initial['shooter']['screen'] == 'null' and initial['shooter']['game_mode'] == 'SURVIVAL'
          and initial['shooter']['inventory_ammo'].get(AMMO) == 58,
          'stock probe begins with loaded survival AK-47 and reserve58')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47 for stock refit')
    for slot, attachment in ((1, FIRST), (2, SECOND)):
        check('Replaced a slot' in command('item replace entity TaczShooter inventory.' + str(slot)
              + ' with tacz:attachment[minecraft:custom_data={AttachmentId:"' + attachment + '"}]'),
              f'server equips shipped {attachment} in inventory slot {slot}')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(FIRST) == 1
         and value['inventory_attachments'].get(SECOND) == 1 else None,
         'both shipped stocks synchronize into inventory', timeout=10)

    ready = open_refit()
    base_ads, base_pitch, base_yaw = (ready['refit'][key] for key in
                                       ('ads_time', 'recoil_pitch', 'recoil_yaw'))
    check(base_ads > 0 and base_pitch > 0 and base_yaw > 0,
          'AK-47 has positive baseline ADS time and recoil values')
    check(state('observer')['screen'] == 'null', 'observer remains in world during stock refit')
    selected = select_stock()
    check({10, 11}.issubset(set(selected['refit']['attachment_slots'])),
          'both shipped stocks have visible native inventory controls')
    client('shooter', 'refit_install_click', attachment=FIRST)
    first = modified(FIRST, base_ads + 0.01, base_pitch * 0.8, base_yaw * 0.8,
                     {FIRST: 0, SECOND: 1}, 'AK-12 stock install')
    first_both = both(29, FIRST, 'AK-12 stock installed')
    result['first'] = {'screen': first, 'observer': first_both['observer'],
                       'server_gun': server_gun(29, FIRST)}

    close_refit()
    open_refit()
    selected = select_stock()
    check(11 in selected['refit']['attachment_slots'], 'replacement stock remains a visible native control')
    client('shooter', 'refit_install_click', attachment=SECOND)
    replaced = modified(SECOND, base_ads - 0.02, base_pitch * 0.85, base_yaw * 0.8,
                        {FIRST: 1, SECOND: 0}, 'light stock replacement')
    replaced_both = both(29, SECOND, 'light stock replacement')
    result['replaced'] = {'screen': replaced, 'observer': replaced_both['observer'],
                          'server_gun': server_gun(29, SECOND),
                          'baseline': {'ads_time': base_ads, 'recoil_pitch': base_pitch,
                                       'recoil_yaw': base_yaw}}

    close_refit()
    before = {role: state(role) for role in roles}
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'AK-47 fires with replacement stock')
    fired = both(28, SECOND, 'AK-47 shot with replacement stock')
    for role, value in fired.items():
        check(value['fires'].get('TaczShooter', 0) == before[role]['fires'].get('TaczShooter', 0) + 1,
              f'{role} sees one fire event with replacement stock')
        check(value['projectiles'] == before[role]['projectiles'] + 1,
              f'{role} sees one projectile with replacement stock')
    result['fired'] = {'states': fired, 'server_gun': server_gun(28, SECOND)}

    open_refit()
    select_stock()
    wait(lambda: value if (value := state('shooter'))['refit']['unload_buttons'] == 1 else None,
         'replacement stock native unload button ready', timeout=10)
    client('shooter', 'refit_unload_click')
    unloaded = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('stock') is None
                    and near(value['refit'].get('ads_time'), base_ads)
                    and near(value['refit'].get('recoil_pitch'), base_pitch)
                    and near(value['refit'].get('recoil_yaw'), base_yaw)
                    and value['inventory_attachments'].get(FIRST) == 1
                    and value['inventory_attachments'].get(SECOND) == 1 else None,
                    'native unload restores both stocks to inventory and base modifier cache', timeout=15)
    unloaded_both = both(28, None, 'all stocks unloaded')
    result['unloaded'] = {'screen': unloaded, 'observer': unloaded_both['observer'],
                          'server_gun': server_gun(28, None)}
    close_refit()
