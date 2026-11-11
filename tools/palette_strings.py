"""Labels for 0.14.34 PERS-03: «Мої кольори» in «Мої вподобання» and the palette lines on outfit cards.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'prefs_colours_title': ('Мої кольори', 'My colours', 'Moje kolory'),
'prefs_colours_hint': ('Без аналізу фото: позначте кольори, які любите носити, і ті, які краще рідше. Речі цих кольорів з’являються '
    'в підборі трохи частіше або рідше — жодна не зникає. Оцінка образу з 10 не змінюється: вона за правилами поєднання кольорів.',
    'No photo analysis: mark the colours you like wearing and the ones you’d rather wear less. Items in these colours come up a '
    'little more or less often — none disappears. The outfit score out of 10 doesn’t change: it follows the colour-pairing rules.',
    'Bez analizy zdjęć: zaznacz kolory, które lubisz nosić, i te, które wolisz nosić rzadziej. Rzeczy w tych kolorach pojawiają się '
    'w doborze trochę częściej lub rzadziej — żadna nie znika. Ocena stylizacji na 10 się nie zmienia: wynika z zasad łączenia kolorów.'),
'prefs_colours_liked': ('Люблю носити', 'I like wearing', 'Lubię nosić'),
'prefs_colours_avoided': ('Краще рідше', 'Rather less often', 'Raczej rzadziej'),
'style_colours_mine': ('Ваші кольори: %1$s', 'Your colours: %1$s', 'Twoje kolory: %1$s'),
'style_colours_less': ('Рідше за вашим вибором: %1$s', 'Less often by your choice: %1$s', 'Rzadziej z Twojego wyboru: %1$s'),
}
if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    def encode(value):
        return escape(value).replace("'", "\\'").replace('"', '\\"')
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = root / folder / 'strings.xml'; text = path.read_text()
        for key, values in STRINGS.items():
            if f'name="{key}"' in text: continue
            text = text.replace('</resources>', f'    <string name="{key}">{encode(values[column])}</string>\n</resources>', 1)
        path.write_text(text)
