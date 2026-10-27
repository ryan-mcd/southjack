#!/usr/bin/env python3
"""Install reviewed scanner binaries after verifying pinned SHA-256 hashes."""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import tarfile
import urllib.request


def main():
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'AMD64'):
        raise SystemExit('Scanner pins currently target Linux x86_64 only.')
    specs = json.loads(Path(__file__).with_name('security-tools.json').read_text())
    destination = Path(os.environ.get('RUNNER_TEMP', '/tmp')) / 'southjack-security-tools'
    destination.mkdir(parents=True, exist_ok=True)
    for name, spec in specs.items():
        request = urllib.request.Request(spec['url'], headers={'User-Agent': 'SouthJack-CI'})
        with urllib.request.urlopen(request, timeout=120) as response:
            archive = response.read()
        if hashlib.sha256(archive).hexdigest() != spec['sha256']:
            raise SystemExit(f'Checksum mismatch for {name}')
        executable = archive
        if spec['member']:
            with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as bundle:
                members = [member for member in bundle.getmembers()
                           if member.isfile() and Path(member.name).name == spec['member']]
                if len(members) != 1:
                    raise SystemExit(f'Expected one {spec["member"]} in {name} archive')
                executable = bundle.extractfile(members[0]).read()
        target = destination / name
        target.write_bytes(executable)
        target.chmod(0o755)
        print(f'Installed {name} {spec["version"]}; checksum verified.')
    github_path = os.environ.get('GITHUB_PATH')
    if github_path:
        with open(github_path, 'a') as stream:
            stream.write(str(destination) + '\n')
    else:
        print(f'Tools directory: {destination}')


if __name__ == '__main__':
    main()
