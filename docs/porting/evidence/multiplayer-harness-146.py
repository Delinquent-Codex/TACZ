#!/usr/bin/env python3
"""Two installed clients and a dedicated server, confined to loopback.

Creates a fresh disposable installation tree. Offline identities test networking,
not account authentication. Public installed libraries/assets are hard-linked;
all mutable config, worlds, player data and command files are separate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import time

from server_smoke import request

REPO = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--client-template', required=True, type=Path)
    parser.add_argument('--server-template', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--evidence-prefix', required=True, type=Path)
    parser.add_argument('--backend', choices=['OPENGL', 'VULKAN'], default='OPENGL')
    parser.add_argument('--boundary-probe', action='store_true', help='After networking checks, inspect a free projectile and resume its distant chunks')
    parser.add_argument('--reload-probe', action='store_true', help='Exercise empty and interrupted shipped reload scripts')
    parser.add_argument('--bolt-probe', action='store_true', help='Fire the shipped manual-action M870 through empty and dry fire')
    parser.add_argument('--firing-probe', action='store_true', help='Exercise M16A4 burst/mode selection, Rhino charge and minigun heat')
    parser.add_argument('--server-trace', action='store_true', help='Add the separate diagnostic server mod; this is not a TACZ-only server run')
    parser.add_argument('--damage-probe', action='store_true', help='Exercise one AK-47 player hit, synchronized health and gun hurt attribution')
    parser.add_argument('--headshot-probe', action='store_true', help='Exercise one AK-47 player headshot and synchronized hurt attribution')
    parser.add_argument('--kill-probe', action='store_true', help='Follow the controlled headshot with a lethal AK-47 body shot')
    parser.add_argument('--armor-probe', action='store_true', help='Exercise one AK-47 body hit against native diamond chest armor')
    parser.add_argument('--penetration-probe', action='store_true', help='Exercise one shipped M700 projectile through two native iron golems')
    parser.add_argument('--explosion-probe', action='store_true', help='Exercise one shipped M320 blast against native golems and a stone block')
    parser.add_argument('--rpg-block-probe', action='store_true', help='Exercise one shipped RPG-7 blast against wool and obsidian blocks')
    parser.add_argument('--nonplayer-probe', action='store_true', help='Drive a tagged native villager through the test-only server API fixture')
    parser.add_argument('--respawn-probe', action='store_true', help='Exercise native death, keep-inventory respawn and first post-respawn shot')
    parser.add_argument('--death-drop-probe', action='store_true', help='Exercise ordinary native death drops and empty respawn')
    parser.add_argument('--pickup-probe', action='store_true', help='Pick up the death drops, swap hotbar slots, fire and drop the gun')
    parser.add_argument('--dimension-probe', action='store_true', help='Transfer loaded shooter and observer across overworld/nether boundaries')
    parser.add_argument('--draw-packet-probe', action='store_true', help='Exercise duplicate draw packets during reload and repeat fire')
    parser.add_argument('--portal-probe', action='store_true', help='Travel through paired ignited Nether portals with a loaded gun')
    parser.add_argument('--portal-repeat-probe', action='store_true', help='Verify prompt portal reentry and a second loaded-gun round trip')
    parser.add_argument('--end-portal-probe', action='store_true', help='Cross a native End portal, fire, die and respawn with a loaded gun')
    parser.add_argument('--portal-death-probe', action='store_true', help='Die inside a charging native Nether portal, then respawn with a loaded gun')
    parser.add_argument('--portal-walk-probe', action='store_true', help='Walk into paired Nether portals through the client forward key')
    parser.add_argument('--end-exit-probe', action='store_true', help='Use a native End exit portal and close the actual credits screen')
    parser.add_argument('--end-exit-repeat-probe', action='store_true', help='Verify a second End exit returns directly after credits')
    parser.add_argument('--end-fountain-probe', action='store_true', help='Exit through the dragon fight generated fountain')
    parser.add_argument('--gunsmith-probe', action='store_true', help='Place and open a table, then exercise survival craft transaction')
    parser.add_argument('--refit-probe', action='store_true', help='Install and unload a compatible sight through the refit screen')
    parser.add_argument('--extended-mag-probe', action='store_true', help='Refit a shipped extended magazine and verify ammo conservation')
    parser.add_argument('--refit-boundary-probe', action='store_true', help='Replace and lock a magazine, then unload against full inventory')
    parser.add_argument('--gunsmith-multi-probe', action='store_true', help='Craft multi-ingredient attachment and ammo recipes at shipped workbenches')
    parser.add_argument('--gunsmith-full-probe', action='store_true', help='Craft and recover an attachment with every inventory slot occupied')
    parser.add_argument('--gunsmith-split-creative-probe', action='store_true', help='Craft a 100-round output in survival and creative modes')
    args = parser.parse_args()
    if args.kill_probe and not args.headshot_probe:
        parser.error('--kill-probe requires --headshot-probe')
    if args.nonplayer_probe and not args.server_trace:
        parser.error('--nonplayer-probe requires --server-trace')
    root, prefix = args.root.resolve(), args.evidence_prefix.resolve()
    if root.exists():
        parser.error('--root must be a new disposable directory')
    for port in (25570, 25580):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    root.mkdir(parents=True)
    prefix.parent.mkdir(parents=True, exist_ok=True)
    server = root / 'server'
    server.mkdir()
    shutil.copytree(args.server_template / 'libraries', server / 'libraries', copy_function=os.link)
    shutil.copy2(args.server_template / 'forge-26.2-65.1.0-shim.jar', server)
    (server / 'user_jvm_args.txt').write_text('-Xmx2G\n', encoding='utf-8')
    candidate = REPO / 'build/libs/tacz-26.2-1.1.8-hotfix-port.1.jar'
    (server / 'mods').mkdir()
    shutil.copy2(candidate, server / 'mods' / candidate.name)
    trace_project = REPO / 'tools/porting/server-trace-fixture'
    if args.server_trace:
        trace_jar = trace_project / 'build/libs/tacz-server-trace-fixture-1.0.jar'
        shutil.copy2(trace_jar, server / 'mods' / trace_jar.name)
    password = secrets.token_hex(24)
    (server / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=25570', 'online-mode=false', 'enforce-secure-profile=false',
        'enable-rcon=true', 'rcon.port=25580', 'rcon.password=' + password,
        'level-name=world', 'level-seed=262', 'level-type=minecraft:flat',
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}',
        'view-distance=3', 'simulation-distance=2', 'spawn-protection=0', 'difficulty=peaceful', 'gamemode=survival',
        'pause-when-empty-seconds=0', 'motd=TACZ local multiplayer fixture', ''
    ]), encoding='utf-8')
    (server / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    report = {'scope': 'two independent installed clients, client-only fixture, TACZ-only loopback dedicated server',
              'authentication': 'offline test identities; no account or Internet multiplayer evidence',
              'candidate_sha256': digest(candidate), 'result': 'failed', 'checks': [], 'events': [],
              'input_sha256': {str(p): digest(p) for p in [Path(__file__), REPO / 'tools/porting/server_smoke.py',
                  REPO / 'tools/porting/installed_client.py', REPO / 'tools/porting/reload_scenarios.py',
                  REPO / 'tools/porting/bolt_scenarios.py', REPO / 'tools/porting/firing_scenarios.py',
                  REPO / 'tools/porting/damage_scenarios.py', REPO / 'tools/porting/headshot_scenarios.py',
                  REPO / 'tools/porting/kill_scenarios.py', REPO / 'tools/porting/armor_scenarios.py',
                  REPO / 'tools/porting/penetration_scenarios.py',
                  REPO / 'tools/porting/explosion_scenarios.py',
                  REPO / 'tools/porting/rpg_block_scenarios.py',
                  REPO / 'tools/porting/nonplayer_scenarios.py',
                  REPO / 'tools/porting/respawn_scenarios.py',
                  REPO / 'tools/porting/death_drop_scenarios.py',
                  REPO / 'tools/porting/pickup_scenarios.py',
                  REPO / 'tools/porting/dimension_scenarios.py',
                  REPO / 'tools/porting/draw_packet_scenarios.py',
                  REPO / 'tools/porting/portal_scenarios.py',
                  REPO / 'tools/porting/portal_repeat_scenarios.py',
                  REPO / 'tools/porting/end_portal_scenarios.py',
                  REPO / 'tools/porting/portal_death_scenarios.py',
                  REPO / 'tools/porting/portal_walk_scenarios.py',
                  REPO / 'tools/porting/end_exit_scenarios.py',
                  REPO / 'tools/porting/end_exit_repeat_scenarios.py',
                  REPO / 'tools/porting/end_fountain_scenarios.py',
                  REPO / 'tools/porting/gunsmith_scenarios.py',
                  REPO / 'tools/porting/refit_scenarios.py',
                  REPO / 'tools/porting/extended_mag_scenarios.py',
                  REPO / 'tools/porting/refit_boundary_scenarios.py',
                  REPO / 'tools/porting/gunsmith_multi_scenarios.py',
                  REPO / 'tools/porting/gunsmith_full_scenarios.py',
                  REPO / 'tools/porting/gunsmith_split_creative_scenarios.py']},
              'server_libraries_sha256': {str(p.relative_to(server)): digest(p) for p in (server / 'libraries').rglob('*') if p.is_file()},
              'server_shim_sha256': digest(server / 'forge-26.2-65.1.0-shim.jar'), 'forced_stop': False}
    report['server_configuration'] = dict(line.split('=', 1) for line in
        (server / 'server.properties').read_text(encoding='utf-8').splitlines()
        if '=' in line and not line.startswith('rcon.password='))
    report['server_mods_sha256'] = {p.name: digest(p) for p in (server / 'mods').glob('*.jar')}
    if args.server_trace:
        report['scope'] = 'two installed clients and dedicated TACZ plus test-only server trace mod; diagnostic observations, not a TACZ-only run'
        trace_inputs = [trace_jar, trace_project / 'build.gradle', trace_project / 'settings.gradle', trace_project / 'gradle.properties']
        trace_inputs += [p for p in (trace_project / 'src').rglob('*') if p.is_file()]
        report['input_sha256'].update({str(p): digest(p) for p in trace_inputs})
    clients, handles = {}, []
    process, connection = None, None
    rcon_id = 2

    def check(condition, label):
        if not condition:
            raise AssertionError(label)
        report['checks'].append(label)

    def wait(predicate, label, timeout=60):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = predicate()
            if value:
                return value
            if process.poll() is not None:
                raise RuntimeError('Server exited while waiting for ' + label)
            time.sleep(0.15)
        raise TimeoutError(label)

    def command(text, allow_no_entity=False):
        nonlocal rcon_id
        rcon_id += 1
        response = request(connection, rcon_id, 2, text)
        report['events'].append({'rcon': text, 'response': response})
        failures = ['Unknown or incomplete', 'Incorrect argument']
        if not allow_no_entity:
            failures.append('No entity was found')
        if any(marker in response for marker in failures):
            raise AssertionError(response)
        return response

    def silent_command(text):
        nonlocal rcon_id
        # The attachment_lock command has no success message. Minecraft's RCON
        # sends no response packet for it, so send then verify via data get.
        rcon_id += 1
        body = struct.pack('<ii', rcon_id, 2) + text.encode('utf-8') + b'\0\0'
        connection.sendall(struct.pack('<i', len(body)) + body)
        report['events'].append({'rcon_without_reply': text})

    def launch(role):
        directory = root / role
        directory.mkdir()
        for folder in ('libraries', 'assets'):
            shutil.copytree(args.client_template / folder, directory / folder, copy_function=os.link)
        shutil.copytree(args.client_template / 'versions', directory / 'versions')
        (directory / 'options.txt').write_text('renderDistance:3\nsimulationDistance:5\nmaxFps:30\npauseOnLostFocus:false\n', encoding='utf-8')
        control = directory / 'fixture-control'
        control.mkdir()
        client_prefix = prefix.with_name(prefix.name + '-' + role)
        output = client_prefix.with_name(client_prefix.name + '-controller.log').open('wb')
        handles.append(output)
        invocation = [sys.executable, str(REPO / 'tools/porting/installed_client.py'), '--installation', str(directory),
                      '--java', str(args.java.resolve()), '--backend', args.backend, '--multiplayer', '127.0.0.1:25570',
                      '--username', 'Tacz' + role.title(), '--control-directory', str(control), '--evidence-prefix', str(client_prefix)]
        clients[role] = {'process': subprocess.Popen(invocation, stdout=output, stderr=subprocess.STDOUT, cwd=REPO),
                         'control': control, 'id': 0, 'prefix': client_prefix, 'command': invocation}

    def client(role, action='snapshot', **parameters):
        data = clients[role]
        data['id'] += 1
        cmd = {'id': data['id'], 'action': action, **parameters}
        temp = data['control'] / f'command-{cmd["id"]}.tmp'
        temp.write_text(json.dumps(cmd), encoding='utf-8')
        temp.replace(data['control'] / f'command-{cmd["id"]}.json')
        def response():
            if data['process'].poll() is not None:
                raise RuntimeError(role + ' controller exited before ' + action)
            path = data['control'] / f'response-{cmd["id"]}.json'
            if path.exists():
                result = json.loads(path.read_text(encoding='utf-8'))
                if result.get('id') == cmd['id']:
                    return result
        result = wait(response, role + ' response ' + action, timeout=75)
        report['events'].append({'client': role, **result})
        if result.get('status') != 'ok':
            raise AssertionError(result)
        return result

    def state(role):
        return client(role)['snapshot']

    def joined(role):
        value = state(role)
        return value if value['connected'] and value['screen'] == 'null' else None

    def synced(value, role):
        check(value['dedicated_connection'], role + ' has no integrated server')
        check(value['game_mode'] == 'SURVIVAL', role + ' is in survival mode')
        for key, expected in [('client_guns', 54), ('common_guns', 54), ('common_ammo', 24), ('common_attachments', 99), ('recipes', 173)]:
            check(value[key] == expected, f'{role} synchronized {key}={expected}')

    def ammo(role, count):
        value = state(role)
        gun = value['players'].get('TaczShooter', {})
        return value if gun.get('magazine') == count and gun.get('chamber') is True else None

    def server_number(path, expected):
        response = command('data get entity TaczShooter ' + path)
        match = re.search(r': (-?\d+)(?:[bBsSlLfFdD])?$', response)
        check(match is not None and int(match[1]) == expected, 'server ' + path + '=' + str(expected))

    magazine_path = 'Inventory[{Slot:0b}].components."minecraft:custom_data".GunCurrentAmmoCount'
    reserve_path = 'Inventory[{Slot:9b}].count'
    server_log = prefix.with_name(prefix.name + '-server.log')
    started = time.monotonic()
    try:
        output = server_log.open('wb')
        handles.append(output)
        invocation = [str(args.java.resolve()), '-Dterminal.jline=false', '@user_jvm_args.txt',
                      '@libraries/net/minecraftforge/forge/26.2-65.1.0/win_args.txt', '--nogui']
        if args.server_trace:
            invocation.insert(1, '-Dmixin.debug.countInjections=true')
        report['server_command'] = invocation
        process = subprocess.Popen(invocation, cwd=server, stdout=output, stderr=subprocess.STDOUT, stdin=subprocess.PIPE)
        wait(lambda: 'RCON running on' in server_log.read_text(encoding='utf-8', errors='replace'), 'server startup', 90)
        connection = socket.create_connection(('127.0.0.1', 25580), timeout=30)
        request(connection, 1, 3, password)
        launch('shooter')
        synced(wait(lambda: joined('shooter'), 'shooter join'), 'shooter')
        check('1 of a max' in command('list'), 'one initial dedicated player')
        command('tp TaczShooter 0 -60 0 0 0')
        check('Successfully filled' in command('fill -8 -60 16 8 -45 16 minecraft:stone'), 'loaded-area projectile backstop created')
        check('Replaced a slot' in command('item replace entity TaczShooter weapon.mainhand with tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47",GunFireMode:"AUTO",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}]'), 'server equips gun through native item command')
        check('Replaced a slot' in command('item replace entity TaczShooter inventory.0 with tacz:ammo[minecraft:custom_data={AmmoId:"tacz:762x39"}] 60'), 'server equips legal ammo stack through native item command')
        wait(lambda: ammo('shooter', 30), 'initial equipment')
        wait(lambda: state('shooter').get('draw_cooldown') == 0, 'initial draw')
        time.sleep(1)
        check(client('shooter', 'shoot').get('shoot_result') == 'SUCCESS', 'first client shot accepted')
        wait(lambda: ammo('shooter', 29), 'first shot sync')
        server_number(magazine_path, 29)
        server_number(reserve_path, 60)
        time.sleep(2)
        check('No entity was found' in command('data get entity @e[type=tacz:bullet,limit=1] Pos', allow_no_entity=True),
              'server confirms first projectile removed before observer joins')
        launch('observer')
        synced(wait(lambda: joined('observer'), 'observer late join'), 'observer')
        check('2 of a max' in command('list'), 'two independent clients on dedicated server')
        command('tp TaczObserver 3 -60 0 0 0')
        value = wait(lambda: ammo('observer', 29), 'late join equipment sync')
        check(value['shots'].get('TaczShooter', 0) == 0, 'late observer receives no historical shot event')
        check(value['projectiles'] == 0, 'late observer receives no expired projectile')
        check(client('shooter', 'shoot').get('shoot_result') == 'SUCCESS', 'second client shot accepted')
        for role, events in [('shooter', 2), ('observer', 1)]:
            value = wait(lambda role=role: ammo(role, 28), role + ' live shot sync')
            check(value['shots'].get('TaczShooter') == events, role + ' exact shoot event count')
            check(value['projectiles'] == events, role + ' exact synchronized projectile count')
        server_number(magazine_path, 28)
        client('shooter', 'reload')
        saw_reload = set()
        def reloaded():
            values = {role: state(role) for role in clients}
            for role, value in values.items():
                if value['players'].get('TaczShooter', {}).get('reloading'):
                    saw_reload.add(role)
            return values if all(value['players'].get('TaczShooter', {}).get('magazine') == 30
                                 and not value['players']['TaczShooter']['reloading'] for value in values.values()) else None
        values = wait(reloaded, 'remote partial reload')
        check(saw_reload == {'shooter', 'observer'}, 'reload state visible to shooter and observer')
        for role, value in values.items():
            check(value['reloads'].get('TaczShooter') == 1, role + ' exact reload event count')
        check(values['shooter']['reserve_slot_count'] == 58, 'client reserve consumes two rounds')
        server_number(magazine_path, 30)
        server_number(reserve_path, 58)
        client('shooter', 'disconnect')
        wait(lambda: '1 of a max' in command('list'), 'server observes disconnect')
        wait(lambda: 'TaczShooter' not in state('observer')['players'], 'observer removes disconnected player')
        check(not state('shooter')['connected'], 'shooter disconnected in same process')
        client('shooter', 'connect')
        synced(wait(lambda: joined('shooter'), 'same-process reconnect'), 'reconnected shooter')
        value = wait(lambda: ammo('shooter', 30), 'reconnected gun state')
        check(value['reserve_slot_count'] == 58, 'reconnect preserves reserve')
        wait(lambda: ammo('observer', 30), 'observer sees reconnected equipment')
        wait(lambda: state('shooter').get('draw_cooldown') == 0, 'reconnect draw')
        time.sleep(1)
        check(client('shooter', 'shoot').get('shoot_result') == 'SUCCESS', 'firing scheduler works after reconnect')
        for role, events in [('shooter', 3), ('observer', 2)]:
            value = wait(lambda role=role: ammo(role, 29), role + ' post-reconnect shot')
            check(value['shots'].get('TaczShooter') == events, role + ' no duplicate reconnect shot')
            check(value['projectiles'] == events, role + ' no duplicate reconnect projectile')
        server_number(magazine_path, 29)
        server_number(reserve_path, 58)
        if args.boundary_probe:
            # Separate from the loaded-area networking scenario: no assertion of
            # wall-clock expiry or baseline parity is inferred from tick lifetime.
            command('tp TaczShooter 0 -60 0 180 0')
            time.sleep(1)
            check(client('shooter', 'shoot').get('shoot_result') == 'SUCCESS', 'boundary probe shot accepted')
            wait(lambda: ammo('shooter', 28), 'boundary shot ammunition')
            time.sleep(2)
            first = command('data get entity @e[type=tacz:bullet,limit=1] Pos', allow_no_entity=True)
            report['boundary_probe'] = {'position_after_two_seconds': first}
            if 'No entity was found' not in first:
                motion = command('data get entity @e[type=tacz:bullet,limit=1] Motion')
                time.sleep(2)
                second = command('data get entity @e[type=tacz:bullet,limit=1] Pos', allow_no_entity=True)
                report['boundary_probe'].update(position_after_four_seconds=second, motion=motion)
                check(first == second, 'free projectile position remains unchanged outside nearby simulation')
                check('Marked' in command('forceload add -16 -512 16 16'), 'load projectile flight corridor')
                wait(lambda: 'No entity was found' in command('data get entity @e[type=tacz:bullet,limit=1] Pos', allow_no_entity=True),
                     'projectile removal after corridor resumes ticking', timeout=30)
                check(True, 'surviving projectile removed after distant chunks resume')
                command('forceload remove all')
                report['boundary_probe']['result'] = 'suspension and removal after chunk resume observed; baseline runtime parity unverified'
            else:
                report['boundary_probe']['result'] = 'no surviving projectile reproduced'
            server_number(magazine_path, 28)
            server_number(reserve_path, 58)
        if args.reload_probe:
            from reload_scenarios import run_reload_scenarios
            run_reload_scenarios(check, command, client, state, wait, server_number, report)
        if args.bolt_probe:
            from bolt_scenarios import run_bolt_scenarios
            run_bolt_scenarios(check, command, client, state, wait, server_number, report)
        if args.firing_probe:
            from firing_scenarios import run_firing_scenarios
            run_firing_scenarios(check, command, client, state, wait, server_number, report, reject_probe=args.server_trace)
        if args.damage_probe:
            from damage_scenarios import run_damage_scenarios
            run_damage_scenarios(check, command, client, state, wait, server_number, report)
        if args.headshot_probe:
            from headshot_scenarios import run_headshot_scenarios
            run_headshot_scenarios(check, command, client, state, wait, server_number, report)
        if args.kill_probe:
            from kill_scenarios import run_kill_scenarios
            run_kill_scenarios(check, command, client, state, wait, server_number, report)
        if args.armor_probe:
            from armor_scenarios import run_armor_scenarios
            run_armor_scenarios(check, command, client, state, wait, server_number, report)
        if args.penetration_probe:
            from penetration_scenarios import run_penetration_scenarios
            run_penetration_scenarios(check, command, client, state, wait, server_number, report)
        if args.explosion_probe:
            from explosion_scenarios import run_explosion_scenarios
            run_explosion_scenarios(check, command, client, state, wait, server_number, report)
        if args.rpg_block_probe:
            from rpg_block_scenarios import run_rpg_block_scenarios
            run_rpg_block_scenarios(check, command, client, state, wait, server_number, report)
        if args.nonplayer_probe:
            from nonplayer_scenarios import run_nonplayer_scenarios
            run_nonplayer_scenarios(check, command, client, state, wait, report)
        if args.respawn_probe:
            from respawn_scenarios import run_respawn_scenarios
            run_respawn_scenarios(check, command, client, state, wait, server_number, report)
        if args.death_drop_probe:
            from death_drop_scenarios import run_death_drop_scenarios
            run_death_drop_scenarios(check, command, client, state, wait, report)
        if args.pickup_probe:
            from pickup_scenarios import run_pickup_scenarios
            run_pickup_scenarios(check, command, client, state, wait, server_number, report)
        if args.dimension_probe:
            from dimension_scenarios import run_dimension_scenarios
            run_dimension_scenarios(check, command, client, state, wait, server_number, report)
        if args.draw_packet_probe:
            from draw_packet_scenarios import run_draw_packet_scenarios
            run_draw_packet_scenarios(check, client, state, wait, server_number, report)
        if args.portal_probe:
            from portal_scenarios import run_portal_scenarios
            run_portal_scenarios(check, command, client, state, wait, server_number, report)
        if args.portal_repeat_probe:
            from portal_repeat_scenarios import run_portal_repeat_scenarios
            run_portal_repeat_scenarios(check, command, client, state, wait, server_number, report)
        if args.end_portal_probe:
            from end_portal_scenarios import run_end_portal_scenarios
            run_end_portal_scenarios(check, command, client, state, wait, server_number, report)
        if args.portal_death_probe:
            from portal_death_scenarios import run_portal_death_scenarios
            run_portal_death_scenarios(check, command, client, state, wait, server_number, report)
        if args.portal_walk_probe:
            from portal_walk_scenarios import run_portal_walk_scenarios
            run_portal_walk_scenarios(check, command, client, state, wait, server_number, report)
        if args.end_exit_probe:
            from end_exit_scenarios import run_end_exit_scenarios
            run_end_exit_scenarios(check, command, client, state, wait, server_number, report)
        if args.end_exit_repeat_probe:
            from end_exit_repeat_scenarios import run_end_exit_repeat_scenarios
            run_end_exit_repeat_scenarios(check, command, client, state, wait, server_number, report)
        if args.end_fountain_probe:
            from end_fountain_scenarios import run_end_fountain_scenarios
            run_end_fountain_scenarios(check, command, client, state, wait, server_number, report)
        if args.gunsmith_probe:
            from gunsmith_scenarios import run_gunsmith_scenarios
            run_gunsmith_scenarios(check, command, client, state, wait, server_number, report)
        if args.refit_probe:
            from refit_scenarios import run_refit_scenarios
            run_refit_scenarios(check, command, client, state, wait, server_number, report)
        if args.extended_mag_probe:
            from extended_mag_scenarios import run_extended_mag_scenarios
            run_extended_mag_scenarios(check, command, client, state, wait, server_number, report)
        if args.refit_boundary_probe:
            from refit_boundary_scenarios import run_refit_boundary_scenarios
            run_refit_boundary_scenarios(check, command, silent_command, client, state, wait, server_number, report)
        if args.gunsmith_multi_probe:
            from gunsmith_multi_scenarios import run_gunsmith_multi_scenarios
            run_gunsmith_multi_scenarios(check, command, client, state, wait, server_number, report)
        if args.gunsmith_full_probe:
            from gunsmith_full_scenarios import run_gunsmith_full_scenarios
            run_gunsmith_full_scenarios(check, command, client, state, wait, server_number, report)
        if args.gunsmith_split_creative_probe:
            from gunsmith_split_creative_scenarios import run_gunsmith_split_creative_scenarios
            run_gunsmith_split_creative_scenarios(check, command, client, state, wait, server_number, report)
        for role in clients:
            client(role, 'quit')
        for role, data in clients.items():
            check(data['process'].wait(timeout=45) == 0, role + ' normal installed client exit')
            result = json.loads(data['prefix'].with_name(data['prefix'].name + '-result.json').read_text(encoding='utf-8'))
            check(result['result'] == 'passed' and not result['forced_stop'], role + ' lifecycle report passes')
            log = data['prefix'].with_suffix('.log').read_text(encoding='utf-8', errors='replace')
            check('Error parsing option value' not in log, role + ' native client options accepted')
        check('Stopping the server' in command('stop'), 'normal dedicated stop requested')
        check(process.wait(timeout=30) == 0, 'dedicated server exit zero')
        log = server_log.read_text(encoding='utf-8', errors='replace')
        check('All dimensions are saved' in log[log.rfind('Stopping server'):], 'dedicated worlds saved after stop')
        if args.server_trace:
            trace = server / 'server-trace.jsonl'
            check(trace.is_file(), 'diagnostic server trace preserved on normal stop')
            rows = [json.loads(line) for line in trace.read_text(encoding='utf-8').splitlines()]
            check(sum(row['kind'] == 'trace_loaded' for row in rows) == 1, 'separate server diagnostic mod loaded once')
            check(any(row['kind'] == 'shoot_result' for row in rows), 'diagnostic Mixin observed actual shoot return values')
            if args.firing_probe:
                for timestamp, expected in [(-10_000, 'COOL_DOWN'), (1_000_000_000, 'NETWORK_FAIL')]:
                    invalid = [row for row in rows if row['kind'] == 'shoot_result' and row['request_timestamp'] == timestamp]
                    check(len(invalid) == 1 and invalid[0]['result'] == expected,
                          'server rejects invalid timestamp ' + str(timestamp) + ' with ' + expected)
            if args.damage_probe:
                observed = report['damage_scenario']
                hits = [row for row in rows if row['kind'] == 'gun_hurt' and row['target'] == observed['target_uuid']]
                check(len(hits) == 1, 'one authoritative server gun hurt event')
                hit = hits[0]
                check(hit['attacker'] == observed['shooter_uuid'] and hit['source_attacker'] == observed['shooter_uuid']
                      and hit['gun'] == 'tacz:ak47', 'server event and damage source attribute AK-47 shooter')
                check(hit['damage'] == 9 and not hit['headshot'], 'server hurt event reports shipped unarmored body damage')
                check(hit['source_direct'] in {row['uuid'] for row in rows if row['kind'] == 'projectile_join'},
                      'server damage source direct entity is a spawned TACZ bullet')
                check(not any(row['kind'] == 'gun_kill' for row in rows), 'no server gun kill event')
            if args.headshot_probe:
                observed = report['headshot_scenario']
                hits = [row for row in rows if row['kind'] == 'gun_hurt' and row['target'] == observed['target_uuid']]
                check(len(hits) == 1, 'one authoritative server headshot hurt event')
                hit = hits[0]
                check(hit['attacker'] == observed['shooter_uuid'] and hit['source_attacker'] == observed['shooter_uuid']
                      and hit['gun'] == 'tacz:ak47', 'server headshot attributes AK-47 shooter')
                check(hit['damage'] == 13.5 and hit['headshot'], 'server hurt event reports multiplied headshot damage')
                check(hit['source_direct'] in {row['uuid'] for row in rows if row['kind'] == 'projectile_join'},
                      'server headshot direct source is a spawned TACZ bullet')
                if not args.kill_probe:
                    check(not any(row['kind'] == 'gun_kill' for row in rows), 'no server gun kill after one headshot')
            if args.kill_probe:
                observed = report['kill_scenario']
                kills = [row for row in rows if row['kind'] == 'gun_kill' and row['target'] == observed['target_uuid']]
                check(len(kills) == 1, 'one authoritative server gun kill event')
                kill = kills[0]
                check(kill['attacker'] == observed['shooter_uuid'] and kill['source_attacker'] == observed['shooter_uuid']
                      and kill['gun'] == 'tacz:ak47', 'server kill event and damage source attribute AK-47 shooter')
                check(kill['damage'] == 9 and not kill['headshot'], 'server kill event reports shipped body damage')
                check(kill['source_direct'] in {row['uuid'] for row in rows if row['kind'] == 'projectile_join'},
                      'server lethal direct source is a spawned TACZ bullet')
            if args.armor_probe:
                observed = report['armor_scenario']
                hits = [row for row in rows if row['kind'] == 'gun_hurt' and row['target'] == observed['target_uuid']]
                check(len(hits) == 1, 'one authoritative server armored hurt event')
                hit = hits[0]
                check(hit['attacker'] == observed['shooter_uuid'] and hit['source_attacker'] == observed['shooter_uuid']
                      and hit['gun'] == 'tacz:ak47' and hit['damage'] == 9 and not hit['headshot'],
                      'server armored hit preserves AK-47 body attribution and base damage')
                check(not hit['source_normal_bypasses_armor'] and hit['source_piercing_bypasses_armor'],
                      'server armored hit uses normal and armor-bypassing damage types')
                check(hit['source_direct'] in {row['uuid'] for row in rows if row['kind'] == 'projectile_join'},
                      'server armored hit direct source is a spawned TACZ bullet')
                check(not any(row['kind'] == 'gun_kill' for row in rows), 'armored shot produces no server gun kill')
            if args.penetration_probe:
                observed = report['penetration_scenario']
                targets = set(observed['target_uuids'].values())
                hits = [row for row in rows if row['kind'] == 'gun_hurt' and row['target'] in targets]
                check(len(hits) == 2 and {row['target'] for row in hits} == targets,
                      'one authoritative server hurt event for each native golem')
                sources = {row['source_direct'] for row in hits}
                check(len(sources) == 1 and sources <= {row['uuid'] for row in rows if row['kind'] == 'projectile_join'},
                      'both server golem hits came from the same spawned M700 projectile')
                for hit in hits:
                    check(hit['attacker'] == observed['shooter_uuid'] and hit['source_attacker'] == observed['shooter_uuid']
                          and hit['gun'] == 'tacz:m700' and hit['damage'] == 24 and not hit['headshot'],
                          'server golem hurt retains shipped M700 body damage and shooter attribution')
                check(not any(row['kind'] == 'gun_kill' and row['target'] in targets for row in rows),
                      'one M700 penetration shot does not kill either golem')
            if args.explosion_probe:
                observed = report['explosion_scenario']
                direct = observed['target_uuids']['tacz_blast_direct']
                hits = [row for row in rows if row['kind'] == 'gun_hurt'
                        and row['target'] in observed['target_uuids'].values()]
                check(len(hits) == 1 and hits[0]['target'] == direct,
                      'one authoritative direct M320 gun hurt event; collateral uses native blast damage')
                hit = hits[0]
                check(hit['attacker'] == observed['shooter_uuid']
                      and hit['source_attacker'] == observed['shooter_uuid']
                      and hit['gun'] == 'tacz:m320' and hit['damage'] == 10 and not hit['headshot'],
                      'server direct M320 hurt retains shooter and shipped body damage')
                check(hit['source_direct'] in {row['uuid'] for row in rows if row['kind'] == 'projectile_join'
                                                     and row['gun'] == 'tacz:m320'},
                      'server direct M320 hurt source is the spawned explosive bullet')
            if args.rpg_block_probe:
                joins = [row for row in rows if row['kind'] == 'projectile_join' and row['gun'] == 'tacz:rpg7']
                leaves = [row for row in rows if row['kind'] == 'projectile_leave' and row['gun'] == 'tacz:rpg7']
                check(len(joins) == 1 and len(leaves) == 1 and joins[0]['uuid'] == leaves[0]['uuid'],
                      'one server RPG rocket spawns and leaves after block explosion')
                check(not any(row['kind'] in ('gun_hurt', 'gun_kill') for row in rows),
                      'RPG block explosion records no gun hit or kill event')
            if args.nonplayer_probe:
                observed = report['nonplayer_scenario']
                shooter_uuid = observed['shooter_uuid']
                target_uuid = observed['target_uuid']
                draws = [row for row in rows if row['kind'] == 'mob_draw' and row['shooter'] == shooter_uuid]
                fires = [row for row in rows if row['kind'] == 'mob_fire' and row['shooter'] == shooter_uuid]
                check(len(draws) == 1 and draws[0]['gun'] == 'tacz:ak47',
                      'diagnostic driver draws the equipped AK-47 for one native villager')
                check(len(fires) == 2 and [row['result'] for row in fires] == ['SUCCESS', 'NO_AMMO'],
                      'non-player public operator accepts one shot and rejects the empty repeat')
                check(all(row['magazine'] == 0 and not row['chamber'] for row in fires),
                      'non-player shot consumes its sole chambered round')
                results = [row for row in rows if row['kind'] == 'shoot_result' and row['shooter'] == shooter_uuid]
                check(len(results) == 2 and [row['result'] for row in results] == ['SUCCESS', 'NO_AMMO'],
                      'unmodified production shoot path returns the same non-player results')
                hits = [row for row in rows if row['kind'] == 'gun_hurt' and row['target'] == target_uuid]
                joins = [row for row in rows if row['kind'] == 'projectile_join' and row['gun'] == 'tacz:ak47'
                         and draws[0]['wall_time'] <= row['wall_time'] <= fires[-1]['wall_time']]
                check(len(joins) == 1 and len(hits) == 1,
                      'one authoritative bullet and target hurt; dry fire creates neither')
                check(hits[0]['attacker'] == shooter_uuid and hits[0]['source_attacker'] == shooter_uuid
                      and hits[0]['source_direct'] == joins[0]['uuid'],
                      'non-player shooter and spawned bullet retain damage-source ownership')
        report['result'] = 'passed'
    except Exception as error:
        report['failure'] = str(error)
        raise
    finally:
        for role, data in clients.items():
            if data['process'].poll() is None:
                try:
                    client(role, 'quit')
                    data['process'].wait(timeout=45)
                except Exception as error:
                    report.setdefault('cleanup_failures', []).append(role + ': ' + str(error))
                    # The installed client controller owns and bounds its JVM.
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b'stop\n')
                process.stdin.flush()
                process.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                report['forced_stop'] = True
                process.kill()
                process.wait(timeout=15)
        if connection is not None:
            connection.close()
        for handle in handles:
            handle.close()
        report['elapsed_seconds'] = round(time.monotonic() - started, 3)
        report['server_exit_code'] = process.returncode if process else None
        report['server_log_sha256'] = digest(server_log) if server_log.exists() else None
        if args.server_trace:
            trace = server / 'server-trace.jsonl'
            if trace.exists():
                target = prefix.with_name(prefix.name + '-server-trace.jsonl')
                shutil.copy2(trace, target)
                report['server_trace_sha256'] = digest(target)
                rows = [json.loads(line) for line in target.read_text(encoding='utf-8').splitlines()]
                report['server_trace_counts'] = {kind: sum(row['kind'] == kind for row in rows) for kind in sorted({row['kind'] for row in rows})}
            else:
                report['server_trace_missing'] = True
        report['client_commands'] = {role: data['command'] for role, data in clients.items()}
        prefix.with_name(prefix.name + '-result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({key: report.get(key) for key in ('result', 'failure', 'elapsed_seconds', 'forced_stop')}))
        print('Checks:', len(report['checks']))


if __name__ == '__main__':
    main()
