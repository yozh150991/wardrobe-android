#!/usr/bin/env python3
"""STYLE-01–03: run the production style rules with their cases (JDK 17) and guard how the Daily screen uses them."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# The cases cover every base colour of the app.
matching = (source / 'Matching.kt').read_text()
colours = matching.split('val colors = listOf(', 1)[1].split(')', 1)[0]
cases = (root / 'app/src/test/java/com/familywardrobe/app/StyleRulesCases.java').read_text()
check(colours == cases.split('List<String> COLOURS = List.of(', 1)[1].split(')', 1)[0], 'StyleRulesCases.COLOURS must equal Matching.colors')
rules = (source / 'StyleRules.java').read_text()
for colour in re.findall(r'"(\w+)"', colours):
    check(colour == 'other' or f'COLOURS.put("{colour}"' in rules, f'StyleRules has no attributes for {colour}')
views = (source / 'OutfitStyleViews.kt').read_text()
for colour in re.findall(r'"(\w+)"', colours):
    check(colour == 'other' or f'"{colour}" -> Color(' in views, f'no swatch for {colour}')

daily = (source / 'DailyScreen.kt').read_text()
check('OutfitComposition(' not in daily, 'Daily cards use the tile grid (STYLE-03)')
check(daily.count('ScoreBadge(rating)') == 2 and daily.count('StyleReasons(rating') == 2 and daily.count('OutfitGrid(') == 2,
      'variants and «Давно не вдягали» both show the score, the reasons and the grid')
check('withContext(Dispatchers.Default) {\n            buildMap {' in daily, '«Краще» is computed off the UI thread')
check('pins + anchorId' in daily, 'the item of «Давно не вдягали» is never swapped away')
check('LookRules.OCCASIONS.forEach { code ->' in daily and 'occasion = code' in daily, 'the occasion chip picks the occasion')
style = (source / 'OutfitStyle.kt').read_text()
check('DailyOutfits.items(s, date, allowed)' in style, 'swaps use only what the daily suggestions may use')
check('DailyOutfits.rejected(s, wardrobe, occasion, weather)' in style, 'swaps keep hidden combinations hidden')
check("it.reason == PreferenceRules.DISLIKE" in style, 'swaps skip items marked «Незручно»')
check('clearAndSetSemantics { contentDescription = description }' in views, 'the score is read as one sentence')
check('fontScale >= 1.4f) 2 else 3' in views, 'two tiles per row with a large font')

keys = ['style_label_great', 'style_label_good', 'style_label_fair', 'style_label_weak', 'style_score_description', 'style_pair_line',
        'style_pair_warm_cool', 'style_pair_contrast', 'style_pair_accent', 'style_pair_calm', 'style_pair_tonal', 'style_pair_unknown',
        'style_pair_same_accent', 'style_pair_black_brown', 'style_pair_dark_dark', 'style_pair_two_accents', 'style_one_style',
        'style_one_style_other', 'style_mixed', 'style_weather_ok', 'style_weather_no_outerwear', 'style_weather_season', 'style_now',
        'style_better', 'style_better_change', 'style_better_style', 'style_better_weather', 'style_apply', 'style_occasion_chip']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.(style_\w+)', views + daily)):
    check(name in keys, f'R.string.{name} is not in the checked key list')
for code in re.findall(r'public static final String (\w+) = "\w+";', rules.split('// Weather codes.')[0]):
    check(f'StyleRules.{code} ->' in views or code == 'UNKNOWN', f'no words for pair {code}')
if failures:
    raise SystemExit('FAIL style:\n' + '\n'.join(failures))

with tempfile.TemporaryDirectory(prefix='wardrobe-style-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'DailyRules.java', 'PreferenceRules.java', 'PreferenceProfile.java', 'StyleRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/StyleRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.StyleRulesCases'], check=True)
print(f'PASS style guards: {len(keys)} strings × 4 locales, every colour rated and drawn, grid/score/reasons on Daily')
