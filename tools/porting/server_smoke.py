#!/usr/bin/env python3
"""Launch an isolated installed Forge server and verify reload/save/clean shutdown.

Requires a server installed by the official installer and exactly one TACZ JAR in
mods/. --initialize creates local-only test settings; never point it at a user's
server. RCON credentials stay in that test directory, outside evidence output.
"""
import argparse
import hashlib
import json
import re
import secrets
import socket
import struct
import subprocess
import time
from pathlib import Path


def receive(sock, size):
    data = bytearray()
    while len(data) < size:
        part = sock.recv(size - len(data))
        if not part:
            raise EOFError("RCON closed before the response completed")
        data.extend(part)
    return bytes(data)


def request(sock, request_id, kind, text):
    body = struct.pack('<ii', request_id, kind) + text.encode('utf-8') + b'\0\0'
    sock.sendall(struct.pack('<i', len(body)) + body)
    size = struct.unpack('<i', receive(sock, 4))[0]
    if not 10 <= size <= 4096 + 10:
        raise ValueError(f"Invalid RCON response size: {size}")
    response = receive(sock, size)
    response_id, response_kind = struct.unpack('<ii', response[:8])
    if response_id != request_id or response_kind != (2 if kind == 3 else 0) or response[-2:] != b'\0\0':
        raise ValueError("RCON response identity/type/terminator mismatch")
    # These smoke commands have short responses. Never silently truncate a long response.
    if size == 4096 + 10:
        raise ValueError("Smoke command response exceeds one RCON packet")
    return response[8:-2].decode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path)
    parser.add_argument('--log', required=True, type=Path)
    parser.add_argument('--evidence', required=True, type=Path)
    parser.add_argument('--initialize', action='store_true')
    args = parser.parse_args()
    server = args.server.resolve()
    properties = server / 'server.properties'
    if args.initialize:
        if properties.exists() or (server / 'world').exists():
            parser.error('--initialize requires a fresh test installation')
        properties.write_text('\n'.join([
            'server-ip=127.0.0.1', 'server-port=25569', 'online-mode=true',
            'enable-rcon=true', 'rcon.port=25579', f'rcon.password={secrets.token_hex(24)}',
            'level-name=world', 'level-seed=262', 'level-type=minecraft:flat',
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}',
            'view-distance=2', 'simulation-distance=2', 'spawn-protection=0',
            'pause-when-empty-seconds=0', 'motd=TACZ isolated port verification', ''
        ]), encoding='utf-8')
        (server / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    settings = dict(line.split('=', 1) for line in properties.read_text(encoding='utf-8').splitlines()
                    if '=' in line and not line.startswith('#'))
    if settings.get('server-ip') != '127.0.0.1' or settings.get('enable-rcon') != 'true':
        parser.error('The smoke server must bind to 127.0.0.1 and enable RCON')
    jars = sorted((server / 'mods').glob('*.jar'))
    if len(jars) != 1 or not jars[0].name.startswith('tacz-'):
        parser.error('Expected exactly one TACZ JAR in the isolated mods directory')
    command = [str(args.java.resolve()), '-Dterminal.jline=false', '@user_jvm_args.txt',
               '@libraries/net/minecraftforge/forge/26.2-65.1.0/win_args.txt', '--nogui']
    report = {'command': command, 'working_directory': str(server),
              'artifact': jars[0].name, 'artifact_sha256': hashlib.sha256(jars[0].read_bytes()).hexdigest(),
              'harness_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'commands': [], 'result': 'failed', 'forced_stop': False}
    args.log.parent.mkdir(parents=True, exist_ok=True)
    process = None

    def log_text():
        return args.log.read_text(encoding='utf-8', errors='replace')

    def wait_for(predicate, label, timeout=90):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = log_text()
            if predicate(value):
                return value
            if process.poll() is not None:
                raise RuntimeError(f'Server exited before {label}: {process.returncode}')
            time.sleep(0.25)
        raise TimeoutError(f'Timed out waiting for {label}')

    try:
        with args.log.open('wb') as output:
            process = subprocess.Popen(command, cwd=server, stdout=output, stderr=subprocess.STDOUT,
                                       stdin=subprocess.PIPE)
            wait_for(lambda value: 'Done (' in value and 'RCON running on' in value, 'world and RCON startup')
            with socket.create_connection(('127.0.0.1', int(settings['rcon.port'])), timeout=30) as connection:
                request(connection, 1, 3, settings['rcon.password'])
                for index, (text, expected) in enumerate([
                    ('list', 'There are 0 of a max of'),
                    ('tacz reload', '[TACZ]'),
                    ('reload', 'Reloading!'),
                    ('save-all flush', 'Saved the game'),
                    ('stop', 'Stopping the server'),
                ], 2):
                    before = log_text().count('Loaded 1769 recipes')
                    response = request(connection, index, 2, text)
                    report['commands'].append({'command': text, 'response': response})
                    if expected not in response:
                        raise AssertionError(f'{text}: unexpected response {response!r}')
                    if text in ('tacz reload', 'reload'):
                        wait_for(lambda value: value.count('Loaded 1769 recipes') > before, f'{text} recipe reload')
            report['exit_code'] = process.wait(timeout=30)
            final_log = log_text()
            report['recipe_loads'] = re.findall(r'Loaded (\d+) recipes', final_log)
            errors = [line for line in final_log.splitlines()
                      if '/ERROR]' in line or '/FATAL]' in line or ' ERROR ' in line]
            # Record the host diagnostics explicitly; these do not invalidate the
            # command/save assertions and are not a claim of an error-free log.
            host_errors = [line for line in errors if '[os.dr.wi.re.HkeyPerformanceDataUtil/]' in line
                           and 'Unable to locate English counter names in registry Perflib 009' in line]
            if ('Could not initialize class io.netty.channel.kqueue.Native' in final_log
                    and 'Only supported on OSX/BSD' in final_log
                    and 'Could not initialize class io.netty.channel.epoll.Native' in final_log
                    and 'Only supported on Linux' in final_log):
                host_errors += [line for line in errors if line.endswith('Server thread ERROR An exception occurred processing Appender DebugFile')]
            report['host_diagnostics'] = host_errors
            report['error_lines'] = [line for line in errors if line not in host_errors]
            report['error_free_log'] = not errors
            if report['exit_code'] != 0 or report['error_lines']:
                raise AssertionError('Server exit or error-level log check failed')
            stop = final_log.rfind('Stopping server')
            if stop < 0 or 'All dimensions are saved' not in final_log[stop:]:
                raise AssertionError('Missing shutdown and post-shutdown save evidence')
            report['result'] = 'passed-with-host-diagnostics' if host_errors else 'passed'
    except Exception as error:
        report['failure'] = str(error)
        raise
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b'stop\n')
                process.stdin.flush()
                process.wait(timeout=15)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                report['forced_stop'] = True
                process.kill()
                process.wait(timeout=10)
        if process is not None:
            report['exit_code'] = process.returncode
        if args.log.exists():
            report['log_sha256'] = hashlib.sha256(args.log.read_bytes()).hexdigest()
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
