#!/usr/bin/env python3
"""Dependency-free source checks. Does NOT compile Kotlin or replace Android tests."""
from pathlib import Path
from source_paths import source_paths
import re
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
res = ROOT / 'app/src/main/res'
source = ROOT / 'app/src/main/java/com/familywardrobe/app'
checked = []

def check(condition, description):
    if not condition:
        raise AssertionError(description)
    checked.append(description)

xmls = list((ROOT / 'app/src/main').rglob('*.xml'))
for path in xmls:
    ET.parse(path)
check(len(xmls) >= 8, 'All Android XML files parse')
translations = {}
for language in ['values', 'values-uk', 'values-en', 'values-pl']:
    tree = ET.parse(res / language / 'strings.xml')
    pairs = [(s.attrib['name'], s.text or '') for s in tree.getroot()]
    check(len(pairs) == len(dict(pairs)), f'No duplicate strings in {language}')
    translations[language] = dict(pairs)
base = translations['values']
for language, values in translations.items():
    check(values.keys() == base.keys(), f'Complete resource coverage in {language}')
    for key, value in values.items():
        check(re.findall(r'%\d+\$[ds]', value) == re.findall(r'%\d+\$[ds]', base[key]), f'Format parameters: {language}/{key}')
all_kotlin = '\n'.join(p.read_text() for p in source.glob('*.kt'))
refs = set(re.findall(r'R\.string\.(\w+)', all_kotlin))
check(refs <= base.keys(), f'All {len(refs)} Kotlin string references exist')

def check_delimiters(path):
    text = path.read_text()
    # Ignore literals and comments; this catches truncation, not type errors.
    tokens = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*', '', text)
    stack = []
    for c in tokens:
        if c in '([{': stack.append(c)
        elif c in ')]}':
            if not stack or stack.pop() != {')':'(', ']':'[', '}':'{'}[c]:
                raise AssertionError(f'Unbalanced delimiter in {path.name}')
    check(not stack, f'Balanced delimiters: {path.name}')

for path in source_paths(ROOT):
    check_delimiters(path)
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
# Dependencies can merge permissions. Inspect the merged manifest during Android lint/build.
check('android:allowBackup="false"' in manifest, 'Automatic OS backup disabled; user controls ZIP export')
check('android:exported="false"' in manifest, 'FileProvider is not exported')
check('fallbackToDestructiveMigration' not in all_kotlin, 'No destructive Room migration fallback')
check('.thumbnail' not in all_kotlin, 'Photo originals and previews have explicit filenames')
sha = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
for name in ['build-apk.sh', 'build-apk.ps1', 'gradle/wrapper/gradle-wrapper.properties']:
    check(sha in (ROOT / name).read_text(), f'Pinned official Gradle checksum: {name}')

# Exercise the actual DAO SQL on a minimal SQLite fixture. Room code generation
# and the on-device schema still require connectedDebugAndroidTest.
db = sqlite3.connect(':memory:')
# Use the real exported schema and production migrations, rather than a hand-built subset.
import json
schema = json.loads((ROOT / 'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
data = (source / 'Data.kt').read_text()
target = int(re.search(r'version = (\d+)', data).group(1))
from host_schema import migrate
migrate(db, target=target)
queries = re.findall(r'@Query\("([^"\n]+)"\)\s*(?:suspend\s+)?fun\s+(\w+)', data)
params = {'outfitId':'first','garmentId':'a','id':'a','wardrobe':'home','signature':'a|b|c','date':'2026-09-21','source':'outfit:first','name':'Renamed'}
for sql, name in queries:
    for binding in re.findall(r':([A-Za-z_]\w*)', sql): params.setdefault(binding, 'fixture')
    db.execute('EXPLAIN '+sql, params)
check(len(queries) >= 15, f'All {len(queries)} DAO SQL queries are accepted by SQLite')
q = {name: sql for sql,name in queries}
db.executemany('INSERT INTO wears VALUES (?,?,?)', [
    ('a','2026-09-21','item:a'),('a','2026-09-21','outfit:first'),
    ('b','2026-09-21','outfit:first'),('b','2026-09-21','outfit:second')])
db.execute(q['clearSourceDay'], params)
check(db.execute('SELECT source FROM wears WHERE garmentId="a"').fetchall() == [('item:a',)], 'Actual DAO undo query preserves independent item wear')
check(db.execute('SELECT source FROM wears WHERE garmentId="b"').fetchall() == [('outfit:second',)], 'Actual DAO undo query preserves overlapping outfit wear')
db.close()
tests = list((ROOT / 'app/src/test').rglob('*.kt')) + list((ROOT / 'app/src/androidTest').rglob('*.kt'))
test_count = sum(p.read_text().count('@Test') for p in tests)
check(test_count >= 38, f'{test_count} Kotlin test cases are included (execution pending)')
print(f'PASS: {len(checked)} source/SQL checks; {len(xmls)} XML files; {len(base)} localized strings; {len(queries)} DAO queries.')
print('NOT RUN: Kotlin compilation, Gradle lint, JVM tests, Android tests, APK build, device and emulator checks.')
