"""Labels for 0.14.52 UI-02 (the «Сьогодні» screen by the approved design).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'today_other': ('Інший', 'Another', 'Inny'),
'today_variant_position': ('Варіант %1$d із %2$d', 'Option %1$d of %2$d', 'Wariant %1$d z %2$d'),
'today_note_rain': ('Верхній одяг на дощ', 'Outerwear for the rain', 'Okrycie na deszcz'),
'today_rotation_idle': ('Давно не вдягали · днів: %1$d', 'Not worn for a while · days: %1$d', 'Dawno nienoszone · dni: %1$d'),
'today_rotation_collapse': ('Згорнути', 'Collapse', 'Zwiń'),
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
    print(f'{len(STRINGS)} today-design strings × 4 locales')
