#!/usr/bin/env python3
"""ROT-01: run the production rotation rules with their cases (JDK 17) and guard how the app uses them."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# One definition of «давно не вдягали» for the statistics and the daily suggestion.
stats = (source / 'StatisticsScreen.kt').read_text()
check('RotationRules.forgotten(' in stats, 'WardrobeStats must use RotationRules.forgotten')
check(not re.search(r'>=\s*90\b|\b90\s*<=', stats), 'no separate 90-day literal in the statistics')
check('pickToday(g)' in stats and 'WardrobeRules.ready(g.rulePiece())) TextButton(onClick = { pickToday(g) }' in stats,
      'statistics offer «Підібрати на сьогодні» only for ready items')
outfits = (source / 'RotationOutfits.kt').read_text()
check('RotationRules.candidates(' in outfits and 'RotationRules.order(' in outfits and 'RotationRules.ATTEMPTS' in outfits,
      'suggest uses the shared candidates, order and attempt limit')
check('it.date <= today' in outfits, 'only actual wears on or before today count')
check('it.reason == PreferenceRules.DISLIKE' in outfits and "!it.signature.contains('|')" in outfits,
      'uncomfortable items come from PERS-01 item rows')
check('if (fixed[g.category]?.let { it != g.id } == true) continue' in outfits, 'a pinned item of the same category wins')
check('fixed.filterKeys { it != g.category }' in outfits, 'an item chosen in the statistics replaces a pin of its category')
daily = (source / 'DailyScreen.kt').read_text()
check('val rotationInput = if (capsule != null) ""' in daily, 'no rotation card in capsules')
check('withContext(Dispatchers.Default) {\n            RotationOutfits.suggest(' in daily, 'the suggestion runs off the UI thread')
check('!(rotation && item.id == rotationAnchorId)' in daily, 'the anchor item is not replaced (that is «Інша річ»)')
check('if (anchor.isNotEmpty()) clearAnchor()' in daily, '«Інша річ» leaves the item chosen in the statistics')
ui = (source / 'Ui.kt').read_text()
check('.put("rotationAnchor", rotationAnchor)' in ui and 'rotationAnchor = f.optString("rotationAnchor")' in ui,
      'the chosen item is part of the navigation frame')
check('go("daily")' not in ui.replace('fun openDaily(anchor: String = "") { go("daily")', ''), 'every other way to Daily clears the chosen item')

# Languages: the same keys and placeholders in every locale.
keys = ['rotation_title', 'rotation_anchor_title', 'rotation_reason_worn', 'rotation_reason_never', 'rotation_fits', 'rotation_saved_name',
        'rotation_other', 'rotation_pick_today', 'rotation_problem_unavailable', 'rotation_problem_excluded', 'rotation_problem_weather',
        'rotation_problem_repeat', 'rotation_problem_items']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.(rotation_\w+)', daily + stats)):
    check(name in keys, f'R.string.{name} is not in the checked key list')
if failures:
    raise SystemExit('FAIL rotation:\n' + '\n'.join(failures))

with tempfile.TemporaryDirectory(prefix='wardrobe-rotation-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'DailyRules.java', 'PreferenceRules.java', 'PreferenceProfile.java', 'RotationRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/RotationRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.RotationRulesCases'], check=True)
print(f'PASS rotation guards: {len(keys)} strings × 4 locales, one 90-day rule, capsule/pin/anchor handling')
