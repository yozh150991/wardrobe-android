#!/usr/bin/env python3
"""0.14.47 UX review: delete a saved outfit (UX-07), season and style in the main form (UX-08), an empty wardrobe
starts in the catalog and «Сьогодні» is not duplicated as buttons (UX-09), the item page puts everyday actions first
(UX-10), the item name is optional (UX-11)."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')
ui = read('Ui.kt'); data = read('Data.kt'); catalog = read('CatalogScreen.kt')

# UX-07: removing an outfit keeps the wear history and the backup invariants (a wear's source must exist).
body = data.split('suspend fun removeOutfit', 1)[1].split('suspend fun toggleItemWear', 1)[0]
check('setOf("outfit:$id") + dao.allPlans().filter { it.outfitId == id }.map { "plan:${it.id}" }' in body, 'wear through the outfit and its plans is found')
check('dao.insertWears(kept.map { Wear(it.garmentId, it.date, "item:${it.garmentId}") }.distinct())' in body, 'that wear becomes the items\' own')
check(body.index('sources.forEach { dao.clearSource(it) }') < body.index('dao.deleteOutfit(id)') < body.index('capsules.forEach { reconcileCapsule(it) }'),
      'sources are cleared before the outfit goes; capsules are reconciled after')
check('db.withTransaction' in body and body.index('photos?.remove(oldPhotos - referencedPhotos())') > body.index('db.withTransaction'), 'one transaction; photos only after the commit')
check('@Query("DELETE FROM outfits WHERE id=:id") suspend fun deleteOutfit(id: String)' in data, 'the outfit row is deleted (parts, ideas, plans, capsule links cascade)')
saved = ui.split('fun SavedOutfit(', 1)[1].split('\nfun Settings(', 1)[0]
check('R.string.outfit_delete_title, outfit.name' in saved and 'vm.removeOutfit(outfit.id, removed)' in saved and 's.plans.count { it.outfitId == outfit.id }' in saved,
      'deleting asks first and says how many plans go')
check('removed = { go("__back") }' in ui, 'after deleting the screen goes back')

# UX-08 / UX-11: the editor.
editor = ui.split('fun EditItem(', 1)[1].split('\nfun OutfitList(', 1)[0]
advanced = editor.split('if (advanced) {', 1)[1].split('if (validation && photo.isBlank())', 1)[0]
check('Matching.seasons' not in advanced and 'Matching.styles' not in advanced, 'season and style are not under «Додаткові деталі»')
check(editor.index('Choice(season, Matching.seasons') < editor.index('R.string.additional_details') and
      editor.index('Choice(style, Matching.styles') < editor.index('R.string.additional_details') and 'R.string.item_season_style_hint' in editor,
      'season and style are in the main form, with what they do')
check('val effectiveName = ItemNameRules.effective(name, autoName)' in editor and editor.count('name = effectiveName') == 2, 'the saved name is the typed one or the suggestion')
check('name.trim()' not in editor.split('fun saveItem', 1)[1].split('if (editingPhoto', 1)[0], 'no other name is saved')
check('R.string.item_name_auto, autoName' in editor and 'R.string.item_name_optional' in editor, 'the field says what the item will be called')
check(editor.index('category !in Matching.categories -> R.string.photo_save_category') < editor.index('!TextLimits.hasVisibleText(effectiveName) -> R.string.wish_name_required'),
      'a missing category is reported before a missing name')

# UX-09: start and duplicates.
check('if (route == "daily" && returnStack.isEmpty() && snapshot.garments.none { it.wardrobeId == current.id }) { route = "catalog"; entryKey = "catalog" }' in ui,
      'a wardrobe without items opens on the catalog')
check('var startChecked by rememberSaveable { mutableStateOf(false) }' in ui, 'only once per start')
check('R.string.daily_title' not in catalog and 'onDaily' not in catalog and 'onDaily' not in ui, 'no «Що вдягнути сьогодні» button in the catalog')
check('R.string.daily_title' not in ui.split('fun OutfitList(', 1)[1].split('fun SavedOutfit(', 1)[0], 'nor in the outfits list')

# UX-10: the item page.
detail = ui.split('fun Detail(', 1)[1].split('\nfun EditItem(', 1)[0]
check('AssistChip(onClick = {}' not in ui, 'no chips that look like buttons and do nothing')
order = [detail.index(x) for x in ('R.string.match', 'R.string.undo_wear else R.string.wear', 'R.string.mark_laundry', 'Info(R.string.size', 'R.string.wear_history_title',
                                   'R.string.rules_exclude', 'R.string.restore_item else R.string.archive')]
check(order == sorted(order), 'everyday actions, then facts and history, then rare decisions')
check(detail.count('if (g.subcategory.isNotBlank())') == 1 and detail.index('g.subcategory') < detail.index('StatusLabel('), 'the subcategory sits under the name')

# UX-13 (0.14.48): one style for several items, with undo. UX-14: preferences and weather are in Settings too.
ops = read('ImportOperations.kt')
check('"style" -> g.copy(style = style)' in ops and '&& style in Matching.styles)' in ops, 'bulk style changes only the style, to a known one')
check('g.season != expected.season || g.style != expected.style' in ops and 'season = old.season, style = old.style))' in ops, 'undo restores the style and refuses a later edit')
check('applyBulk("style", styleValue = chosenStyle)' in catalog and 'R.string.bulk_style_hint' in catalog, 'the catalog offers it and says what a style does')
settings = ui.split('\nfun Settings(', 1)[1].split('internal val LocalLookPhotos', 1)[0]
check('PreferencesDialog(snapshot, wardrobe, vm, today)' in settings and 'WeatherPanel(vm.weather, today)' in settings, 'Settings open the preferences and the weather')
check('snapshot = snapshot, wardrobe = current.id, today = today)' in ui, 'for the wardrobe that is open')

from ux_review_strings import STRINGS as _FIRST
from bulk_style_strings import STRINGS as _SECOND
STRINGS = {**_FIRST, **_SECOND}
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    for key in STRINGS:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for key in STRINGS: check(f'R.string.{key}' in ui + catalog, f'{key} is used')
if failures: raise SystemExit('FAIL UX review:\n' + '\n'.join(failures))

java = shutil.which('java') or 'java'
with tempfile.TemporaryDirectory(prefix='wardrobe-ux-') as output:
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, str(source / 'ItemNameRules.java'),
                    str(source / 'TextLimits.java'), str(root / 'app/src/test/java/com/familywardrobe/app/ItemNameRulesCases.java')], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.ItemNameRulesCases'], check=True)
print(f'PASS UX-review guards: outfit removal, editor, start screen, item page, {len(STRINGS)} strings × 4 locales')
