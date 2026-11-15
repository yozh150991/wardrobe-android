#!/usr/bin/env python3
"""ROT-01/02: run the production rotation rules with their cases (JDK 17) and guard how the app uses them."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# One definition of «давно не вдягали» for the statistics and the daily suggestion.
stats = (source / 'ui/StatisticsScreen.kt').read_text()
check('RotationRules.forgotten(' in stats, 'WardrobeStats must use RotationRules.forgotten')
check(not re.search(r'>=\s*90\b|\b90\s*<=', stats), 'no separate 90-day literal in the statistics')
check('pickToday(g)' in stats and 'WardrobeRules.ready(g.rulePiece())) TextButton(onClick = { pickToday(g) }' in stats,
      'statistics offer «Підібрати на сьогодні» only for ready items')
outfits = (source / 'outfits/RotationOutfits.kt').read_text()
check('RotationRules.candidates(' in outfits and 'RotationRules.order(' in outfits and 'RotationRules.ATTEMPTS' in outfits,
      'suggest uses the shared candidates, order and attempt limit')
check('it.date <= today' in outfits, 'only actual wears on or before today count')
check('it.reason == PreferenceRules.DISLIKE' in outfits and "!it.signature.contains('|')" in outfits,
      'uncomfortable items come from PERS-01 item rows')
check('if (fixed[g.category]?.let { it != g.id } == true) continue' in outfits, 'a pinned item of the same category wins')
check('fixed.filterKeys { it != g.category }' in outfits, 'an item chosen in the statistics replaces a pin of its category')
daily = (source / 'ui/DailyScreen.kt').read_text()
check('val rotationInput = if (capsule != null || !rotationVisible) ""' in daily, 'no rotation card in capsules or when it is off/hidden')
check('withContext(Dispatchers.Default) {\n            RotationOutfits.suggest(' in daily, 'the suggestion runs off the UI thread')
check('!(rotation && item.id == rotationAnchorId)' in daily, 'the anchor item is not replaced (that is «Інша річ»)')
check('if (anchor.isNotEmpty()) clearAnchor()' in daily, '«Інша річ» leaves the item chosen in the statistics')
# ROT-02: postponing, reasons, frequency; account-private settings that never bump on a mere «shown».
store = (source / 'data/RotationStore.kt').read_text()
check('getSharedPreferences("preferences", Context.MODE_PRIVATE)' in store, 'settings live in the account preferences (erased with the account)')
mark = store.split('fun markShown', 1)[1].split('fun hideToday', 1)[0]
check('mutable.value++' not in mark, 'recording that the card was shown must not recompute the card')
check('RotationRules.snoozed(until, today)' in store, 'only postponements still in force are read')
check('state.snoozes[g.id]?.until.orEmpty(), state.seen[g.id].orEmpty()' in outfits, 'suggestions see postponements and history')
check('RotationRules.visible(rotationState.frequency, rotationState.lastShown, rotationState.hiddenOn, today)' in daily,
      'the card follows the chosen frequency and «Сховати на сьогодні»')
check('anchor.isNotEmpty() || RotationRules.visible(' in daily, 'an item chosen in the statistics shows whatever the frequency')
check('vm.rotation.markShown(wardrobe, shown.id, today)' in daily and 'if (anchor.isEmpty() && rotationLook.isNotEmpty())' in daily,
      'only the regular card records what it showed')
check('record(rotationLook.takeIf' in daily and 'FeedbackReason.UNCOMFORTABLE)' in daily, '«Незручно» is the PERS-01 reason with its undo')
check('vm.changeAvailability(item, archived = true)' in daily and 'rotation_snooze_instead' in daily, 'archive only after confirmation, or postpone instead')
check('postpone(item, RotationRules.reasonDays(RotationRules.NO_MATCH), RotationRules.NO_MATCH); buildWith(item)' in daily,
      '«Немає з чим поєднати» postpones a month and opens manual building')
vm = (source / 'ui/WardrobeViewModel.kt').read_text()
check('rotation.clear(wardrobe)' in vm.split('fun resetPreferences', 1)[1].split('fun saveCanvasLook', 1)[0], '«Скинути всі вподобання» resets the card too')
prefs = (source / 'ui/PreferencesDialog.kt').read_text()
check('vm.rotation.setFrequency(wardrobe, value)' in prefs and 'vm.rotation.restore(wardrobe, snooze.garmentId)' in prefs,
      '«Мої вподобання» change the frequency and bring postponed items back')
ui = (source / 'ui/Ui.kt').read_text()
check('.put("rotationAnchor", rotationAnchor)' in ui and 'rotationAnchor = f.optString("rotationAnchor")' in ui,
      'the chosen item is part of the navigation frame')
check('go("daily")' not in ui.replace('fun openDaily(anchor: String = "") { go("daily")', ''), 'every other way to Daily clears the chosen item')

# Languages: the same keys and placeholders in every locale.
keys = ['rotation_title', 'rotation_anchor_title', 'rotation_reason_worn', 'rotation_reason_never', 'rotation_fits', 'rotation_saved_name',
        'rotation_other', 'rotation_pick_today', 'rotation_problem_unavailable', 'rotation_problem_excluded', 'rotation_problem_weather',
        'rotation_problem_repeat', 'rotation_problem_items',
        'rotation_snooze', 'rotation_snooze_title', 'rotation_snooze_hint', 'rotation_snooze_week', 'rotation_snooze_month', 'rotation_snooze_season',
        'rotation_why', 'rotation_reason_size', 'rotation_reason_size_scope', 'rotation_reason_waiting', 'rotation_reason_waiting_scope',
        'rotation_reason_no_match', 'rotation_reason_no_match_scope', 'rotation_reason_later', 'rotation_archive_title', 'rotation_archive_text',
        'rotation_snooze_instead', 'rotation_snoozed', 'rotation_hide_today', 'rotation_hidden', 'rotation_show', 'rotation_frequency_link',
        'rotation_daily', 'rotation_weekly', 'rotation_off', 'prefs_rotation_hint', 'prefs_rotation_postponed', 'prefs_rotation_until']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.((?:prefs_)?rotation_\w+)', daily + stats + prefs)):
    check(name in keys, f'R.string.{name} is not in the checked key list')
if failures:
    raise SystemExit('FAIL rotation:\n' + '\n'.join(failures))

with tempfile.TemporaryDirectory(prefix='wardrobe-rotation-') as output:
    files = [source / name for name in ('rules/WardrobeRules.java', 'rules/DailyRules.java', 'rules/PreferenceRules.java', 'rules/PreferenceProfile.java', 'rules/StyleRules.java', 'rules/WeatherRules.java', 'rules/RotationRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/RotationRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.RotationRulesCases'], check=True)
print(f'PASS rotation guards: {len(keys)} strings × 4 locales, one 90-day rule, capsule/pin/anchor, postponing and frequency')
