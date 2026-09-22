"""Controlled installed-client AK-47 hit and attribution probe."""
import time


def run_damage_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' damage input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 6'), 'damage shooter aimed at target body')
    check('Teleported' in command('tp TaczObserver 0 -60 6 180 0'), 'damage target on loaded firing line')
    time.sleep(1)
    before = {role: state(role) for role in roles}
    shooter = before['shooter']['players']['TaczShooter']
    target = before['observer']['players']['TaczObserver']
    check(shooter['gun'] == 'tacz:ak47' and shooter['magazine'] == 29 and shooter['chamber'], 'AK-47 ready for one damage shot')
    check(target['health'] == 20 and before['shooter']['players']['TaczObserver']['health'] == 20, 'target starts at full synchronized health')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'client accepts aimed AK-47 shot')

    def observed():
        values = {role: state(role) for role in roles}
        if any(len(values[role]['gun_hurts']) != len(before[role]['gun_hurts']) + 1 for role in roles):
            return None
        if any(values[role]['players']['TaczObserver']['health'] >= 20 for role in roles):
            return None
        return values

    after = wait(observed, 'AK-47 hit and client hurt event', timeout=15)
    for role in roles:
        current = after[role]
        old = before[role]
        hit = current['gun_hurts'][-1]
        check(0 < current['players']['TaczObserver']['health'] < 20, role + ' target survives with reduced health')
        check(current['players']['TaczObserver']['health'] == 11, role + ' unarmored body hit applies shipped nine damage')
        check(hit['target'] == target['uuid'] and hit['attacker'] == shooter['uuid'] and hit['gun'] == 'tacz:ak47',
              role + ' gun hurt event retains target attacker and gun')
        check(hit['damage'] == 9 and not hit['headshot'], role + ' client event reports shipped body damage')
        check(len(current['gun_kills']) == len(old['gun_kills']), role + ' no gun kill event')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1, role + ' one gun trigger event')
        check(current['projectiles'] == old['projectiles'] + 1, role + ' one tracked projectile')
    check(after['shooter']['players']['TaczObserver']['health'] == after['observer']['players']['TaczObserver']['health'],
          'remote and local target health reconcile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    report['damage_scenario'] = {'before': before, 'after': after,
                                 'shooter_uuid': shooter['uuid'], 'target_uuid': target['uuid']}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' damage input restored')
