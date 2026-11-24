#!/usr/bin/env python3
"""PreToolUse hook for shell commands: blocks what AGENTS.md says never to do.

Reads the hook's JSON from stdin; exit code 2 blocks the command and shows stderr to the agent. Permission patterns
cannot do this job alone: `Bash(git push *)` does not match `git -c push.negotiate=true push`, the form this
repository uses. Test: `python3 tools/check_harness.py`.
"""
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SECRET = r'(google-services\.json|\.jks\b|\.keystore\b|keystore\.properties|key\.properties|signing\.properties|secrets\.properties|firebase-adminsdk|service[-_]account[^ ]*\.json|(^|[\s/\\])\.env\b)'
RULES = [
    (r'--no-verify\b', 'Git Safety is never bypassed (--no-verify). Fix what the hook reports.'),
    (r'\bgit\b[^|;&]*\bcommit\b[^|;&]*\s-[a-mo-zA-Z]*n[a-zA-Z]*\b', 'git commit -n skips the Git Safety hook. Commit without it.'),
    (r'\bgit\b[^|;&]*\bpush\b[^|;&]*(\s--force\b|\s--force-with-lease\b|\s-[a-zA-Z]*f[a-zA-Z]*\b|\s\+\S)', 'No force push: history is never rewritten here.'),
    (r'\bgit\b[^|;&]*\b(filter-branch|filter-repo)\b', 'History is never rewritten here.'),
    (r'core\.hooksPath', 'The Git hooks path stays as install-git-safety set it.'),
    (r'\bfirebase\b[^|;&]*\bdeploy\b', 'The agent never deploys. Give the owner the scoped command instead (see .claude/rules/cloud.md).'),
    (r'deploy-rules\.ps1', 'cloud/deploy-rules.ps1 is never run or recommended: with -Deploy it deploys storage.rules without the family rules.'),
    (r'\bgh\b[^|;&]*\bpr\s+merge\b|\bgh\b[^|;&]*pulls/\d+/merge\b', 'Merging is the owner\'s. Report that the PR is ready.'),
    (r'\b(cat|type|more|less|head|tail|bat|Get-Content|gc|cp|copy|Copy-Item|scp|curl|base64|xxd|strings|nl)\b[^|;&]*' + SECRET,
     'Keys and Firebase config are not read or copied by the agent.'),
    (r'\bgit\b[^|;&]*\badd\b[^|;&]*(\s-f\b|\s--force\b)', 'git add -f would stage ignored files (keys, local config). Stage files by name.'),
]


def version(ref):
    shown = subprocess.run(['git', 'show', f'{ref}:app/build.gradle.kts'], cwd=ROOT, text=True, capture_output=True)
    if shown.returncode:
        return None
    code = re.search(r'versionCode\s*=\s*(\d+)', shown.stdout)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', shown.stdout)
    return (int(code.group(1)), name.group(1)) if code and name else None


def pushed_version_problem():
    """The CI lesson of 0.14.58: once a branch is pushed, never rename the version with the same code or change the code alone."""
    before, now = version('@{upstream}'), version('HEAD')
    if not before or not now or before == now:
        return None
    if now[0] < before[0]:
        return f'versionCode went down ({before[0]} -> {now[0]}).'
    if now[1] != before[1] and now[0] == before[0]:
        return f'versionName changed ({before[1]} -> {now[1]}) with the same versionCode {now[0]}: bump the code too.'
    if now[1] == before[1] and now[0] != before[0]:
        return f'versionCode changed ({before[0]} -> {now[0]}) with the same versionName: give it a new name too.'
    return None


def spoken(command):
    """The command without here-document bodies: text written into a file is not a command."""
    return re.sub(r"<<-?\s*(['\"]?)(\w+)\1.*?^\s*\2\s*$", '', command, flags=re.S | re.M)


def verdict(command):
    command = spoken(command)
    for pattern, reason in RULES:
        if re.search(pattern, command, re.I | re.S):
            return reason
    if re.search(r'\bgit\b[^|;&]*\bpush\b', command):
        problem = pushed_version_problem()
        if problem:
            return 'This branch is already pushed and CI compares versions with its previous push. ' + problem
    return None


def main():
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0
    command = (event.get('tool_input') or {}).get('command') or ''
    reason = verdict(command)
    if reason:
        print('Blocked by the Wardrobe harness: ' + reason + ' (If the words are only quoted text, put them in a file with the Write tool.)', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
