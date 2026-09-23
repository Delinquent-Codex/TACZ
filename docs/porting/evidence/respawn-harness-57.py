"""Controlled keep-inventory death and native respawn on a TACZ-only server."""
import time


def run_respawn_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    check('true' in command('gamerule keepInventory true'), 'disposable world retains inventory on death')
    command('spawnpoint TaczShooter 0 -60 0')
    check('Teleported' in command('tp TaczObserver 4 -60 0 0 0'),
          'observer remains beside respawn firing line')
    before = {role: state(role) for role in roles}
    original = before['shooter']['players']['TaczShooter']
    check(original['health'] == 20 and original['gun'] == 'tacz:ak47'
          and original['magazine'] == 29 and original['chamber'],
          'shooter enters death transition with live loaded AK-47')
    check(before['shooter']['reserve_slot_count'] == 58,
          'shooter enters death transition with 58 reserve rounds')
    check('Killed TaczShooter' in command('kill TaczShooter'), 'native command kills shooter')

    def death_screen():
        value = state('shooter')
        return value if 'DeathScreen' in value['screen'] else None

    dead = wait(death_screen, 'native death screen', timeout=15)
    check(dead['players']['TaczShooter']['health'] <= 0, 'local shooter health is zero at death screen')
    check(client('shooter', 'respawn')['status'] == 'ok', 'native client respawn packet sent')

    def reborn():
        values = {role: state(role) for role in roles}
        local = values['shooter']['players'].get('TaczShooter', {})
        remote = values['observer']['players'].get('TaczShooter', {})
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        if any(player.get('health') != 20 or player.get('gun') != 'tacz:ak47'
               or player.get('magazine') != 29 or player.get('chamber') is not True
               for player in (local, remote)):
            return None
        if values['shooter']['draw_cooldown'] != 0 or values['shooter']['state_locked']:
            return None
        return values

    after = wait(reborn, 'respawned gun and health synchronized to both clients', timeout=30)
    for role in roles:
        current = after[role]['players']['TaczShooter']
        old = before[role]['players']['TaczShooter']
        check(current['uuid'] == old['uuid'] and current['entity_id'] != old['entity_id'],
              role + ' tracks a new player entity with the same account UUID')
        check(after[role]['shots'].get('TaczShooter', 0) == before[role]['shots'].get('TaczShooter', 0)
              and after[role]['projectiles'] == before[role]['projectiles'],
              role + ' death and respawn emit no duplicate shot or projectile')
        check(current['reload_phase'] == 'NOT_RELOADING' and not current['bolting'],
              role + ' respawn has no stale reload or bolt state')
    check(after['shooter']['reserve_slot_count'] == 58, 'reserve ammo retained exactly once')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'respawned client operator accepts one normal shot')

    def fired():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 28
                             and values[role]['players']['TaczShooter']['chamber'] is True
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             after[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == after[role]['projectiles'] + 1
                             for role in roles) else None

    fired_state = wait(fired, 'single post-respawn shot and ammo sync', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    time.sleep(0.75)
    for role in roles:
        value = state(role)
        check(value['shots'].get('TaczShooter', 0) == fired_state[role]['shots'].get('TaczShooter', 0)
              and value['projectiles'] == fired_state[role]['projectiles'],
              role + ' post-respawn shot does not repeat')
    check('false' in command('gamerule keepInventory false'), 'restore disposable world death rule')
    report['respawn_scenario'] = {'before': before, 'dead': dead, 'after': after, 'fired': fired_state}
