#!/usr/bin/env python3
"""GEN-03 / UX-05 / UX-06: guard recent-wear counting, the remembered occasion and «Ношу рідко»."""
from pathlib import Path
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

daily = read('outfits/DailyOutfits.kt')
check('DailyRules.recentWears(rows.map { it.date }, date)' in daily and 'wear[g.id]?.maxOfOrNull { it.date }' in daily,
      'only recent wear counts against an item; the last wear still drives the repeat rule')
check('s.wears.filter { it.date <= date }' in daily, 'future wear never counts')
rules = read('rules/DailyRules.java')
check('public static final int RECENT_DAYS = 30;' in rules, 'thirty days')
rotation = read('rules/RotationRules.java')
check('item.rare || problem(item, weather, temperature) != null' in rotation, '«Ношу рідко» is never offered as forgotten')
check('state.seen[g.id].orEmpty(), g.rarelyWorn)' in read('outfits/RotationOutfits.kt'), 'the flag reaches the rotation rules')
check('!g.rarelyWorn && RotationRules.forgotten(' in read('ui/StatisticsScreen.kt'), 'the statistics agree')
check('if (g.rarelyWorn) Text(stringResource(R.string.rare_hint)' in read('ui/Ui.kt'), 'the chip says what it does')
screen = read('ui/DailyScreen.kt')
check('var occasion by rememberSaveable(wardrobe) { mutableStateOf(vm.lastOccasion(wardrobe)) }' in screen
      and 'LaunchedEffect(wardrobe, occasion) { vm.rememberOccasion(wardrobe, occasion) }' in screen, 'the occasion is remembered per wardrobe')
store = read('data/PersonalPreferences.kt')
check('"daily.occasion.$wardrobe"' in store and 'require(occasion in LookRules.OCCASIONS)' in store, 'only a known occasion is stored')
check('occasionKey' not in store.split('fun clear', 1)[1].split('}', 3)[0] + store.split('fun clear', 1)[1].split('mutable.value++', 1)[0],
      'a reset of preferences keeps the occasion (it is a convenience)')
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    check(bool(strings.get('rare_hint', '').strip()), f'{folder}: rare_hint missing')
if failures: raise SystemExit('FAIL recent wears:\n' + '\n'.join(failures))
print('PASS recent-wear guards: 30-day wear, occasion remembered, «Ношу рідко» not forgotten, 1 string × 4 locales')
