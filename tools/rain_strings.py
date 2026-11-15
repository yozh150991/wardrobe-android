"""Labels for 0.14.45 WTH-02 (rain from the forecast).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'weather_rain_auto': ('Ймовірність дощу %1$d%% — тому «Дощ»: до образу додано куртку чи плащ. Змінити можна в умовах.',
    'Chance of rain %1$d%%, so «Rain»: a jacket or raincoat is added. You can change it in the conditions.',
    'Szansa na deszcz %1$d%%, więc «Deszcz»: dodano kurtkę lub płaszcz. Możesz to zmienić w warunkach.'),
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
    print(f'{len(STRINGS)} rain strings × 4 locales')
