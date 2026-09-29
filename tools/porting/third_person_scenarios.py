"""Capture a remote player's AK-47 from a fixed installed-client camera.

This is target-runtime third-person evidence, without matched 1.20.1 frames.
The observer stays in first person; the subject is another player's avatar.
"""

import hashlib
from pathlib import Path
import re
import shutil
import time

from PIL import Image, ImageChops, UnidentifiedImageError


REPO = Path(__file__).resolve().parents[2]
EVIDENCE = REPO / 'docs' / 'porting' / 'evidence'
GUN_SLOT = 'Inventory[{Slot:0b}]'
BACKUP_SLOT = 'Inventory[{Slot:1b}]'
RESERVE_SLOT = 'Inventory[{Slot:9b}]'
OBSERVER_POSITION = (0.0, -60.0, -4.0)
OBSERVER_ROTATION = (0.0, 0.0)  # Minecraft yaw zero looks along +Z.
SHOOTER_POSITION = (0.0, -60.0, 0.0)
SHOOTER_ROTATION = (-90.0, 0.0)  # A side profile exposes the held gun.


def _near(actual, expected, tolerance=0.03):
    return len(actual) == len(expected) and all(
        abs(float(a) - float(b)) <= tolerance for a, b in zip(actual, expected)
    )


def _transform(snapshot, name):
    player = snapshot['players'][name]
    return {'position': player['position'], 'rotation': player['rotation']}


def _teleport(command, name, transform):
    coordinates = [*transform['position'], *transform['rotation']]
    command('tp ' + name + ' ' + ' '.join(str(float(value)) for value in coordinates))


def _same_stack(first, second):
    """The Inventory entry's slot tag changes when the complete stack is copied."""
    return re.sub(r'\bSlot: [01]b\b', 'Slot: <backup>', first) == re.sub(
        r'\bSlot: [01]b\b', 'Slot: <backup>', second
    )


def _difference(first, second, box):
    """Count substantial RGB changes inside the remote avatar's image region."""
    difference = ImageChops.difference(first.crop(box), second.crop(box))
    pixels = difference.width * difference.height
    changed = 0
    channel_sum = 0
    for red, green, blue in difference.getdata():
        changed += max(red, green, blue) >= 32
        channel_sum += red + green + blue
    return {
        'changed_pixels': changed,
        'changed_fraction': round(changed / pixels, 6),
        'mean_absolute_rgb_delta': round(channel_sum / (3 * pixels), 4),
    }


def run_third_person_scenarios(server, check, command, client, state, wait, report, prefix):
    """Compare two empty-hand and two held-gun frames of the remote shooter.

    The ordinary two-client baseline has one loaded AK-47 in shooter hotbar 0,
    an empty hotbar 1 and 29 rounds in the magazine. Native item commands move
    the exact stack through slot 1 so all gun components survive the probe.
    """
    result = report['third_person_scenario'] = {
        'scope': 'Forge 26.2 installed observer rendering a remote AK-47 holder',
        'limitation': 'No matched Minecraft 1.20.1 frame; image difference alone does not prove model parity.',
        'result': 'failed',
        'images': {},
    }
    shooter_before = state('shooter')
    observer_before = state('observer')
    shooter = shooter_before['players'].get('TaczShooter', {})
    remote = observer_before['players'].get('TaczShooter', {})
    observer = observer_before['players'].get('TaczObserver', {})
    check(shooter_before['connected'] and observer_before['connected']
          and shooter_before['screen'] == observer_before['screen'] == 'null'
          and shooter_before['dimension'] == observer_before['dimension'] == 'minecraft:overworld'
          and shooter_before['game_mode'] == observer_before['game_mode'] == 'SURVIVAL'
          and shooter_before['selected_slot'] == 0 and shooter_before['free_slot'] == 1
          and not shooter_before['overlay_active'] and not observer_before['overlay_active']
          and shooter.get('gun') == remote.get('gun') == 'tacz:ak47'
          and shooter.get('magazine') == remote.get('magazine') == 29
          and shooter.get('chamber') is remote.get('chamber') is True
          and shooter_before['inventory_guns'].get('tacz:ak47') == 1
          and observer and observer.get('gun') is None
          and observer_before['inventory_guns'].get('tacz:ak47', 0) == 0,
          'third-person probe begins with one loaded shooter AK-47 and an unarmed observer')

    original_item = command('data get entity TaczShooter ' + GUN_SLOT)
    reserve_item = command('data get entity TaczShooter ' + RESERVE_SLOT)
    observer_inventory = command('data get entity TaczObserver Inventory')
    check('tacz:ak47' in original_item and 'GunCurrentAmmoCount: 29' in original_item
          and '58' in reserve_item,
          'server records the exact AK-47 stack and unchanged reserve ammunition')
    result['server_item_before'] = original_item
    result['server_reserve_before'] = reserve_item
    original_transforms = {
        'TaczShooter': _transform(shooter_before, 'TaczShooter'),
        'TaczObserver': _transform(observer_before, 'TaczObserver'),
    }
    result['original_transforms'] = original_transforms

    name = Path(prefix).name
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', name):
        raise ValueError('Third-person evidence prefix must have a simple basename')
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    source_root = (Path(server).resolve().parent / 'observer').resolve()
    frames = {}
    cameras = {}
    backup_created = False
    original_cleared = False
    failure = None

    def capture(label, gun_id):
        response = client('observer', 'capture_frame', name='third-person-' + label)
        frame = response['frame']
        source = Path(frame['path'])
        check(source.is_absolute() and source.suffix.lower() == '.png'
              and source.resolve().is_relative_to(source_root),
              'frame ' + label + ' is a PNG inside the isolated observer installation')

        def valid_png():
            if not source.is_file() or source.stat().st_size < 64:
                return None
            try:
                with Image.open(source) as image:
                    if image.format != 'PNG':
                        raise ValueError('Capture is not a PNG: ' + str(source))
                    image.verify()
                with Image.open(source) as image:
                    return image.convert('RGB')
            except (OSError, UnidentifiedImageError):
                return None  # Screenshot callback and file visibility can race.

        image = wait(valid_png, 'complete third-person PNG ' + label, timeout=15)
        check(image.width == frame['width'] and image.height == frame['height']
              and image.width >= 640 and image.height >= 360,
              'frame ' + label + ' dimensions match the observer capture response')
        snapshot = response['snapshot']
        target = snapshot['players'].get('TaczShooter', {})
        camera_player = snapshot['players'].get('TaczObserver', {})
        check(snapshot['connected'] and snapshot['screen'] == 'null'
              and snapshot['dimension'] == 'minecraft:overworld'
              and not snapshot['overlay_active']
              and _near(camera_player.get('position', ()), OBSERVER_POSITION)
              and _near(camera_player.get('rotation', ()), OBSERVER_ROTATION)
              and _near(target.get('position', ()), SHOOTER_POSITION)
              and _near(target.get('rotation', ()), SHOOTER_ROTATION)
              and target.get('gun') == gun_id
              and (gun_id is None or target.get('magazine') == 29),
              'frame ' + label + ' observes the expected remote shooter and fixed +Z camera')
        destination = EVIDENCE / (name + '-third-person-' + label + '.png')
        shutil.copyfile(source, destination)
        relative = destination.relative_to(REPO).as_posix()
        result['images'][relative] = hashlib.sha256(destination.read_bytes()).hexdigest()
        frames[label] = image
        cameras[label] = {
            'dimension': snapshot['dimension'],
            'observer': _transform(snapshot, 'TaczObserver'),
            'shooter': _transform(snapshot, 'TaczShooter'),
            'screen': snapshot['screen'],
        }

    def restore_gun():
        nonlocal backup_created, original_cleared
        if original_cleared:
            restored = command('item replace entity TaczShooter hotbar.0 '
                               'from entity TaczShooter hotbar.1')
            check('Replaced a slot' in restored, 'server restores the exact original AK-47 stack')
            restored_item = command('data get entity TaczShooter ' + GUN_SLOT)
            check(restored_item == original_item,
                  'restored AK-47 stack exactly matches the original server item data')
            result['server_item_after'] = restored_item
            original_cleared = False
        if backup_created:
            removed = command('item replace entity TaczShooter hotbar.1 with minecraft:air')
            check('Replaced a slot' in removed, 'server removes the temporary AK-47 copy')
            backup_created = False

    try:
        client('observer', 'focus_window')
        _teleport(command, 'TaczShooter', {
            'position': SHOOTER_POSITION, 'rotation': SHOOTER_ROTATION,
        })
        _teleport(command, 'TaczObserver', {
            'position': OBSERVER_POSITION, 'rotation': OBSERVER_ROTATION,
        })

        def fixed_scene():
            values = {role: state(role) for role in ('shooter', 'observer')}
            target = values['observer']['players'].get('TaczShooter', {})
            camera = values['observer']['players'].get('TaczObserver', {})
            return values if (_near(target.get('position', ()), SHOOTER_POSITION)
                              and _near(target.get('rotation', ()), SHOOTER_ROTATION)
                              and _near(camera.get('position', ()), OBSERVER_POSITION)
                              and _near(camera.get('rotation', ()), OBSERVER_ROTATION)) else None

        wait(fixed_scene, 'fixed third-person shooter and observer camera', timeout=10)
        backup_created = True  # The command may apply even if reporting fails.
        copied = command('item replace entity TaczShooter hotbar.1 '
                         'from entity TaczShooter hotbar.0')
        check('Replaced a slot' in copied, 'server copies the full AK-47 stack to an empty backup slot')
        check(_same_stack(command('data get entity TaczShooter ' + BACKUP_SLOT), original_item),
              'temporary backup exactly matches the original AK-47 stack')
        original_cleared = True
        cleared = command('item replace entity TaczShooter hotbar.0 with minecraft:air')
        check('Replaced a slot' in cleared, 'server clears the held AK-47 for remote empty-hand frames')

        def empty_hand():
            values = {role: state(role) for role in ('shooter', 'observer')}
            return values if (all(values[role]['players']['TaczShooter'].get('gun') is None
                                  for role in values)
                              and values['shooter']['draw_cooldown'] == 0
                              and values['shooter']['inventory_guns'].get('tacz:ak47') == 1) else None

        wait(empty_hand, 'remote empty hand synchronized on both clients', timeout=15)
        time.sleep(0.75)
        capture('empty-1', None)
        capture('empty-2', None)
        restore_gun()

        def held_gun():
            values = {role: state(role) for role in ('shooter', 'observer')}
            return values if (all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                                  and values[role]['players']['TaczShooter'].get('magazine') == 29
                                  and values[role]['players']['TaczShooter'].get('chamber') is True
                                  for role in values)
                              and values['shooter']['draw_cooldown'] == 0) else None

        held = wait(held_gun, 'restored remote AK-47 synchronized on both clients', timeout=15)
        check(held['shooter']['inventory_guns'].get('tacz:ak47') == 1
              and held['shooter']['selected_slot'] == 0
              and held['shooter']['free_slot'] == 1,
              'one restored AK-47 is selected and the backup slot is free')
        time.sleep(0.75)
        capture('held-1', 'tacz:ak47')
        capture('held-2', 'tacz:ak47')

        sizes = {(image.width, image.height) for image in frames.values()}
        check(len(sizes) == 1, 'remote empty-hand and held-gun frames share dimensions')
        width, height = sizes.pop()
        # Centered on the remote avatar; stops above the HUD and to the left
        # of the observer's ordinary first-person hand at the lower right.
        box = (int(width * 0.30), int(height * 0.18),
               int(width * 0.70), int(height * 0.75))
        metrics = {
            'empty_repeat': _difference(frames['empty-1'], frames['empty-2'], box),
            'held_repeat': _difference(frames['held-1'], frames['held-2'], box),
            'empty_to_held_1': _difference(frames['empty-1'], frames['held-1'], box),
            'empty_to_held_2': _difference(frames['empty-2'], frames['held-2'], box),
        }
        result['comparison'] = {'crop_xyxy': list(box), 'pixel_threshold_rgb_max': 32,
                                'metrics': metrics}
        result['cameras'] = cameras
        first_camera = next(iter(cameras.values()))
        check(all(camera == first_camera for camera in cameras.values()),
              'all remote visual frames share the same observer and shooter transforms')
        stable_pixels = max(metrics['empty_repeat']['changed_pixels'],
                            metrics['held_repeat']['changed_pixels'])
        cross_pixels = min(metrics['empty_to_held_1']['changed_pixels'],
                           metrics['empty_to_held_2']['changed_pixels'])
        crop_pixels = (box[2] - box[0]) * (box[3] - box[1])
        check(cross_pixels >= max(150, int(0.003 * crop_pixels))
              and cross_pixels >= 2 * stable_pixels + 50,
              'held AK-47 changes a remote-avatar image region beyond repeated-frame drift')
    except Exception as error:
        failure = error
    finally:
        cleanup_errors = []
        if original_cleared or backup_created:
            try:
                restore_gun()
            except Exception as error:
                cleanup_errors.append(error)
        for player_name in ('TaczShooter', 'TaczObserver'):
            try:
                _teleport(command, player_name, original_transforms[player_name])
            except Exception as error:
                cleanup_errors.append(error)
        if not cleanup_errors:
            try:
                def original_positions():
                    snapshots = {role: state(role) for role in ('shooter', 'observer')}
                    return snapshots if (all(
                        _near(snapshots['observer']['players'][name]['position'], transform['position'])
                        and _near(snapshots['observer']['players'][name]['rotation'], transform['rotation'])
                        for name, transform in original_transforms.items()
                    )) else None

                wait(original_positions, 'restored shooter and observer transforms', timeout=10)
                check(command('data get entity TaczShooter ' + GUN_SLOT) == original_item
                      and command('data get entity TaczShooter ' + RESERVE_SLOT) == reserve_item
                      and command('data get entity TaczObserver Inventory') == observer_inventory,
                      'server shooter stack, reserve and observer inventory match the pre-probe state')
                values = {role: state(role) for role in ('shooter', 'observer')}
                check(all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                          and values[role]['players']['TaczShooter'].get('magazine') == 29
                          and values[role]['players']['TaczShooter'].get('chamber') is True
                          for role in values)
                      and values['shooter']['inventory_guns'].get('tacz:ak47') == 1
                      and values['shooter']['reserve_slot_count'] == shooter_before['reserve_slot_count']
                      and values['observer']['inventory_guns'] == observer_before['inventory_guns']
                      and values['observer']['selected_slot'] == observer_before['selected_slot'],
                      'both clients retain the baseline gun, magazine and inventories')
                result['server_reserve_after'] = reserve_item
                result['cleanup'] = 'restored'
            except Exception as error:
                cleanup_errors.append(error)
        if cleanup_errors:
            result['cleanup_errors'] = [str(error) for error in cleanup_errors]

    if failure is not None and cleanup_errors:
        raise ExceptionGroup('Third-person probe and cleanup failed', [failure, *cleanup_errors])
    if failure is not None:
        raise failure
    if cleanup_errors:
        raise ExceptionGroup('Third-person cleanup failed', cleanup_errors)
    result['result'] = 'passed'
