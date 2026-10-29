#!/usr/bin/env python3
"""Turns Kotlin, Java, lint and Gradle failures from a build log into GitHub annotations.

Annotations appear in the pull request and are available through the checks API even where the raw job log
cannot be downloaded. Usage: python3 tools/ci_annotations.py build.log
"""
import os
import re
import sys

KOTLIN = re.compile(r'^e: (?:file://)?(?P<path>/\S+?\.kts?):(?P<line>\d+):(?P<col>\d+) (?P<msg>.*)$')
JAVA = re.compile(r'^(?P<path>/\S+?\.java):(?P<line>\d+): error: (?P<msg>.*)$')
LINT = re.compile(r'^(?P<path>/\S+?):(?P<line>\d+): Error: (?P<msg>.*)$')


def escape(text):
    return text.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def main(path):
    root = os.getcwd(); found = []; gradle = []
    lines = open(path, encoding='utf-8', errors='replace').read().splitlines()
    for index, raw in enumerate(lines):
        line = re.sub(r'^\S*Z ', '', raw).strip()  # tolerate timestamped logs
        match = KOTLIN.match(line) or JAVA.match(line) or LINT.match(line)
        if match:
            relative = os.path.relpath(match['path'], root)
            column = f",col={match['col']}" if 'col' in match.groupdict() and match['col'] else ''
            found.append((relative, match['line'], column, match['msg']))
        elif line == '* What went wrong:':
            gradle.append(' '.join(l.strip() for l in lines[index + 1:index + 4] if l.strip()))
    for relative, number, column, message in found[:10]:
        print(f'::error file={relative},line={number}{column}::{escape(message)}')
    if found:
        summary = '\n'.join(f'{r}:{n} {m}' for r, n, _, m in found[:60])
        print(f'::error title=Build errors ({len(found)})::{escape(summary)}')
    for message in gradle[:3]:
        print(f'::error title=Gradle::{escape(message)}')
    if not found and not gradle:
        tail = '\n'.join(lines[-40:])
        print(f'::error title=Build failed (log tail)::{escape(tail)}')


if __name__ == '__main__':
    main(sys.argv[1])
