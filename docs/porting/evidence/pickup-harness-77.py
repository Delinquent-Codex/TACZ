"""Native dropped-item pickup, hotbar swapping and hand drop after ordinary respawn."""
import time

from death_drop_scenarios import run_death_drop_scenarios


def run_pickup_scenarios(check, command, client, state, wait, server_number, report):
    run_death_drop_scenarios(check, command, client, state, wait, report)
    roles = ('shooter', 'observer')
    empty = report['death_drop_scenario']['after']
    gun_selector = '@e[type=minecraft:item,nbt={Item:{id:"tacz:modern_kinetic_gun"}},limit=1]'
    ammo_selector = '@e[type=minecraft:item,nbt={Item:{id:"tacz:ammo"}},limit=1]'
    check('Teleported' in command(f'tp {gun_selector} TaczShooter'),
          'native world gun moved to respawned player for normal pickup')

    def gun_picked_up():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 29
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    gun_pickup = wait(gun_picked_up, 'picked-up gun synchronized to both clients', timeout=15)
    check('No entity was found' in command(f'data get entity {gun_selector} Item', allow_no_entity=True),
          'world gun entity consumed by player pickup')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    check('Teleported' in command(f'tp {ammo_selector} TaczShooter'),
          'native ammo drop moved to player for normal pickup')

    def ammo_picked_up():
        value = state('shooter')
        return value if value['inventory_ammo'].get('tacz:762x39') == 58 else None

    ammo_pickup = wait(ammo_picked_up, '58 reserve rounds in client inventory after pickup', timeout=15)
    check('No entity was found' in command(f'data get entity {ammo_selector} Item', allow_no_entity=True),
          'world ammo entity consumed by player pickup')
    server_number('Inventory[{id:"tacz:ammo"}].count', 58)
    for role in roles:
        value = state(role)
        check(value['shots'].get('TaczShooter', 0) == empty[role]['shots'].get('TaczShooter', 0)
              and value['projectiles'] == empty[role]['projectiles'],
              role + ' pickups create no shot or projectile')
    check(client('shooter', 'select_slot', slot=1)['snapshot']['selected_slot'] == 1,
          'native client selects ammo hotbar slot')

    def holstered():
        values = {role: state(role) for role in roles}
        return values if all('gun' not in values[role]['players'].get('TaczShooter', {}) for role in roles) else None

    no_gun = wait(holstered, 'hotbar switch hides gun on both clients', timeout=10)
    check(client('shooter', 'shoot')['shoot_result'] != 'SUCCESS', 'no gun shot accepted from ammo slot')
    for role in roles:
        value = state(role)
        check(value['shots'].get('TaczShooter', 0) == no_gun[role]['shots'].get('TaczShooter', 0)
              and value['projectiles'] == no_gun[role]['projectiles'],
              role + ' ammo-slot shoot creates no event or projectile')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native client reselects picked-up gun')

    def ready():
        values = {role: state(role) for role in roles}
        return values if (all(values[role]['players'].get('TaczShooter', {}).get('gun') == 'tacz:ak47'
                              and values[role]['players']['TaczShooter'].get('magazine') == 29
                              for role in roles)
                          and values['shooter']['draw_cooldown'] == 0
                          and not values['shooter']['state_locked']) else None

    ready_state = wait(ready, 'redrawn picked-up gun after hotbar swap', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'picked-up gun fires once after hotbar swap')

    def fired():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 28
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             ready_state[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == ready_state[role]['projectiles'] + 1
                             for role in roles) else None

    after_shot = wait(fired, 'one pickup gun shot synchronized to both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{id:"tacz:ammo"}].count', 58)
    check(client('shooter', 'drop_one')['dropped'], 'native client drops selected gun with drop packet')

    def dropped_again():
        values = {role: state(role) for role in roles}
        return values if all('gun' not in values[role]['players'].get('TaczShooter', {}) for role in roles) else None

    after_drop = wait(dropped_again, 'hand drop clears gun for both clients', timeout=10)
    check('Found no elements matching' in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'server hand slot empty after client drop')
    server_number('Inventory[{id:"tacz:ammo"}].count', 58)
    response = command(f'data get entity {gun_selector} Item.components."minecraft:custom_data".GunCurrentAmmoCount')
    check(response.rstrip().endswith(': 28'), 're-dropped gun retains post-shot magazine28')
    for role in roles:
        check(after_drop[role]['shots'].get('TaczShooter', 0) == after_shot[role]['shots'].get('TaczShooter', 0)
              and after_drop[role]['projectiles'] == after_shot[role]['projectiles'],
              role + ' hand drop creates no duplicate shot or projectile')
    report['pickup_scenario'] = {'empty': empty, 'gun_pickup': gun_pickup, 'ammo_pickup': ammo_pickup,
                                 'holstered': no_gun, 'ready': ready_state, 'fired': after_shot,
                                 'dropped': after_drop}
