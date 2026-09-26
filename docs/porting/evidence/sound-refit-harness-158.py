"""Trace AK-47 sound submissions and started playback channels through muzzle refit."""

import time


SILENCER = 'tacz:muzzle_silencer_phantom_s1'
SOUND_IDS = {
    'ordinary_local': 'tacz:ak47/ak47_shoot',
    'ordinary_remote': 'tacz:ak47/ak47_shoot_3p',
    'silenced_local': 'tacz:ak47/ak47_silence',
    'silenced_remote': 'tacz:ak47/ak47_silence_3p',
}
MAG_PATH = 'Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount'


def run_sound_refit_scenarios(check, command, client, state, wait, server_number, report):
    result = report['sound_refit_scenario'] = {}
    roles = ('shooter', 'observer')
    sound_ids = set(SOUND_IDS.values())

    def player(value):
        return value['players'].get('TaczShooter', {})

    def counts(value, key):
        return {sound_id: sum(row['id'] == sound_id for row in value[key])
                for sound_id in sound_ids}

    def sound_rows(value, key, sound_id):
        return [row for row in value[key] if row['id'] == sound_id]

    def fire(magazine, muzzle, local_id, remote_id, label):
        before = {role: state(role) for role in roles}
        check(client('shooter', 'shoot')['shoot_result'] == 'SUCCESS', label + ' native shot accepted')

        def ready():
            values = {role: state(role) for role in roles}
            if not all(player(value).get('magazine') == magazine
                       and player(value).get('muzzle') == muzzle
                       and player(value).get('chamber') is True for value in values.values()):
                return None
            if counts(values['shooter'], 'gun_sounds')[local_id] != counts(before['shooter'], 'gun_sounds')[local_id] + 1:
                return None
            if counts(values['observer'], 'gun_sounds')[remote_id] != counts(before['observer'], 'gun_sounds')[remote_id] + 1:
                return None
            if counts(values['shooter'], 'gun_sources')[local_id] != counts(before['shooter'], 'gun_sources')[local_id] + 1:
                return None
            if counts(values['observer'], 'gun_sources')[remote_id] != counts(before['observer'], 'gun_sources')[remote_id] + 1:
                return None
            return values

        wait(ready, label + ' synchronized gun, sound submission and started channel', timeout=20)
        time.sleep(0.5)  # Expose delayed duplicate submissions or channels before exact-delta assertions.
        values = {role: state(role) for role in roles}
        for role, expected in (('shooter', local_id), ('observer', remote_id)):
            for key in ('gun_sounds', 'gun_sources'):
                delta = {sound_id: counts(values[role], key)[sound_id]
                         - counts(before[role], key)[sound_id] for sound_id in sound_ids}
                check(delta == {sound_id: int(sound_id == expected) for sound_id in sound_ids},
                      label + ': ' + role + ' has exactly the expected AK-47 ' + key)
            row = sound_rows(values[role], 'gun_sounds', expected)[-1]
            check(row['player_tick'] >= 0,
                  label + ': ' + role + ' submits the sound during a live world tick')
            source = sound_rows(values[role], 'gun_sources', expected)[-1]
            check(source['channel_present'] and source['volume'] > 0
                  and 0.9 <= source['pitch'] <= 1.025,
                  label + ': ' + role + ' starts a positive-volume pitched playback channel')
            check(values[role]['fires'].get('TaczShooter', 0)
                  == before[role]['fires'].get('TaczShooter', 0) + 1,
                  label + ': ' + role + ' sees one matching fire event')
        server_number(MAG_PATH, magazine)
        server_number('Inventory[{Slot:9b}].count', 58)
        result[label] = {'before': before, 'after': values}

    initial = {role: state(role) for role in roles}
    check(all(player(value).get('gun') == 'tacz:ak47' and player(value).get('magazine') == 29
              and player(value).get('muzzle') is None for value in initial.values()),
          'sound probe begins with unsilenced loaded AK-47 on both clients')
    check(initial['shooter']['screen'] == 'null' and initial['shooter']['game_mode'] == 'SURVIVAL'
          and initial['shooter']['inventory_ammo'].get('tacz:762x39') == 58,
          'sound probe begins in survival with 58 reserve rounds')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds AK-47 for sound probe')
    fire(28, None, SOUND_IDS['ordinary_local'], SOUND_IDS['ordinary_remote'], 'ordinary_before')

    check('Replaced a slot' in command('item replace entity TaczShooter inventory.1 with '
                                      'tacz:attachment[minecraft:custom_data={AttachmentId:"' + SILENCER + '"}]'),
          'server equips one shipped compatible muzzle silencer')
    wait(lambda: value if (value := state('shooter'))['inventory_attachments'].get(SILENCER) == 1 else None,
         'silencer synchronizes into shooter inventory', timeout=10)

    def open_muzzle():
        client('shooter', 'focus_window')
        wait(lambda: value if not (value := state('shooter'))['native_input_suppressed'] else None,
             'shooter native game-input focus for refit key', timeout=10)
        check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
              'native refit key opens actual AK-47 screen')
        ready = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
                     and 'MUZZLE' in value['refit']['allowed_types'] else None,
                     'AK-47 MUZZLE control ready', timeout=10)
        check(client('shooter', 'refit_type_click', type='MUZZLE')['snapshot']['refit']['type'] == 'MUZZLE',
              'native MUZZLE button selected')
        wait(lambda: value if (value := state('shooter'))['refit']['transform_progress'] >= 1 else None,
             'muzzle refit transform completes', timeout=10)
        return ready

    open_muzzle()
    wait(lambda: value if 10 in (value := state('shooter'))['refit']['attachment_slots'] else None,
         'visible silencer inventory button ready', timeout=10)
    client('shooter', 'refit_install_click', attachment=SILENCER)
    installed = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') == SILENCER
                     and value['refit'].get('silence_sound') is True
                     and value['inventory_attachments'].get(SILENCER, 0) == 0 else None,
                     'silencer install updates authoritative gun and client sound cache', timeout=15)
    check(installed['refit']['unload_buttons'] == 1, 'native muzzle unload control appears')
    check(SILENCER in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'server gun component contains installed silencer')
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes refit before silenced shot')
    fire(27, SILENCER, SOUND_IDS['silenced_local'], SOUND_IDS['silenced_remote'], 'silenced')

    open_muzzle()
    wait(lambda: value if (value := state('shooter'))['refit']['unload_buttons'] == 1 else None,
         'native silencer unload button ready', timeout=10)
    client('shooter', 'refit_unload_click')
    unloaded = wait(lambda: value if (value := state('shooter')).get('refit', {}).get('muzzle') is None
                    and value['refit'].get('silence_sound') is False
                    and value['inventory_attachments'].get(SILENCER) == 1 else None,
                    'silencer unload restores ordinary sound cache and inventory', timeout=15)
    check(SILENCER not in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'server gun component removes unloaded silencer')
    check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
          'native key closes refit before final ordinary shot')
    fire(26, None, SOUND_IDS['ordinary_local'], SOUND_IDS['ordinary_remote'], 'ordinary_after')
    result['installed'] = installed
    result['unloaded'] = unloaded
