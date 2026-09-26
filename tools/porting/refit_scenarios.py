"""Use the shipped AK-47 refit screen to install and remove a compatible sight."""
import time


SIGHT = 'tacz:sight_t2'


def run_refit_scenarios(check, command, client, state, wait, server_number, report):
    initial = state('shooter')
    check(initial['screen'] == 'null' and initial['game_mode'] == 'SURVIVAL'
          and initial['inventory_guns'].get('tacz:ak47') == 1,
          'refit probe begins with loaded survival AK-47 and no screen')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47 for refit')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with '
                                       'tacz:attachment[minecraft:custom_data={AttachmentId:"tacz:sight_t2"}]'),
          'server equips one shipped AK-47-compatible sight')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(SIGHT) == 1 else None,
         'client synchronizes sight in player inventory', timeout=10)
    for action, kwargs in (
        ('refit_packet', {'attachment_slot': -1, 'gun_slot': 0, 'type': 'SCOPE'}),
        ('refit_packet', {'attachment_slot': 10, 'gun_slot': 1000, 'type': 'SCOPE'}),
        ('refit_unload_packet', {'gun_slot': -1, 'type': 'SCOPE'}),
        ('refit_unload_packet', {'gun_slot': 1000, 'type': 'SCOPE'}),
        ('refit_laser_packet', {'gun_slot': -2}),
        ('refit_laser_packet', {'gun_slot': 1000}),
    ):
        client('shooter', action, **kwargs)
    time.sleep(1)
    check(state('shooter')['connected'] and '2 of a max' in command('list'),
          'malformed refit packet indices leave both clients and dedicated server connected')
    check('tacz:sight_t2' not in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'malformed refit packets do not attach the sight')
    server_number('Inventory[{Slot:10b}].count', 1)
    opened = client('shooter', 'refit_key')
    check('GunRefitScreen' in opened['snapshot']['screen'],
          'native refit key listener opens the actual screen')
    ready = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
                 and 'SCOPE' in value['refit']['allowed_types'] else None,
                 'refit screen exposes AK-47 scope control after opening animation', timeout=10)
    check(state('observer')['screen'] == 'null', 'observer remains in world during refit')
    selected = client('shooter', 'refit_type_click', type='SCOPE')
    check(selected['snapshot']['refit']['type'] == 'SCOPE',
          'native scope button selects compatible attachment type')
    wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1
         and 10 in value['refit']['attachment_slots'] else None,
         'visible inventory sight button appears after refit animation', timeout=10)
    client('shooter', 'refit_install_click', attachment=SIGHT)

    def installed():
        value = state('shooter')
        return value if value['refit']['scope'] == SIGHT and value['inventory_attachments'].get(SIGHT, 0) == 0 else None

    installed_state = wait(installed, 'server installs sight and synchronizes gun plus inventory', timeout=15)
    server_gun = command('data get entity TaczShooter Inventory[{Slot:0b}]')
    check('tacz:sight_t2' in server_gun and 'GunCurrentAmmoCount: 29' in server_gun,
          'server gun NBT contains installed sight and unchanged magazine29')
    check(installed_state['refit']['unload_buttons'] == 1,
          'refit screen exposes the native unload control for installed sight')
    client('shooter', 'refit_unload_click')

    def unloaded():
        value = state('shooter')
        return value if value['refit'].get('scope') is None and value['inventory_attachments'].get(SIGHT) == 1 else None

    unloaded_state = wait(unloaded, 'server unloads sight back to inventory and synchronizes', timeout=15)
    server_gun = command('data get entity TaczShooter Inventory[{Slot:0b}]')
    check('tacz:sight_t2' not in server_gun and 'GunCurrentAmmoCount: 29' in server_gun,
          'server gun NBT has no sight after unload and retains magazine29')
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native refit key listener closes actual screen')
    time.sleep(0.5)
    check(state('shooter')['inventory_attachments'].get(SIGHT) == 1,
          'sight remains in player inventory after screen close')
    server_number('Inventory[{Slot:9b}].count', 58)
    report['refit_scenario'] = {'initial': initial, 'ready': ready,
                                'installed': installed_state, 'unloaded': unloaded_state}
