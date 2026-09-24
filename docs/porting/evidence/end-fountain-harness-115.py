"""Activate the dragon fight's generated End fountain, then exit with TACZ state."""
import re
import time

from end_exit_repeat_scenarios import run_end_exit_repeat_scenarios


def run_end_fountain_scenarios(check, command, client, state, wait, server_number, report):
    run_end_exit_repeat_scenarios(check, command, client, state, wait, server_number, report)
    roles = ('shooter', 'observer')
    before = report['end_exit_repeat_scenario']['final_shot']
    check(all(before[role]['dimension'] == 'minecraft:overworld'
              and before[role]['players']['TaczShooter']['magazine'] == 25
              for role in roles), 'generated fountain probe begins after both End returns')
    server_number('seenCredits', 1)
    check('false' in command('gamerule keep_inventory'),
          'generated fountain probe retains ordinary death rule')
    command('execute in minecraft:overworld run forceload add 16 -16 48 16')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 30.5 -59 0.5 0 0'),
          'same-dimension placement enters the original native End portal a third time')

    def arrived():
        values = {role: state(role) for role in roles}
        gun = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_end'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and gun.get('gun') == 'tacz:ak47'
                          and gun.get('magazine') == 25
                          and gun.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    end = wait(arrived, 'third native End arrival retains loaded gun', timeout=40)
    server_number('seenCredits', 1)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 25)
    server_number('Inventory[{Slot:9b}].count', 58)

    # An unsuccessful /execute if block gives no RCON reply. Derive the rim height
    # from a positive heightmap operation instead of probing unknown Y values.
    check('Marked' in command('execute in minecraft:the_end run forceload add 0 0 0 0'),
          'dragon-fountain central chunk is loaded')
    check('Summoned' in command('execute in minecraft:the_end positioned 3 0 0 '
                                'positioned over motion_blocking run summon minecraft:marker ~ ~ ~ '
                                '{Tags:["tacz_fountain_probe"]}'),
          'heightmap probe marks the top of the generated fountain rim')
    marker_y = command('execute in minecraft:the_end run data get entity '
                       '@e[type=minecraft:marker,tag=tacz_fountain_probe,limit=1] Pos[1]')
    match = re.search(r': (-?\d+(?:\.\d+)?)d?\s*$', marker_y)
    check(match is not None, 'heightmap marker reports a numeric Y coordinate')
    y = int(float(match.group(1))) - 1
    check('Killed' in command('execute in minecraft:the_end run '
                              'kill @e[type=minecraft:marker,tag=tacz_fountain_probe]'),
          'temporary heightmap marker removed')
    check('false' in command(f'execute in minecraft:the_end if block 3 {y} 0 minecraft:bedrock '
                             'run gamerule keep_inventory'),
          'dragon fight generated the bedrock fountain rim')
    check('false' in command(f'execute in minecraft:the_end if block 1 {y} 0 minecraft:air '
                             'run gamerule keep_inventory'),
          'generated fountain is inactive before the tracked dragon dies')
    check('false' in command(f'execute in minecraft:the_end if block 0 {y + 2} 0 minecraft:bedrock '
                             'run gamerule keep_inventory'),
          'generated fountain has its native central bedrock pillar')
    wait(lambda: 'false' in command('execute in minecraft:the_end '
                                    'if entity @e[type=minecraft:ender_dragon,limit=1] '
                                    'run gamerule keep_inventory'),
         'dragon fight has spawned the tracked dragon', timeout=35)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'third End draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'loaded gun fires before dragon-fountain activation')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        gun = values['shooter']['players'].get('TaczShooter', {})
        return values if (gun.get('magazine') == 24
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          end['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == end['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'third End shot reaches shooter only', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == end['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == end['observer']['projectiles'],
          'Overworld observer receives no third End shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 24)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Killed' in command('execute in minecraft:the_end run '
                              'kill @e[type=minecraft:ender_dragon,limit=1]'),
          'native kill command reaches the dragon fight controller')
    wait(lambda: 'false' in command(f'execute in minecraft:the_end '
                                    f'if block 1 {y} 0 minecraft:end_portal '
                                    'run gamerule keep_inventory'),
         'dragon fight activates its generated exit portal', timeout=35)
    check('false' in command(f'execute in minecraft:the_end if block 3 {y} 0 minecraft:bedrock '
                             'run gamerule keep_inventory'),
          'activated generated fountain retains the bedrock rim')
    check('Teleported' in command(f'execute as TaczShooter at @s run tp @s 1.5 {y} 0.5 0 0'),
          'same-dimension placement contacts generated fountain portal')

    def returned():
        values = {role: state(role) for role in roles}
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('health') == 20
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 24
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    back = wait(returned, 'generated fountain returns shooter directly', timeout=40)
    server_number('seenCredits', 1)
    for role in roles:
        check(back[role]['players']['TaczShooter']['uuid'] ==
              before[role]['players']['TaczShooter']['uuid'],
              role + ' retains UUID after generated-fountain exit')
        check(back[role]['shots'].get('TaczShooter', 0) ==
              (alone[role] if role == 'shooter' else end[role])['shots'].get('TaczShooter', 0)
              and back[role]['projectiles'] ==
              (alone[role] if role == 'shooter' else end[role])['projectiles'],
              role + ' generated-fountain return does not replay the End shot')
        check(back[role]['players']['TaczShooter']['reload_phase'] == 'NOT_RELOADING'
              and not back[role]['players']['TaczShooter']['bolting'],
              role + ' has no stale gun action after generated-fountain exit')
    check(back['shooter']['reserve_slot_count'] == 58,
          'generated-fountain exit preserves reserve exactly once')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 24)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'fountain return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'generated-fountain traveler fires beside observer')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 23
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    final = wait(final_shot, 'post-fountain shared shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 23)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:overworld run forceload remove all')
    command('execute in minecraft:the_end run forceload remove all')
    report['end_fountain_scenario'] = {'fountain_y': y, 'before': before, 'end': end,
                                       'isolated_shot': alone, 'returned': back, 'final_shot': final}
