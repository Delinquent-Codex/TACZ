"""Install the shipped level-one AK-47 magazine and verify ammo conservation."""
import re


MAGAZINE = 'tacz:extended_mag_1'
AMMO = 'tacz:762x39'
GUN_AMMO_PATH = 'Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount'


def run_extended_mag_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    result = report['extended_mag_scenario'] = {}

    def player(snapshot):
        return snapshot['players'].get('TaczShooter', {})

    def both(magazine, capacity, label, reloading=False):
        def ready():
            values = {role: state(role) for role in roles}
            return values if all(player(value).get('gun') == 'tacz:ak47'
                                 and player(value).get('magazine') == magazine
                                 and player(value).get('magazine_capacity') == capacity
                                 and player(value).get('chamber') is True
                                 and player(value).get('reloading') is reloading
                                 for value in values.values()) else None
        values = wait(ready, label, timeout=20)
        for role, value in values.items():
            check(player(value)['magazine'] == magazine
                  and player(value)['magazine_capacity'] == capacity
                  and player(value)['chamber'] is True,
                  f'{label}: {role} sees {magazine}/{capacity} and chambered round')
        return values

    def server_reserve(expected):
        reply = command('clear TaczShooter tacz:ammo 0')
        match = re.search(r'Found (\d+) matching item\(s\)', reply)
        check(match is not None and int(match[1]) == expected,
              f'server inventory holds exactly {expected} rifle rounds')
        return reply

    initial = both(29, 30, 'pre-refit standard magazine')
    check(initial['shooter']['screen'] == 'null'
          and initial['shooter']['game_mode'] == 'SURVIVAL'
          and initial['shooter']['inventory_ammo'].get(AMMO) == 58,
          'extended-mag probe begins with loaded survival AK-47 and reserve58')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47 for extended-mag refit')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with '
                                       'tacz:attachment[minecraft:custom_data={AttachmentId:"tacz:extended_mag_1"}]'),
          'server equips one shipped AK-47-compatible level-one extended magazine')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(MAGAZINE) == 1 else None,
         'extended magazine synchronizes into shooter inventory', timeout=10)
    client('shooter', 'focus_window')
    wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
         'shooter window has native game-input focus for refit key', timeout=10)
    opened = client('shooter', 'refit_key')
    check('GunRefitScreen' in opened['snapshot']['screen'],
          'native refit key opens actual screen for extended magazine')
    wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
         and 'EXTENDED_MAG' in value['refit']['allowed_types'] else None,
         'AK-47 refit screen exposes compatible extended-magazine control', timeout=10)
    check(state('observer')['screen'] == 'null', 'observer remains in world during magazine refit')
    selected = client('shooter', 'refit_type_click', type='EXTENDED_MAG')
    check(selected['snapshot']['refit']['type'] == 'EXTENDED_MAG',
          'native attachment button selects extended magazine')
    wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1
         and 10 in value['refit']['attachment_slots'] else None,
         'visible extended-magazine inventory button appears after transform', timeout=10)
    client('shooter', 'refit_install_click', attachment=MAGAZINE)

    def installed():
        value = state('shooter')
        return value if (value.get('refit', {}).get('extended_mag') == MAGAZINE
                         and value['inventory_attachments'].get(MAGAZINE, 0) == 0
                         and value['inventory_ammo'].get(AMMO) == 87) else None

    installed_state = wait(installed, 'install ejects 29 rounds and consumes attachment', timeout=15)
    installed_both = both(0, 34, 'installed level-one extended magazine')
    check(installed_state['refit']['unload_buttons'] == 1,
          'installed magazine exposes native unload button')
    gun_nbt = command('data get entity TaczShooter Inventory[{Slot:0b}]')
    check(MAGAZINE in gun_nbt and 'GunCurrentAmmoCount: 0' in gun_nbt
          and 'HasBulletInBarrel: 1b' in gun_nbt,
          'server gun has extended magazine, empty magazine and chambered round')
    server_reserve(87)
    result['installed'] = {'shooter': installed_state, 'observer': installed_both['observer'],
                           'server_gun': gun_nbt}

    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'refit key closes screen before live extended-magazine reload')
    client('shooter', 'reload')
    loaded = both(34, 34, 'extended-magazine tactical reload')
    check(loaded['shooter']['inventory_ammo'].get(AMMO) == 53,
          '34-round reload consumes exactly 34 of 87 reserve rounds')
    server_number(GUN_AMMO_PATH, 34)
    server_reserve(53)
    result['loaded'] = loaded

    client('shooter', 'focus_window')
    wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
         'shooter window regains native focus after reload', timeout=10)
    check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
          'refit screen reopens around loaded extended magazine')
    wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
         and 'EXTENDED_MAG' in value['refit']['allowed_types'] else None,
         'extended-magazine control ready for unload', timeout=10)
    check(client('shooter', 'refit_type_click', type='EXTENDED_MAG')['snapshot']['refit']['type'] == 'EXTENDED_MAG',
          'native extended-magazine control reselected')
    wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1
         and value['refit']['unload_buttons'] == 1 else None,
         'native unload button visible for loaded magazine', timeout=10)
    client('shooter', 'refit_unload_click')

    def unloaded():
        value = state('shooter')
        return value if (value.get('refit', {}).get('extended_mag') == 'none'
                         and value['inventory_attachments'].get(MAGAZINE) == 1
                         and value['inventory_ammo'].get(AMMO) == 87) else None

    unloaded_state = wait(unloaded, 'unload returns magazine and ejects all 34 rounds', timeout=15)
    unloaded_both = both(0, 30, 'unloaded standard magazine')
    gun_nbt = command('data get entity TaczShooter Inventory[{Slot:0b}]')
    check(MAGAZINE not in gun_nbt and 'GunCurrentAmmoCount: 0' in gun_nbt
          and 'HasBulletInBarrel: 1b' in gun_nbt,
          'server gun loses extended attachment, keeps chamber and empties magazine')
    server_reserve(87)
    result['unloaded'] = {'shooter': unloaded_state, 'observer': unloaded_both['observer'],
                          'server_gun': gun_nbt}

    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'refit key closes screen after magazine unload')
    client('shooter', 'reload')
    restored = both(30, 30, 'standard-magazine tactical reload')
    check(restored['shooter']['inventory_ammo'].get(AMMO) == 57
          and restored['shooter']['inventory_attachments'].get(MAGAZINE) == 1,
          'standard reload consumes 30 rounds and keeps returned attachment')
    server_number(GUN_AMMO_PATH, 30)
    server_reserve(57)
    result['restored'] = restored
