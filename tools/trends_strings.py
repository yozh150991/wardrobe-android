"""Labels for 0.14.57 TREND-01 («Сезонні тренди»: trend pairs and colours of the season).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'prefs_trends_title': ('Сезонні тренди', 'Seasonal trends', 'Trendy sezonu'),
'prefs_trends_hint': ('Трохи частіше пропонує пари кольорів із дописів, які ви обрали, і кольори сезону: %1$s. Оцінка образу не змінюється.',
                      'Suggests colour pairs from the posts you picked a little more often, and the colours of the season: %1$s. The outfit score does not change.',
                      'Nieco częściej proponuje pary kolorów z wybranych przez Ciebie postów i kolory sezonu: %1$s. Ocena stylizacji się nie zmienia.'),
'style_trend_pair': ('Тренд сезону: %1$s + %2$s', 'Trend of the season: %1$s + %2$s', 'Trend sezonu: %1$s + %2$s'),
'style_trend_colours': ('Колір сезону: %1$s', 'Colour of the season: %1$s', 'Kolor sezonu: %1$s'),
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
    print(f'{len(STRINGS)} trend strings × 4 locales')
