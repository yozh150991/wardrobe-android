#!/usr/bin/env python3
"""Fast verification: run only the host suites that read the files you changed.

Usage: verify.py [--base REF] [--files PATH ...] [--list] [--all]

Changed files are the working tree, the index, untracked files and the commits since the merge base with REF
(default origin/main). A suite is related to a file when its script names the file (`Ui.kt`, `PhotoCutoutRules`,
`strings.xml`) or is the file. `check_harness.py` always runs. The full run before a push is still
`tools/run_host_checks.py`: this tool writes no evidence file. Exit code 1 when a suite fails.
"""
import argparse
import os
import pathlib
import re
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
ALWAYS = ['check_harness.py']
# Needs the owner's Firebase file; it is skipped in the full run without it.
SKIPPED_WITHOUT_CONFIG = 'check_auth_config.py'
# Suites that read whole folders instead of naming files.
FOLDERS = {
    'check_consistency.py': ('app/src/main/res/values', 'app/src/main/java/com/familywardrobe/app/ui/'),
    'check_account_erasure.py': ('app/src/main/java/com/familywardrobe/app/',),
    'check_family_backend.py': ('cloud/family-functions/',),
    'check_project.py': ('app/build.gradle.kts', 'app/src/main/AndroidManifest.xml', '.github/'),
}


def suites():
    source = (ROOT / 'tools/run_host_checks.py').read_text(encoding='utf-8')
    return re.findall(r"'(check_\w+\.py)'", source.split('names = [', 1)[1].split(']', 1)[0])


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, text=True, capture_output=True).stdout.splitlines()


def changed(base):
    merge = git('merge-base', 'HEAD', base)
    files = set(git('diff', '--name-only', 'HEAD')) | set(git('ls-files', '--others', '--exclude-standard'))
    if merge:
        files |= set(git('diff', '--name-only', merge[0], 'HEAD'))
    return sorted(f for f in files if f and not f.startswith('release-evidence/'))


def related(files, names):
    texts = {name: (ROOT / 'tools' / name).read_text(encoding='utf-8') for name in names}
    chosen, uncovered = [], []
    for path in files:
        base = pathlib.PurePosixPath(path).name
        stem = base.rsplit('.', 1)[0]
        hits = [n for n in names if path == f'tools/{n}'
                or base in texts[n] or (len(stem) > 5 and re.search(rf'\b{re.escape(stem)}\b', texts[n]))
                or any(path.startswith(prefix) for prefix in FOLDERS.get(n, ()))]
        if path.startswith('app/src/test/') and stem.endswith('Cases'):
            hits += [n for n in names if stem in texts[n]]
        chosen += hits
        if not hits and path.startswith(('app/', 'cloud/')):
            uncovered.append(path)
    return [n for n in names if n in set(chosen) | set(ALWAYS)], uncovered


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='origin/main')
    parser.add_argument('--files', nargs='*')
    parser.add_argument('--list', action='store_true', help='only print what would run')
    parser.add_argument('--all', action='store_true', help='every suite, without an evidence file')
    args = parser.parse_args()
    names = suites()
    files = args.files if args.files is not None else changed(args.base)
    selected, uncovered = (names, []) if args.all else related(files, names)
    if not (ROOT / 'app/google-services.json').exists():
        selected = [n for n in selected if n != SKIPPED_WITHOUT_CONFIG]
    print(f'{len(files)} changed files, {len(selected)} of {len(names)} suites')
    for path in uncovered:
        print(f'  no host suite reads {path}: only CI (build, unit tests, lint) checks it')
    if args.list:
        print('\n'.join('  ' + n for n in selected))
        return 0
    env = dict(os.environ)
    env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '') + ' -XX:-UsePerfData').strip()
    failed, started = [], time.time()
    for name in selected:
        run = subprocess.run([sys.executable, str(ROOT / 'tools' / name)], cwd=ROOT, env=env, text=True, capture_output=True)
        if run.returncode:
            failed.append(name)
            tail = [line for line in (run.stdout + run.stderr).splitlines() if 'Picked up JAVA_TOOL_OPTIONS' not in line][-12:]
            print(f'FAIL {name}\n    ' + '\n    '.join(tail))
    print(f'{len(selected) - len(failed)} passed, {len(failed)} failed in {time.time() - started:.0f} s' +
          (': ' + ', '.join(failed) if failed else ''))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
