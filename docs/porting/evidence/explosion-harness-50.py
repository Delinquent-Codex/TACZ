"""Installed M320 direct-hit blast, collateral, distance and block-preservation case."""
import time

from penetration_scenarios import _entity_health, _entity_uuid


def run_explosion_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' explosion input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'M320 shooter faces native target')
    check('Teleported' in command('tp TaczObserver 12 -60 0 0 0'), 'observer stands outside blast radius')
    for tag, x in [('tacz_blast_direct', 0), ('tacz_blast_collateral', 3), ('tacz_blast_far', 10)]:
        response = command(f'summon minecraft:iron_golem {x} -60 8 {{NoAI:1b,Tags:["{tag}"]}}')
        check('Summoned' in response, tag + ' native golem summoned')
    target_uuids = {tag: _entity_uuid(command, tag)
                    for tag in ('tacz_blast_direct', 'tacz_blast_collateral', 'tacz_blast_far')}
    check(len(set(target_uuids.values())) == 3, 'three separate native living target UUIDs')
    check(all(_entity_health(command, tag) == 100 for tag in target_uuids), 'all blast targets start at full health')
    check('Changed the block' in command('setblock 0 -59 10 minecraft:stone'), 'stone block placed inside blast radius')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with '
          'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:m320",GunFireMode:"SEMI",'
          'GunCurrentAmmoCount:1,HasBulletInBarrel:1b}]'), 'native shipped M320 equipped')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
          'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:40mm"}] 5'), 'native 40mm reserve equipped')
    equipped = time.monotonic()

    def ready():
        values = {role: state(role) for role in roles}
        return values if (time.monotonic() - equipped >= 1 and
                          all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:m320'
                              and values[role]['players']['TaczShooter'].get('magazine') == 1 for role in roles)
                          and values['shooter']['draw_cooldown'] == 0
                          and not values['shooter']['state_locked']) else None

    before = wait(ready, 'M320 equipment and draw synchronized')
    check(not before['shooter']['auto_reload'], 'M320 blast test has automatic reload disabled')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'one M320 explosive shot accepted')

    def exploded():
        direct = _entity_health(command, 'tacz_blast_direct')
        collateral = _entity_health(command, 'tacz_blast_collateral')
        return (direct, collateral) if direct < 100 and collateral < 100 else None

    wait(exploded, 'M320 impact and nearby native blast damage', timeout=15)
    time.sleep(0.4)
    health_after = {tag: _entity_health(command, tag) for tag in target_uuids}
    check(0 < health_after['tacz_blast_direct'] < 90, 'direct golem takes gun hit and blast damage without dying')
    check(0 < health_after['tacz_blast_collateral'] < 100, 'nearby golem takes collateral blast damage')
    check(health_after['tacz_blast_far'] == 100, 'out-of-range golem remains unharmed')
    intact = command('execute if block 0 -59 10 minecraft:stone run '
                     'data get entity @e[tag=tacz_blast_direct,limit=1] Health')
    check('Iron Golem has the following entity data:' in intact, 'M320 default blast preserves stone block')

    after = {role: state(role) for role in roles}
    shooter_uuid = before['shooter']['players']['TaczShooter']['uuid']
    direct_uuid = target_uuids['tacz_blast_direct']
    for role in roles:
        current, old = after[role], before[role]
        hits = current['gun_hurts'][len(old['gun_hurts']):]
        check(len(hits) == 1 and hits[0]['target'] == direct_uuid and hits[0]['attacker'] == shooter_uuid,
              role + ' receives one direct M320 hurt event with native target and shooter')
        check(hits[0]['gun'] == 'tacz:m320' and hits[0]['damage'] == 10 and not hits[0]['headshot'],
              role + ' direct M320 gun event reports shipped body damage')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1,
              role + ' observes exactly one M320 trigger')
        check(current['projectiles'] == old['projectiles'] + 1,
              role + ' tracks one M320 explosive projectile')
        check(len(current['gun_kills']) == len(old['gun_kills']), role + ' observes no gun kill')
        check(current['players']['TaczShooter']['health'] == 20
              and current['players']['TaczObserver']['health'] == 20,
              role + ' both players remain outside damaging blast')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 0)
    server_number('Inventory[{Slot:9b}].count', 5)
    report['explosion_scenario'] = {'before': before, 'after': after, 'target_uuids': target_uuids,
                                    'health_after': health_after, 'shooter_uuid': shooter_uuid,
                                    'stone_response': intact}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' explosion input restored')
