"""Native End-portal transfer followed by End death and Overworld respawn."""
import time


def run_end_portal_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles)
          and before['shooter']['players']['TaczShooter']['gun'] == 'tacz:ak47'
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'End probe starts with loaded gun and both clients in overworld')
    check('true' in command('gamerule keep_inventory true'),
          'End death retains inventory in disposable world')
    command('spawnpoint TaczShooter 0 -60 0')
    command('forceload add 16 -16 48 16')
    command('fill 28 -59 -2 32 -55 2 minecraft:air')
    command('fill 28 -60 -2 32 -60 2 minecraft:stone')
    check('Changed' in command('setblock 30 -59 0 minecraft:end_portal'),
          'native End portal block placed in disposable overworld')
    check('true' in command('execute if block 30 -59 0 minecraft:end_portal run gamerule keep_inventory'),
          'End portal block exists before player contact')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 30.5 -59 0.5 0 0'),
          'same-dimension placement enters native End portal')

    def arrived():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_end'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and shooter.get('gun') == 'tacz:ak47'
                          and shooter.get('magazine') == 29
                          and shooter.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    end = wait(arrived, 'native End portal transfers loaded shooter', timeout=40)
    check(end['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'End portal crossing preserves account UUID')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'End arrival draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'End shooter fires one normal shot')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        local = values['shooter']['players'].get('TaczShooter', {})
        return values if (local.get('magazine') == 28
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          end['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == end['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'End shot reaches shooter only', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == end['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == end['observer']['projectiles'],
          'overworld observer receives no End shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Killed TaczShooter' in command('kill TaczShooter'),
          'native death occurs while shooter is in the End')
    dead = wait(lambda: value if 'DeathScreen' in (value := state('shooter'))['screen'] else None,
                'End death screen', timeout=15)
    check(dead['players']['TaczShooter']['health'] <= 0,
          'End death leaves local shooter at zero health')
    check(client('shooter', 'respawn')['status'] == 'ok',
          'native respawn packet sent from End death screen')

    def reborn():
        values = {role: state(role) for role in roles}
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('health') == 20
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 28
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    back = wait(reborn, 'End death respawns loaded shooter in overworld', timeout=35)
    for role in roles:
        check(back[role]['players']['TaczShooter']['uuid'] ==
              before[role]['players']['TaczShooter']['uuid'],
              role + ' retains account UUID through End death and respawn')
        check(back[role]['shots'].get('TaczShooter', 0) ==
              (alone[role] if role == 'shooter' else end[role])['shots'].get('TaczShooter', 0)
              and back[role]['projectiles'] ==
              (alone[role] if role == 'shooter' else end[role])['projectiles'],
              role + ' receives no replayed shot or projectile after End respawn')
        check(back[role]['players']['TaczShooter']['reload_phase'] == 'NOT_RELOADING'
              and not back[role]['players']['TaczShooter']['bolting'],
              role + ' has no stale gun transition after End respawn')
    check(back['shooter']['reserve_slot_count'] == 58,
          'End death and respawn retain reserve ammunition exactly once')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'End respawn draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'respawned End traveler fires in shared overworld')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    fired = wait(final_shot, 'post-End-respawn shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('false' in command('gamerule keep_inventory false'),
          'restore disposable world death rule')
    command('forceload remove all')
    report['end_portal_scenario'] = {'before': before, 'end': end, 'isolated_shot': alone,
                                     'dead': dead, 'respawned': back, 'final_shot': fired}
