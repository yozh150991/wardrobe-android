#!/usr/bin/env python3
"""UI-04: the item page by the approved design — photo on top, three numbers, a bar at the bottom, every action kept."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tools'))
from item_page_strings import STRINGS

source = root / 'app/src/main/java/com/familywardrobe/app'
page = (source / 'ui/ItemPage.kt').read_text(encoding='utf-8')
ui = (source / 'ui/Ui.kt').read_text(encoding='utf-8')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# The page owns its header: back over the photo, the app's bar hidden only here.
check('if (!(route == "detail" && garment != null)) WardrobeTopBar(' in ui, 'the bar is hidden only on the item page')
check('Detail(garment, snapshot, vm, today, onBack = ::back,' in ui, 'back on the photo goes where the bar did')
check('fun Detail(' not in ui, 'the page lives in ItemPage.kt only')
check('RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), enabled = true, onClick = onBack)' in page,
      'back is always available, even while busy')
check('PhotoExportDialog(g.photoId, g.name, vm.photos' in page and 'if (g.photoId.isNotBlank())' in page, 'share is on the photo when there is one')
check('RoundButton(Icons.Outlined.Edit, stringResource(R.string.edit), !busy, onClick = onEdit)' in page, 'edit is on the photo')
check('height((if (compact) 252.dp else 320.dp) + statusBar + PHOTO_BUTTON_BAND)' in page, 'the photo is shorter on short screens')
# BUG-61-01: the app draws under the status bar; the picture and the buttons start below it, the picture below the buttons.
check('val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()' in page
      and 'inset = PaddingValues(top = statusBar + PHOTO_BUTTON_BAND, bottom = 12.dp)' in page
      and 'Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)' in page, 'the photo and its buttons clear the status bar')
# BUG-61-02: «Ношу рідко» is a switch row among the actions, not a chip among the labels.
labels = page[page.index('FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)'):page.index('val stats = listOf(')]
check('FilterChip' not in labels and 'R.string.rare)' not in labels, 'no switch hides among the labels')
check('toggleable(g.rarelyWorn, enabled = !busy, role = Role.Switch' in page and 'Switch(checked = g.rarelyWorn, onCheckedChange = null' in page,
      '«Ношу рідко» is a switch row')

# Every action of the earlier page.
for code, what in [('vm.update(g) { it.copy(favorite = !it.favorite) }', 'favourite'),
                   ('vm.update(g) { it.copy(rarelyWorn = !it.rarelyWorn) }', '«Ношу рідко»'),
                   ('onClick = onMatch, enabled = !busy && !g.archived && g.availability == "available"', 'build an outfit'),
                   ('OutlinedButton(onClick = { vm.wear(g) }', 'wear today and its undo'),
                   ('ActionRow(stringResource(R.string.wear_other_day), !busy) { wearOtherDay = true }', 'wear on another day'),
                   ('vm.changeAvailability(g, availability = if (g.availability == "laundry") "available" else "laundry")', 'laundry and back'),
                   ('vm.excludeFromRecommendations(g.id, !g.suggestionExcluded)', 'out of / back into suggestions'),
                   ('if (g.archived) vm.changeAvailability(g, archived = false) else confirmArchive = true', 'archive with a confirmation, restore'),
                   ('onClick = { moreMenu = false; deleting = true }', 'delete'),
                   ('PurchaseHistory(it, vm)', 'purchase history'),
                   ('onClick = { removingWear = day }', 'remove a wear day')]:
    check(code in page, f'the page keeps: {what}')

# Three numbers, read as one phrase each.
check('dates.size.toString() to stringResource(R.string.item_stat_wears)' in page, 'wear days')
check('ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today)).coerceAtLeast(0)' in page, 'days since the last wear, never negative')
check('s.parts.filter { it.garmentId == g.id }.map { it.outfitId }.distinct().size' in page, 'outfits with the item, each once')
check('clearAndSetSemantics { contentDescription = "$value $title" }' in page, 'a number is read with its label')

# Large text: numbers and the bottom buttons stack.
check('if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stats.forEach' in page, 'numbers stack at a large font')
check('matchButton(Modifier.fillMaxWidth()); wearButton(Modifier.fillMaxWidth())' in page, 'bottom buttons stack at a large font')
check('heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button' in page, 'list rows are 48 dp buttons')

res = root / 'app/src/main/res'
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    tree = {n.get('name'): (n.text or '') for n in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key, values in STRINGS.items():
        check(key in tree, f'{folder}: {key}')
        if key in tree: check(re.findall(r'%\d\$[ds]', tree[key]) == re.findall(r'%\d\$[ds]', values[0]), f'{folder}: {key} placeholders')
for key in STRINGS: check(f'R.string.{key}' in page, f'{key} is used')

if failures:
    print('\n'.join('FAIL: ' + f for f in failures)); sys.exit(1)
print(f'PASS item-page guards: photo header, three numbers, bottom bar, every action kept, {len(STRINGS)} strings × 4 locales')
