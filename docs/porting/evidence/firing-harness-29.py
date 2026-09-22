"""Installed-client operator tests using unchanged M16A4, Rhino and minigun data."""
import re
import time


def run_firing_scenarios(check, command, client, state, wait, server_number, report, reject_probe=False):
    roles = ('shooter', 'observer')
    path = 'Inventory[{Slot:0b}].components."minecraft:custom_data".'
    results = report['firing_scenarios'] = {}
    # Keep native keyboard/mouse input from changing this operator-only scene,
    # including between sequences and during cooling. World/server ticking stays live.
    for role in roles:
        value = client(role, 'input_guard', enabled=True)['snapshot']
        check(value['input_barrier_owned'] and value['native_input_suppressed'], role + ' firing scene input isolated')
    check('Teleported' in command('tp TaczShooter 0 -60 0 0 0'), 'shooter faces the loaded backstop')
    check('Teleported' in command('tp TaczObserver 3 -60 0 0 0'), 'observer positioned beside shooter')

    def both():
        return {role: state(role) for role in roles}

    def player(value):
        return value['players'].get('TaczShooter', {})

    def delta(value, before, key):
        return value[key].get('TaczShooter', 0) - before[key].get('TaczShooter', 0)

    def ready(gun, mode, magazine, chamber):
        values = both()
        return values if (all(player(v).get('gun') == 'tacz:' + gun
                             and player(v)['fire_mode'] == mode
                             and player(v)['magazine'] == (v['inventory_ammo'].get('tacz:308', 0)
                                 if gun == 'minigun' and role == 'shooter' else magazine)
                             and player(v)['chamber'] is chamber and not player(v)['reloading'] for role, v in values.items())
                          and not values['shooter']['state_locked'] and values['shooter']['draw_cooldown'] == 0
                          and values['shooter']['shoot_cooldown'] == 0) else None

    def setup(gun, mode, magazine, chamber, ammo):
        count = 60 if ammo == '556x45' else 48
        spec = f'tacz:modern_kinetic_gun[minecraft:custom_data={{GunId:"tacz:{gun}",GunFireMode:"{mode}",GunCurrentAmmoCount:{magazine},HasBulletInBarrel:{int(chamber)}b}}]'
        check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with ' + spec), gun + ' native setup')
        check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
              f'tacz:ammo[minecraft:custom_data={{AmmoId:"tacz:{ammo}"}}] {count}'), gun + ' legal reserve setup')
        equipped = time.monotonic()
        values = wait(lambda: ready(gun, mode, magazine, chamber) if time.monotonic() - equipped > 1 else None,
                      gun + ' draw and equipment')
        check(not values['shooter']['auto_reload'], gun + ' auto-reload disabled')
        return values

    def finish(label, before, gun, mode, magazine, chamber, shots, fires, projectiles):
        wait(lambda: ready(gun, mode, magazine, chamber), label + ' authoritative gun sync')
        time.sleep(0.8)  # Beyond a burst interval: catch continuing scheduled tasks.
        values = both()
        for role, value in values.items():
            check(player(value).get('magazine') == magazine and player(value).get('chamber') is chamber, label + ' ' + role + ' stable ammo')
            check(delta(value, before[role], 'shots') == shots, label + ' ' + role + ' exact trigger events')
            check(delta(value, before[role], 'fires') == fires[role], label + ' ' + role + ' exact fire events')
            check(value['projectiles'] - before[role]['projectiles'] == projectiles, label + ' ' + role + ' exact projectiles')
            check(value['reloads'] == before[role]['reloads'], label + ' ' + role + ' no reload')
        reserve = before['shooter']['reserve_slot_count']
        check(values['shooter']['reserve_slot_count'] == reserve, label + ' reserve retained')
        server_number(path + 'GunCurrentAmmoCount', magazine)
        server_number(path + 'HasBulletInBarrel', int(chamber))
        server_number('Inventory[{Slot:9b}].count', reserve)
        results[label] = {'observed': values}
        return values

    before = setup('m16a4', 'BURST', 30, True, '556x45')
    check(client('shooter', 'shoot_twice')['shoot_results'] == ['SUCCESS', 'COOL_DOWN'], 'M16A4 immediate second trigger rejected')
    finish('m16a4_burst', before, 'm16a4', 'BURST', 27, True, 1, dict.fromkeys(roles, 3), 3)

    def select(mode):
        before = both()
        client('shooter', 'fire_select')
        values = wait(lambda: ready('m16a4', mode, 27, True), 'M16A4 ' + mode + ' sync')
        for role, value in values.items():
            check(delta(value, before[role], 'fire_selects') == 1, role + ' one fire-select event to ' + mode)
        check(command('data get entity TaczShooter ' + path + 'GunFireMode').strip().endswith('"' + mode + '"'), 'server selected mode ' + mode)
        return values

    before = select('SEMI')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'M16A4 selected semi shot accepted')
    finish('m16a4_semi', before, 'm16a4', 'SEMI', 26, True, 1, dict.fromkeys(roles, 1), 1)

    before = setup('m16a4', 'BURST', 1, True, '556x45')
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'M16A4 short burst accepted')
    # Baseline posts the server fire event before checking whether a scheduled
    # cycle can consume ammo. The third remote event has no third projectile.
    finish('m16a4_short_burst', before, 'm16a4', 'BURST', 0, False, 1,
           {'shooter': 2, 'observer': 3}, 2)

    def sequence(pressed, released, hot=False):
        client('shooter', 'sequence_start', pressed_ticks=pressed, released_ticks=released, stop_on_overheat=hot)
        value = wait(lambda: (v if v['sequence']['status'] != 'running' else None) if (v := state('shooter')) else None,
                     'operator sequence', timeout=65)
        check(value['sequence']['status'] == ('overheated' if hot else 'finished'), 'bounded operator sequence completed')
        response = client('shooter', 'sequence_result')
        check(response['snapshot']['input_barrier_owned'], 'operator sequence restores the outer input barrier')
        check(all(t['native_input_suppressed'] for t in response['trace']), 'operator sequence exclusively advances charge each tick')
        return response['trace']

    before = setup('rhino357', 'SEMI', 6, False, '357mag')
    trace = sequence(2, 4)
    check(not any(t['ready'] for t in trace), 'Rhino partial charge never reaches fire threshold')
    check(0 < trace[1]['after_charge'] < 0.3 and trace[-1]['after_charge'] == 0, 'Rhino partial charge increases then decays to zero')
    finish('rhino_cancel_charge', before, 'rhino357', 'SEMI', 6, False, 0, dict.fromkeys(roles, 0), 0)
    results['rhino_cancel_charge']['trace'] = trace
    before = both()
    trace = sequence(3, 8)
    check([t.get('shoot_result') for t in trace if t['ready']] == ['SUCCESS'], 'Rhino three charge ticks produce one shot')
    check(not trace[0]['ready'] and not trace[1]['ready'] and trace[2]['ready'], 'Rhino fires only after the third charge tick')
    check(trace[-1]['after_charge'] == 0, 'Rhino charge resets after firing')
    finish('rhino_full_charge', before, 'rhino357', 'SEMI', 5, False, 1, dict.fromkeys(roles, 1), 1)
    results['rhino_full_charge']['trace'] = trace

    setup('minigun', 'AUTO', 30, False, '308')
    for slot in range(1, 8):
        check('Replaced a slot' in command(f'item replace entity TaczShooter inventory.{slot} with '
              'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:308"}] 48'), 'minigun reserve stack ' + str(slot))
    wait(lambda: state('shooter')['inventory_ammo'].get('tacz:308') == 384, 'minigun eight legal ammo stacks')
    before = both()
    trace = sequence(1000, 0, hot=True)
    def heat_locked():
        values = both()
        shooter = values['shooter']
        return values if (all(player(x)['overheated'] for x in values.values())
                          and player(shooter)['magazine'] == shooter['inventory_ammo'].get('tacz:308', 0)) else None
    values = wait(heat_locked, 'minigun heat lock and local HUD inventory cache')
    check(all(player(v)['heat'] == 500 for v in values.values()), 'minigun reaches shipped heat cap')
    server_number(path + 'OverHeated', 1)
    hot = client('shooter', 'shoot')
    check(hot['shoot_result'] == 'OVERHEATED', 'minigun rejects shot while heat locked')
    fired = values['observer']['projectiles'] - before['observer']['projectiles']
    check(250 <= fired < 384, 'minigun reaches heat cap through real shots before reserve exhaustion')
    requested = sum(t.get('shoot_result') == 'SUCCESS' for t in trace)
    prediction = {'successful_client_calls': requested,
                  'local_trigger_events': delta(values['shooter'], before['shooter'], 'shots'),
                  'local_fire_events': delta(values['shooter'], before['shooter'], 'fires'),
                  'remote_trigger_events': delta(values['observer'], before['observer'], 'shots'),
                  'remote_fire_events': delta(values['observer'], before['observer'], 'fires'),
                  'authoritative_projectiles': fired}
    # Local events precede network acceptance. The server independently checks
    # cooldown/heat; it does not roll back already-emitted local prediction events.
    # Preserve every difference, without treating local callbacks as server acks.
    check(prediction['local_trigger_events'] == requested, 'minigun local triggers match successful operator calls')
    check(fired <= prediction['local_fire_events'] <= requested, 'minigun local fire callbacks cover authoritative rounds without exceeding requests')
    check(fired <= requested, 'minigun authoritative rounds do not exceed client requests')
    for role, value in values.items():
        check(value['projectiles'] - before[role]['projectiles'] == fired, role + ' synchronized minigun projectile total')
        if role == 'observer':
            check(delta(value, before[role], 'shots') == fired and delta(value, before[role], 'fires') == fired,
                  'minigun authoritative remote trigger/fire events match projectiles')
        expected = 384 - fired if role == 'shooter' else 30
        check(player(value)['magazine'] == expected and player(value)['chamber'] is False,
              role + ' minigun local inventory cache or remote stored magazine')
    check(values['shooter']['inventory_ammo']['tacz:308'] == 384 - fired, 'minigun inventory consumption matches actual bullets')

    def server_reserve(expected):
        total = 0
        for slot in range(9, 17):
            value = command(f'data get entity TaczShooter Inventory[{{Slot:{slot}b}}].count')
            if 'Found no elements' not in value:
                match = re.search(r': (\d+)$', value)
                check(match is not None, 'native minigun reserve query parses')
                total += int(match[1])
        check(total == expected, 'server minigun reserve=' + str(expected))

    server_reserve(384 - fired)
    server_number(path + 'GunCurrentAmmoCount', 30)
    server_number(path + 'HasBulletInBarrel', 0)
    results['minigun_heat_lock'] = {'shots': fired, 'prediction': prediction, 'observed': values, 'trace': trace}
    time.sleep(0.5)
    stable = both()
    for role, value in stable.items():
        check(player(value)['overheated'], role + ' minigun remains locked during delay')
        check(value['shots'] == values[role]['shots'] and value['projectiles'] == values[role]['projectiles'], role + ' no shot after overheat rejection')
    def cooled():
        values = both()
        return values if all(not player(v)['overheated'] and player(v)['heat'] == 0 for v in values.values()) else None
    values = wait(cooled, 'minigun native cooling and unlock', timeout=25)
    server_number(path + 'OverHeated', 0)
    check('0.0f' in command('data get entity TaczShooter ' + path + 'HeatAmount'), 'server minigun cooled to zero')
    before = values
    check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', 'minigun fires after cooling unlock')
    wait(lambda: state('shooter')['inventory_ammo'].get('tacz:308') == 383 - fired, 'minigun post-cooling reserve')
    time.sleep(0.5)
    values = both()
    for role, value in values.items():
        check(value['projectiles'] - before[role]['projectiles'] == 1 and delta(value, before[role], 'shots') == 1,
              role + ' minigun one shot after cooling')
        check(not player(value)['overheated'], role + ' minigun remains unlocked')
    server_reserve(383 - fired)
    results['minigun_cooled'] = {'observed': values}
    if reject_probe:
        for timestamp in (-10_000, 1_000_000_000):
            before = both()
            client('shooter', 'invalid_shoot_timestamp', timestamp=timestamp)
            time.sleep(0.8)
            values = both()
            for role in roles:
                check(values[role]['shots'] == before[role]['shots']
                      and values[role]['fires'] == before[role]['fires']
                      and values[role]['projectiles'] == before[role]['projectiles'],
                      role + ' invalid timestamp ' + str(timestamp) + ' creates no gun event or projectile')
            check(values['shooter']['inventory_ammo']['tacz:308'] == 383 - fired,
                  'invalid timestamp ' + str(timestamp) + ' leaves reserve unchanged')
            server_reserve(383 - fired)
            results['invalid_timestamp_' + str(timestamp)] = {'before': before, 'after': values}
    for role in roles:
        own = values[role]['players']['TaczShooter' if role == 'shooter' else 'TaczObserver']
        check(own['health'] == 20 and own['position'] == [0.5 if role == 'shooter' else 3.5, -60.0, 0.5],
              role + ' stays alive at the firing scene position')
        value = client(role, 'input_guard', enabled=False)['snapshot']
        check(value['screen'] == 'null', role + ' firing scene restores the normal screen')
