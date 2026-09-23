"""Controlled installed-client AK-47 hit against native diamond chest armor."""
import time


def run_armor_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' armor input isolated')
    check('false' in command('gamerule natural_health_regeneration false').lower(), 'armor case disables regeneration')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 12'), 'armor shooter aims at target body')
    check('Teleported' in command('tp TaczObserver 0 -60 3 180 0'), 'armored target on short firing line')
    check('Replaced a slot' in command('item replace entity TaczObserver armor.chest with minecraft:diamond_chestplate'),
          'native server equips diamond chestplate')
    time.sleep(1)
    before = {role: state(role) for role in roles}
    shooter = before['shooter']['players']['TaczShooter']
    target = before['observer']['players']['TaczObserver']
    check(shooter['gun'] == 'tacz:ak47' and shooter['magazine'] == 29 and shooter['chamber'], 'AK-47 ready for armored shot')
    for role in roles:
        value = before[role]['players']['TaczObserver']
        check(value['health'] == 20 and value['diamond_chestplate'] and value['armor'] == 8
              and value['armor_toughness'] == 2, role + ' target has synchronized native diamond chest armor')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'client accepts armored body shot')

    def observed():
        values = {role: state(role) for role in roles}
        return values if all(len(values[role]['gun_hurts']) == len(before[role]['gun_hurts']) + 1 for role in roles) else None

    after = wait(observed, 'armored AK-47 hit and client hurt event', timeout=15)
    # 75% normal damage is mitigated by 8 armor/2 toughness; the remaining
    # 25% uses the shipped bypasses_armor damage type. Minecraft 26.2
    # CombatRules.getDamageAfterAbsorb yields 5.319 + 2.25 = 7.569 damage.
    expected_health = 20 - (6.75 * (1 - (8 - 6.75 / (2 + 2 / 4)) / 25) + 2.25)
    for role in roles:
        current, old = after[role], before[role]
        health = current['players']['TaczObserver']['health']
        hit = current['gun_hurts'][-1]
        check(abs(health - expected_health) < 0.05, role + ' armor and piercing split matches target combat rules')
        check(hit['target'] == target['uuid'] and hit['attacker'] == shooter['uuid'] and hit['gun'] == 'tacz:ak47',
              role + ' armored hit retains target attacker and gun')
        check(hit['damage'] == 9 and not hit['headshot'], role + ' armored hit event reports base body damage')
        check(len(current['gun_kills']) == len(old['gun_kills']), role + ' armored shot does not kill')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1, role + ' one armored trigger')
        check(current['projectiles'] == old['projectiles'] + 1, role + ' one armored projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 28)
    server_number('Inventory[{Slot:9b}].count', 58)
    report['armor_scenario'] = {'before': before, 'after': after, 'expected_health': expected_health,
                                'shooter_uuid': shooter['uuid'], 'target_uuid': target['uuid']}
    for role in roles:
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(not value['input_barrier_owned'], role + ' armor input restored')
