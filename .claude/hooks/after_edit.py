#!/usr/bin/env python3
"""PostToolUse hook for file edits: runs the host suites that read the edited file and tells the agent what failed.

It warns, it does not undo: the edit has happened. Exit code 2 shows stderr to the agent next to the tool result.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WATCHED = ('app/', 'cloud/', 'tools/', '.claude/', 'AGENTS.md', 'CLAUDE.md')


def main():
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0
    path = (event.get('tool_input') or {}).get('file_path') or ''
    try:
        relative = pathlib.Path(path).resolve().relative_to(ROOT).as_posix()
    except (ValueError, OSError):
        return 0
    if not relative.startswith(WATCHED):
        return 0
    run = subprocess.run([sys.executable, str(ROOT / 'tools/verify.py'), '--files', relative], cwd=ROOT, text=True,
                         capture_output=True, timeout=240)
    if run.returncode == 0:
        return 0
    lines = [line for line in run.stdout.splitlines() if line.strip()][-25:]
    print(f'Fast checks fail after the edit of {relative} (python3 tools/verify.py):\n' + '\n'.join(lines), file=sys.stderr)
    return 2


if __name__ == '__main__':
    try:
        sys.exit(main())
    except subprocess.TimeoutExpired:
        sys.exit(0)
