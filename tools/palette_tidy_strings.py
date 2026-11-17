"""Labels for 0.14.56 UI-06 (the palette list folds the earlier palettes away).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'appearance_current_older': ('Ваша обрана палітра', 'Your chosen palette', 'Twoja wybrana paleta'),
'appearance_show_older': ('Показати попередні палітри: %1$d', 'Show earlier palettes: %1$d', 'Pokaż wcześniejsze palety: %1$d'),
'appearance_hide_older': ('Сховати попередні палітри', 'Hide earlier palettes', 'Ukryj wcześniejsze palety'),
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
    print(f'{len(STRINGS)} palette-tidy strings × 4 locales')
