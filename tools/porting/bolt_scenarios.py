"""Manual-action chamber/bolt and pellet synchronization using the shipped M870.

Only setup uses native RCON item commands. Firing uses the actual local-player
operator; the production client tick initiates every subsequent bolt packet.
"""
import time


def run_bolt_scenarios(check, command, client, state, wait, server_number, report):
    roles = ('shooter', 'observer')
    gun_path = 'Inventory[{Slot:0b}].components."minecraft:custom_data".'
    results = report['bolt_scenarios'] = {}

    def player(value):
        return value['players'].get('TaczShooter', {})

    def both():
        return {role: state(role) for role in roles}

    def matches(value, magazine, chamber):
        p = player(value)
        return (p.get('gun') == 'tacz:m870' and p.get('magazine') == magazine
                and p.get('chamber') is chamber and not p['bolting'] and not p['reloading'])

    check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with '
          'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:m870",GunFireMode:"SEMI",'
          'GunCurrentAmmoCount:5,HasBulletInBarrel:1b}]'), 'M870 bolt test native gun setup')
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
          'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:12g"}] 10'), 'M870 bolt test native reserve setup')
    equipped = time.monotonic()

    def ready():
        values = both()
        return values if (time.monotonic() - equipped >= 1
                          and all(matches(v, 5, True) for v in values.values())
                          and values['shooter']['reserve_slot_count'] == 10
                          and values['shooter']['draw_cooldown'] == 0
                          and not values['shooter']['state_locked']) else None

    initial = wait(ready, 'M870 bolt test equipment and draw')
    check(not initial['shooter']['auto_reload'], 'M870 dry-fire test has auto-reload disabled')
    for shot in range(1, 7):
        before = both()
        check(client('shooter', 'shoot').get('shoot_result') == 'SUCCESS', f'M870 shot {shot} accepted')
        magazine, chamber = max(5 - shot, 0), shot < 6

        def complete():
            values = both()
            return values if (all(matches(v, magazine, chamber) for v in values.values())
                              and not values['shooter']['state_locked']) else None

        values = wait(complete, f'M870 shot {shot} chamber and automatic bolt', timeout=10)
        # Beyond one complete bolt cycle: no later feed, automatic reload or shot.
        time.sleep(0.7)
        values = both()
        for role, value in values.items():
            label = f'M870 shot {shot} {role}'
            check(matches(value, magazine, chamber), label + ' stable magazine/chamber/bolt')
            check(value['shots'].get('TaczShooter', 0) - initial[role]['shots'].get('TaczShooter', 0) == shot,
                  label + ' exact shoot events')
            check(value['projectiles'] - initial[role]['projectiles'] == shot * 9, label + ' nine pellets per shell')
            check(value['reloads'] == initial[role]['reloads'], label + ' no unexpected reload')
            transitions = value['gun_transitions'][len(before[role]['gun_transitions']):]
            check(any(t.get('chamber') is False for t in transitions), label + ' consumed chamber observed')
            if chamber:
                bolting = [i for i, t in enumerate(transitions) if t['bolting']]
                check(bool(bolting), label + ' synchronized bolt observed')
                check(any(t.get('chamber') is True and t.get('magazine') == magazine
                          for t in transitions[bolting[0]:]), label + ' magazine feeds chamber after bolt starts')
            else:
                check(not any(t['bolting'] for t in transitions), label + ' no bolt with empty magazine')
        check(values['shooter']['reserve_slot_count'] == 10, f'M870 shot {shot} reserve unchanged')
        server_number(gun_path + 'GunCurrentAmmoCount', magazine)
        server_number(gun_path + 'HasBulletInBarrel', int(chamber))
        server_number('Inventory[{Slot:9b}].count', 10)
        results[f'shot_{shot}'] = {'magazine': magazine, 'chamber': chamber, 'observed': values}

    before = both()
    check(client('shooter', 'shoot').get('shoot_result') == 'NO_AMMO', 'M870 empty gun rejects dry fire')
    time.sleep(1)
    values = both()
    for role, value in values.items():
        check(matches(value, 0, False), role + ' M870 remains empty after dry fire')
        check(value['shots'] == before[role]['shots'] and value['projectiles'] == before[role]['projectiles'],
              role + ' M870 dry fire emits no shot/projectile')
        check(value['reloads'] == before[role]['reloads'], role + ' M870 dry fire emits no reload')
    check(values['shooter']['reserve_slot_count'] == 10, 'M870 dry fire retains reserve')
    server_number(gun_path + 'GunCurrentAmmoCount', 0)
    server_number(gun_path + 'HasBulletInBarrel', 0)
    server_number('Inventory[{Slot:9b}].count', 10)
    results['dry_fire'] = {'observed': values}
