"""One shipped M700 projectile against two stationary native living targets."""
import re
import time
import uuid


def _entity_uuid(command, tag):
    response = command(f'data get entity @e[tag={tag},limit=1] UUID')
    values = re.search(r'\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]', response)
    if values is None:
        raise AssertionError('Unexpected native entity UUID response: ' + response)
    number = 0
    for value in values.groups():
        number = (number << 32) | (int(value) & 0xffffffff)
    return str(uuid.UUID(int=number))


def _entity_health(command, tag):
    response = command(f'data get entity @e[tag={tag},limit=1] Health')
    value = re.search(r':\s*(-?\d+(?:\.\d+)?)[fFdD]?$', response)
    if value is None:
        raise AssertionError('Unexpected native entity health response: ' + response)
    return float(value.group(1))


def run_penetration_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'],
              role + ' penetration input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'M700 shooter faces stationary living targets')
    check('Teleported' in command('tp TaczObserver 4 -60 0 0 0'), 'observer stands outside penetration path')
    for tag, z in [('tacz_probe_near', 3), ('tacz_probe_far', 5)]:
        response = command(f'summon minecraft:iron_golem 0 -60 {z} {{NoAI:1b,Tags:["{tag}"]}}')
        check('Summoned' in response, tag + ' native golem summoned')
    target_uuids = {tag: _entity_uuid(command, tag) for tag in ('tacz_probe_near', 'tacz_probe_far')}
    check(len(set(target_uuids.values())) == 2, 'two separate native living target UUIDs')
    for tag in target_uuids:
        check(_entity_health(command, tag) == 100, tag + ' starts at full native golem health')
    check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with '
          'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:m700",GunFireMode:"SEMI",'
          'GunCurrentAmmoCount:5,HasBulletInBarrel:1b}]'), 'native M700 with shipped pierce-two data equipped')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
          'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:30_06"}] 20'), 'native M700 reserve equipped')
    equipped = time.monotonic()

    def ready():
        values = {role: state(role) for role in roles}
        return values if (time.monotonic() - equipped >= 1 and
                          all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:m700'
                              and values[role]['players']['TaczShooter'].get('magazine') == 5
                              and values[role]['players']['TaczShooter'].get('chamber') is True for role in roles)
                          and values['shooter']['draw_cooldown'] == 0
                          and not values['shooter']['state_locked']) else None

    before = wait(ready, 'M700 equipment and draw synchronized')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'one M700 manual-action shot accepted')

    def observed():
        values = {role: state(role) for role in roles}
        return values if all(len(values[role]['gun_hurts']) >= len(before[role]['gun_hurts']) + 2
                             for role in roles) else None

    after = wait(observed, 'two native golem hurt events on both installed clients', timeout=15)
    targets = set(target_uuids.values())
    shooter_uuid = before['shooter']['players']['TaczShooter']['uuid']
    for role in roles:
        old, current = before[role], after[role]
        hits = current['gun_hurts'][len(old['gun_hurts']):]
        check(len(hits) == 2 and {hit['target'] for hit in hits} == targets,
              role + ' receives one hurt event for each golem UUID')
        check(all(hit['attacker'] == shooter_uuid and hit['gun'] == 'tacz:m700'
                  and hit['damage'] == 24 and not hit['headshot'] for hit in hits),
              role + ' observes shipped M700 body damage and shooter attribution')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1,
              role + ' observes exactly one M700 trigger')
        check(current['projectiles'] == old['projectiles'] + 1,
              role + ' tracks one M700 projectile for both hits')
        check(len(current['gun_kills']) == len(old['gun_kills']), role + ' observes no golem gun kill')
    health_after = {tag: _entity_health(command, tag) for tag in target_uuids}
    for tag, health in health_after.items():
        check(health == 76, tag + ' native health reflects one 24-damage body hit')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 4)
    server_number('Inventory[{Slot:9b}].count', 20)
    report['penetration_scenario'] = {'before': before, 'after': after, 'target_uuids': target_uuids,
                                      'health_after': health_after, 'shooter_uuid': shooter_uuid}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' penetration input restored')
