#!/usr/bin/env python3
"""Turns Kotlin, Java, lint and Gradle failures from a build log into GitHub annotations.

Annotations appear in the pull request and are available through the checks API even where the raw job log
cannot be downloaded. Usage: python3 tools/ci_annotations.py build.log [--junit RESULTS_DIR]
"""
import glob
import xml.etree.ElementTree as ET
import os
import re
import sys

KOTLIN = re.compile(r'^e: (?:file://)?(?P<path>/\S+?\.kts?):(?P<line>\d+):(?P<col>\d+) (?P<msg>.*)$')
JAVA = re.compile(r'^(?P<path>/\S+?\.java):(?P<line>\d+): error: (?P<msg>.*)$')
LINT = re.compile(r'^(?P<path>/\S+?):(?P<line>\d+): Error: (?P<msg>.*)$')
# Gradle test output: "com.example.SomeTest > name[device] FAILED" followed by the indented exception.
TEST = re.compile(r'^(?P<test>[\w.$]+ > [^\n]+?) FAILED$')


def escape(text):
    return text.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def junit_failures(directory):
    """Failed test cases from JUnit XML reports (connected and JVM tests write the same format)."""
    failed = []
    for report in sorted(glob.glob(os.path.join(directory, '**', '*.xml'), recursive=True)):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter('testcase'):
            problem = case.find('failure') if case.find('failure') is not None else case.find('error')
            if problem is None:
                continue
            text = (problem.get('message') or problem.text or '').strip().splitlines()
            frames = [l.strip() for l in (problem.text or '').splitlines() if 'com.familywardrobe' in l][:3]
            failed.append(f"{case.get('classname')} > {case.get('name')} — {' '.join(text[:3])[:500]} | {' | '.join(frames)}")
    return failed


def main(path, junit=None):
    root = os.getcwd(); found = []; gradle = []
    tests = junit_failures(junit) if junit else []
    lines = open(path, encoding='utf-8', errors='replace').read().splitlines()
    for index, raw in enumerate(lines):
        line = re.sub(r'\x1b\[[0-9;]*m', '', re.sub(r'^\S*Z ', '', raw)).strip()  # timestamps, ANSI colours
        match = KOTLIN.match(line) or JAVA.match(line) or LINT.match(line)
        if match:
            relative = os.path.relpath(match['path'], root)
            column = f",col={match['col']}" if 'col' in match.groupdict() and match['col'] else ''
            found.append((relative, match['line'], column, match['msg']))
        elif TEST.match(line) and not junit:
            detail = [l.strip() for l in lines[index + 1:index + 4] if l.strip() and not TEST.match(l.strip())]
            tests.append(TEST.match(line)['test'] + ' — ' + ' | '.join(detail)[:600])
        elif line == '* What went wrong:':
            gradle.append(' '.join(l.strip() for l in lines[index + 1:index + 4] if l.strip()))
    for relative, number, column, message in found[:10]:
        print(f'::error file={relative},line={number}{column}::{escape(message)}')
    if found:
        summary = '\n'.join(f'{r}:{n} {m}' for r, n, _, m in found[:60])
        print(f'::error title=Build errors ({len(found)})::{escape(summary)}')
    for message in tests[:10]:
        print(f'::error title=Test failed::{escape(message)}')
    if len(tests) > 10:
        print(f'::error title=Failed tests ({len(tests)})::{escape(chr(10).join(tests[:60]))}')
    for message in gradle[:3]:
        print(f'::error title=Gradle::{escape(message)}')
    if not found and not gradle and not tests:
        tail = '\n'.join(lines[-40:])
        print(f'::error title=Build failed (log tail)::{escape(tail)}')


if __name__ == '__main__':
    arguments = sys.argv[1:]
    junit = arguments[arguments.index('--junit') + 1] if '--junit' in arguments else None
    main(arguments[0], junit)
