"""Upload an explicitly authorized GitHub backup using normal Git credentials.

Creates a draft prerelease, checks every remote asset's size/SHA-256, then
publishes only when --publish is supplied. Credentials stay in process memory.
"""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            value.update(block)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--assets', required=True, type=Path)
    parser.add_argument('--body-file', required=True, type=Path)
    parser.add_argument('--receipt', required=True, type=Path)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
        parser.error('Expected owner/repository')
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        parser.error('Expected full source commit SHA')
    paths = sorted(path for path in args.assets.iterdir() if path.is_file())
    if not paths or any(path.stat().st_size >= 2 * 1024 ** 3 for path in paths):
        parser.error('Need nonempty release assets, each under 2 GiB')
    expected = {path.name: {'sha256': digest(path), 'bytes': path.stat().st_size} for path in paths}
    credential = subprocess.run(['git', 'credential', 'fill'],
        input='protocol=https\nhost=github.com\n\n', capture_output=True,
        text=True, encoding='utf-8', check=True,
        env={**os.environ, 'GIT_TERMINAL_PROMPT': '0'})
    fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
    if not fields.get('password'):
        raise RuntimeError('No authenticated GitHub Git credential')
    headers = {'Authorization': 'Bearer ' + fields['password'],
               'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2026-03-10',
               'User-Agent': 'TACZ-checkpoint-backup'}
    base = 'https://api.github.com/repos/' + args.repository

    def api(method, url, value=None):
        data = json.dumps(value).encode('utf-8') if value is not None else None
        request = urllib.request.Request(url, method=method, data=data,
            headers={**headers, **({'Content-Type': 'application/json'} if data is not None else {})})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                message = json.loads(raw).get('message', 'GitHub API error')
            except (ValueError, AttributeError):
                message = 'GitHub API error'
            raise RuntimeError(f'GitHub {method} HTTP {error.code}: {message}') from None

    body = args.body_file.read_text(encoding='utf-8')
    releases = api('GET', base + '/releases?per_page=100')
    existing = [release for release in releases if release['tag_name'] == args.tag]
    if len(existing) > 1:
        raise AssertionError('Ambiguous checkpoint release')
    if existing:
        release = existing[0]
        if not release['prerelease'] or release['name'] != 'Forge 26.2 port backup — 2026-09-29 (incomplete)':
            raise AssertionError('Existing tag belongs to a different release')
    else:
        release = api('POST', base + '/releases', {
            'tag_name': args.tag, 'target_commitish': args.commit,
            'name': 'Forge 26.2 port backup — 2026-09-29 (incomplete)',
            'body': body, 'draft': True, 'prerelease': True, 'make_latest': 'false',
        })
    print('Prepared checkpoint release', release['id'], flush=True)
    assets = {asset['name']: asset for asset in api('GET', release['assets_url'] + '?per_page=100')}
    receipt_assets = {}
    for path in paths:
        asset = assets.get(path.name)
        if asset is not None and asset['state'] == 'starter':
            api('DELETE', asset['url'])  # Only an incomplete upload from this checkpoint.
            asset = None
        if asset is None:
            url = urllib.parse.urlsplit(release['upload_url'].split('{')[0])
            if url.scheme != 'https' or url.hostname != 'uploads.github.com':
                raise AssertionError('Unexpected GitHub upload endpoint')
            connection = http.client.HTTPSConnection(url.hostname, timeout=180)
            endpoint = url.path + '?' + urllib.parse.urlencode({'name': path.name})
            connection.putrequest('POST', endpoint)
            for name, value in {**headers, 'Content-Type': 'application/octet-stream',
                                'Content-Length': str(path.stat().st_size)}.items():
                connection.putheader(name, value)
            connection.endheaders()
            sent = 0
            last_update = time.monotonic()
            with path.open('rb') as source:
                for block in iter(lambda: source.read(1024 * 1024), b''):
                    connection.send(block)
                    sent += len(block)
                    if time.monotonic() - last_update >= 25:
                        print('Uploading', path.name, sent, '/', path.stat().st_size, 'bytes', flush=True)
                        last_update = time.monotonic()
            response = connection.getresponse()
            raw = response.read()
            status = response.status
            connection.close()
            if status != 201:
                raise RuntimeError('GitHub asset upload HTTP ' + str(status))
            asset = json.loads(raw)
        wanted = expected[path.name]
        if (asset['state'] != 'uploaded' or asset['size'] != wanted['bytes']
                or asset.get('digest') != 'sha256:' + wanted['sha256']):
            raise AssertionError('Remote checkpoint asset failed SHA-256/size verification: ' + path.name)
        receipt_assets[path.name] = {**wanted, 'github_digest': asset['digest'],
                                    'download_url': asset['browser_download_url'], 'asset_id': asset['id']}
        print('Verified uploaded asset', path.name, wanted['bytes'], 'bytes', flush=True)
    if args.publish:
        release = api('PATCH', release['url'], {'draft': False, 'prerelease': True,
                                               'make_latest': 'false', 'body': body})
    receipt = {'repository': args.repository, 'source_commit': args.commit, 'tag': args.tag,
               'release_url': release['html_url'], 'release_id': release['id'],
               'published': not release['draft'], 'prerelease': release['prerelease'],
               'assets': receipt_assets, 'all_remote_sizes_and_sha256_verified': True}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('Checkpoint', release['html_url'], 'published=' + str(not release['draft']), flush=True)


if __name__ == '__main__':
    main()
