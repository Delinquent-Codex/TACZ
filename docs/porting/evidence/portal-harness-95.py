"""Native ignited Nether portals with a loaded gun and a remote observer."""
import time


def run_portal_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles)
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'portal probe begins with loaded shooter and observer in overworld')

    def portal(dimension, left, bottom):
        prefix = f'execute in minecraft:{dimension} run '
        command(prefix + f'forceload add {left - 16} -16 {left + 16} 16')
        command(prefix + f'fill {left - 3} {bottom - 1} -3 {left + 8} {bottom + 6} 3 minecraft:air')
        command(prefix + f'fill {left - 3} {bottom - 1} -3 {left + 8} {bottom - 1} 3 minecraft:stone')
        command(prefix + f'fill {left} {bottom} 0 {left + 3} {bottom} 0 minecraft:obsidian')
        command(prefix + f'fill {left} {bottom + 4} 0 {left + 3} {bottom + 4} 0 minecraft:obsidian')
        command(prefix + f'fill {left} {bottom + 1} 0 {left} {bottom + 3} 0 minecraft:obsidian')
        command(prefix + f'fill {left + 3} {bottom + 1} 0 {left + 3} {bottom + 3} 0 minecraft:obsidian')
        command(prefix + f'setblock {left + 1} {bottom + 1} 0 minecraft:fire')
        check('portal_ready' in command(
            f'execute in minecraft:{dimension} if block {left + 1} {bottom + 1} 0 '
            'minecraft:nether_portal run say portal_ready'),
            dimension + ' obsidian frame ignites into native portal blocks')

    portal('overworld', 30, -60)
    portal('the_nether', 3, 70)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 31.5 -59 0.5 0 0'),
          'move shooter into overworld portal without changing command dimension')

    def arrived():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_nether'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and shooter.get('gun') == 'tacz:ak47' and shooter.get('magazine') == 29
                          and shooter.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    nether = wait(arrived, 'native portal transfers loaded shooter to nether', timeout=35)
    check(nether['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'portal crossing preserves account UUID')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 10.5 70 1.5 0 0'),
          'move shooter clear of exit portal within nether')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'portal arrival draw ready', timeout=15)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'shooter fires after native portal travel')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (shooter.get('magazine') == 28
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          nether['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == nether['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'portal-traveled shooter shot in nether', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == nether['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == nether['observer']['projectiles'],
          'overworld observer receives no nether shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)

    # Minecraft 26.2's native portal cooldown is 300 ticks. The player has
    # moved out of the portal so it can expire before the return crossing.
    time.sleep(14)
    wait(lambda: command('data get entity TaczShooter PortalCooldown').rstrip().endswith(': 0'),
         'native portal cooldown expires outside portal', timeout=20)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 4.5 71 0.5 0 0'),
          'move shooter into nether portal without changing command dimension')

    def returned():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 28
                             for role in roles) else None

    back = wait(returned, 'native portal returns loaded shooter to overworld', timeout=35)
    check(back['observer']['shots'].get('TaczShooter', 0) ==
          nether['observer']['shots'].get('TaczShooter', 0)
          and back['observer']['projectiles'] == nether['observer']['projectiles'],
          'returning through portal does not replay nether shot')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 0.5 -60 0.5 0 0'),
          'move returned shooter beside observer within overworld')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'portal return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'returned shooter fires after both portal crossings')

    def return_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    fired_back = wait(return_shot, 'post-portal shot synchronized to both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:overworld run forceload remove all')
    command('execute in minecraft:the_nether run forceload remove all')
    report['portal_scenario'] = {'before': before, 'nether': nether, 'isolated_shot': alone,
                                 'returned': back, 'return_shot': fired_back}
