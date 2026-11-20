#!/usr/bin/env python3
"""TREND-01: «Сезонні тренди» — the journal, the generator bonuses, the switch, the card lines and the strings (JDK 17)."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

# Scores only, through every generator entry point; the 0–10 rating and manual building keep the colour table alone.
rules = read('rules/DailyRules.java')
check('pairs += pairScore(selected.piece, next.piece) + trendScore(selected.piece, next.piece, trends);' in rules, 'trend pairs count in the generator')
check('return StyleRules.pairPoints(StyleRules.pair(a.color, b.color)) + (a.style.equals(b.style) ? 4 : 0);' in rules, 'pairScore (manual building) is the table alone')
check('rejected, fixed, 1, penalty, targetStyle, bonus, temperature, trends);' in rules, 'replacing one piece follows the trends')
check('TrendRules' not in read('rules/StyleRules.java'), 'the 0–10 rating does not know the trends')
daily = read('outfits/DailyOutfits.kt')
check('skip, fixed, 3, scores, target, liked, temperature, trends(choices))' in daily, 'the daily variants get the trend pairs')
check('bonus(s, wardrobe, choices), temperature, trends(choices))' in daily, 'replacing one piece gets the trend pairs')
check('TrendRules.bonus(pieces, choices.avoided)' in daily and 'season + PreferenceProfile.bonus(' in daily,
      'colours of the season join the bonus; a liked colour keeps its larger one')
check('fun trends(choices: PersonalChoices = PersonalChoices()): Set<String> = TrendRules.pairs(choices.trends)' in daily, 'switched off = no pairs')
check('liked, temperature, DailyOutfits.trends(choices))' in read('outfits/TripPlan.kt'), 'trip packing follows the trends')

# The switch: account-private, per wardrobe, on unless switched off, back on after «Скинути всі вподобання».
store = read('data/PersonalPreferences.kt')
check('private fun trendsKey(wardrobe: String) = "pref.trends.$wardrobe"' in store, 'the switch is stored per wardrobe')
check('prefs.getString(trendsKey(wardrobe), null) != OFF)' in store, 'on unless switched off')
check('remove(trendsKey(wardrobe))' in store.split('fun clear', 1)[1], 'reset turns trends back on')
check('val trends: Boolean = false)' in store, 'choices made without the store leave trends off')
for name in ('backup/Backup.kt', 'family/FamilyEnvelope.kt', 'family/FamilyArchive.kt'):
    path = source / name
    if path.exists(): check('pref.trends' not in path.read_text(encoding='utf-8'), f'{name} must not carry the switch')
prefs = read('ui/PreferencesDialog.kt')
check('Switch(checked = choices.trends, onCheckedChange = { vm.setTrends(wardrobe, it) }, enabled = !busy)' in prefs, 'the switch in «Мої вподобання»')
check('choices == PersonalChoices(trends = true)' in prefs, '«nothing set yet» counts trends on as the default')
check('fun setTrends(wardrobe: String, on: Boolean) = personalChoices.setTrends(wardrobe, on)' in read('ui/WardrobeViewModel.kt'), 'the view model sets it')

# The card says why a variant moved, only with trends on.
views = read('ui/OutfitStyleViews.kt')
check('if (palette.trends) {' in views and 'TrendRules.found(colours).take(2)' in views, 'trend pairs on the card')
check('R.string.style_trend_colours' in views and 'palette.palette(it) != PersonalChoices.AVOIDED' in views, 'colours of the season on the card')

# Languages: the same keys and placeholders in every locale.
keys = ['prefs_trends_title', 'prefs_trends_hint', 'style_trend_pair', 'style_trend_colours']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.((?:prefs_trends|style_trend)_\w+)', prefs + views)):
    check(name in keys, f'R.string.{name} is not in the checked key list')
# The journal in the release document lists the same pairs as the code.
journal = (root / 'docs/releases/TRENDS-0.14.57.md').read_text(encoding='utf-8')
code_pairs = re.findall(r'\{"(\w+)", "(\w+)"\}', read('rules/TrendRules.java'))
for a, b in code_pairs: check(f'`{a}` + `{b}`' in journal, f'the release journal lists {a} + {b}')
if failures: raise SystemExit('FAIL trends:\n' + '\n'.join(failures))
with tempfile.TemporaryDirectory(prefix='wardrobe-trends-') as output:
    files = [source / name for name in ('rules/WardrobeRules.java', 'rules/DailyRules.java', 'rules/PreferenceRules.java', 'rules/PreferenceProfile.java',
                                        'rules/StyleRules.java', 'rules/TrendRules.java', 'rules/WeatherRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/TrendRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.TrendRulesCases'], check=True)
print(f'PASS trend guards: {len(keys)} strings × 4 locales, {len(code_pairs)} pairs in code and release journal, scores only, switch and card')
