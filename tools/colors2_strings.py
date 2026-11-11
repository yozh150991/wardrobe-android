"""Labels for 0.14.32 COL-02: ten more base colours and the note for older family servers.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'cream': ('Молочний', 'Cream', 'Kremowy'),
'camel': ('Кемел', 'Camel', 'Camel'),
'khaki': ('Хакі', 'Khaki', 'Khaki'),
'light_blue': ('Блакитний', 'Light blue', 'Błękitny'),
'green': ('Зелений', 'Green', 'Zielony'),
'burgundy': ('Бордовий', 'Burgundy', 'Bordowy'),
'pink': ('Рожевий', 'Pink', 'Różowy'),
'orange': ('Помаранчевий', 'Orange', 'Pomarańczowy'),
'yellow': ('Жовтий', 'Yellow', 'Żółty'),
'purple': ('Фіолетовий', 'Purple', 'Fioletowy'),
'style_pair_analogous': ('сусідні на колі кольорів — гармонійно', 'neighbours on the colour wheel — harmonious', 'sąsiednie na kole barw — harmonijnie'),
'style_pair_complementary': ('доповняльні кольори — сміливий контраст; краще приглушити один або зменшити його частку',
    'complementary colours — a bold contrast; best with one toned down or used less',
    'kolory dopełniające — odważny kontrast; najlepiej jeden przygasić albo użyć go mniej'),
'family_color_older': ('Сервер спільного гардероба ще не знає кольору «%1$s» — там він буде «%2$s». У вашому гардеробі колір не зміниться.',
    'The shared wardrobe’s server doesn’t know “%1$s” yet — it will be “%2$s” there. Your own wardrobe keeps the colour.',
    'Serwer wspólnej szafy nie zna jeszcze koloru „%1$s” — będzie tam „%2$s”. W Twojej szafie kolor się nie zmieni.'),
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
