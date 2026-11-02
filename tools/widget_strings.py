"""Labels for 0.14.28 WID-01: the «Образ на сьогодні» home-screen widget.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'widget_title': ('Образ на сьогодні', 'Today’s outfit', 'Zestaw na dziś'),
'widget_description': ('Запланований на сьогодні образ активного гардероба з кнопкою «Одягнув сьогодні».',
    'Today’s planned outfit from the active wardrobe, with a “Wore it today” button.',
    'Zaplanowany na dziś zestaw z aktywnej szafy z przyciskiem „Założone dziś”.'),
'widget_signed_out': ('Увійдіть у застосунок, щоб бачити образ на сьогодні.', 'Sign in to the app to see today’s outfit.',
    'Zaloguj się w aplikacji, aby zobaczyć zestaw na dziś.'),
'widget_open_app': ('Відкрити застосунок', 'Open the app', 'Otwórz aplikację'),
'widget_no_plan': ('На сьогодні образ не заплановано.', 'No outfit is planned for today.', 'Na dziś nie zaplanowano zestawu.'),
'widget_pick': ('Підібрати образ', 'Pick an outfit', 'Dobierz zestaw'),
'widget_open': ('Відкрити образ', 'Open outfit', 'Otwórz zestaw'),
'widget_wear': ('Одягнув сьогодні', 'Wore it today', 'Założone dziś'),
'widget_picture': ('Речі образу «%1$s»', 'Items of the outfit “%1$s”', 'Rzeczy zestawu „%1$s”'),
'widget_worn': ('Носіння підтверджено', 'Wear confirmed', 'Użycie potwierdzone'),
'widget_unavailable': ('Зараз недоступно: %1$s', 'Unavailable now: %1$s', 'Teraz niedostępne: %1$s'),
'widget_ideas': ('Є речі, яких ще немає в гардеробі', 'Has pieces you don’t own yet', 'Zawiera rzeczy, których jeszcze nie masz'),
'widget_no_photos': ('Без фото', 'No photos', 'Bez zdjęć'),
'widget_more': ('Ще планів на сьогодні: %1$d', 'More plans today: %1$d', 'Więcej planów na dziś: %1$d'),
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
