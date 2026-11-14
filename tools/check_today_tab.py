#!/usr/bin/env python3
"""UX-04: «Сьогодні» is the first tab and the start screen; the tab never keeps an item chosen in the statistics."""
from pathlib import Path
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
ui = (root / 'app/src/main/java/com/familywardrobe/app/ui/Ui.kt').read_text(encoding='utf-8')
failures = []
def check(condition, message):
    if not condition: failures.append(message)
check('val roots = setOf("daily", "catalog", "shopping", "outfits", "settings")' in ui, '«Сьогодні» is a root')
check('"look" -> "look"; else -> "daily" ' in ui, 'the app opens on «Сьогодні»')
check('listOf("daily" to Icons.Outlined.Today, "catalog" to' in ui, '«Сьогодні» is the first tab')
check('"importedit", "daily",' not in ui and '"saved", "daily",' not in ui, 'the daily screen is not nested any more')
check('onClick = { if (screen == "daily") rotationAnchor = ""; navigate(screen) }' in ui, 'the tab is the plain daily screen')
check('val navColumns = if (LocalConfiguration.current.fontScale >= 1.4f) 3 else 5' in ui, 'five tabs fit; large text wraps to rows of three')
check('route = if (route == "daily") "daily" else "catalog"; entryKey = route;' in ui and 'go(if (route == "daily") "daily" else "catalog")' in ui, 'switching the wardrobe stays on «Сьогодні»')
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    check(bool(strings.get('today_tab', '').strip()), f'{folder}: today_tab missing')
if failures: raise SystemExit('FAIL today tab:\n' + '\n'.join(failures))
print('PASS today-tab guards: first tab and start screen, plain on tap, wardrobe switch keeps it, 1 string × 4 locales')
