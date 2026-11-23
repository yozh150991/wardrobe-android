#!/usr/bin/env python3
"""Guards for 0.14.62-qa-fixes: the findings of docs/releases/QA-EXPLORATORY-0.14.61.md that this release fixes.

Each check names its finding (QA-61-xx). Runs the Java cases of the new rules with JDK 17.
"""
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/familywardrobe/app'
RES = ROOT / 'app/src/main/res'
failures = []


def check(ok, message):
    if not ok:
        failures.append(message)


def read(name):
    return (SRC / name).read_text(encoding='utf-8')


def strings(folder):
    return {e.get('name'): ''.join(e.itertext()) for e in ET.parse(RES / folder / 'strings.xml').getroot() if e.tag == 'string'}


ui, analyzer, store = read('ui/Ui.kt'), read('photo/PhotoAnalyzer.kt'), read('photo/PhotoStore.kt')
catalog, wishes, viewer = read('ui/CatalogScreen.kt'), read('ui/WishlistScreen.kt'), read('photo/PhotoViewer.kt')
locales = {folder: strings(folder) for folder in ('values', 'values-uk', 'values-en', 'values-pl')}
check(locales['values'] == locales['values-uk'], 'values is the Ukrainian locale (TXT-01)')

# QA-61-30: a failed cutout says where it failed (logcat tag WardrobePhoto), with the mask shape when the mask is refused.
check('private const val TAG = "WardrobePhoto"' in analyzer, 'QA-61-30: photo diagnostics have their own logcat tag')
for stage in ('"model"', '"segment"', '"refine"', '"save"'):
    check(f'stage = {stage}' in analyzer, f'QA-61-30: the {stage} stage is recorded')
check('Log.w(TAG, "Cutout failed at $stage", e)' in analyzer, 'QA-61-30: an unknown failure is logged with its stage')
check('Log.w(TAG, "Mask rejected: " + PhotoCutoutRules.describe(confidence, bitmap.width, bitmap.height)); throw e' in analyzer,
      'QA-61-30: a refused mask is described (sizes and range, no pixels) and still refused')
check('Log.w(TAG, "Labels failed", e)' in analyzer, 'QA-61-30: label failures are logged too')

# QA-61-01: the camera shot survives the process being stopped behind the camera.
check('var pendingCapture by rememberSaveable { mutableStateOf(initial("pendingCapture", "")) }' in ui, 'QA-61-01: the pending shot comes back from the draft')
check('.put("pendingCapture", pendingCapture).toString()' in ui, 'QA-61-01: the draft keeps the pending shot')
capture = ui.split('fun startCapture()', 1)[1][:700] if 'fun startCapture()' in ui else ''
check(capture.index('vm.keepDraft(') < capture.index('camera.launch(uri)') if 'camera.launch(uri)' in capture and 'vm.keepDraft(' in capture else False,
      'QA-61-01: the draft is written before the camera opens')
check(ui.count('camera.launch(') == 1 and ui.count('startCapture()') == 3, 'QA-61-01: both camera buttons go through startCapture')
check('if (vm.photos.captureReady(Uri.parse(shot))) vm.importPhoto(Uri.parse(shot), ::receivePhoto)' in ui,
      'QA-61-01: a written camera file is imported after a restore')
check('val shot = pendingCapture; pendingCapture = ""' in ui, 'QA-61-01: the callback and the restore import a shot once')
ready = store.split('fun captureReady(', 1)[1][:600] if 'fun captureReady(' in store else ''
check('file.length() > 0' in ready and 'sharedUri(file) == uri' in ready and 'ID.matches(' in ready,
      'QA-61-01: only a non-empty camera file this store handed out is taken')

# QA-61-02: on a card the photo opens the item; the magnifier opens the photo.
check('zoomButtonOnly = true' in catalog.split('private fun GarmentCard(', 1)[1][:1500], 'QA-61-02: garment card photo opens the item')
check('garmentColour = wish.color, zoomButtonOnly = true' in wishes, 'QA-61-02: wish card photo opens the wish')
check('if (canOpen && !zoomButtonOnly) Modifier.clickable' in viewer and 'Surface(onClick = { opened = true }' in viewer,
      'QA-61-02: with zoomButtonOnly only the magnifier opens the viewer')

# QA-61-03 / QA-61-21: no name error without a category; the category error under the category button, small.
check('val nameMissing = validation && category in Matching.categories && !TextLimits.hasVisibleText(effectiveName)' in ui,
      'QA-61-03: the optional name shows no error while the category is missing')
form = ui.split('title = R.string.category_required, enabled = !busy,', 1)[1][:500]
check('wish_category_required' in form and 'typography.bodySmall' in form, 'QA-61-03: the category error is under the category button')
check(ui.count('R.string.wish_category_required') == 1, 'QA-61-03: the category error is shown once')

# QA-61-04: the photo is marked required; the add menu says «Заповнити вручну».
check('Text(stringResource(R.string.item_photo_required)' in ui, 'QA-61-04: the photo has a required mark')
for folder, value in (('values-uk', 'Фото *'), ('values-en', 'Photo *'), ('values-pl', 'Zdjęcie *')):
    check(locales[folder].get('item_photo_required') == value, f'QA-61-04: {folder} item_photo_required')
check(locales['values-uk'].get('shop_manual') == 'Заповнити вручну', 'QA-61-04: «Заповнити вручну»')

# QA-61-05: family texts match the features.
for folder in ('values-uk', 'values-en', 'values-pl'):
    for key in ('family_hint', 'family_local_changes'):
        text = locales[folder][key].lower()
        check(not any(stale in text for stale in ('недоступн', 'ще немає', 'not available yet', 'nie jest jeszcze', 'nie są jeszcze')),
              f'QA-61-05: {folder} {key} no longer says invitations or sync are missing')

# QA-61-06 / QA-61-07: unique wardrobe names, the open one marked, a new one opened.
data, management, bar = read('Data.kt'), read('ui/WardrobeManagement.kt'), read('ui/WardrobeTopBar.kt')
check(data.count('WardrobeNameRules.taken(') == 2, 'QA-61-06: the repository refuses a taken name on create and rename')
check('taken = { WardrobeNameRules.taken(wardrobeNames, it) }' in ui and 'enabled = TextLimits.hasVisibleText(name) && !duplicate' in ui,
      'QA-61-06: the new-wardrobe dialog refuses a taken name')
check('WardrobeNameRules.taken(names.filterKeys { id -> id != renameId }.values, editedName)' in management, 'QA-61-06: rename refuses a taken name')
check('trailingIcon = if (active) { { Icon(Icons.Outlined.Check, null) } } else null' in bar and 'selected = active' in bar,
      'QA-61-06: the switcher marks the open wardrobe')
check('vm.addWardrobe(it) { id -> vm.select(id)' in ui, 'QA-61-07: a new wardrobe opens')
check('suspend fun addWardrobe(name: String): String' in data, 'QA-61-07: creating returns the id')

# QA-61-08: the card badge is a state.
check('PhotoBadge(R.string.worn_today_badge)' in catalog and 'PhotoBadge(R.string.wear)' not in catalog, 'QA-61-08: «Вдягнено сьогодні» badge')
check(locales['values-uk'].get('worn_today_badge') == 'Вдягнено сьогодні', 'QA-61-08: Ukrainian badge text')

# QA-61-09: copying the previous item keeps the form's category and skips empty fields.
check('arrayListOf("season", "style").apply { if (initialCategory !in Matching.categories) add(0, "category") }' in ui,
      'QA-61-09: «Категорія» is not ticked when the form was opened for a category')
check('"subcategory" -> previousItem.subcategory.isNotBlank(); "size" -> previousItem.size.isNotBlank()' in ui,
      'QA-61-09: empty fields of the previous item are not offered')
check('stringResource(R.string.filter_subcategory) + ": " + previousItem.subcategory' in ui, 'QA-61-09: the field label, not the placeholder')

# QA-61-10: «З магазину» from «Додати річ» starts on «Моя річ», with radio buttons.
shop, vmodel = read('ui/ShopImportScreen.kt'), read('ui/WardrobeViewModel.kt')
check('"shop" -> openShop(target = "garment")' in ui, 'QA-61-10: the add menu opens the shop import for an owned item')
check('.put("target", if (target == "garment") "garment" else "wish")' in vmodel, 'QA-61-10: the draft starts with that target')
check('RadioRow(stringResource(R.string.shop_owned), target == "garment"' in shop and 'SelectionRow(stringResource(R.string.shop_' not in shop,
      'QA-61-10: one of two answers is a radio button')

# QA-61-11: leaving a form.
editor = read('ui/OutfitEditor.kt')
check('Text(stringResource(R.string.discard_changes), color = MaterialTheme.colorScheme.error)' in ui, 'QA-61-11: the discard choice is red')
check('remove("stage"); remove("manual")' in editor and 'onDirty(content != baseline' in editor, 'QA-61-11: an empty new outfit asks nothing')
check(locales['values-uk'].get('discard_item_title') == 'Вийти без збереження?', 'QA-61-11: a clear title')
check(not any(w in locales['values-uk']['discard_item_hint'] for w in ('чернетк', 'запис')), 'QA-61-11: no jargon in the hint')

# QA-61-18: the second colour of a pair is lower-case, like the outfit title.
styles = read('ui/OutfitStyleViews.kt')
check('stringResource(label(b)).replaceFirstChar { it.lowercase(locale) }' in styles, 'QA-61-18: «Коричневий + сірий» in the reasons')
check(locales['values-uk'].get('style_mixed') == 'Стилі змішані: %1$s — %2$d із %3$d речей', 'QA-61-18: style_mixed wording')

# QA-61-19: gender-neutral.
for key in ('draft_restored', 'auth_reauth_hint', 'family_member_left'):
    check('зупинився' not in locales['values-uk'][key] and 'вийшов' not in locales['values-uk'][key], f'QA-61-19: {key} is gender-neutral')

# QA-61-25: the profile is a list of sections; backups and family sharing have their own screens; signing out is last.
settings = ui.split('\nfun Settings(', 1)[1].split('\nprivate fun SettingsRow(', 1)[0]
for title in ('R.string.appearance', 'R.string.language', 'R.string.prefs_open', 'R.string.settings_weather', 'R.string.settings_wardrobes',
              'R.string.settings_backups', 'R.string.settings_family'):
    check(f'SettingsRow(Icons.Outlined.' in settings and title + ',' in settings, f'QA-61-25: the profile has a {title} row')
for moved in ('BackupHealthPanel(', 'FamilyTransferControl(', 'FamilyAccessControl(', 'R.string.import_backup', 'onNewWardrobe'):
    check(moved not in settings, f'QA-61-25: {moved} is no longer in the profile list')
check(settings.index('R.string.settings_family') < settings.index('R.string.auth_sign_out') < settings.index('AccountDeletionControl('),
      'QA-61-25: «Вийти з акаунта» comes after the sections')
check('"backups" -> BackupSettings(vm)' in ui and '"family" -> FamilySettings(vm)' in ui, 'QA-61-25: the two new screens are routed')
check('"manage", "appearance", "backups", "family" -> "settings"' in ui, 'QA-61-25: the profile tab stays selected on them')
backups = ui.split('fun BackupSettings(', 1)[1][:1500]
check('BackupHealthPanel(' in backups and 'SingleWardrobeBackupControl(vm)' in backups and 'R.string.import_backup' in backups,
      'QA-61-25: backups, one-wardrobe copy and restore are on the backup screen')
family_screen = ui.split('fun FamilySettings(', 1)[1][:600]
check('FamilyAccessControl(vm)' in family_screen and 'FamilyTransferControl(vm)' in family_screen, 'QA-61-25: family sharing has its screen')
check('Button(onClick = onNew' in management, 'QA-61-25: «Новий гардероб» is on the wardrobes screen')
check('R.string.app_version, version' in settings and 'getPackageInfo(context.packageName, 0).versionName' in settings,
      'the profile shows the real app version (the old text said 0.11.0)')

# QA-61-25: one weather city per account, and «Сьогодні» offers it when none is chosen.
weather, daily = read('outfits/Weather.kt'), read('ui/DailyScreen.kt')
check('WeatherController(accountContext, viewModelScope)' in vmodel and 'getSharedPreferences("weather", 0)' in weather,
      'QA-61-25: the city is kept for the account, not per wardrobe')
check('if (weatherCity == null && !cityHintClosed) OutlinedCard(' in daily and 'onClick = vm.weather::closeHint' in daily
      and 'if (choosingCity) CityDialog(vm.weather)' in daily, 'QA-61-25: a card on «Сьогодні» offers a city, once')
check('prefs.edit().putBoolean("hint_closed", true)' in weather, 'QA-61-25: «Не зараз» is remembered')

# QA-61-27: the wish name is optional; the add-wish dialog has three equal answers and «Скасувати».
check('val effectiveName = ItemNameRules.effective(name, autoName)' in wishes and 'copy(name = effectiveName' in wishes
      and 'R.string.item_name_optional' in wishes and 'R.string.name_required' not in wishes, 'QA-61-27: the wish name is optional')
dialog = wishes.split('if (choosing) AlertDialog(', 1)[1][:1400]
check(dialog.count('OutlinedButton(') == 3 and 'R.string.cancel' in dialog, 'QA-61-27: three answers in one style and «Скасувати»')
check(locales['values-uk'].get('source_mixed') == 'Гардероб і «Хочу купити»', 'QA-61-27: the tab name is quoted')

# QA-61-28: leaving the selection is a close.
check('if (selecting) Icons.Outlined.Close else Icons.Outlined.Checklist' in catalog, 'QA-61-28: selection closes with ✕')

java = shutil.which('java')
if not java:
    failures.append('Java 17 with jdk.compiler required')
else:
    with tempfile.TemporaryDirectory(prefix='wardrobe-qa-fixes-') as output:
        files = [SRC / 'rules/WardrobeNameRules.java', ROOT / 'app/src/test/java/com/familywardrobe/app/rules/WardrobeNameRulesCases.java']
        built = subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)],
                               text=True, capture_output=True)
        check(built.returncode == 0, 'Java cases compile: ' + built.stderr[-400:])
        if built.returncode == 0:
            run = subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.WardrobeNameRulesCases'], text=True, capture_output=True)
            check(run.returncode == 0, 'WardrobeNameRulesCases: ' + (run.stdout + run.stderr)[-400:])
            print(run.stdout.strip())

if failures:
    raise SystemExit('FAIL qa-fixes guards:\n  - ' + '\n  - '.join(failures))
print('PASS qa-fixes guards (QA-61-01…11, 17–19, 25, 27, 28, 30): profile sections, city card, optional wish name, camera shot kept, card photo opens the item, form errors in place, '
      'unique wardrobe names, state badge, copy and shop defaults, leaving a form, photo diagnostics in logcat')
