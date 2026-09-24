"""Immediate blocked portal reentry followed by another native round trip."""
import re
import time

from portal_scenarios import run_portal_scenarios


def run_portal_repeat_scenarios(check, command, client, state, wait, server_number, report):
    run_portal_scenarios(check, command, client, state, wait, server_number, report)
    roles = ('shooter', 'observer')
    before = report['portal_scenario']['return_shot']
    check(all(before[role]['dimension'] == 'minecraft:overworld'
              and before[role]['players']['TaczShooter']['magazine'] == 27 for role in roles),
          'repeat portal probe begins after first completed round trip and shot')

    def cooldown():
        response = command('data get entity TaczShooter PortalCooldown')
        match = re.search(r': (\d+)\s*$', response)
        if match is None:
            raise AssertionError('Unreadable native portal cooldown: ' + response)
        return int(match.group(1))

    check(cooldown() > 0, 'native portal cooldown active after returning to overworld')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 31.5 -59 0.5 0 0'),
          'immediate repeat entry is placed inside overworld portal in same dimension')
    time.sleep(2.5)
    blocked = {role: state(role) for role in roles}
    for role in roles:
        check(blocked[role]['dimension'] == 'minecraft:overworld'
              and blocked[role]['players']['TaczShooter']['magazine'] == 27,
              role + ' remains in overworld with magazine27 during portal cooldown')
        check(blocked[role]['shots'].get('TaczShooter', 0) == before[role]['shots'].get('TaczShooter', 0)
              and blocked[role]['projectiles'] == before[role]['projectiles'],
              role + ' blocked reentry creates no shot or projectile')
    check(cooldown() > 0, 'portal contact refreshes native cooldown without teleporting')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 0.5 -60 0.5 0 0'),
          'leave overworld portal while cooldown expires')
    time.sleep(14)
    wait(lambda: cooldown() == 0, 'refreshed native portal cooldown expires', timeout=20)

    command('execute in minecraft:the_nether run forceload add -16 -16 16 16')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 31.5 -59 0.5 0 0'),
          'second overworld portal entry stays within current dimension')

    def nether_again():
        values = {role: state(role) for role in roles}
        gun = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_nether'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and gun.get('gun') == 'tacz:ak47' and gun.get('magazine') == 27
                          and gun.get('chamber') is True
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    nether = wait(nether_again, 'second native portal crossing retains gun', timeout=35)
    check(nether['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'repeated crossing retains player UUID')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 10.5 70 1.5 0 0'),
          'move shooter clear of nether exit portal')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'second nether arrival draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'shooter fires after second nether arrival')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        return values if (values['shooter']['players'].get('TaczShooter', {}).get('magazine') == 26
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          nether['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == nether['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'second nether shot reaches shooter', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == nether['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == nether['observer']['projectiles'],
          'observer receives no second cross-dimension shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 26)
    server_number('Inventory[{Slot:9b}].count', 58)
    time.sleep(14)
    wait(lambda: cooldown() == 0, 'second nether portal cooldown expires', timeout=20)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 4.5 71 0.5 0 0'),
          'second nether portal return begins in same dimension')

    def returned_again():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter']['magazine'] == 26
                             for role in roles) else None

    back = wait(returned_again, 'second native return preserves gun and ammo', timeout=35)
    check(back['observer']['shots'].get('TaczShooter', 0) ==
          nether['observer']['shots'].get('TaczShooter', 0)
          and back['observer']['projectiles'] == nether['observer']['projectiles'],
          'second return does not replay nether shot')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 26)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 0.5 -60 0.5 0 0'),
          'move returned shooter beside observer')
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'second overworld return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'shooter fires after repeated round trip')

    def final_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 25
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    fired_back = wait(final_shot, 'post-repeat shot synchronized to both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 25)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:the_nether run forceload remove all')
    report['portal_repeat_scenario'] = {'before': before, 'blocked': blocked, 'nether': nether,
                                        'isolated_shot': alone, 'returned': back, 'final_shot': fired_back}
