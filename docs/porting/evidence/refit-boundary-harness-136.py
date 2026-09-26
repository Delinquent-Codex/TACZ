"""Replace a shipped magazine, enforce its lock, and unload with a full inventory."""
import re
import time


FIRST = 'tacz:extended_mag_1'
SECOND = 'tacz:extended_mag_2'
AMMO = 'tacz:762x39'
GUN_AMMO = 'Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount'


def run_refit_boundary_scenarios(check, command, silent_command, client, state, wait, server_number, report):
    evidence = report['refit_boundary_scenario'] = {}

    def gun(role):
        return state(role)['players']['TaczShooter']

    def observed(magazine, capacity, reserve, label, attachment=None):
        def ready():
            shooter, observer = state('shooter'), state('observer')
            a, b = shooter['players']['TaczShooter'], observer['players']['TaczShooter']
            return (shooter, observer) if (all(p['magazine'] == magazine and p['magazine_capacity'] == capacity
                                               and p['chamber'] is True and not p['reloading'] for p in (a, b))
                                           and shooter['inventory_ammo'].get(AMMO, 0) == reserve
                                           and (attachment is None or shooter.get('refit', {}).get('extended_mag') == attachment)) else None
        shooter, observer = wait(ready, label, timeout=20)
        check(shooter['inventory_ammo'].get(AMMO, 0) == reserve,
              f'{label}: shooter inventory contains exactly {reserve} rifle rounds')
        check(observer['players']['TaczShooter']['magazine_capacity'] == capacity,
              f'{label}: observer sees effective capacity {capacity}')
        evidence[label] = {'shooter': shooter, 'observer': observer}
        return shooter

    def reserve(expected):
        reply = command('clear TaczShooter tacz:ammo 0')
        match = re.search(r'Found (\d+) matching item\(s\)', reply)
        check(match is not None and int(match[1]) == expected,
              f'server inventory contains exactly {expected} rifle rounds')

    def refit_open():
        client('shooter', 'focus_window')
        wait(lambda: v if not (v := state('shooter'))['native_input_suppressed'] else None,
             'native shooter input focus for refit', timeout=10)
        check('GunRefitScreen' in client('shooter', 'refit_key')['snapshot']['screen'],
              'native key opens refit screen')
        wait(lambda: v if (v := state('shooter')).get('refit', {}).get('opening_progress', 0) >= 1
             and 'EXTENDED_MAG' in v['refit']['allowed_types'] else None,
             'extended-magazine button appears', timeout=10)
        check(client('shooter', 'refit_type_click', type='EXTENDED_MAG')['snapshot']['refit']['type'] == 'EXTENDED_MAG',
              'native extended-magazine button selected')
        wait(lambda: v if (v := state('shooter'))['refit']['transform_progress'] >= 1 else None,
             'refit transformation finishes', timeout=10)

    def refit_close():
        check('GunRefitScreen' not in client('shooter', 'refit_key')['snapshot']['screen'],
              'native key closes refit screen')

    initial = observed(29, 30, 58, 'baseline')
    check(initial['screen'] == 'null' and initial['game_mode'] == 'SURVIVAL',
          'replacement probe starts with the normal survival AK-47')
    check(client('shooter', 'select_slot', slot=0)['snapshot']['selected_slot'] == 0,
          'native hotbar selection holds the AK-47')
    for slot, attachment in ((1, FIRST), (2, SECOND)):
        check('Replaced a slot' in command('item replace entity TaczShooter inventory.' + str(slot) +
                                           ' with tacz:attachment[minecraft:custom_data={AttachmentId:"' + attachment + '"}]'),
              f'server equips shipped {attachment} in inventory slot {slot}')
    wait(lambda: v if (v := state('shooter'))['inventory_attachments'].get(FIRST) == 1
         and v['inventory_attachments'].get(SECOND) == 1 else None,
         'both shipped magazines synchronize into inventory', timeout=10)

    refit_open()
    wait(lambda: v if {10, 11}.issubset(set((v := state('shooter'))['refit']['attachment_slots'])) else None,
         'both visible magazine inventory buttons appear', timeout=10)
    client('shooter', 'refit_install_click', attachment=FIRST)
    installed = observed(0, 34, 87, 'first_install', FIRST)
    check(installed['inventory_attachments'].get(FIRST, 0) == 0
          and installed['inventory_attachments'].get(SECOND) == 1,
          'first magazine enters gun while second remains in inventory')
    reserve(87)
    refit_close()
    client('shooter', 'reload')
    observed(34, 34, 53, 'first_reload')
    server_number(GUN_AMMO, 34)

    refit_open()
    wait(lambda: v if 11 in (v := state('shooter'))['refit']['attachment_slots'] else None,
         'second magazine remains available for direct replacement', timeout=10)
    client('shooter', 'refit_install_click', attachment=SECOND)
    replaced = observed(0, 37, 87, 'replacement', SECOND)
    check(replaced['inventory_attachments'].get(FIRST) == 1
          and replaced['inventory_attachments'].get(SECOND, 0) == 0,
          'replacement swaps original magazine back into the source inventory slot')
    gun_nbt = command('data get entity TaczShooter Inventory[{Slot:0b}]')
    check(SECOND in gun_nbt and FIRST not in gun_nbt and 'GunCurrentAmmoCount: 0' in gun_nbt,
          'server gun stores only replacement magazine and ejects 34 rounds')
    reserve(87)
    refit_close()
    client('shooter', 'reload')
    observed(37, 37, 50, 'second_reload')
    server_number(GUN_AMMO, 37)

    silent_command('tacz attachment_lock TaczShooter true')
    wait(lambda: reply if '1b' in (reply := command(
        'data get entity TaczShooter Inventory[{Slot:0b}].components."minecraft:custom_data".AttachmentLock')) else None,
        'server applies attachment-lock command', timeout=10)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".AttachmentLock', 1)
    wait(lambda: v if (v := state('shooter'))['players']['TaczShooter'].get('attachment_lock') is True else None,
         'server attachment lock synchronizes to shooter', timeout=10)
    check(state('observer')['players']['TaczShooter']['attachment_lock'] is True,
          'observer sees locked gun')
    client('shooter', 'focus_window')
    wait(lambda: v if not (v := state('shooter'))['native_input_suppressed'] else None,
         'native input ready for locked-key check', timeout=10)
    check(client('shooter', 'refit_key')['snapshot']['screen'] == 'null',
          'locked gun rejects native refit key')
    client('shooter', 'refit_unload_packet', gun_slot=0, type='EXTENDED_MAG')
    client('shooter', 'refit_packet', attachment_slot=11, gun_slot=0, type='EXTENDED_MAG')
    time.sleep(0.5)
    locked = observed(37, 37, 50, 'locked_packet_rejection')
    check(locked['inventory_attachments'].get(FIRST) == 1
          and SECOND in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'locked server gun rejects direct install and unload packets')
    silent_command('tacz attachment_lock TaczShooter false')
    wait(lambda: reply if '0b' in (reply := command(
        'data get entity TaczShooter Inventory[{Slot:0b}].components."minecraft:custom_data".AttachmentLock')) else None,
        'server clears attachment lock', timeout=10)
    server_number('Inventory[{Slot:0b}].components."minecraft:custom_data".AttachmentLock', 0)
    wait(lambda: v if (v := state('shooter'))['players']['TaczShooter'].get('attachment_lock') is False else None,
         'server unlock synchronizes to shooter', timeout=10)

    # Preserve the original magazine at slot 11 and fill the other 33 empty
    # player slots; the gun and 64-round reserve occupy the remaining two.
    check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with '
                                       'tacz:ammo[minecraft:custom_data={AmmoId:"tacz:762x39"}] 64'),
          'server fills rifle-ammo stack to its 64-round limit')
    for slot in range(1, 9):
        command(f'item replace entity TaczShooter hotbar.{slot} with minecraft:stone 64')
    for slot in range(1, 27):
        if slot != 2:
            command(f'item replace entity TaczShooter inventory.{slot} with minecraft:stone 64')
    full = wait(lambda: v if (v := state('shooter'))['free_slot'] == -1
                and v['inventory_ammo'].get(AMMO) == 64 else None,
                'all 36 player inventory slots are occupied', timeout=10)
    check(full['inventory_attachments'].get(FIRST) == 1,
          'full inventory still holds the returned first magazine')
    refit_open()
    wait(lambda: v if (v := state('shooter'))['refit']['unload_buttons'] == 1 else None,
         'unload control is visible with full inventory', timeout=10)
    client('shooter', 'refit_unload_click')
    client('shooter', 'refit_unload_packet', gun_slot=0, type='EXTENDED_MAG')
    time.sleep(0.5)
    denied = observed(37, 37, 64, 'full_inventory_rejection', SECOND)
    check(denied['free_slot'] == -1 and SECOND in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'full inventory denies both UI and direct unload without losing attachment')
    check('No entity was found' in command('data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item',
                                           allow_no_entity=True),
          'denied full-inventory unload creates no dropped item')

    check('Replaced a slot' in command('item replace entity TaczShooter hotbar.1 with minecraft:air'),
          'server frees exactly one hotbar slot')
    wait(lambda: v if (v := state('shooter'))['free_slot'] == 1 else None,
         'client sees the newly free slot', timeout=10)
    client('shooter', 'refit_unload_click')
    unloaded = observed(0, 30, 64, 'single_slot_unload', 'none')
    check(unloaded['inventory_attachments'].get(FIRST) == 1
          and unloaded['inventory_attachments'].get(SECOND) == 1
          and unloaded['free_slot'] == -1,
          'one free slot receives replacement magazine and inventory is full again')
    server_number(GUN_AMMO, 0)
    reserve(64)
    drop = wait(lambda: reply if AMMO in (reply := command(
        'data get entity @e[type=minecraft:item,sort=nearest,limit=1] Item', allow_no_entity=True)) else None,
        'ejected 37 rounds appear as an item when inventory is full', timeout=10)
    check(re.search(r'count: 37\b', drop) is not None,
          'ground ammo stack contains all 37 ejected rounds')
    check(SECOND not in command('data get entity TaczShooter Inventory[{Slot:0b}]'),
          'server gun has no extended magazine after successful one-slot unload')
    refit_close()
    evidence['ground_ammo'] = drop
