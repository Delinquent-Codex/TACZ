"""Refit the shipped AK-47 silencer and verify its live cache and fire path."""

import re


SILENCER = 'tacz:muzzle_silencer_phantom_s1'
AMMO = 'tacz:762x39'


def run_muzzle_refit_scenarios(check, command, client, state, wait, server_number, report):
    result = report['muzzle_refit_scenario'] = {}
    roles = ('shooter', 'observer')

    def shooter(value):
        return value['players'].get('TaczShooter', {})

    def both(magazine, muzzle, label):
        def ready():
            values = {role: state(role) for role in roles}
            return values if all(shooter(value).get('gun') == 'tacz:ak47'
                                 and shooter(value).get('magazine') == magazine
                                 and shooter(value).get('chamber') is True
                                 and shooter(value).get('muzzle') == muzzle
                                 for value in values.values()) else None
        values = wait(ready, label, timeout=20)
        for role, value in values.items():
            check(shooter(value)['magazine'] == magazine and shooter(value).get('muzzle') == muzzle,
                  f'{label}: {role} sees the muzzle and {magazine} rounds')
        return values

    def server_gun(magazine, installed):
        value = command('data get entity TaczShooter Inventory[{Slot:0b}]')
        check((SILENCER in value) is installed and f'GunCurrentAmmoCount: {magazine}' in value
              and 'HasBulletInBarrel: 1b' in value,
              f'server gun has expected muzzle, chamber and magazine{magazine}')
        return value

    def reserve():
        reply = command('clear TaczShooter tacz:ammo 0')
        match = re.search(r'Found (\d+) matching item\(s\)', reply)
        check(match is not None and int(match[1]) == 58, 'server retains exactly 58 rifle reserve rounds')

    initial = both(29, None, 'unmodified AK-47')
    check(initial['shooter']['screen'] == 'null' and initial['shooter']['game_mode'] == 'SURVIVAL'
          and initial['shooter']['inventory_ammo'].get(AMMO) == 58,
          'muzzle probe begins with loaded survival AK-47 and reserve58')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47 for muzzle refit')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with '
                                      'tacz:attachment[minecraft:custom_data={AttachmentId:"' + SILENCER + '"}]'),
          'server equips one shipped AK-47-compatible muzzle silencer')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(SILENCER) == 1 else None,
         'silencer synchronizes into shooter inventory', timeout=10)
    client('shooter', 'focus_window')
    wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
         'shooter window has native game-input focus for refit key', timeout=10)
    check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
          'native refit key opens actual screen for muzzle')
    ready = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
                 and 'MUZZLE' in value['refit']['allowed_types']
                 and 'silence_sound' in value['refit'] else None,
                 'AK-47 screen exposes compatible muzzle and sound cache', timeout=10)
    check(ready['refit']['silence_sound'] is False,
          'unmodified gun cache selects ordinary fire sound')
    base_distance = ready['refit']['sound_distance']
    check(state('observer')['screen'] == 'null', 'observer remains in world during muzzle refit')
    check(client('shooter', 'refit_type_click', type='MUZZLE')['snapshot']['refit']['type'] == 'MUZZLE',
          'native muzzle button selects compatible attachment type')
    wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1
         and 10 in value['refit']['attachment_slots'] else None,
         'visible silencer inventory button appears after transform', timeout=10)
    client('shooter', 'refit_install_click', attachment=SILENCER)
    installed = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') == SILENCER
                     and value['refit'].get('silence_sound') is True
                     and value['refit'].get('sound_distance') == base_distance - 20
                     and value['inventory_attachments'].get(SILENCER, 0) == 0 else None,
                     'native install updates muzzle, inventory and silence modifier cache', timeout=15)
    check(installed['refit']['unload_buttons'] == 1, 'installed muzzle exposes native unload button')
    installed_both = both(29, SILENCER, 'installed AK-47 silencer')
    result['installed'] = {'screen': installed, 'observer': installed_both['observer'],
                           'server_gun': server_gun(29, True), 'base_distance': base_distance}
    reserve()
    check(installed['inventory_ammo'].get(AMMO) == 58, 'muzzle installation leaves reserve ammo intact')

    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes refit before silenced shot')
    before = {role: state(role) for role in roles}
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'silenced AK-47 shot accepted')
    fired = both(28, SILENCER, 'silenced AK-47 shot')
    for role, value in fired.items():
        check(value['fires'].get('TaczShooter', 0) == before[role]['fires'].get('TaczShooter', 0) + 1,
              f'{role} sees exactly one fire event with silencer installed')
        check(value['projectiles'] == before[role]['projectiles'] + 1,
              f'{role} sees exactly one projectile with silencer installed')
    result['fired'] = {'states': fired, 'server_gun': server_gun(28, True)}
    reserve()

    client('shooter', 'focus_window')
    wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
         'shooter window regains native focus for muzzle unload', timeout=10)
    check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
          'native refit screen reopens around fired silencer')
    wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
         and 'MUZZLE' in value['refit']['allowed_types'] else None,
         'muzzle control ready for unload', timeout=10)
    check(client('shooter', 'refit_type_click', type='MUZZLE')['snapshot']['refit']['type'] == 'MUZZLE',
          'native muzzle control reselected')
    wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1
         and value['refit']['unload_buttons'] == 1 else None,
         'native unload button visible for installed muzzle', timeout=10)
    client('shooter', 'refit_unload_click')
    unloaded = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') is None
                    and value['refit'].get('silence_sound') is False
                    and value['refit'].get('sound_distance') == base_distance
                    and value['inventory_attachments'].get(SILENCER) == 1 else None,
                    'native unload restores inventory and ordinary fire sound cache', timeout=15)
    unloaded_both = both(28, None, 'unloaded AK-47 silencer')
    result['unloaded'] = {'screen': unloaded, 'observer': unloaded_both['observer'],
                          'server_gun': server_gun(28, False)}
    reserve()
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes screen after muzzle unload')
    server_number('Inventory[{Slot:9b}].count', 58)
