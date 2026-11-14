"""Labels for 0.14.48 (UX-13 style for several items, UX-14 preferences and weather in Settings).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'bulk_style': ('Змінити стиль', 'Change style', 'Zmień styl'),
'bulk_style_hint': ('Стиль визначає, для яких нагод річ пропонується: діловий — для роботи й подій, спортивний — для активного дня.',
    'The style decides which occasions an item is suggested for: formal for work and events, sport for an active day.',
    'Styl decyduje, na jakie okazje rzecz jest proponowana: formalny do pracy i na wydarzenia, sportowy na aktywny dzień.'),
'settings_suggestions': ('Підбір образів', 'Outfit suggestions', 'Dobór zestawów'),
'settings_suggestions_hint': ('Для гардероба, який зараз відкрито.', 'For the wardrobe that is open now.', 'Dla garderoby, która jest teraz otwarta.'),
'settings_weather': ('Погода й місто', 'Weather and city', 'Pogoda i miasto'),
}

if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    import re
    res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = res / folder / 'strings.xml'; text = path.read_text(encoding='utf-8')
        known = set(re.findall(r'<string name="(\w+)"', text))
        lines = [f'    <string name="{k}">' + escape(v[column]).replace("'", "\\'").replace('"', '\\"') + '</string>'
                 for k, v in STRINGS.items() if k not in known]
        if lines: path.write_text(text.replace('</resources>', '\n'.join(lines) + '\n</resources>'), encoding='utf-8')
    print(f'{len(STRINGS)} bulk-style strings × 4 locales')
