"""First native End exit, actual credits screen and retained loaded gun."""
import time


def run_end_exit_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles)
          and before['shooter']['players']['TaczShooter']['gun'] == 'tacz:ak47'
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'End exit probe starts with loaded gun and both clients in overworld')
    check('false' in command('gamerule keep_inventory'),
          'End credits retain gun under ordinary keep-inventory-false rule')
    command('spawnpoint TaczShooter 0 -60 0')
    command('forceload add 16 -16 48 16')
    command('fill 28 -59 -2 32 -55 2 minecraft:air')
    command('fill 28 -60 -2 32 -60 2 minecraft:stone')
    check('Changed' in command('setblock 30 -59 0 minecraft:end_portal'),
          'native End entry portal placed in disposable overworld')
    check('false' in command('execute if block 30 -59 0 minecraft:end_portal run gamerule keep_inventory'),
          'native End entry portal block exists')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 30.5 -59 0.5 0 0'),
          'same-dimension placement contacts End entry portal')

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

    end = wait(arrived, 'native End entry preserves loaded gun', timeout=40)
    check(end['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'End arrival preserves account UUID')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'End arrival draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'End shooter fires one normal shot before exit')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        local = values['shooter']['players'].get('TaczShooter', {})
        return values if (local.get('magazine') == 28
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          end['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == end['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'End exit probe shot reaches shooter', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == end['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == end['observer']['projectiles'],
          'Overworld observer receives no End shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:the_end run forceload add 80 -16 128 16')
    command('execute in minecraft:the_end run fill 103 48 -2 107 48 2 minecraft:stone')
    check('Changed' in command('execute in minecraft:the_end run setblock 105 49 0 minecraft:end_portal'),
          'native End exit portal block placed on safe platform')
    check('false' in command('execute in minecraft:the_end if block 105 49 0 minecraft:end_portal '
                             'run gamerule keep_inventory'),
          'End exit portal exists under keep-inventory-false rule')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 105.5 49 0.5 0 0'),
          'same-dimension placement contacts native End exit portal')

    credits = wait(lambda: value if 'WinScreen' in (value := state('shooter'))['screen'] else None,
                   'native first-End-exit credits screen', timeout=30)
    check(credits['dimension'] == 'minecraft:the_end',
          'credits screen opens before client returns from End')
    observer = state('observer')
    check(observer['dimension'] == 'minecraft:overworld'
          and 'TaczShooter' not in observer['players']
          and observer['shots'].get('TaczShooter', 0) == end['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == end['observer']['projectiles'],
          'observer remains isolated while shooter sees credits')
    check(client('shooter', 'finish_end_credits')['status'] == 'ok',
          'close actual WinScreen through its native onClose callback')

    def returned():
        values = {role: state(role) for role in roles}
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('health') == 20
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 28
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    back = wait(returned, 'native credits completion returns loaded shooter to overworld', timeout=40)
    for role in roles:
        check(back[role]['players']['TaczShooter']['uuid'] ==
              before[role]['players']['TaczShooter']['uuid'],
              role + ' keeps account UUID through End credits')
        check(back[role]['shots'].get('TaczShooter', 0) ==
              (alone[role] if role == 'shooter' else end[role])['shots'].get('TaczShooter', 0)
              and back[role]['projectiles'] ==
              (alone[role] if role == 'shooter' else end[role])['projectiles'],
              role + ' receives no replayed End shot or projectile after credits')
        check(back[role]['players']['TaczShooter']['reload_phase'] == 'NOT_RELOADING'
              and not back[role]['players']['TaczShooter']['bolting'],
              role + ' has no stale reload or bolt state after credits')
    check(back['shooter']['reserve_slot_count'] == 58,
          'credits return preserves reserve ammo without keep-inventory rule')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'post-credits draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'End credits return accepts one normal shared shot')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    fired = wait(final_shot, 'post-credits shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:overworld run forceload remove all')
    command('execute in minecraft:the_end run forceload remove all')
    report['end_exit_scenario'] = {'before': before, 'end': end, 'isolated_shot': alone,
                                   'credits': credits, 'returned': back, 'final_shot': fired}
