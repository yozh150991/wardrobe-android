#!/usr/bin/env python3
"""LOOK-01–06 guard: six findings of the first screenshot review (SCREENSHOTS-0.14.59.md) stay fixed."""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
ui_dir = root / 'app/src/main/java/com/familywardrobe/app/ui'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


ui = (ui_dir / 'Ui.kt').read_text(encoding='utf-8')
wishes = (ui_dir / 'WishlistScreen.kt').read_text(encoding='utf-8')
catalog = (ui_dir / 'CatalogScreen.kt').read_text(encoding='utf-8')
page = (ui_dir / 'ItemPage.kt').read_text(encoding='utf-8')
controls = (ui_dir / 'FormControls.kt').read_text(encoding='utf-8')

# LOOK-01: the reason an item cannot be saved shows after a save attempt, never on an untouched form.
check('if (validation && saveBlocker != null) Text(stringResource(saveBlocker)' in ui
      and 'if (saveBlocker != null) Text(' not in ui, 'the save hint waits for «Зберегти»')
check("validation = !TextLimits.hasVisibleText(effectiveName) || finalPhoto.isBlank()" in ui, 'a blocked save still turns the hint on')

# LOOK-02: «Хочу купити» uses the new design's search field and chips; cards of a row are equally tall.
check('SearchBox(query, { query = TextLimits.input(it, 120) }' in wishes and 'OutlinedTextField(query' not in wishes,
      'the wishlist search is the shared SearchBox and still limits the input')
check('PillChip(kind == code, stringResource(title), { kind = code })' in wishes and 'FilterChip(' not in wishes,
      'the wishlist filters are the shared PillChip')
rows = wishes[wishes.index('items(found.chunked(columns)'):]
check('Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)' in rows and 'Modifier.weight(1f).fillMaxHeight()' in rows
      and 'repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }' in rows, 'a row of wish cards shares one height')
need = rows[rows.index('if (wish.kind == "need") {'):]
check(need.index('Spacer(Modifier.weight(1f))') < need.index('R.string.found_candidate'), '«Знайшов товар» sits at the bottom of the card')
for shared in ('internal fun PillChip(', 'internal fun SearchBox(', 'selectedContainerColor = MaterialTheme.colorScheme.onSurface'):
    check(shared in controls, f'FormControls keeps {shared}')
check('PillChip(selected, title, click)' in catalog and 'SearchBox(query, { query = it }' in catalog,
      '«Гардероб» uses the same chip and search field')

# LOOK-03: «Додати образ» in the primary colour.
outfits = ui[ui.index('fun OutfitList('):]
outfits = outfits[:outfits.index('R.string.add_outfit')]
check('colorScheme.secondary' not in outfits[-400:], '«Додати образ» is not terracotta')

# LOOK-04: round buttons on the item photo stay visible on a white background.
button = page[page.index('private fun RoundButton('):page.index('private fun ItemLabel(')]
check('.shadow(2.dp, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)' in button,
      'round photo buttons have an outline and a shadow')

# LOOK-05: nothing floats over the cards while the header is on screen.
check('val headerShown by remember { derivedStateOf { grid.firstVisibleItemIndex == 0 } }' in catalog
      and 'LazyVerticalGrid(state = grid,' in catalog, 'the catalog knows whether its header is on screen')
check('AnimatedVisibility(!selecting && !newWardrobe && !headerShown' in catalog and 'testTag("catalog-add-floating")' in catalog
      and "Icon(Icons.Outlined.Add, stringResource(R.string.add_item))" in catalog, 'the round «+» floats only after the header scrolled away')
check('modifier = Modifier.testTag("catalog-add"), onClick = onAddMethods' in catalog, 'the header «Додати річ» is what the screenshot tour taps')

# LOOK-06: the new-item buttons stay side by side up to a very large font.
check('private const val FORM_STACK_SCALE = 1.6f' in ui and 'fontScale >= FORM_STACK_SCALE) Column(' in ui,
      'the bottom buttons stack only from font 1.6')

if failures:
    for message in failures:
        print(f'FAIL: {message}')
    sys.exit(1)
print('Look fixes LOOK-01–06: save hint, «Хочу купити», «Додати образ», photo buttons, «Додати річ», bottom buttons — pass')
