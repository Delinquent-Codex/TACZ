"""Capture and compare the installed client's first-person AK-47 rendering.

This is target-runtime visual evidence. It does not establish image parity with
the 1.20.1 reference, for which no matched camera/frame capture is available.
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


def _difference(first, second, box):
    """Count substantial RGB changes in the gun/hand region, above PNG noise."""
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


def _camera(snapshot):
    player = snapshot['players']['TaczShooter']
    return {
        'dimension': snapshot['dimension'],
        'position': player['position'],
        'rotation': player['rotation'],
        'screen': snapshot['screen'],
    }


def run_visual_scenarios(server, check, command, client, state, wait, report, prefix):
    """Compare an empty hand and the same loaded AK-47 at a fixed camera.

    The multiplayer baseline leaves slot 0 holding the unsilenced AK-47 with 29
    rounds, slot 1 empty, and both players connected. The server's native item
    command copies the entire stack to slot 1 before clearing slot 0. Restoring
    from that copy retains all components, including any gun data not modeled
    by this probe.
    """
    result = report['visual_scenario'] = {
        'scope': 'Forge 26.2 installed client, first-person target rendering',
        'limitation': 'No matched Minecraft 1.20.1 baseline frame; image difference is not version parity.',
        'result': 'failed',
        'images': {},
    }
    before = state('shooter')
    observer = state('observer')
    player = before['players'].get('TaczShooter', {})
    check(before['connected'] and before['screen'] == 'null'
          and before['game_mode'] == 'SURVIVAL' and before['selected_slot'] == 0
          and before['free_slot'] == 1 and not before['overlay_active']
          and player.get('gun') == 'tacz:ak47' and player.get('magazine') == 29
          and player.get('chamber') is True
          and before['inventory_guns'].get('tacz:ak47') == 1
          and observer['players'].get('TaczShooter', {}).get('magazine') == 29,
          'visual probe begins with the one loaded AK-47 and an empty backup slot')
    original_item = command('data get entity TaczShooter ' + GUN_SLOT)
    check('tacz:ak47' in original_item and 'GunCurrentAmmoCount: 29' in original_item,
          'server records the full original AK-47 stack and magazine')
    result['original_item'] = original_item

    # Hold the camera fixed across all four captures. The server owns the
    # position/orientation; the client only requests its normal window focus.
    client('shooter', 'focus_window')
    command('tp TaczShooter 0 -60 0 0 0')
    wait(lambda: value if (value := state('shooter'))['players']['TaczShooter']['rotation'] == [0.0, 0.0]
         else None, 'fixed visual camera yaw and pitch', timeout=10)

    name = Path(prefix).name
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', name):
        raise ValueError('Visual evidence prefix must have a simple basename')
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    source_root = (Path(server).resolve().parent / 'shooter').resolve()
    frames = {}
    cameras = {}

    def capture(label):
        response = client('shooter', 'capture_frame', name='visual-' + label)
        frame = response['frame']
        source = Path(frame['path'])
        check(source.is_absolute() and source.suffix.lower() == '.png'
              and source.resolve().is_relative_to(source_root),
              'frame ' + label + ' is a PNG inside the isolated shooter installation')

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
                return None  # Callback/file visibility can race on Windows.

        image = wait(valid_png, 'complete PNG frame ' + label, timeout=15)
        check(image.width == frame['width'] and image.height == frame['height']
              and image.width >= 640 and image.height >= 360,
              'frame ' + label + ' dimensions match the client capture response')
        destination = EVIDENCE / (name + '-visual-' + label + '.png')
        shutil.copyfile(source, destination)
        relative = destination.relative_to(REPO).as_posix()
        result['images'][relative] = hashlib.sha256(destination.read_bytes()).hexdigest()
        frames[label] = image
        cameras[label] = _camera(response['snapshot'])
        check(cameras[label]['screen'] == 'null' and cameras[label]['dimension'] == 'minecraft:overworld',
              'frame ' + label + ' captures the live overworld without a screen')

    backup_created = False
    original_cleared = False
    try:
        copied = command('item replace entity TaczShooter hotbar.1 '
                         'from entity TaczShooter hotbar.0')
        backup_created = True
        check('Replaced a slot' in copied, 'server copies the complete AK-47 stack to a free backup slot')
        cleared = command('item replace entity TaczShooter hotbar.0 with minecraft:air')
        original_cleared = True
        check('Replaced a slot' in cleared, 'server removes the held AK-47 for empty-hand capture')

        def empty_hand():
            values = {role: state(role) for role in ('shooter', 'observer')}
            return values if (all(values[role]['players']['TaczShooter'].get('gun') is None
                                  for role in values)
                              and values['shooter']['draw_cooldown'] == 0) else None

        empty_state = wait(empty_hand, 'empty hand synchronized on both clients', timeout=15)
        check(empty_state['shooter']['inventory_guns'].get('tacz:ak47') == 1,
              'backed-up gun remains in the shooter inventory')
        time.sleep(0.75)  # Let the ordinary hand/draw animation settle.
        capture('empty-1')
        capture('empty-2')
    finally:
        # Even a capture or assertion failure must return the original stack.
        if original_cleared:
            restored = command('item replace entity TaczShooter hotbar.0 '
                               'from entity TaczShooter hotbar.1')
            check('Replaced a slot' in restored, 'server restores the full original AK-47 stack')
        if backup_created:
            removed = command('item replace entity TaczShooter hotbar.1 with minecraft:air')
            check('Replaced a slot' in removed, 'server removes the temporary AK-47 copy')

    def held_gun():
        values = {role: state(role) for role in ('shooter', 'observer')}
        return values if (all(values[role]['players']['TaczShooter'].get('gun') == 'tacz:ak47'
                              and values[role]['players']['TaczShooter'].get('magazine') == 29
                              for role in values)
                          and values['shooter']['draw_cooldown'] == 0) else None

    held_state = wait(held_gun, 'restored AK-47 synchronized on both clients', timeout=15)
    check(held_state['shooter']['inventory_guns'].get('tacz:ak47') == 1
          and held_state['shooter']['selected_slot'] == 0,
          'one restored gun remains selected after backup cleanup')
    restored_item = command('data get entity TaczShooter ' + GUN_SLOT)
    check(restored_item == original_item, 'restored AK-47 stack exactly matches the original server item data')
    result['restored_item'] = restored_item
    time.sleep(0.75)
    capture('held-1')
    capture('held-2')

    sizes = {(image.width, image.height) for image in frames.values()}
    check(len(sizes) == 1, 'empty-hand and AK-47 frames have identical dimensions')
    width, height = sizes.pop()
    # The HUD hotbar is below this region. It must not supply the measured
    # change when the gun icon moves between the temporary slots.
    box = (int(width * 0.42), int(height * 0.25),
           int(width * 0.98), int(height * 0.90))
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
          'all visual frames share the same server position, rotation and dimension')
    stable_pixels = max(metrics['empty_repeat']['changed_pixels'],
                        metrics['held_repeat']['changed_pixels'])
    cross_pixels = min(metrics['empty_to_held_1']['changed_pixels'],
                       metrics['empty_to_held_2']['changed_pixels'])
    crop_pixels = (box[2] - box[0]) * (box[3] - box[1])
    check(cross_pixels >= max(2000, int(0.03 * crop_pixels))
          and cross_pixels >= 3 * stable_pixels + 1000,
          'held AK-47 changes a substantial first-person image region beyond repeat-frame drift')
    result['result'] = 'passed'
