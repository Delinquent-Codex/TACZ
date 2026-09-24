"""First credits exit, then direct native End return after seenCredits persists."""
import time

from end_exit_scenarios import run_end_exit_scenarios


def run_end_exit_repeat_scenarios(check, command, client, state, wait, server_number, report):
    run_end_exit_scenarios(check, command, client, state, wait, server_number, report)
    roles = ('shooter', 'observer')
    before = report['end_exit_scenario']['final_shot']
    check(all(before[role]['dimension'] == 'minecraft:overworld'
              and before[role]['players']['TaczShooter']['magazine'] == 27
              for role in roles),
          'second End trip begins after first credits return and shared shot')
    server_number('seenCredits', 1)
    check('false' in command('gamerule keep_inventory'),
          'repeat End exit still uses ordinary keep-inventory-false rule')
    command('execute in minecraft:overworld run forceload add 16 -16 48 16')
    command('execute in minecraft:the_end run forceload add 80 -16 128 16')
    check('false' in command('execute in minecraft:overworld if block 30 -59 0 minecraft:end_portal '
                             'run gamerule keep_inventory'),
          'original native End entry portal remains present')
    check('false' in command('execute in minecraft:the_end if block 105 49 0 minecraft:end_portal '
                             'run gamerule keep_inventory'),
          'original native End exit portal remains present')
    wait(lambda: command('data get entity TaczShooter PortalCooldown').rstrip().endswith(': 0'),
         'portal cooldown clears after first credits return', timeout=20)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 30.5 -59 0.5 0 0'),
          'same-dimension placement contacts End entry portal again')

    def arrived_again():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_end'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and shooter.get('gun') == 'tacz:ak47'
                          and shooter.get('magazine') == 27
                          and shooter.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    end = wait(arrived_again, 'second native End entry preserves loaded gun', timeout=40)
    check(end['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'second End entry retains account UUID')
    server_number('seenCredits', 1)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'second End draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'second End visit accepts one normal shot')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        local = values['shooter']['players'].get('TaczShooter', {})
        return values if (local.get('magazine') == 26
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          end['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == end['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'second End shot reaches shooter only', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == end['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == end['observer']['projectiles'],
          'observer receives no second End shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 26)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 105.5 49 0.5 0 0'),
          'same-dimension placement contacts End exit portal again')

    def returned_directly():
        values = {role: state(role) for role in roles}
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('health') == 20
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 26
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    back = wait(returned_directly, 'second End exit returns directly without credits', timeout=40)
    server_number('seenCredits', 1)
    for role in roles:
        check(back[role]['players']['TaczShooter']['uuid'] ==
              before[role]['players']['TaczShooter']['uuid'],
              role + ' retains UUID after second End exit')
        check(back[role]['shots'].get('TaczShooter', 0) ==
              (alone[role] if role == 'shooter' else end[role])['shots'].get('TaczShooter', 0)
              and back[role]['projectiles'] ==
              (alone[role] if role == 'shooter' else end[role])['projectiles'],
              role + ' second End return does not replay isolated shot')
        check(back[role]['players']['TaczShooter']['reload_phase'] == 'NOT_RELOADING'
              and not back[role]['players']['TaczShooter']['bolting'],
              role + ' has no stale gun action after direct return')
    check(back['shooter']['reserve_slot_count'] == 58,
          'second End exit preserves reserve exactly once')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 26)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'second End return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'direct-return traveler fires beside observer')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 25
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    final = wait(final_shot, 'post-direct-return shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 25)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:overworld run forceload remove all')
    command('execute in minecraft:the_end run forceload remove all')
    report['end_exit_repeat_scenario'] = {'before': before, 'end': end, 'isolated_shot': alone,
                                          'returned': back, 'final_shot': final}
