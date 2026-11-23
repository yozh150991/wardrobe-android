#!/usr/bin/env python3
"""UI-03: «Гардероб» and the tab header by the approved design — every earlier catalog action still on the screen."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tools'))
from catalog_design_strings import STRINGS

source = root / 'app/src/main/java/com/familywardrobe/app'
catalog = (source / 'ui/CatalogScreen.kt').read_text(encoding='utf-8')
bar = (source / 'ui/WardrobeTopBar.kt').read_text(encoding='utf-8')
ui = (source / 'ui/Ui.kt').read_text(encoding='utf-8')
selection = (source / 'ui/SelectionControls.kt').read_text(encoding='utf-8')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# The tab header: a large title, the wardrobe as a pill with every earlier wardrobe action, stacked at a large font.
check('MaterialTheme.typography.headlineMedium' in bar and 'semantics { heading() }' in bar, 'a tab has a large title read as a heading')
check('Surface(onClick = { wardrobesOpen = true }, enabled = enabled, shape = RoundedCornerShape(50)' in bar, 'the wardrobe is a pill')
for action in ('select(w.id)', 'create()', 'manage()'):
    check(action in bar, f'the wardrobe menu still offers {action}')
check('if (largeText) Column(' in bar, 'at a large font the title and the pill stack')
check('IconButton(onClick = back, enabled = enabled)' in bar, 'a nested screen keeps its back button')
check('"daily" -> R.string.today_tab; "catalog" -> R.string.catalog;' in ui, 'the tabs are titled as in the navigation')
check('statistics = if (route == "catalog")' not in ui and 'imports = if (route == "catalog")' not in ui,
      'statistics and imports are not hidden under «⋮» of the header any more')

# Every catalog tool is one tap away on the screen.
check('ToolButton(Icons.Outlined.BarChart, stringResource(R.string.statistics_title), !busy, onClick = onStatistics)' in catalog,
      'statistics is a visible button')
check('selecting = !selecting; selectedIds = emptyList()' in catalog and 'R.string.bulk_select' in catalog and 'R.string.bulk_finish' in catalog,
      'selecting items is a named toggle')
check('onClick = { moreMenu = false; onBatch() }' in catalog, 'adding several items stays in «⋮»')
check('columns = if (columns == 2) 3 else 2' in catalog, 'two or three columns stay available')
check('R.string.bulk_all' in catalog and 'bulkMenu = true' in catalog, 'choosing all shown and the actions on the chosen items stay')
for code in ('"wardrobe" to R.string.collection_all', '"laundry" to R.string.laundry', '"archive" to R.string.archive_collection'):
    check(code in catalog, f'the collections are offered: {code}')
check('Icon(Icons.Outlined.Tune, filterText)' in catalog and 'R.string.catalog_filters_active, activeFilters' in catalog,
      'the filter button says how many filters are on')
check('sort != "newest"' in catalog.split('val activeFilters', 1)[1].split('.count', 1)[0], 'a changed sort counts as a filter')
check('Modifier.clearAndSetSemantics { }) } }' in catalog, 'the badge number is not read twice')
check('horizontalScroll(rememberScrollState())' in catalog and 'R.string.browse_categories' in catalog,
      'categories scroll in one row and every category stays reachable')
check('SubcategoryChoice(subcategory, subcategories, { subcategory = it })' in catalog, 'the subcategory stays')
# BUG-58-01: in a sideways-scrolling row the width is unbounded; a weighted label got the button's minimum width and
# wrapped one letter per line («Categories» stood as a column).
choice = selection[selection.index('fun CategoryChoice('):selection.index('if (choosing) AlertDialog(')]
compact = choice[choice.index('} else {'):]
check('weight(' not in compact, 'the compact «Категорії» label has no weight (it sits in a scrolling row)')
check('Text(stringResource(title), maxLines = 1, softWrap = false)' in compact, 'the compact «Категорії» label stays on one line')
row = catalog[catalog.index('horizontalScroll(rememberScrollState())'):catalog.index('SubcategoryChoice(subcategory')]
check('weight(' not in row, 'nothing in the category row is weighted')

# BUG-58-03: a tab label wrapped to two lines («Хочу купити») and pushed its icon above the others. Labels stay on one
# line and shrink together to fit.
nav = ui[ui.index('bottomBar = {'):ui.index(') { padding ->', ui.index('bottomBar = {'))]
label = ui[ui.index('private fun NavLabel('):] if 'private fun NavLabel(' in ui else ''
check('maxLines = 2' not in nav and 'label = { NavLabel(stringResource(name), navScale)' in nav,
      'tab labels are never two lines; they use the shared one-line label')
check('var navScale by remember(navLabels, navColumns) { mutableFloatStateOf(1f) }' in nav and 'navScale == measured' in nav,
      'all tab labels share one size, which shrinks one step at a time')
check('maxLines = 1, softWrap = false' in label and 'layout.hasVisualOverflow' in label and 'drawWithContent { if (!overflowing) drawContent() }' in label,
      'a tab label is measured on one line and shown only once it fits')
check('private const val NAV_LABEL_MIN = 0.7f' in ui, 'labels shrink no further than 70 %')

# The grid and «Додати річ».
check('contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)' in catalog, 'the last row is not hidden under the add button')
check('if (!selecting) Button(onClick = onAddMethods, enabled = !busy' in catalog and 'Modifier.align(Alignment.BottomEnd)' in catalog,
      '«Додати річ» floats at the bottom right')
check('R.string.add_first_item' in catalog, 'an empty wardrobe still says «Додати першу річ»')
card = catalog[catalog.index('private fun GarmentCard('):catalog.index('private fun PhotoBadge(')]
check('role = Role.Checkbox' in card and 'R.string.favorite' in card and 'R.string.wear_count' in card, 'a card keeps its selection role, favourite and wear count')
check('if (wornToday) PhotoBadge(R.string.wear)' in card, 'worn today is a badge on the photo')
check('aspectRatio(photoRatio)' in card, 'the photo is taller than wide as in the design')
# BUG-58-02: the card clips to its shape; with the photo's 22 dp bottom corners the last text line lost its first letter.
check('private val CardShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 12.dp, bottomEnd = 12.dp)' in catalog
      and 'shape = CardShape)' in card and 'clip(PhotoShape)' in card, 'the photo keeps its round corners, the card text is not cut by them')
check('padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)' in card, 'the card text ends above the bottom corners')

# Strings in four locales with the same placeholders.
res = root / 'app/src/main/res'
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    tree = {n.get('name'): (n.text or '') for n in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key, values in STRINGS.items():
        check(key in tree, f'{folder}: {key}')
        if key in tree: check(re.findall(r'%\d\$[ds]', tree[key]) == re.findall(r'%\d\$[ds]', values[0]), f'{folder}: {key} placeholders')
for key in STRINGS: check(f'R.string.{key}' in catalog + bar, f'{key} is used')

if failures:
    print('\n'.join('FAIL: ' + f for f in failures)); sys.exit(1)
print('Catalog design: tab header, visible tools, filters with a count, one-row categories, floating add — pass')
