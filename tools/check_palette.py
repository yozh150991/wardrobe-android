#!/usr/bin/env python3
"""PERS-03: guard how «Мої кольори» reaches the daily suggestions, «Краще», the cards and «Мої вподобання»."""
from pathlib import Path
import re, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

# Account-private: the palette lives with the other PERS-02 choices and never in a backup or a family copy.
store = read('PersonalPreferences.kt')
check('getSharedPreferences("preferences", Context.MODE_PRIVATE)' in store, 'choices live in the account preferences (erased with the account)')
check('"pref.palette.$wardrobe.$kind"' in store, 'the palette is stored per wardrobe')
clear = store.split('fun clear', 1)[1]
check('remove(paletteKey(wardrobe, PersonalChoices.LIKED)); remove(paletteKey(wardrobe, PersonalChoices.AVOIDED))' in clear,
      '«Скинути всі вподобання» clears the palette too')
check('val PALETTE: List<String> = Matching.colors.filter { it != "other" }' in store, 'every base colour except «Інший» can be chosen')
check('current.liked - colour' in store and 'current.avoided - colour' in store, 'a colour is in one list at most')
for name in ('Backup.kt', 'FamilyEnvelope.kt', 'FamilyArchive.kt'):
    path = source / name
    if path.exists(): check('pref.palette' not in path.read_text(encoding='utf-8') and 'personalChoices' not in path.read_text(encoding='utf-8'),
                            f'{name} must not carry the palette')
vm = read('WardrobeViewModel.kt')
check('personalChoices.clear(wardrobe)' in vm.split('fun resetPreferences', 1)[1].split('fun saveCanvasLook', 1)[0], 'reset clears the choices')

# Effects: scores only, through the same generator entry points; «Краще» never offers a colour worn less.
daily = read('DailyOutfits.kt')
check('pieces(s, wardrobe), choices.avoided)' in daily, 'colours worn less are penalties (capped with the others)')
check('skip, fixed, 3, scores, target, liked, temperature)' in daily, 'the daily variants get the liked-colour bonus')
check('style(s, wardrobe, occasion, choices).style, bonus(s, wardrobe, choices), temperature)' in daily, 'replacing one piece gets the bonus too')
rules = read('DailyRules.java')
check('+ Math.min(PreferenceProfile.PALETTE_LIKED, Math.max(0, bonus.getOrDefault(item.piece.id, 0)))' in rules, 'the bonus is capped per item')
style = read('OutfitStyle.kt')
check('it.color !in choices.avoided' in style, '«Краще» skips colours worn less')
check('StyleRules.rate(look.map { it.rulePiece() }, weather, target, temperature)' in style, 'the 0–10 rating stays the colour theory’s')

# Screens: the card says why a variant moved; «Мої вподобання» set both lists.
screen = read('DailyScreen.kt')
check(screen.count('StyleReasons(') == 2 and screen.count(', target, choices)') == 2, 'both outfit cards show the palette lines')
views = read('OutfitStyleViews.kt')
check('R.string.style_colours_mine' in views and 'R.string.style_colours_less' in views, 'the palette lines are on the card')
prefs = read('PreferencesDialog.kt')
check(prefs.count('PaletteChoice(stringResource(') == 2 and 'PersonalChoices.PALETTE.forEach' in prefs, 'two palette lists in «Мої вподобання»')
check('vm.setPaletteColour(wardrobe, colour, value)' in prefs and 'if (selected) PersonalChoices.NONE else kind' in prefs,
      'a chip toggles its colour in that list')

# Languages: the same keys and placeholders in every locale.
keys = ['prefs_colours_title', 'prefs_colours_hint', 'prefs_colours_liked', 'prefs_colours_avoided', 'style_colours_mine', 'style_colours_less']
res = root / 'app/src/main/res'
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.((?:prefs_colours|style_colours)_\w+)', prefs + views)):
    check(name in keys, f'R.string.{name} is not in the checked key list')
if failures:
    raise SystemExit('FAIL palette:\n' + '\n'.join(failures))
print(f'PASS palette guards: {len(keys)} strings × 4 locales, account-private, scores only, «Краще» and cards')
