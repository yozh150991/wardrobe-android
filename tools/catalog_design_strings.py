"""Labels for 0.14.53 UI-03 (the «Гардероб» tab and the shared tab header by the approved design).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'collection_all': ('Усі речі', 'All items', 'Wszystkie rzeczy'),
'catalog_in_wash': ('у пранні: %1$d', 'in the wash: %1$d', 'w praniu: %1$d'),
'catalog_more': ('Ще дії з гардеробом', 'More wardrobe actions', 'Więcej działań z garderobą'),
'catalog_search_clear': ('Очистити пошук', 'Clear search', 'Wyczyść wyszukiwanie'),
'catalog_filters_active': ('Фільтри й сортування, увімкнено: %1$d', 'Filters and sorting, on: %1$d', 'Filtry i sortowanie, włączone: %1$d'),
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
    print(f'{len(STRINGS)} catalog-design strings × 4 locales')
