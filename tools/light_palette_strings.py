"""Labels for 0.14.51 UI-01 (light palettes).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'appearance_group_light': ('Світлі', 'Light', 'Jasne'),
'appearance_group_rich': ('Насичені', 'Rich', 'Nasycone'),
'palette_light_olive': ('Світла олива', 'Light olive', 'Jasna oliwka'),
'palette_light_olive_hint': ('Майже білий фон, зелені кнопки й теракотовий акцент', 'Near-white canvas, green buttons and a terracotta accent',
    'Prawie białe tło, zielone przyciski i akcent terakoty'),
'palette_mono': ('Чорно-біла', 'Black and white', 'Czarno-biała'),
'palette_mono_hint': ('Нейтральний фон і чорні кнопки: кольори одягу без стороннього відтінку', 'Neutral canvas and black buttons: garment colours without a tint',
    'Neutralne tło i czarne przyciski: kolory ubrań bez obcego odcienia'),
'palette_sky': ('Небесна', 'Sky', 'Błękitna'),
'palette_sky_hint': ('Ледь блакитний фон, сині кнопки й кораловий акцент', 'A hint of blue in the canvas, blue buttons and a coral accent',
    'Lekko błękitne tło, niebieskie przyciski i koralowy akcent'),
'palette_lilac': ('Бузкова', 'Lilac', 'Liliowa'),
'palette_lilac_hint': ('Ледь бузковий фон, фіолетові кнопки й бірюзовий акцент', 'A hint of lilac in the canvas, violet buttons and a teal accent',
    'Lekko liliowe tło, fioletowe przyciski i turkusowy akcent'),
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
    print(f'{len(STRINGS)} light-palette strings × 4 locales')
