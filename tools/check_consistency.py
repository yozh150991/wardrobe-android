#!/usr/bin/env python3
"""Text and screen consistency guards for 0.14.61 (TXT-01…08, BUG-61-01/02, LOOK-07…).

The text rules are written down in tools/consistency_strings.py; this check keeps them true for every later string.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
sys.path.insert(0, str(ROOT / 'tools'))
from consistency_strings import INFORMAL_RE  # noqa: E402

failures = []


def check(ok, message):
    if not ok:
        failures.append(message)


def load(folder):
    return {e.get('name'): ''.join(e.itertext()).replace("\\'", "'").replace('\\"', '"')
            for e in ET.parse(RES / folder / 'strings.xml').getroot() if e.tag == 'string'}


uk, en, pl = load('values-uk'), load('values-en'), load('values-pl')
langs = {'uk': uk, 'en': en, 'pl': pl}

# TXT-01: the default locale is the Ukrainian one, byte for byte.
check((RES / 'values/strings.xml').read_text(encoding='utf-8') == (RES / 'values-uk/strings.xml').read_text(encoding='utf-8'),
      'values/strings.xml must equal values-uk/strings.xml (run tools/consistency_strings.py)')
check(set(uk) == set(en) == set(pl), 'every key exists in every language')
placeholder = re.compile(r'%(\d+\$)?[sdf]')
for k in uk:
    check(sorted(placeholder.findall(uk[k])) == sorted(placeholder.findall(en.get(k, ''))) == sorted(placeholder.findall(pl.get(k, ''))),
          f'{k}: the same placeholders in every language')


def where(d, pattern, keep=()):
    return [k for k, v in d.items() if k not in keep and re.search(pattern, v)]


# TXT-02: «ти», never «Ви».
check(not [k for k, v in uk.items() if INFORMAL_RE.search(v)], f'Ukrainian says «ти»: {[k for k, v in uk.items() if INFORMAL_RE.search(v)][:5]}')
# TXT-03: no gendered verbs aimed at the user, one verb for wearing.
gendered = where(uk, r'(?<![А-Яа-яЇїІіЄєҐґ’])(Вдягнув|вдягнув|Одягнув|одягнув|одягав|вдягав|Знайшов|знайшов|обирав|вибрав|обрав|забув|Купив|купив|[Нн]осив|додав|зберіг|бачив|хотів|зробив|створив|відкрив|натиснув|змінив|видалив|отримав)(?![А-Яа-яЇїІіЄєҐґ’])')
check(not gendered, f'no gendered verbs for the user: {gendered}')
check(not where(uk, r'(?<![А-Яа-яЇїІіЄєҐґ’])[Оо]дяг(ну|а)'), 'wearing is «вдягати», not «одягати»')
# TXT-04: one name per thing.
for lang, pattern, why, keep in [
        ('uk', r'(?<![А-Яа-яЇїІіЄєҐґ’])[Кк]омплект', '«образ», not «комплект»', ()),
        ('uk', r'список\w* (покупок|бажань)|бажан(ь|ої речі)', 'the tab «Хочу купити»', ()),
        ('uk', r'катало[гз]|колекці', 'the tab is «Гардероб»', ()),
        ('en', r'\b[Ll]ooks?\b(?! for|ing)', '“outfit”, not “look”', ()),
        ('en', r'\b[Pp]ieces?\b', '“item”, not “piece”', ('photo_region_hint', 'photo_region_empty')),
        ('en', r'[Ww]ish ?list|[Ss]hopping list', 'the tab “Want to buy”', ()),
        ('en', r'\b[Cc]atalog|\bcollection\b', 'the tab is “Wardrobe”', ()),
        ('pl', r'[Ss]tylizacj', '„zestaw”, not „stylizacja”', ()),
        ('pl', r'(?<![a-ząćęłńóśźż])[Ss]zaf', '„garderoba”, not „szafa”', ('location_hint',)),
        ('pl', r'list\w* (zakupów|życzeń)', 'the tab „Chcę kupić”', ()),
        ('pl', r'katalog|kolekcj', 'the tab is „Garderoba”', ()),
]:
    found = where(langs[lang], pattern, keep)
    check(not found, f'{lang}: {why}: {found}')
# TXT-05: a number never stands before a noun that has to agree with it. Constants (31 days, 16 days, 2 answers) are fine.
CONSTANT = {'trip_truncated', 'trip_city_forecast', 'prefs_warmth_default'}
nouns = {'uk': r'гардероб\w*|реч(ей|і)|образ\w*|змін\w*|днів|дні|дня|відгук\w*|товар\w*|фото',
         'en': r'wardrobes?|items?|outfits?|pieces?|changes?|days?|products?|photos?',
         'pl': r'garder\w*|rzecz\w*|zestaw\w*|zmian\w*|dni|produkt\w*|zdjęć'}
for lang, d in langs.items():
    bad = [k for k, v in d.items() if k not in CONSTANT and re.search(rf'%\d+\$d\s+({nouns[lang]})\b', v)]
    check(not bad, f'{lang}: put the noun before the number: {bad}')
# TXT-06: quoted names — «» in Ukrainian, “” in English, „” in Polish, the same number of them, and real labels.
check(not where(en, '[«»„]'), f'English quotes are “…”: {where(en, "[«»„]")}')
check(not where(pl, '[«»“]'), f'Polish quotes are „…”: {where(pl, "[«»“]")}')
check(not where(uk, '[“”„]'), f'Ukrainian quotes are «…»: {where(uk, "[“”„]")}')
uneven = [k for k in uk if not (uk[k].count('«') == en[k].count('“') == pl[k].count('„'))]
check(not uneven, f'a name quoted in one language is quoted in all: {uneven}')
STATES = {'спаковано', 'packed', 'spakowane'}  # a state word, not a button
for lang, d, pattern in (('uk', uk, r'«([^«»%]+)»'), ('en', en, r'“([^“”%]+)”'), ('pl', pl, r'„([^„”%]+)”')):
    labels = {v.strip().lower() for v in d.values()}
    unknown = [(k, m.group(1)) for k, v in d.items() for m in re.finditer(pattern, v)
               if m.group(1).strip().lower() not in labels and m.group(1) not in STATES]
    check(not unknown, f'{lang}: a quoted name is a real label: {unknown}')
# TXT-07: British English, typographic apostrophes.
for pattern, why in ((r'\b[Cc]olor', 'colour'), (r'\b[Ff]avorite', 'favourite'), (r'[Rr]ecogniz', 'recognise'),
                     (r'[Oo]rganiz', 'organise'), (r'[Aa]nalyz', 'analyse'), (r"'", 'the apostrophe ’')):
    check(not where(en, pattern), f'English: {why}: {where(en, pattern)}')
check(not where(uk, "'"), 'Ukrainian apostrophe is ’')
check(not where(pl, r'e-mail'), 'Polish: email without a hyphen')
# TXT-03/04: the same wear words on the item page, outfit, widget and calendar.
for d in (uk, en, pl):
    check(d['wear'] == d['wear_outfit'] == d['widget_wear'] == d['wear_look_today'], f'one label for wearing today: {d["wear"]}')
WORN = r'[Вв]дягнут(о|ий|а|е|і|ого|ому|им|ою|у|их|ими)(?![А-Яа-яЇїІіЄєҐґ’])'  # «вдягнути» (to put on) is fine
check(not where(uk, WORN), f'a recorded wear is «вдягнено»: {where(uk, WORN)}')

for d in (uk, en, pl):
    check(d['statistics_forgotten'] == d['rotation_title'], 'the 90-day list has one name in the statistics and on «Сьогодні»')
# LOOK-07…10: one look for the same control on every screen.
SRC = ROOT / 'app/src/main/java/com/familywardrobe/app'
sources = {f.relative_to(SRC).as_posix(): f.read_text(encoding='utf-8') for f in SRC.rglob('*.kt')}
old_chips = [name for name, text in sources.items() if 'FilterChip(' in text and name != 'ui/FormControls.kt']
check(not old_chips, f'LOOK-07: choice chips are PillChip/ChipChoice, not the outlined FilterChip: {old_chips}')
check(sources['ui/FormControls.kt'].count('FilterChip(') == 1, 'LOOK-07: PillChip is the only FilterChip; ChipChoice uses PillChip')
SEARCH_LABELS = ('search_colors', 'search_categories', 'picker_search', 'search_subcategories', 'family_read_search', 'catalog_search_hint')
old_search = [name for name, text in sources.items()
              for line in text.splitlines() if 'TextField(' in line and any(f'R.string.{k}' in line for k in SEARCH_LABELS)]
check(not old_search, f'LOOK-08: every search field is SearchBox: {old_search}')
check(all(sum(f'R.string.{k}' in t for t in sources.values() if 'SearchBox(' in t) for k in SEARCH_LABELS),
      'LOOK-08: each search label is used by a SearchBox')
white_pages = [name for name, text in sources.items() if 'usePlatformDefaultWidth = false' in text
               and 'Surface(Modifier.fillMaxSize()) {' in text]
check(not white_pages, f'LOOK-09: full-screen dialogs sit on the app background like the screens: {white_pages}')
check(not [n for n, t in sources.items() if 'buttonColors(containerColor = MaterialTheme.colorScheme.secondary' in t],
      'LOOK-10: main actions are primary buttons')
check('shape = RoundedCornerShape(50),' in sources['ui/ItemPage.kt'].split('InputChip(', 1)[1][:400], 'wear days are round like the labels')
# Found on the phone (scrcpy, 23.11): text under the clock once the item page scrolls; the statistics and the subcategory
# filter still in the old style.
check('if (scroll.value > 0 && statusBarPx > 0f) drawRect(pageColour' in sources['ui/ItemPage.kt'], 'BUG-61-03: a band under the status bar once the item page scrolls')
check('OutlinedCard(' not in sources['ui/StatisticsScreen.kt'] and 'Choice(group' not in sources['ui/StatisticsScreen.kt']
      and 'PillChip(group == code' in sources['ui/StatisticsScreen.kt'], 'LOOK-11: statistics views are pills, rows are plain cards')
sub = sources['ui/CategoryControls.kt'].split('fun SubcategoryChoice(', 1)[1][:900]
check('PillChip(value.isNotBlank()' in sub and 'OutlinedButton(' not in sub, 'LOOK-12: the subcategory filter is a pill')
cat = sources['ui/SelectionControls.kt'].split('fun CategoryChoice(', 1)[1].split('if (choosing) AlertDialog(', 1)[0]
check('PillChip(false, stringResource(title)' in cat, 'LOOK-13: the catalog\'s «Категорії ▾» is a pill like the chips beside it')

if failures:
    print('FAIL consistency guards:'); [print('  -', f) for f in failures]
    sys.exit(1)
print(f'PASS consistency guards: default locale = Ukrainian, «ти», gender-neutral, one name per thing, nouns before numbers, '
      f'quoted real labels, British English — {len(uk)} strings × 4 locales; one chip, one search field, dialogs on the background')
