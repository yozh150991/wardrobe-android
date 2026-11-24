#!/usr/bin/env python3
"""The agent harness stays usable: a short AGENTS.md that matches the build, CLAUDE.md that imports it, and area
rules whose paths still point at real files."""
import glob
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures = []


def check(ok, message):
    if not ok:
        failures.append(message)


agents = (ROOT / 'AGENTS.md').read_text(encoding='utf-8')
claude = (ROOT / 'CLAUDE.md').read_text(encoding='utf-8')
gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
check(len(agents.splitlines()) <= 120, 'AGENTS.md is a map, at most 120 lines: history goes to docs/history/HANDOFFS.md')
check(len(claude.splitlines()) <= 60 and claude.startswith('@AGENTS.md\n'), 'CLAUDE.md starts by importing AGENTS.md and stays short')
name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
code = re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1)
state = agents.split('## Current state', 1)[1].split('\n## ', 1)[0] if '## Current state' in agents else ''
check(name in state and f'code {code}' in state, f'AGENTS.md "Current state" names the built version ({name} / code {code})')
for heading in ('## Where things are', '## Commands', '## Never', '## How a change goes'):
    check(heading in agents, f'AGENTS.md has the section {heading}')
for path in re.findall(r'`((?:app|tools|docs|cloud)/[^`*<>… ]+)`', agents):
    check((ROOT / path).exists(), f'AGENTS.md mentions {path}, which does not exist')
check((ROOT / 'docs/history/HANDOFFS.md').is_file(), 'the handoff history is kept in docs/history/HANDOFFS.md')

rules = sorted((ROOT / '.claude/rules').glob('*.md'))
check(len(rules) >= 5, 'area rules exist in .claude/rules/')
for rule in rules:
    text = rule.read_text(encoding='utf-8')
    front = re.match(r'---\npaths:\n((?:  - "[^"\n]+"\n)+)---\n', text)
    check(front is not None, f'{rule.name}: starts with a paths list')
    check(len(text.splitlines()) <= 60, f'{rule.name}: at most 60 lines')
    for pattern in re.findall(r'"([^"]+)"', front.group(1)) if front else []:
        check(bool(glob.glob(str(ROOT / pattern), recursive=True)), f'{rule.name}: {pattern} matches no file')
    for path in re.findall(r'`((?:app|tools|docs|cloud)/[^`*<>… ]+)`', text):
        check((ROOT / path).exists(), f'{rule.name} mentions {path}, which does not exist')

skills = sorted((ROOT / '.claude/skills').glob('*/SKILL.md'))
check(len(skills) >= 4, 'project skills exist in .claude/skills/')
for skill in skills:
    text = skill.read_text(encoding='utf-8')
    front = re.match(r'---\n(.*?)\n---\n', text, re.S)
    check(front is not None, f'{skill.parent.name}: SKILL.md starts with frontmatter')
    fields = dict(re.findall(r'(?m)^([a-z-]+):\s*(.+)$', front.group(1))) if front else {}
    check(fields.get('name') == skill.parent.name, f'{skill.parent.name}: name equals the folder name')
    check(40 <= len(fields.get('description', '')) <= 1024, f'{skill.parent.name}: a description says what it does and when to use it')
    check(len(text.splitlines()) <= 120, f'{skill.parent.name}: at most 120 lines')
    for path in re.findall(r'`((?:app|tools|docs|cloud)/[^`*<>… ]+)`', text):
        check((ROOT / path).exists(), f'{skill.parent.name} mentions {path}, which does not exist')
    for other in re.findall(r'`([a-z-]+)` skill', text):
        check((ROOT / '.claude/skills' / other / 'SKILL.md').is_file(), f'{skill.parent.name} refers to a missing skill {other}')

verify = (ROOT / 'tools/verify.py').read_text(encoding='utf-8')
check("split('names = [', 1)" in verify and 'ALWAYS = [\'check_harness.py\']' in verify, 'tools/verify.py takes its suites from run_host_checks.py')
for folder_suite in re.findall(r"'(check_\w+\.py)':", verify):
    check((ROOT / 'tools' / folder_suite).is_file(), f'tools/verify.py maps a missing suite {folder_suite}')

# Hooks and permissions (step 4): prohibitions block, edits are checked, a turn does not end on failing fast checks.
import importlib.util
import json
settings = json.loads((ROOT / '.claude/settings.json').read_text(encoding='utf-8'))
hooks = settings.get('hooks', {})
for event, script in (('PreToolUse', 'guard'), ('PostToolUse', 'after_edit'), ('Stop', 'stop_gate')):
    commands = [h.get('command', '') for group in hooks.get(event, []) for h in group.get('hooks', [])]
    check(any(c.endswith(f'run.sh" {script}') for c in commands), f'settings.json runs the {script} hook on {event}')
    check((ROOT / '.claude/hooks' / f'{script}.py').is_file(), f'.claude/hooks/{script}.py exists')
check((ROOT / '.claude/hooks/run.sh').is_file(), 'the hook launcher exists')
allowed, denied = ' '.join(settings['permissions']['allow']), ' '.join(settings['permissions']['deny'])
for word in ('push', 'git merge *', 'pr merge', 'deploy', 'gradlew', 'adb', 'Bash(rm', 'reset', 'rebase', '-X', 'Bash(git *)', 'Bash(gh *)', 'Bash(*)'):
    check(word not in allowed, f'the allow list does not pre-approve "{word}"')
for secret in ('google-services.json', '*.jks', '*.keystore', 'firebase-adminsdk', '.env'):
    check(secret in denied, f'reading {secret} is denied')
spec = importlib.util.spec_from_file_location('guard', ROOT / '.claude/hooks/guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
guard.pushed_version_problem = lambda: None
BLOCKED = ['git commit --no-verify -m x', 'git -c user.name=Claude commit -n -m x', 'git push --force origin main',
           'git -c push.negotiate=true push -f', 'git push origin +main', 'cd cloud && npx firebase deploy --only functions',
           'pwsh cloud/deploy-rules.ps1 -Deploy', 'gh pr merge 66 --squash',
           'gh api repos/yozh150991/wardrobe-android/pulls/66/merge -X PUT', 'cat app/google-services.json',
           'cp ~/keys/release.jks /tmp/x', 'git config core.hooksPath /dev/null', 'git add -f app/google-services.json',
           'git filter-branch --tree-filter x HEAD']
ALLOWED = ['git -c push.negotiate=true push -q -u origin harness/hooks',
           'git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -q -F /tmp/m.txt',
           'git commit --amend --no-edit', 'grep -n "google-services.json" app/build.gradle.kts',
           "cat > docs/x.md <<'EOF'\nnpx firebase deploy --only firestore:rules\nnever --no-verify\nEOF",
           'python3 tools/run_host_checks.py --allow-missing-client-config',
           'gh api repos/yozh150991/wardrobe-android/pulls -X POST -f title=x -f head=y -f base=main -F body=@/tmp/pr.md',
           'git add -A && git status --short', 'ls app/ | head']
for command in BLOCKED:
    check(guard.verdict(command) is not None, f'the guard blocks: {command}')
for command in ALLOWED:
    check(guard.verdict(command) is None, f'the guard allows: {command[:60]}')
versions = {}
real = importlib.util.module_from_spec(spec)
spec.loader.exec_module(real)
real.version = lambda ref: versions.get(ref)
for before, now, bad in (((92, '0.14.62-a'), (92, '0.14.62-b'), True), ((92, '0.14.62-a'), (93, '0.14.62-a'), True),
                         ((92, '0.14.62-a'), (91, '0.14.61-a'), True), ((92, '0.14.62-a'), (93, '0.14.63-b'), False),
                         ((92, '0.14.62-a'), (92, '0.14.62-a'), False), (None, (92, '0.14.62-a'), False)):
    versions = {'@{upstream}': before, 'HEAD': now}
    check((real.pushed_version_problem() is not None) == bad, f'pushed-version rule: {before} -> {now}')

ignore = (ROOT / '.gitignore').read_text(encoding='utf-8')
check('CLAUDE.local.md' in ignore and '.claude/settings.local.json' in ignore, 'personal Claude Code files are ignored by Git')
if failures:
    raise SystemExit('FAIL harness guards:\n  - ' + '\n  - '.join(failures))
print(f'PASS harness guards: AGENTS.md {len(agents.splitlines())} lines for {name}, CLAUDE.md imports it, {len(rules)} area rules and {len(skills)} skills with live paths')
