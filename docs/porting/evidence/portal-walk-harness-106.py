"""Walk a loaded client through paired native Nether portals using keyUp."""
import time


def run_portal_walk_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles)
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'walk probe begins with loaded shooter and observer in overworld')

    def portal(dimension, left, bottom):
        prefix = f'execute in minecraft:{dimension} run '
        command(prefix + f'forceload add {left - 16} -16 {left + 16} 16')
        command(prefix + f'fill {left - 3} {bottom - 1} -6 {left + 8} {bottom + 6} 3 minecraft:air')
        command(prefix + f'fill {left - 3} {bottom - 1} -6 {left + 8} {bottom - 1} 3 minecraft:stone')
        command(prefix + f'fill {left} {bottom} 0 {left + 3} {bottom} 0 minecraft:obsidian')
        command(prefix + f'fill {left} {bottom + 4} 0 {left + 3} {bottom + 4} 0 minecraft:obsidian')
        command(prefix + f'fill {left} {bottom + 1} 0 {left} {bottom + 3} 0 minecraft:obsidian')
        command(prefix + f'fill {left + 3} {bottom + 1} 0 {left + 3} {bottom + 3} 0 minecraft:obsidian')
        command(prefix + f'setblock {left + 1} {bottom + 1} 0 minecraft:fire')
        check('80' in command(prefix + f'execute if block {left + 1} {bottom + 1} 0 '
                              'minecraft:nether_portal run gamerule players_nether_portal_default_delay'),
              dimension + ' native portal ignites with survival delay')
        command(prefix + f'fill {left + 1} {bottom} -5 {left + 2} {bottom} -1 minecraft:stone')
        command(prefix + f'fill {left + 1} {bottom + 1} 1 {left + 2} {bottom + 3} 1 minecraft:stone')

    portal('overworld', 30, -60)
    portal('the_nether', 3, 70)

    def walk_into_portal(dimension, x, y):
        check('Teleported' in command(f'execute as TaczShooter at @s run tp @s {x}.5 {y} -3.5 0 0'),
              dimension + ' staging point is outside portal in current dimension')
        staged = state('shooter')
        check(staged['dimension'] == f'minecraft:{dimension}'
              and staged['players']['TaczShooter']['position'][2] < -2.5,
              dimension + ' shooter starts behind portal')
        started = client('shooter', 'forward_key', enabled=True)['snapshot']
        check(started['forward_key_down'] and started['screen'] == 'null',
              dimension + ' native forward key is held in the world')

        def touching():
            response = command('execute as TaczShooter at @s if block ~ ~ ~ minecraft:nether_portal '
                               'run gamerule players_nether_portal_default_delay')
            return response if '80' in response else None

        wait(touching, dimension + ' forward movement reaches native portal block', timeout=20)
        contact = state('shooter')
        check(contact['dimension'] == f'minecraft:{dimension}'
              and contact['players']['TaczShooter']['position'][2] > -1.0,
              dimension + ' player moved into portal without position command')
        return staged, contact

    overworld_stage, overworld_contact = walk_into_portal('overworld', 31, -59)

    def nether_arrival():
        values = {role: state(role) for role in roles}
        gun = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_nether'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and gun.get('gun') == 'tacz:ak47'
                          and gun.get('magazine') == 29 and gun.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    nether = wait(nether_arrival, 'walked portal transfers loaded shooter to Nether', timeout=35)
    check(not client('shooter', 'forward_key', enabled=False)['snapshot']['forward_key_down'],
          'release forward key after Nether arrival')
    check(nether['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'walked Nether portal preserves account UUID')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 10.5 71 1.5 0 0'),
          'move shooter clear of Nether exit within same dimension')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'Nether draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'walked Nether traveler fires one normal shot')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        return values if (values['shooter']['players'].get('TaczShooter', {}).get('magazine') == 28
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          nether['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == nether['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'walked Nether traveler shot reaches shooter', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == nether['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == nether['observer']['projectiles'],
          'Overworld observer receives no walked Nether shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    time.sleep(14)
    wait(lambda: command('data get entity TaczShooter PortalCooldown').rstrip().endswith(': 0'),
         'native Nether portal cooldown clears outside portal', timeout=20)

    nether_stage, nether_contact = walk_into_portal('the_nether', 4, 71)

    def returned():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 28
                             for role in roles) else None

    back = wait(returned, 'second walked portal returns loaded shooter to overworld', timeout=35)
    check(not client('shooter', 'forward_key', enabled=False)['snapshot']['forward_key_down'],
          'release forward key after Overworld return')
    check(back['observer']['shots'].get('TaczShooter', 0) ==
          nether['observer']['shots'].get('TaczShooter', 0)
          and back['observer']['projectiles'] == nether['observer']['projectiles'],
          'walked return does not replay Nether shot')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 0.5 -60 0.5 0 0'),
          'move returned shooter beside observer in current dimension')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'walked return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'walked portal traveler fires beside observer')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    final = wait(final_shot, 'walked portal post-return shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:overworld run forceload remove all')
    command('execute in minecraft:the_nether run forceload remove all')
    report['portal_walk_scenario'] = {'before': before, 'overworld_stage': overworld_stage,
                                      'overworld_contact': overworld_contact, 'nether': nether,
                                      'isolated_shot': alone, 'nether_stage': nether_stage,
                                      'nether_contact': nether_contact, 'returned': back,
                                      'final_shot': final}
