"""Drive the public gun operator on a native villager via the test-only server fixture."""
import time

from penetration_scenarios import _entity_health, _entity_uuid


def run_nonplayer_scenarios(check, command, client, state, wait, report):
    roles = ('shooter', 'observer')
    for role in roles:
        snapshot = client(role, 'input_guard', enabled=True)['snapshot']
        check(snapshot['input_barrier_owned'] and snapshot['native_input_suppressed'],
              role + ' non-player input isolated')
    check('Teleported' in command('tp TaczShooter 4 -60 0 0 0'), 'shooter tracks mob from beside firing line')
    check('Teleported' in command('tp TaczObserver 5 -60 0 0 0'), 'observer tracks mob from beside firing line')
    check('Summoned' in command('summon minecraft:villager 0 -60 0 {NoAI:1b,Tags:["tacz_probe_operator"]}'),
          'native stationary villager summoned')
    check('Summoned' in command('summon minecraft:iron_golem 0 -60 5 {NoAI:1b,Tags:["tacz_probe_target"]}'),
          'native stationary golem target summoned')
    shooter_uuid = _entity_uuid(command, 'tacz_probe_operator')
    target_uuid = _entity_uuid(command, 'tacz_probe_target')
    check(shooter_uuid != target_uuid and _entity_health(command, 'tacz_probe_target') == 100,
          'distinct living shooter and full-health target')
    check('Teleported' in command('tp @e[tag=tacz_probe_operator,limit=1] 0 -60 0 0 0'),
          'villager faces golem at body height')
    check('Replaced a slot' in command('item replace entity @e[tag=tacz_probe_operator,limit=1] weapon.mainhand with '
          'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47",GunFireMode:"SEMI",'
          'GunCurrentAmmoCount:0,HasBulletInBarrel:1b}]'), 'native command equips one chambered AK-47 round')
    check('Added tag' in command('tag @e[tag=tacz_probe_operator,limit=1] add tacz_probe_draw'),
          'request public non-player draw through diagnostic tag')
    time.sleep(1.5)
    before = {role: state(role) for role in roles}
    check('Added tag' in command('tag @e[tag=tacz_probe_operator,limit=1] add tacz_probe_fire'),
          'request public non-player shoot through diagnostic tag')

    def observed():
        snapshots = {role: state(role) for role in roles}
        return snapshots if all(any(hit['target'] == target_uuid and hit['attacker'] == shooter_uuid
                                    for hit in snapshots[role]['gun_hurts'][len(before[role]['gun_hurts']):])
                                for role in roles) else None

    after = wait(observed, 'villager shot and client gun hurt events', timeout=15)
    check(_entity_health(command, 'tacz_probe_target') == 91, 'native golem health reflects shipped AK body damage')
    for role in roles:
        old, current = before[role], after[role]
        hits = current['gun_hurts'][len(old['gun_hurts']):]
        check(len(hits) == 1 and hits[0]['target'] == target_uuid and hits[0]['attacker'] == shooter_uuid
              and hits[0]['gun'] == 'tacz:ak47' and hits[0]['damage'] == 9,
              role + ' observes one attributed villager AK-47 hit')
        check(current['shots'].get('Villager', 0) == old['shots'].get('Villager', 0) + 1,
              role + ' observes one non-player gun trigger')
        check(current['projectiles'] == old['projectiles'] + 1,
              role + ' tracks one non-player projectile')
    time.sleep(0.5)
    check('Added tag' in command('tag @e[tag=tacz_probe_operator,limit=1] add tacz_probe_fire'),
          'request non-player dry-fire through diagnostic tag')
    time.sleep(0.5)
    settled = {role: state(role) for role in roles}
    for role in roles:
        check(settled[role]['shots'].get('Villager', 0) == after[role]['shots'].get('Villager', 0)
              and settled[role]['projectiles'] == after[role]['projectiles']
              and len(settled[role]['gun_hurts']) == len(after[role]['gun_hurts']),
              role + ' dry fire adds no gun event, projectile, or hit')
        snapshot = client(role, 'input_guard', enabled=False)['snapshot']
        check(not snapshot['input_barrier_owned'], role + ' non-player input restored')
    report['nonplayer_scenario'] = {'before': before, 'after': after, 'settled': settled,
                                    'shooter_uuid': shooter_uuid, 'target_uuid': target_uuid,
                                    'health_after': _entity_health(command, 'tacz_probe_target')}
