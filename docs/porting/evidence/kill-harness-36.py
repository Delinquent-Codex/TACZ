"""Lethal AK-47 follow-up after a controlled nonlethal headshot."""


def run_kill_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    previous = report['headshot_scenario']
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' kill input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 6'), 'kill shooter aims at surviving target body')
    check('Teleported' in command('tp TaczObserver 0 -60 6 180 0'), 'kill target restored to firing line')
    wait(lambda: value if (value := state('shooter'))['shoot_cooldown'] == 0 else None,
         'AK-47 cooldown before lethal shot')
    before = {role: state(role) for role in roles}
    check(all(before[role]['players']['TaczObserver']['health'] == 6.5 for role in roles),
          'target retains 6.5 health with target native regeneration disabled')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'client accepts lethal AK-47 body shot')

    def observed():
        values = {role: state(role) for role in roles}
        return values if all(len(values[role]['gun_kills']) == len(before[role]['gun_kills']) + 1 for role in roles) else None

    after = wait(observed, 'AK-47 kill events on both clients', timeout=15)
    for role in roles:
        current, old = after[role], before[role]
        kill = current['gun_kills'][-1]
        check(kill['target'] == previous['target_uuid'] and kill['attacker'] == previous['shooter_uuid']
              and kill['gun'] == 'tacz:ak47', role + ' gun kill event retains target attacker and gun')
        check(kill['damage'] == 9 and not kill['headshot'], role + ' gun kill event reports body damage')
        check(len(current['gun_hurts']) == len(old['gun_hurts']), role + ' lethal shot emits no nonlethal hurt event')
        check(current['shots'].get('TaczShooter', 0) == old['shots'].get('TaczShooter', 0) + 1,
              role + ' one lethal trigger event')
        check(current['projectiles'] == old['projectiles'] + 1, role + ' one lethal projectile')
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount', 27)
    server_number('Inventory[{Slot:9b}].count', 58)
    report['kill_scenario'] = {'before': before, 'after': after,
                               'shooter_uuid': previous['shooter_uuid'], 'target_uuid': previous['target_uuid']}
    value = client('shooter', 'input_guard', enabled=False)['snapshot']
    check(not value['input_barrier_owned'], 'shooter input restored after kill')
