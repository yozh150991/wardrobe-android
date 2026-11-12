#!/usr/bin/env python3
"""GEN-01/02: run the production daily generator with its fairness and variety cases (JDK 17)."""
from pathlib import Path
import shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
rules = (source / 'DailyRules.java').read_text()
failures = []
if 'pairs * 1000 / pairCount' not in rules: failures.append('pairs are averaged over the pairs (GEN-01)')
if 'for (Candidate c : distinct(shown, limit))' not in rules: failures.append('the variants shown are chosen for variety (GEN-02)')
if 'Collections.sort(taken);' not in rules: failures.append('the chosen variants keep the original order')
# «Показати інші варіанти» skips what was already shown and starts again when nothing new is left.
daily = (source / 'DailyScreen.kt').read_text()
if 'var skipped by rememberSaveable(wardrobe, occasion, weather, today)' not in daily: failures.append('shown variants are remembered per conditions')
if 'DailyOutfits.daily(s, wardrobe, occasion, weather, today, "$today:$revision", skip, allowed, fixed, choices, temperature = temperature)' not in daily:
    failures.append('the next variants skip the ones already shown')
if 'if (skip.isEmpty() || other.looks.isNotEmpty() && !other.hiddenShown) other to false' not in daily:
    failures.append('when nothing new is left the best variants return (never the hidden ones instead)')
if 'skipped = ArrayList(if (skippedAll) shown else (skipped + shown).distinct()); revision++' not in daily: failures.append('the button adds the shown variants to the skipped ones')
import re, xml.etree.ElementTree as E
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    if not strings.get('daily_other_variants', '').strip(): failures.append(f'{folder}: daily_other_variants missing')
if failures: raise SystemExit('FAIL generator:\n' + '\n'.join(failures))
with tempfile.TemporaryDirectory(prefix='wardrobe-generator-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'DailyRules.java', 'PreferenceRules.java', 'PreferenceProfile.java', 'StyleRules.java', 'WeatherRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/GeneratorRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.GeneratorRulesCases'], check=True)
