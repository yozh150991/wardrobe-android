#!/usr/bin/env python3
"""UI-06: a short palette list (earlier palettes fold away, a chosen one stays) and the widget in «Світла олива»."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tools'))
from palette_tidy_strings import STRINGS

source = root / 'app/src/main/java/com/familywardrobe/app'
screen = (source / 'ui/AppearanceScreen.kt').read_text(encoding='utf-8')
theme = (source / 'ui/Theme.kt').read_text(encoding='utf-8')
res = root / 'app/src/main/res'
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# The list: four light palettes, the earlier ones behind a toggle, a chosen earlier palette never hidden.
check('WardrobePalette.LIGHT.forEach { option(it) }' in screen, 'the light palettes are always listed')
check('val keptOlder = settings.palette.takeIf { it in older }' in screen and 'if (keptOlder != null && !showOlder)' in screen,
      'an earlier palette in use stays visible')
check('if (showOlder) older.forEach' in screen and 'R.string.appearance_show_older, older.size' in screen, 'the earlier palettes fold away with their number')
check('WardrobePalette.entries.forEach' not in screen, 'the screen no longer lists all fifteen at once')
check('selectable(selected, role = Role.RadioButton' in screen and 'Modifier.selectableGroup()' in screen, 'the options stay one radio group')

# The widget: the «Світла олива» tokens, readable in both modes.
block = theme[theme.index('WardrobePalette.LIGHT_OLIVE -> if (dark) PaletteTokens('):]
dark_block, light_block = block.split(') else PaletteTokens(', 1)
def tokens(text):
    return {k: v.upper() for k, v in re.findall(r'(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', text.split('\n    )', 1)[0])}
def colors(folder):
    return {n.get('name'): n.text.strip().upper()[3:] for n in E.parse(res / folder / 'colors.xml').getroot().iter('color') if n.get('name', '').startswith('widget_')}
def lum(h):
    c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True); return (la + 0.05) / (lb + 0.05)
for folder, t in (('values', tokens(light_block)), ('values-night', tokens(dark_block))):
    w = colors(folder)
    expect = {'widget_surface': t.get('paper'), 'widget_text': t.get('ink'), 'widget_secondary': t.get('muted'),
              'widget_accent': t.get('primary'), 'widget_on_accent': t.get('onPrimary')}
    for name, value in expect.items():
        check(value is not None and w.get(name) == value, f'{folder}: {name} is the «Світла олива» token ({w.get(name)} vs {value})')
    for fg, bg in (('widget_text', 'widget_surface'), ('widget_secondary', 'widget_surface'), ('widget_accent', 'widget_surface'),
                   ('widget_on_accent', 'widget_accent')):
        if fg in w and bg in w: check(ratio(w[fg], w[bg]) >= 4.5, f'{folder}: {fg} on {bg} is {ratio(w[fg], w[bg]):.2f}:1')

for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    tree = {n.get('name'): (n.text or '') for n in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key, values in STRINGS.items():
        check(key in tree, f'{folder}: {key}')
        if key in tree: check(re.findall(r'%\d\$[ds]', tree[key]) == re.findall(r'%\d\$[ds]', values[0]), f'{folder}: {key} placeholders')
for key in STRINGS: check(f'R.string.{key}' in screen, f'{key} is used')

if failures:
    print('\n'.join('FAIL: ' + f for f in failures)); sys.exit(1)
print(f'PASS palette-tidy guards: 4 light palettes listed, 11 earlier fold away, widget in «Світла олива» (light and dark), {len(STRINGS)} strings × 4 locales')
