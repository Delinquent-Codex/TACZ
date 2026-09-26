"""Measure AK-47 channel volume and server sound range with a moving observer."""

import time


SILENCER = 'tacz:muzzle_silencer_phantom_s1'
SOUNDS = {
    'shoot': 'tacz:ak47/ak47_shoot',
    'shoot_3p': 'tacz:ak47/ak47_shoot_3p',
    'silence': 'tacz:ak47/ak47_silence',
    'silence_3p': 'tacz:ak47/ak47_silence_3p',
}
MAG_PATH = 'Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount'


def run_sound_distance_scenarios(check, command, client, state, wait, server_number, report):
    result = report['sound_distance_scenario'] = {}
    roles = ('shooter', 'observer')
    ids = set(SOUNDS.values())

    def player(value, name='TaczShooter'):
        return value['players'].get(name, {})

    def counts(value, key):
        return {sound_id: sum(row['id'] == sound_id for row in value[key]) for sound_id in ids}

    def move_observer(x):
        check('Teleported' in command(f'tp TaczObserver {x} -60 0 0 0'),
              f'vanilla command moves observer to x{x}')

        def ready():
            values = {role: state(role) for role in roles}
            observer = player(values['observer'], 'TaczObserver')
            seen_shooter = player(values['observer'])
            return (values if abs(observer.get('position', [0])[0] - (x + 0.5)) < 0.01
                    and seen_shooter.get('gun') == 'tacz:ak47' else None)

        values = wait(ready, f'observer at x{x} still tracks shooter', timeout=15)
        check(abs(player(values['observer'], 'TaczObserver')['position'][0] - (x + 0.5)) < 0.01,
              f'observer confirms x{x + 0.5} world position')
        return values

    def fire(label, magazine, installed, local_id, remote_id):
        before = {role: state(role) for role in roles}
        check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', label + ' native shot accepted')

        def ready():
            values = {role: state(role) for role in roles}
            if not all(player(value).get('magazine') == magazine
                       and player(value).get('muzzle') == (SILENCER if installed else None)
                       and player(value).get('chamber') is True for value in values.values()):
                return None
            if counts(values['shooter'], 'gun_sources')[local_id] != counts(before['shooter'], 'gun_sources')[local_id] + 1:
                return None
            if remote_id is not None and (counts(values['observer'], 'gun_sources')[remote_id]
                                          != counts(before['observer'], 'gun_sources')[remote_id] + 1):
                return None
            return values

        wait(ready, label + ' gun and expected playback channel sync', timeout=20)
        time.sleep(0.8)  # Includes delayed packet/channel delivery before absence checks.
        after = {role: state(role) for role in roles}
        volumes = {}
        for role, expected in (('shooter', local_id), ('observer', remote_id)):
            for key in ('gun_sounds', 'gun_sources'):
                delta = {sound_id: counts(after[role], key)[sound_id]
                         - counts(before[role], key)[sound_id] for sound_id in ids}
                check(delta == {sound_id: int(sound_id == expected) for sound_id in ids},
                      f'{label}: {role} exact {key} IDs including out-of-range silence')
            check(after[role]['fires'].get('TaczShooter', 0)
                  == before[role]['fires'].get('TaczShooter', 0) + 1,
                  f'{label}: {role} sees one TACZ fire event')
            if expected is not None:
                source = [row for row in after[role]['gun_sources'] if row['id'] == expected][-1]
                check(source['channel_present'] and source['volume'] > 0
                      and 0.9 <= source['pitch'] <= 1.025,
                      f'{label}: {role} started a positive-volume playback channel')
                volumes[role] = source['volume']
        server_number(MAG_PATH, magazine)
        server_number('Inventory[{Slot:9b}].count', 58)
        result[label] = {'before': before, 'after': after, 'volumes': volumes}
        return volumes

    initial = {role: state(role) for role in roles}
    check(all(player(value).get('gun') == 'tacz:ak47'
              and player(value).get('magazine') == 29
              and player(value).get('muzzle') is None for value in initial.values()),
          'distance probe begins with loaded unsilenced AK-47 on both clients')
    check(initial['shooter']['screen'] == 'null' and initial['shooter']['game_mode'] == 'SURVIVAL'
          and initial['shooter']['inventory_ammo'].get('tacz:762x39') == 58,
          'distance probe begins with 58 reserve rounds in survival')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47')
    move_observer(3)
    ordinary_near = fire('ordinary_near', 28, False, SOUNDS['shoot'], SOUNDS['shoot_3p'])
    move_observer(30)
    ordinary_far = fire('ordinary_far', 27, False, SOUNDS['shoot'], SOUNDS['shoot_3p'])
    check(ordinary_near['observer'] > ordinary_far['observer'] > 0,
          'ordinary third-person gunshot attenuates from 3 to 30 blocks')
    check(ordinary_near['shooter'] == ordinary_far['shooter'],
          'fixed shooter hears unchanged local ordinary volume as observer moves')

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with '
                                      'tacz:attachment[minecraft:custom_data={AttachmentId:"' + SILENCER + '"}]'),
          'server equips shipped AK-47-compatible silencer')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(SILENCER) == 1 else None,
         'silencer synchronizes into inventory', timeout=10)

    def open_muzzle():
        client('shooter', 'focus_window')
        wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
             'shooter native input focus for refit key', timeout=10)
        check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
              'native refit key opens AK-47 screen')
        ready = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
                     and 'MUZZLE' in value['refit']['allowed_types'] else None,
                     'MUZZLE refit control ready', timeout=10)
        check(client('shooter', 'refit_type_click', type='MUZZLE')['snapshot']['refit']['type'] == 'MUZZLE',
              'native MUZZLE type selected')
        wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1 else None,
             'muzzle controls finish transform', timeout=10)
        return ready

    open_muzzle()
    wait(lambda: value if 10 in (value := state('shooter'))['refit']['attachment_slots'] else None,
         'native silencer inventory button visible', timeout=10)
    client('shooter', 'refit_install_click', attachment=SILENCER)
    installed = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') == SILENCER
                     and value['refit'].get('silence_sound') is True
                     and value['refit'].get('sound_distance') == 44
                     and value['inventory_attachments'].get(SILENCER, 0) == 0 else None,
                     'server silencer install synchronizes 44-block sound range', timeout=15)
    check(installed['refit']['unload_buttons'] == 1, 'native silencer unload button appears')
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes refit before distance shots')
    silenced_far = fire('silenced_far', 26, True, SOUNDS['silence'], SOUNDS['silence_3p'])
    check(silenced_far['observer'] < ordinary_far['observer'],
          'silencer reduces third-person volume at the same 30-block distance')
    move_observer(3)
    silenced_near = fire('silenced_near', 25, True, SOUNDS['silence'], SOUNDS['silence_3p'])
    check(silenced_near['observer'] > silenced_far['observer'] > 0,
          'silenced third-person shot attenuates from 3 to 30 blocks')
    check(silenced_near['shooter'] == silenced_far['shooter'],
          'fixed shooter hears unchanged local silenced volume as observer moves')

    move_observer(46)
    fire('silenced_outside_44', 24, True, SOUNDS['silence'], None)
    open_muzzle()
    wait(lambda: value if (value := state('shooter'))['refit']['unload_buttons'] == 1 else None,
         'native silencer unload button ready', timeout=10)
    client('shooter', 'refit_unload_click')
    unloaded = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') is None
                    and value['refit'].get('silence_sound') is False
                    and value['refit'].get('sound_distance') == 64
                    and value['inventory_attachments'].get(SILENCER) == 1 else None,
                    'native unload restores 64-block ordinary range and inventory', timeout=15)
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes refit after unload')
    ordinary_boundary = fire('ordinary_inside_64', 23, False, SOUNDS['shoot'], SOUNDS['shoot_3p'])
    check(ordinary_boundary['observer'] < ordinary_far['observer'],
          'ordinary third-person shot attenuates further from 30 to 46 blocks')
    result['installed'] = installed
    result['unloaded'] = unloaded
