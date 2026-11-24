#!/usr/bin/env python3
"""Stop hook: the agent does not finish a turn while the fast checks fail on what it changed.

Runs tools/verify.py on the changed files (working tree and commits since origin/main). A passing state is cached
by the files' timestamps, so an unchanged tree costs nothing. After three blocks in a row in one session it lets the
turn end and tells the user, so a check that cannot be fixed right now never traps the session.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LIMIT = 3


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, text=True, capture_output=True).stdout.strip()


def fingerprint():
    merge = git('merge-base', 'HEAD', 'origin/main')
    names = set(git('diff', '--name-only', 'HEAD').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
    if merge:
        names |= set(git('diff', '--name-only', merge, 'HEAD').splitlines())
    stamps = []
    for name in sorted(n for n in names if n and not n.startswith('release-evidence/')):
        file = ROOT / name
        stamps.append([name, file.stat().st_mtime_ns if file.exists() else 0])
    return stamps


def main():
    try:
        event = json.load(sys.stdin)
    except ValueError:
        event = {}
    git_dir = pathlib.Path(git('rev-parse', '--absolute-git-dir') or ROOT / '.git')
    cache = git_dir / 'wardrobe-stop-gate.json'
    try:
        state = json.loads(cache.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        state = {}
    stamps = fingerprint()
    if not stamps or state.get('passed') == stamps:
        return 0
    run = subprocess.run([sys.executable, str(ROOT / 'tools/verify.py')], cwd=ROOT, text=True, capture_output=True, timeout=540)
    session = event.get('session_id', '')
    if run.returncode == 0:
        cache.write_text(json.dumps({'passed': stamps}), encoding='utf-8')
        return 0
    blocks = state.get('blocks', 0) + 1 if state.get('session') == session else 1
    cache.write_text(json.dumps({'session': session, 'blocks': blocks}), encoding='utf-8')
    lines = [line for line in run.stdout.splitlines() if line.strip()][-25:]
    if blocks > LIMIT:
        print(json.dumps({'systemMessage': 'Wardrobe harness: the fast checks still fail; the turn ends anyway after '
                          f'{LIMIT} attempts. Run python3 tools/verify.py to see them.'}))
        return 0
    print('The fast checks fail on your changes, so the work is not finished. Fix the cause (or the guard, with a stated '
          'reason), or tell the owner exactly what fails and why it cannot be fixed now.\n' + '\n'.join(lines), file=sys.stderr)
    return 2


if __name__ == '__main__':
    try:
        sys.exit(main())
    except subprocess.TimeoutExpired:
        sys.exit(0)
