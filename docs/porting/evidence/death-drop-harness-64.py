"""Ordinary native death drops and empty respawn on a TACZ-only dedicated server."""
import re


def run_death_drop_scenarios(check, command, client, state, wait, report):
    roles = ('shooter', 'observer')
    check('false' in command('gamerule keep_inventory false'), 'ordinary death drops inventory')
    command('spawnpoint TaczShooter 8 -60 0')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'shooter dies at isolated drop point')
    check('Teleported' in command('tp TaczObserver 5 -60 0 0 0'), 'observer stays outside drop pickup range')
    before = {role: state(role) for role in roles}
    gun = before['shooter']['players']['TaczShooter']
    check(gun['gun'] == 'tacz:ak47' and gun['magazine'] == 29 and gun['chamber']
          and before['shooter']['reserve_slot_count'] == 58,
          'ordinary death starts with loaded AK-47 and 58 reserve rounds')
    check('Killed TaczShooter' in command('kill TaczShooter'), 'native death drops player inventory')

    def death_screen():
        value = state('shooter')
        player = value['players'].get('TaczShooter', {})
        return value if 'DeathScreen' in value['screen'] and player.get('health', 20) <= 0 else None

    dead = wait(death_screen, 'ordinary native death screen', timeout=15)
    check(dead['players']['TaczShooter']['health'] <= 0, 'local player reaches zero health')
    gun_selector = '@e[type=minecraft:item,nbt={Item:{id:"tacz:modern_kinetic_gun"}},limit=1]'
    ammo_selector = '@e[type=minecraft:item,nbt={Item:{id:"tacz:ammo"}},limit=1]'

    def dropped(selector, path, expected):
        def current():
            response = command(f'data get entity {selector} {path}', allow_no_entity=True)
            value = re.search(r':\s*(-?\d+)(?:[bBsSlLfFdD])?$', response)
            return response if value and int(value.group(1)) == expected else None
        return wait(current, 'dropped item ' + path, timeout=15)

    gun_magazine = dropped(gun_selector, 'Item.components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    gun_chamber = dropped(gun_selector, 'Item.components."minecraft:custom_data".HasBulletInBarrel', 1)
    ammo_count = dropped(ammo_selector, 'Item.count', 58)
    check('tacz:ak47' in command(f'data get entity {gun_selector} Item.components."minecraft:custom_data".GunId'),
          'dropped gun retains shipped gun ID')
    check('tacz:762x39' in command(f'data get entity {ammo_selector} Item.components."minecraft:custom_data".AmmoId'),
          'dropped reserve retains ammo ID')
    client('shooter', 'respawn')

    def empty_respawn():
        values = {role: state(role) for role in roles}
        local = values['shooter']['players'].get('TaczShooter', {})
        remote = values['observer']['players'].get('TaczShooter', {})
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        if any(player.get('health') != 20 or 'gun' in player for player in (local, remote)):
            return None
        return values if values['shooter']['reserve_slot_count'] == 0 else None

    after = wait(empty_respawn, 'empty inventory synchronized after ordinary respawn', timeout=30)
    for role in roles:
        old, current = before[role], after[role]
        check(current['players']['TaczShooter']['uuid'] == old['players']['TaczShooter']['uuid'],
              role + ' keeps account identity after ordinary death')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0)
              and current['projectiles'] == old['projectiles'],
              role + ' ordinary death/respawn emits no duplicate shot or projectile')
        check(current['players']['TaczShooter']['reload_phase'] == 'NOT_RELOADING'
              and not current['players']['TaczShooter']['bolting'],
              role + ' ordinary respawn has no stale reload or bolt')
    check('Found no elements matching' in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'server selected gun slot is empty after ordinary respawn')
    check('Found no elements matching' in command('data get entity TaczShooter Inventory[{Slot:9b}]'),
          'server reserve slot is empty after ordinary respawn')
    check(dropped(gun_selector, 'Item.components."minecraft:custom_data".GunCurrentAmmoCount', 29) == gun_magazine,
          'gun drop remains after distant respawn')
    check(dropped(ammo_selector, 'Item.count', 58) == ammo_count,
          'ammo drop remains after distant respawn')
    report['death_drop_scenario'] = {'before': before, 'dead': dead, 'after': after,
                                     'gun_magazine': gun_magazine, 'gun_chamber': gun_chamber,
                                     'ammo_count': ammo_count}
