#!/usr/bin/env python3
"""UI-05: the new-item form by the approved design — larger photo, chips for season and style, two buttons at the bottom."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
ui = (source / 'ui/Ui.kt').read_text(encoding='utf-8')
controls = (source / 'ui/FormControls.kt').read_text(encoding='utf-8')
editor = ui.split('fun EditItem(', 1)[1].split('\nfun OutfitList(', 1)[0]
failures = []
def check(condition, message):
    if not condition: failures.append(message)

check('height(if (LocalConfiguration.current.screenHeightDp < 700) 200.dp else 260.dp)' in editor, 'the photo is larger, shorter on short screens')
check('ChipChoice(season, Matching.seasons, { season = it }, R.string.season, enabled = !busy)' in editor and
      'ChipChoice(style, Matching.styles, { style = it }, R.string.style, enabled = !busy)' in editor, 'season and style are chips')
check('Modifier.semantics { selectableGroup() }' in controls and 'heightIn(min = 48.dp)' in controls and 'stringResource(label(code))' in controls,
      'the chips are one group, 48 dp, with the same labels as the menu')
bottom = editor.split('// UI-05: «Зберегти й додати ще»', 1)[1]
check('val offerAddNext = original == null && purchase == null && imported == null' in bottom, '«додати наступну» only for a new item')
check('onClick = { saveItem(true) }' in bottom and 'onClick = { saveItem(imported != null) }' in bottom, 'both saves keep their meaning')
check(editor.count('saveItem(true)') == 1, '«додати наступну» is only at the bottom')
check('if (LocalConfiguration.current.fontScale >= 1.3f) Column(' in bottom, 'the buttons stack at a large font')
for kept in ('R.string.review_save_queue', 'R.string.review_defer', 'R.string.review_queue', 'R.string.additional_details',
             'R.string.purchase_confirm', 'R.string.review_save_next', 'PhotoProcessingPanel(', 'R.string.import_copy_previous'):
    check(kept in editor, f'the form keeps {kept}')

if failures:
    print('\n'.join('FAIL: ' + f for f in failures)); sys.exit(1)
print('PASS new-item design guards: larger photo, chips for season and style, two buttons at the bottom, review and purchase kept')
