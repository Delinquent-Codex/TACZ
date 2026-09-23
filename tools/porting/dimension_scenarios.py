"""Native dedicated-server dimension transfers with a loaded TACZ gun."""
import time


def run_dimension_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    before = {role: state(role) for role in roles}
    check(all(before[role]['dimension'] == 'minecraft:overworld' for role in roles),
          'both installed clients begin in overworld')
    check(before['shooter']['players']['TaczShooter']['gun'] == 'tacz:ak47'
          and before['shooter']['players']['TaczShooter']['magazine'] == 29
          and before['shooter']['reserve_slot_count'] == 58,
          'dimension transfer starts with loaded gun and reserve')
    command('execute in minecraft:the_nether run forceload add -16 -16 16 32')
    command('execute in minecraft:the_nether run fill -3 70 -3 8 76 32 minecraft:air')
    command('execute in minecraft:the_nether run fill -3 69 -3 8 69 32 minecraft:stone')
    check('Teleported' in command('execute in minecraft:the_nether run tp TaczShooter 0.5 70 0.5 0 0'),
          'native command transfers shooter to nether')

    def split_worlds():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (values['shooter']['dimension'] == 'minecraft:the_nether'
                          and values['observer']['dimension'] == 'minecraft:overworld'
                          and shooter.get('gun') == 'tacz:ak47' and shooter.get('magazine') == 29
                          and values['shooter']['reserve_slot_count'] == 58
                          and 'TaczShooter' not in values['observer']['players']) else None

    separated = wait(split_worlds, 'gun and reserve preserved in separate dimensions', timeout=30)
    check(separated['shooter']['players']['TaczShooter']['uuid'] ==
          before['shooter']['players']['TaczShooter']['uuid'],
          'shooter account UUID retained across dimension transfer')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 29)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'nether draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'nether shooter client accepts one normal shot')

    def isolated_shot():
        values = {role: state(role) for role in roles}
        shooter = values['shooter']['players'].get('TaczShooter', {})
        return values if (shooter.get('magazine') == 28
                          and values['shooter']['shots'].get('TaczShooter', 0) ==
                          separated['shooter']['shots'].get('TaczShooter', 0) + 1
                          and values['shooter']['projectiles'] == separated['shooter']['projectiles'] + 1) else None

    alone = wait(isolated_shot, 'nether shot synchronized to shooter only', timeout=15)
    observer = state('observer')
    check(observer['shots'].get('TaczShooter', 0) == separated['observer']['shots'].get('TaczShooter', 0)
          and observer['projectiles'] == separated['observer']['projectiles'],
          'overworld observer receives no cross-dimension shot or projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    time.sleep(1)
    check('Teleported' in command('execute in minecraft:the_nether run tp TaczObserver 3.5 70 0.5 0 0'),
          'native command joins observer to nether')

    def reunited():
        values = {role: state(role) for role in roles}
        remote = values['observer']['players'].get('TaczShooter', {})
        return values if (values['observer']['dimension'] == 'minecraft:the_nether'
                          and remote.get('gun') == 'tacz:ak47' and remote.get('magazine') == 28
                          and 'TaczObserver' in values['shooter']['players']) else None

    joined = wait(reunited, 'late nether observer sees gun and magazine28', timeout=30)
    check(joined['observer']['shots'].get('TaczShooter', 0) ==
          separated['observer']['shots'].get('TaczShooter', 0)
          and joined['observer']['projectiles'] == separated['observer']['projectiles'],
          'joining dimension does not replay expired shot or projectile')
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'shared-dimension shooter accepts next shot')

    def shared_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             joined[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == joined[role]['projectiles'] + 1
                             for role in roles) else None

    shared = wait(shared_shot, 'nether shot visible to both installed clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    check('Teleported' in command('execute in minecraft:overworld run tp TaczShooter 0.5 -60 0.5 0 0'),
          'native command returns shooter to overworld')
    check('Teleported' in command('execute in minecraft:overworld run tp TaczObserver 3.5 -60 0.5 0 0'),
          'native command returns observer to overworld')

    def returned():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['dimension'] == 'minecraft:overworld'
                             and values[role]['players'].get('TaczShooter', {}).get('magazine') == 27
                             and values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                             for role in roles) else None

    back = wait(returned, 'both clients retain gun after returning to overworld', timeout=30)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    wait(lambda: state('shooter').get('draw_cooldown') == 0
         and not state('shooter').get('state_locked'), 'overworld return draw ready', timeout=15)
    time.sleep(1)
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS',
          'returned shooter fires after second dimension transfer')

    def return_shot():
        values = {role: state(role) for role in roles}
        return values if all(values[role]['players'].get('TaczShooter', {}).get('magazine') == 26
                             and values[role]['shots'].get('TaczShooter', 0) ==
                             back[role]['shots'].get('TaczShooter', 0) + 1
                             and values[role]['projectiles'] == back[role]['projectiles'] + 1
                             for role in roles) else None

    fired_back = wait(return_shot, 'overworld return shot synchronized to both clients', timeout=15)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 26)
    server_number('Inventory[{Slot:9b}].count', 58)
    command('execute in minecraft:the_nether run forceload remove all')
    report['dimension_scenario'] = {'before': before, 'separated': separated, 'isolated_shot': alone,
                                    'joined': joined, 'shared_shot': shared, 'returned': back,
                                    'return_shot': fired_back}
