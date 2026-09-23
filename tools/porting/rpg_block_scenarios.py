"""Shipped RPG-7 explosion against destructible and resistant native blocks."""
import time


def run_rpg_block_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' RPG input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'RPG shooter faces block target')
    check('Teleported' in command('tp TaczObserver 12 -60 0 0 0'), 'RPG observer outside blast radius')
    check('Successfully filled' in command('fill -2 -60 8 2 -56 8 minecraft:white_wool'),
          'native wool impact wall placed')
    check('Changed the block' in command('setblock 0 -58 9 minecraft:obsidian'),
          'resistant obsidian block placed behind impact')
    check('Changed the block' in command('setblock 12 -59 8 minecraft:white_wool'),
          'distant control wool block placed')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with '
          'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:rpg7",GunFireMode:"SEMI",'
          'GunCurrentAmmoCount:1,HasBulletInBarrel:1b}]'), 'native shipped RPG-7 equipped')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
          'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:rpg_rocket"}] 2'), 'native RPG reserve equipped')
    equipped = time.monotonic()

    def ready():
        values = {role: state(role) for role in roles}
        return values if (time.monotonic() - equipped >= 1 and
                          all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:rpg7'
                              and values[role]['players']['TaczShooter'].get('magazine') == 1 for role in roles)
                          and values['shooter']['draw_cooldown'] == 0
                          and not values['shooter']['state_locked']) else None

    before = wait(ready, 'RPG equipment and draw synchronized')
    check(not before['shooter']['auto_reload'], 'RPG test has automatic reload disabled')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'one RPG rocket accepted')

    def destroyed():
        result = command('execute if block 0 -59 8 minecraft:air run data get entity TaczShooter Health')
        return result if 'TaczShooter has the following entity data:' in result else None

    impact_response = wait(destroyed, 'RPG explosion destroys central wool block', timeout=15)
    time.sleep(0.4)
    obsidian_response = command('execute if block 0 -58 9 minecraft:obsidian run '
                                'data get entity TaczShooter Health')
    check('TaczShooter has the following entity data:' in obsidian_response,
          'resistant obsidian behind blast remains')
    distant_response = command('execute if block 12 -59 8 minecraft:white_wool run '
                               'data get entity TaczShooter Health')
    check('TaczShooter has the following entity data:' in distant_response,
          'distant wool outside blast remains')
    check('No entity was found' in command('data get entity @e[type=tacz:bullet,limit=1] Pos',
                                           allow_no_entity=True), 'RPG rocket removed after block impact')

    after = {role: state(role) for role in roles}
    for role in roles:
        current, old = after[role], before[role]
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1,
              role + ' observes one RPG trigger')
        check(current['projectiles'] == old['projectiles'] + 1,
              role + ' tracks one RPG rocket')
        check(len(current['gun_hurts']) == len(old['gun_hurts'])
              and len(current['gun_kills']) == len(old['gun_kills']),
              role + ' block blast emits no living gun-hit event')
        check(current['players']['TaczShooter']['health'] == 20
              and current['players']['TaczObserver']['health'] == 20,
              role + ' both players remain outside RPG blast')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 0)
    server_number('Inventory[{Slot:9b}].count', 2)
    report['rpg_block_scenario'] = {'before': before, 'after': after, 'impact_response': impact_response,
                                    'obsidian_response': obsidian_response, 'distant_response': distant_response}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' RPG input restored')
