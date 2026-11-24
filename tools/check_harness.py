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

ignore = (ROOT / '.gitignore').read_text(encoding='utf-8')
check('CLAUDE.local.md' in ignore and '.claude/settings.local.json' in ignore, 'personal Claude Code files are ignored by Git')
if failures:
    raise SystemExit('FAIL harness guards:\n  - ' + '\n  - '.join(failures))
print(f'PASS harness guards: AGENTS.md {len(agents.splitlines())} lines for {name}, CLAUDE.md imports it, {len(rules)} area rules and {len(skills)} skills with live paths')
