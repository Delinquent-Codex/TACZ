"""Controlled installed-client AK-47 player headshot probe."""
import time


def run_headshot_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' headshot input isolated')
    check('false' in command('gamerule naturalRegeneration false').lower(), 'native regeneration disabled for stable hit measurement')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'headshot shooter faces target at eye height')
    check('Teleported' in command('tp TaczObserver 0 -60 6 180 0'), 'headshot target on loaded firing line')
    time.sleep(1)
    before = {role: state(role) for role in roles}
    shooter = before['shooter']['players']['TaczShooter']
    target = before['observer']['players']['TaczObserver']
    check(shooter['gun'] == 'tacz:ak47' and shooter['magazine'] == 29 and shooter['chamber'], 'AK-47 ready for one headshot')
    check(all(before[role]['players']['TaczObserver']['health'] == 20 for role in roles), 'headshot target starts at full synchronized health')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'client accepts aimed headshot')

    def observed():
        values = {role: state(role) for role in roles}
        if any(len(values[role]['gun_hurts']) != len(before[role]['gun_hurts']) + 1 for role in roles):
            return None
        if any(values[role]['players'].get('TaczObserver', {}).get('health', 20) >= 20 for role in roles):
            return None
        return values

    after = wait(observed, 'AK-47 headshot and client hurt event', timeout=15)
    for role in roles:
        current = after[role]
        old = before[role]
        hit = current['gun_hurts'][-1]
        check(current['players']['TaczObserver']['health'] == 6.5, role + ' headshot applies shipped 1.5 multiplier to nine damage')
        check(hit['target'] == target['uuid'] and hit['attacker'] == shooter['uuid'] and hit['gun'] == 'tacz:ak47',
              role + ' headshot event retains target attacker and gun')
        check(hit['damage'] == 13.5 and hit['headshot'], role + ' client event reports multiplied headshot damage')
        check(len(current['gun_kills']) == len(old['gun_kills']), role + ' no gun kill event after single headshot')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1, role + ' one headshot trigger event')
        check(current['projectiles'] == old['projectiles'] + 1, role + ' one headshot projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    report['headshot_scenario'] = {'before': before, 'after': after,
                                   'shooter_uuid': shooter['uuid'], 'target_uuid': target['uuid']}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' headshot input restored')
