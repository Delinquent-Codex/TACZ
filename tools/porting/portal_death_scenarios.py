"""Death inside a native Nether portal before its 80-tick transfer threshold."""
import time


def run_portal_death_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles)
          and before['shooter']['players']['TaczShooter']['gun'] == 'tacz:ak47'
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'portal-death probe begins with loaded shooter in overworld')
    check('true' in command('gamerule keep_inventory true'),
          'disposable world retains gun and reserve on portal death')
    command('spawnpoint TaczShooter 0 -60 0')
    command('forceload add 14 -16 48 16')
    command('fill 27 -61 -3 38 -54 3 minecraft:air')
    command('fill 27 -61 -3 38 -61 3 minecraft:stone')
    command('fill 30 -60 0 33 -60 0 minecraft:obsidian')
    command('fill 30 -56 0 33 -56 0 minecraft:obsidian')
    command('fill 30 -59 0 30 -57 0 minecraft:obsidian')
    command('fill 33 -59 0 33 -57 0 minecraft:obsidian')
    command('setblock 31 -59 0 minecraft:fire')
    check('80' in command('execute if block 31 -59 0 minecraft:nether_portal '
                          'run gamerule players_nether_portal_default_delay'),
          'ignited native Nether portal has 80-tick survival delay')
    check('Teleported' in command('execute as TaczShooter at @s run tp @s 31.5 -59 0.5 0 0'),
          'same-dimension placement starts native portal contact')
    entered_at = time.monotonic()
    time.sleep(0.8)
    check('80' in command('execute as TaczShooter at @s if block ~ ~ ~ minecraft:nether_portal '
                          'run gamerule players_nether_portal_default_delay'),
          'living shooter remains inside native portal block')
    charging = {role: state(role) for role in roles}
    for role in roles:
        check(charging[role]['dimension'] == 'minecraft:overworld'
              and charging[role]['players']['TaczShooter']['magazine'] == 29,
              role + ' retains loaded gun before native portal threshold')
        check(charging[role]['shots'].get('TaczShooter', 0) ==
              before[role]['shots'].get('TaczShooter', 0)
              and charging[role]['projectiles'] == before[role]['projectiles'],
              role + ' portal charge creates no shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    contact_seconds = time.monotonic() - entered_at
    check(contact_seconds < 3.5,
          'portal contact observed before 80-tick transfer threshold')
    check('Killed TaczShooter' in command('kill TaczShooter'),
          'native command kills shooter while in portal block')
    dead = wait(lambda: value if ('DeathScreen' in (value := state('shooter'))['screen']
                                  and value['players']['TaczShooter']['health'] <= 0) else None,
                'portal-contact death screen and zero health', timeout=15)
    check(dead['dimension'] == 'minecraft:overworld'
          and dead['players']['TaczShooter']['health'] <= 0,
          'shooter died in overworld before Nether transfer')
    time.sleep(5)
    still_dead = state('shooter')
    check('DeathScreen' in still_dead['screen']
          and still_dead['dimension'] == 'minecraft:overworld',
          'pending portal transfer does not run after death')
    check(client('shooter', 'respawn')['status'] == 'ok',
          'native client respawn packet sent after portal death')

    def reborn():
        values = {role: state(role) for role in roles}
        if values['shooter']['screen'] != 'null' or not values['shooter']['connected']:
            return None
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('health') == 20
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             and values[role]['players']['TaczShooter'].get('magazine') == 29
                             and values[role]['players']['TaczShooter'].get('chamber') is True
                             for role in roles) else None

    back = wait(reborn, 'portal-death respawn returns loaded shooter in overworld', timeout=30)
    for role in roles:
        current = back[role]['players']['TaczShooter']
        original = before[role]['players']['TaczShooter']
        check(current['uuid'] == original['uuid'],
              role + ' retains account UUID through portal death and respawn')
        check(back[role]['shots'].get('TaczShooter', 0) ==
              before[role]['shots'].get('TaczShooter', 0)
              and back[role]['projectiles'] == before[role]['projectiles'],
              role + ' portal death creates no duplicate shot or projectile')
        check(current['reload_phase'] == 'NOT_RELOADING' and not current['bolting'],
              role + ' portal-death respawn has no stale reload or bolt state')
    check(back['shooter']['reserve_slot_count'] == 58,
          'portal-death respawn retains reserve exactly once')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'portal-death respawn draw ready', timeout=15)
    time.sleep(5)
    check(all(state(role)['dimension'] == 'minecraft:overworld' for role in roles),
          'respawned shooter does not belatedly transfer to Nether')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'portal-death survivor fires one normal shared shot')

    def fired():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 28
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    final = wait(fired, 'post-portal-death shot reaches both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('false' in command('gamerule keep_inventory false'),
          'restore disposable world death rule')
    command('forceload remove all')
    report['portal_death_scenario'] = {'before': before, 'charging': charging,
                                       'contact_seconds_before_kill': contact_seconds,
                                       'dead': dead, 'still_dead': still_dead,
                                       'respawned': back, 'final_shot': final}
