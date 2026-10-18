#!/usr/bin/env python3
"""Check Android version syntax, release-tag equality and an optional previous Git ref."""
import argparse
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?')


def read_version(text):
    codes = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.M)
    names = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    if len(codes) != 1 or len(names) != 1:
        raise ValueError('Expected one literal versionCode and versionName in app/build.gradle.kts')
    code, name = int(codes[0]), names[0]
    if not 1 <= code <= 2100000000 or not VERSION.fullmatch(name):
        raise ValueError('Invalid Android versionCode or versionName')
    return code, name


def validate(current, tag=None, previous=None):
    code, name = current
    if tag is not None and tag != 'v' + name:
        raise ValueError('Release tag must equal v' + name)
    if previous is not None:
        old_code, old_name = previous
        if code < old_code:
            raise ValueError('versionCode must not decrease')
        if name != old_name and code <= old_code:
            raise ValueError('A new versionName requires a greater versionCode')
        if name == old_name and code != old_code:
            raise ValueError('A release versionCode change requires a new versionName')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag')
    parser.add_argument('--base-ref', help='Optional previous accepted release ref; never inferred from all fetched tags')
    args = parser.parse_args()
    tag = args.tag
    if tag is None and os.environ.get('GITHUB_REF_TYPE') == 'tag':
        tag = os.environ.get('GITHUB_REF_NAME')
    previous = None
    if args.base_ref:
        commit = subprocess.check_output(['git', 'rev-parse', '--verify', '--end-of-options', args.base_ref + '^{commit}'], cwd=ROOT, text=True).strip()
        previous = read_version(subprocess.check_output(['git', 'show', commit + ':app/build.gradle.kts'], cwd=ROOT, text=True))
    current = read_version((ROOT / 'app/build.gradle.kts').read_text())
    validate(current, tag, previous)
    print(f'PASS: versionName={current[1]}, versionCode={current[0]}; tag={tag or "not a release tag"}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
