"""Actual shipped reload scripts over the installed dedicated connection.

Server setup uses native item commands. Reload and attempted-fire interruption
use the production client operator, packets and Lua; no reload state is injected.
"""
import time


def run_reload_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    gun_path = 'Inventory[{Slot:0b}].components."minecraft:custom_data".'
    reserve_path = 'Inventory[{Slot:9b}].count'
    results = report['reload_scenarios'] = {}

    def player(value):
        return value['players'].get('TaczShooter', {})

    def both():
        return {role: state(role) for role in roles}

    def matches(value, gun, magazine, chamber):
        p = player(value)
        return p.get('gun') == 'tacz:' + gun and p.get('magazine') == magazine and p.get('chamber') is chamber

    def setup(gun, magazine, chamber, reserve):
        ammo = '762x39' if gun == 'ak47' else '12g'
        mode = 'AUTO' if gun == 'ak47' else 'SEMI'
        spec = f'tacz:modern_kinetic_gun[minecraft:custom_data={{GunId:"tacz:{gun}",GunFireMode:"{mode}",GunCurrentAmmoCount:{magazine},HasBulletInBarrel:{int(chamber)}b}}]'
        check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with ' + spec), gun + ' native setup')
        check('Replaced a slot' in command(f'item replace entity TaczShooter inventory.0 with tacz:ammo[minecraft:custom_data={{AmmoId:"tacz:{ammo}"}}] {reserve}'), gun + ' reserve setup')
        equipped = time.monotonic()
        def ready():
            values = both()
            return values if (time.monotonic() - equipped >= 1
                              and all(matches(v, gun, magazine, chamber) and not player(v)['reloading'] for v in values.values())
                              and values['shooter']['reserve_slot_count'] == reserve
                              and values['shooter']['draw_cooldown'] == 0
                              and not values['shooter']['state_locked']) else None
        values = wait(ready, gun + ' equipment and draw sync')
        check(not values['shooter']['auto_reload'], 'explicit reload test has auto-reload disabled')
        return both()

    def finish(label, gun, magazine, chamber, reserve, before, phases):
        def complete():
            values = both()
            return values if all(matches(v, gun, magazine, chamber) and not player(v)['reloading'] for v in values.values()) and values['shooter']['reserve_slot_count'] == reserve else None
        values = wait(complete, label + ' final ammo/state', timeout=15)
        # Wait beyond an M870 shell interval to catch continued feeding/duplication.
        time.sleep(0.9)
        values = both()
        for role, value in values.items():
            check(matches(value, gun, magazine, chamber) and not player(value)['reloading'], label + ' ' + role + ' stable gun state')
            check(value['shots'] == before[role]['shots'] and value['projectiles'] == before[role]['projectiles'], label + ' ' + role + ' no unintended shot/projectile')
            check(value['reloads'].get('TaczShooter', 0) - before[role]['reloads'].get('TaczShooter', 0) == 1, label + ' ' + role + ' one reload event')
            observed = [v['reload_phase'] for v in value['reload_transitions'][len(before[role]['reload_transitions']):]]
            check(all(phase in observed for phase in phases), label + ' ' + role + ' actual synchronized phases')
        check(values['shooter']['reserve_slot_count'] == reserve, label + ' stable client reserve')
        server_number(gun_path + 'GunCurrentAmmoCount', magazine)
        server_number(gun_path + 'HasBulletInBarrel', int(chamber))
        if reserve:
            server_number(reserve_path, reserve)
        else:
            check('Found no elements' in command('data get entity TaczShooter Inventory[{Slot:9b}]'), label + ' server reserve slot empty')
        results[label] = {'gun': gun, 'magazine': magazine, 'chamber': chamber, 'reserve': reserve,
                          'observed': values}

    def phase(expected):
        values = both()
        return values if all(player(v).get('reload_phase') == expected for v in values.values()) else None

    empty_phases = ['EMPTY_RELOAD_FEEDING', 'EMPTY_RELOAD_FINISHING', 'NOT_RELOADING']
    tactical_phases = ['TACTICAL_RELOAD_FEEDING', 'TACTICAL_RELOAD_FINISHING', 'NOT_RELOADING']

    before = setup('ak47', 0, False, 60)
    client('shooter', 'reload')
    wait(lambda: phase('EMPTY_RELOAD_FEEDING'), 'AK empty feeding')
    # The shipped AK xmag script has no interrupt hook. A fire request cannot
    # shoot during reload and must not invent magazine cancellation behavior.
    check(client('shooter', 'shoot').get('shoot_result') == 'IS_RELOADING', 'AK empty reload blocks attempted shot')
    finish('ak_empty', 'ak47', 29, True, 30, before, empty_phases)

    before = setup('m870', 0, False, 10)
    client('shooter', 'reload')
    wait(lambda: phase('EMPTY_RELOAD_FEEDING'), 'M870 early empty feeding')
    check(client('shooter', 'shoot').get('shoot_result') == 'IS_RELOADING', 'M870 early interrupt sends cancel without shot')
    finish('m870_cancel_before_feed', 'm870', 0, False, 10, before, empty_phases)

    before = setup('m870', 0, True, 10)
    client('shooter', 'reload')
    wait(lambda: player(state('shooter')).get('magazine') == 1, 'M870 first tactical shell')
    check(client('shooter', 'shoot').get('shoot_result') == 'IS_RELOADING', 'M870 post-feed interrupt sends cancel without shot')
    finish('m870_cancel_after_shell', 'm870', 1, True, 9, before, tactical_phases)

    before = both()
    client('shooter', 'reload')
    finish('m870_resume_partial', 'm870', 5, True, 5, before, tactical_phases)

    before = setup('m870', 0, False, 1)
    client('shooter', 'reload')
    finish('m870_last_reserve_shell', 'm870', 0, True, 0, before, empty_phases)

    before = setup('m870', 0, False, 10)
    client('shooter', 'reload')
    finish('m870_empty_full', 'm870', 5, True, 4, before, empty_phases)
