#!/usr/bin/env python3
"""WEAR-01: guard «Вдягаю сьогодні», wear on another day and the calendar's worn items."""
from pathlib import Path
import re, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

# Data: plain item rows only (no new wear source, so backup format 10 and the cloud copy stay as they are).
ops = read('data/CalendarOperations.kt')
wear = ops.split('suspend fun Repository.wearItems', 1)[1].split('suspend fun Repository.forgetWears', 1)[0]
check('require(RoadmapRules.validDate(date) && RoadmapRules.validDate(today) && date <= today)' in wear, 'wear is never recorded for a future day')
check('require(ids.all { dao.garment(it)?.wardrobeId == wardrobe })' in wear, 'only this wardrobe\'s items')
check('ids.filter { dao.itemCount(it, date) == 0 }' in wear and 'Wear(it, date, "item:$it")' in wear, 'only adds item rows where the day has no wear yet')
forget = ops.split('suspend fun Repository.forgetWears', 1)[1].split('suspend fun', 1)[0]
check('dao.clearWear(it, date, "item:$it")' in forget and 'clearItemDay' not in forget, 'the undo removes only the rows it added')
backup = read('backup/Backup.kt')
check(sorted(set(re.findall(r'wear\.source\.startsWith\("(\w+):"\)', backup))) == ['item', 'outfit', 'plan'], 'backup knows exactly three wear sources')
vm = read('ui/WardrobeViewModel.kt')
check('require(date == null || RoadmapRules.validDate(date) && date <= today)' in vm, 'the view model refuses a future day')

# Daily: one tap on a variant and on «Давно не вдягали»; the card of what was worn; an exact undo.
daily = read('ui/DailyScreen.kt')
check(daily.count('Button(onClick = { wearNow(') == 2, '«Вдягаю сьогодні» on the variants and on the rotation card')
check('fun wearNow(items: List<Garment>) = vm.wearItems(wardrobe, items, today)' in daily, 'the variant is worn today, without saving an outfit')
check('if (wornToday.isNotEmpty()) item(key = "worn-today")' in daily and 'R.string.wear_today_title' in daily, 'the screen shows what was worn today')
check('val undo = wornAdded.filter { id -> wornToday.any { it.id == id } }' in daily and 'vm.undoWears(wardrobe, undo, today)' in daily,
      'the undo covers only what the last tap added')
check(daily.count('OutlinedButton(onClick = { beginSave(') == 4, 'saving and planning stay available as secondary actions')

# Another day: the item's page and the calendar; never a future day.
ui = read('ui/Ui.kt')
check('DateChoiceDialog(today, { wearOtherDay = false }, latest = today)' in ui and 'vm.wearItems(g.wardrobeId, listOf(g), day)' in ui,
      'an earlier day can be marked from the item, only adding')
check('if (day in dates) vm.wear(g, day)' in ui and 'R.string.wear_remove_day' in ui, 'a day recorded by mistake can be removed after a confirmation')
calendar = read('ui/CalendarScreen.kt')
check('!Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(limit)' in calendar, 'the date picker stops at the limit')
check('val wornIds = s.wears.filter { it.date == date }.map { it.garmentId }.toSet()' in calendar and 'R.string.calendar_worn_title' in calendar,
      'the calendar shows what was worn that day, from any source')
check('if (markingWear && date <= today) VisualPickerDialog(' in calendar and 'if (date <= today) OutlinedButton(onClick = { markingWear = true }' in calendar,
      'worn items are marked only for today or an earlier day')
check('if (checked) vm.wearItems(wardrobe, listOf(g), date) else vm.wear(g, date)' in calendar, 'ticking marks, unticking removes')

# Languages: the same keys and placeholders in every locale.
keys = ['wear_look_today', 'wear_today_marked', 'wear_today_title', 'wear_today_hint', 'wear_other_day', 'wear_history_title', 'wear_remove_day',
        'calendar_worn_title', 'calendar_worn_empty', 'calendar_wear_item']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
if failures:
    raise SystemExit('FAIL wear:\n' + '\n'.join(failures))
print(f'PASS wear guards: {len(keys)} strings × 4 locales, item rows only, never a future day, exact undo, calendar diary')
