#!/usr/bin/env python3
"""Check production Kotlin colour constants. This does not compile Kotlin or render Compose."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
text = (root / 'app/src/main/java/com/familywardrobe/app/Theme.kt').read_text()
appearance = (root / 'app/src/main/java/com/familywardrobe/app/Appearance.kt').read_text()
names = re.findall(r'^    (\w+)\("[a-z_]+", R.string.palette_', appearance, re.M)
full = re.findall(r'WardrobePalette\.(\w+) -> if \(dark\) PaletteTokens\(\n(.*?)\n    \) else PaletteTokens\(\n(.*?)\n    \)', text, re.S)
legacy = re.findall(r'WardrobePalette\.(\w+) -> if \(dark\) legacyPaletteTokens\((.*?)\n        else legacyPaletteTokens\((.*?)\n', text)
assert len(names) == 11 and len(full) == 6 and len(legacy) == 5
assert set(names) == {x[0] for x in full + legacy}

def luminance(value):
    rgb = [int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * w for v, w in zip(linear, (.2126, .7152, .0722)))

def contrast(a, b):
    x, y = luminance(a), luminance(b)
    return (max(x, y) + .05) / (min(x, y) + .05)

def shared(name, dark):
    found = re.search(r'\b' + name + r' = if \(dark\) Color\(0xFF([0-9A-F]{6})\) else Color\(0xFF([0-9A-F]{6})\)', text)
    assert found, name
    return found.group(1 if dark else 2)

def constants(raw):
    return dict(re.findall(r'(\w+) = Color\(0xFF([0-9A-F]{6})\)', raw))

palettes = {}
for name, dark_raw, light_raw in full:
    for dark, raw in [(True, dark_raw), (False, light_raw)]:
        tokens = constants(raw)
        assert len(tokens) == 14, (name, dark, 'incomplete role tokens')
        palettes[name, dark] = tokens
for name, dark_raw, light_raw in legacy:
    for dark, raw in [(True, dark_raw), (False, light_raw)]:
        primary, on_primary, selected = re.findall(r'Color\(0xFF([0-9A-F]{6})\)', raw)
        tokens = {role: shared(role, dark) for role in ('details', 'onDetails', 'canvas', 'paper', 'ink', 'muted', 'soft', 'outline', 'outlineVariant')}
        tokens.update(primary=primary, onPrimary=on_primary, selected=selected, secondary=primary, onSecondary=on_primary)
        palettes[name, dark] = tokens
checks = []
failures = []
for (name, dark), t in palettes.items():
    pairs = [('onPrimary', 'primary'), ('onSecondary', 'secondary'), ('onDetails', 'details'),
             ('primary', 'paper'), ('primary', 'canvas'), ('primary', 'selected'), ('secondary', 'paper'),
             ('ink', 'selected'), ('muted', 'selected')]
    pairs += [(fg, bg) for fg in ('ink', 'muted') for bg in ('paper', 'canvas', 'soft')]
    t['photo'] = shared('photo', dark)
    pairs += [('ink', 'photo'), ('muted', 'photo')]
    for foreground, background in pairs:
        ratio = contrast(t[foreground], t[background])
        checks.append(ratio)
        if ratio < 4.5: failures.append((name, dark, foreground, background, round(ratio, 3)))
    outline = contrast(t['outline'], t['paper'])
    if outline < 3: failures.append((name, dark, 'outline', 'paper', round(outline, 3)))
    for fg, bg in [(shared('error', dark), t['canvas']), (shared('onErrorContainer', dark), shared('errorContainer', dark))]:
        ratio = contrast(fg, bg); checks.append(ratio)
        if ratio < 4.5: failures.append((name, dark, 'error text', round(ratio, 3)))
    # onError uses Color.White in light mode, retained as a semantic error pairing.
    on_error = '45211F' if dark else 'FFFFFF'
    ratio = contrast(on_error, shared('error', dark)); checks.append(ratio)
    if ratio < 4.5: failures.append((name, dark, 'onError', round(ratio, 3)))
for dark in (False, True):
    values = [palettes[name, dark] for name, _, _ in full]
    assert len({v['canvas'] for v in values}) == 6
    assert all(v['primary'] != v['secondary'] and v['paper'] != v['canvas'] for v in values)
if failures:
    for failure in failures: print('FAIL:', failure)
    raise AssertionError('Unreadable palette colour roles')
print(f'PASS: {len(checks)} production text colour pairings in 11 palettes × 2 modes; minimum {min(checks):.2f}:1; 22 outline checks ≥3:1.')
print('NOT RUN: Kotlin/Compose compilation, Android rendering, TalkBack and on-device persistence.')
