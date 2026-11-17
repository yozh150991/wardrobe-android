"""Labels for 0.14.54 UI-04 (the item page by the approved design).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'item_more': ('Ще: не пропонувати, архів, видалити', 'More: do not suggest, archive, delete', 'Więcej: nie proponuj, archiwum, usuń'),
'item_size_short': ('розмір %1$s', 'size %1$s', 'rozmiar %1$s'),
'item_stat_wears': ('днів носіння', 'days worn', 'dni noszenia'),
'item_stat_days': ('днів від носіння', 'days since worn', 'dni od noszenia'),
'item_stat_outfits': ('в образах', 'in outfits', 'w stylizacjach'),
'item_about': ('Про річ', 'About the item', 'O rzeczy'),
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
    print(f'{len(STRINGS)} item-page strings × 4 locales')
