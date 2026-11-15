#!/usr/bin/env python3
"""UI-02: «Сьогодні» by the approved design — one variant at a time, every earlier action still one tap away."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tools'))
from today_design_strings import STRINGS

source = root / 'app/src/main/java/com/familywardrobe/app'
daily = (source / 'ui/DailyScreen.kt').read_text(encoding='utf-8')
views = (source / 'ui/OutfitStyleViews.kt').read_text(encoding='utf-8')
dates = (source / 'ui/DateLabels.kt').read_text(encoding='utf-8')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# One variant on screen; the page starts over with new variants and never points past them.
check('var page by rememberSaveable(wardrobe) { mutableIntStateOf(0) }' in daily, 'the variant on screen survives a detail/back transition')
check('generatedFor = input; page = 0' in daily, 'new variants start from the first one')
check('val index = page.coerceIn(0, looks.size - 1)' in daily and 'val items = looks[index]' in daily, 'the page never points past the variants')
check('itemsIndexed(looks' not in daily and daily.count('item(key = "variant")') == 1, 'one variant card, not a list of cards')

# «Інший»: the next variant found, then new ones (GEN-02 skips what was shown). A swipe never asks for new ones.
check('other = { if (index < looks.size - 1) page = index + 1 else otherVariants() }' in daily, '«Інший» shows the next variant, then asks for new ones')
check('fun otherVariants() {' in daily and 'skipped = ArrayList(if (skippedAll) shown else (skipped + shown).distinct()); revision++' in daily,
      'new variants skip the ones already shown')
swipe = daily[daily.index('detectHorizontalDragGestures('):daily.index('{ _, amount -> moved += amount }')]
check('if (moved < -limit && index < looks.size - 1) page = index + 1' in swipe and 'if (moved > limit && index > 0) page = index - 1' in swipe
      and 'otherVariants' not in swipe, 'a swipe moves only between the variants already found')
check('PagerDots(looks.size, index, position)' in daily and 'clearAndSetSemantics { contentDescription = description }' in views,
      'the dots say which variant is on screen')

# Every earlier action of a variant is still there.
check('OutfitCollage(items, vm, fixedIds.toSet(), !busy, collageHeight) { g -> actionItem = g.id; actionIndex = index }' in daily,
      'a tap on a piece opens its actions (details, replace, pin, exclude)')
check('Button(onClick = { wearNow(items) }, enabled = !busy && !wornAll(items)' in daily, '«Вдягаю сьогодні» is the main button')
check('dislike = { reasonFor = index; choosingItems = false; uncomfortable = arrayListOf() }' in daily, '«Не підходить…» opens the reasons')
check('TonalIcon(Icons.Outlined.Event, planText, enabled, plan)' in daily and 'TonalIcon(Icons.Outlined.ThumbDown, dislikeText, enabled, dislike)' in daily,
      'icon buttons are named for TalkBack')
check('BetterBlock(swap, !busy) { lookIds = ArrayList(lookIds.mapIndexed { i, old -> if (i == index) OutfitStyle.replaced(old, swap) else old }) }' in daily,
      '«Краще» changes the variant on screen')

# The picture: the first piece large, names for TalkBack, a lock on pinned pieces.
tile = views[views.index('private fun CollageTile('):views.index('internal fun ScorePill(')]
check('description = g.name' in tile and 'contentDescription = "${g.name}, $lockedText"' in tile and 'Icons.Outlined.Lock' in tile,
      'a piece is named and says when it is pinned')
check('.clickable(enabled = enabled, role = Role.Button) { onSelect(g) }' in tile, 'a piece is a button')
check('val columns = if (rest.size > 3) 2 else 1' in views, 'many pieces go into two columns')
check('R.string.style_score_description, rating.score' in views[views.index('internal fun ScorePill('):views.index('internal fun PhotoNote(')],
      'the score on the picture is read with its label')

# «Давно не вдягали»: one row first; an item chosen in the statistics opens at once.
check('if (!rotationOpen && anchor.isEmpty()) RotationRow(rotationItem, idle, vm, !busy) { rotationOpen = true }' in daily,
      '«Давно не вдягали» is a row until its outfit is asked for')
check('TextButton(onClick = { rotationOpen = false }' in daily, 'the opened card can be folded back')

# Large text: the actions and the row stack instead of squeezing.
check(daily.count('fontScale >= 1.3f') == 2 and 'if (stacked) Column(' in daily, 'large text stacks the actions')

# The rain explanation is next to the weather choice; the chip and the note say it shortly.
sheet = daily[daily.index('if (configuring) ModalBottomSheet('):daily.index('s.garments.find { it.id == actionItem')]
check('R.string.weather_rain_auto, rainChance' in sheet, 'the conditions say why it is «Дощ»')
check('(if (weather == "rain" && rainChance != null) " $rainChance%" else "")' in daily and 'R.string.today_note_rain' in daily,
      'the chip shows the rain chance and the picture notes the outerwear')
check('DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)' in dates and 'Text(dayLabel(today)' in daily, 'the day is shown under the title')

for name in set(re.findall(r'R\.string\.(today_\w+)', daily + views)) - {'today_tab'}:
    check(name in STRINGS, f'{name} has no translations')
for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    for name, texts in STRINGS.items():
        value = strings.get(name, '')
        check(bool(value.strip()), f'{folder}: {name} missing')
        check(sorted(re.findall(r'%\d\$[sd]', value)) == sorted(re.findall(r'%\d\$[sd]', texts[0])), f'{folder}: {name} placeholders differ')
for name in STRINGS:
    check(f'R.string.{name}' in daily + views, f'{name} is not used')

if failures: raise SystemExit('FAIL today design:\n' + '\n'.join(failures))
print(f'PASS today-design guards: one variant with dots, swipe and «Інший»; all actions kept; compact «Давно не вдягали»; {len(STRINGS)} strings × 4 locales')
