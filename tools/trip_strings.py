"""Labels for 0.14.41 TRIP-02 (the days of a trip and whether the packing covers them) and 0.14.42 TRIP-03 (packing).

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'trip_days_title': ('Дні поїздки', 'Trip days', 'Dni wyjazdu'),
'trip_days_hint': ('Один образ на день із речей цієї капсули — за тими самими правилами, що й «Що вдягнути сьогодні». Нічого не зберігається й не вважається вдягненим.',
    'One outfit a day from this capsule’s items, by the same rules as “What to wear today”. Nothing is saved or counted as worn.',
    'Jeden zestaw dziennie z rzeczy tej kapsuły — według tych samych zasad co „Co dziś założyć”. Nic nie jest zapisywane ani liczone jako noszone.'),
'trip_covered': ('Покрито днів: %1$d з %2$d', 'Days covered: %1$d of %2$d', 'Pokryte dni: %1$d z %2$d'),
'trip_forecast_city': ('Погода — за прогнозом для міста %1$s. Якщо їдете в інше місто, умови там можуть бути іншими.',
    'Weather from the forecast for %1$s. If you are going elsewhere, it may be different there.',
    'Pogoda z prognozy dla miasta %1$s. Jeśli jedziesz gdzie indziej, warunki mogą być inne.'),
'trip_no_weather': ('Погоду не ввімкнено: образи підібрано без неї.', 'Weather is off: the outfits ignore it.', 'Pogoda jest wyłączona: zestawy dobrano bez niej.'),
'trip_weather_known': ('%1$s · %2$d°', '%1$s · %2$d°', '%1$s · %2$d°'),
'trip_weather_unknown': ('Прогнозу на цей день ще немає — погоду не враховано.', 'No forecast for this day yet — weather is not taken into account.',
    'Brak jeszcze prognozy na ten dzień — pogoda nie jest uwzględniona.'),
'trip_repeated': ('Нових образів не лишилося — повтор одного з попередніх днів.', 'No new outfits left — one from an earlier day again.',
    'Nie ma już nowych zestawów — powtórka z jednego z wcześniejszych dni.'),
'trip_no_outerwear': ('Для такої погоди в капсулі немає готового верхнього одягу.', 'For this weather the capsule has no ready outerwear.',
    'Na taką pogodę w kapsule nie ma gotowego okrycia wierzchniego.'),
'trip_rain': ('Імовірність дощу %1$d%%: прогноз не гарантує, що речі водостійкі.', 'Chance of rain %1$d%%: the forecast does not make the items waterproof.',
    'Szansa na deszcz %1$d%%: prognoza nie sprawia, że rzeczy są wodoodporne.'),
'trip_past': ('Минулі дні поїздки не показано: %1$d.', 'Past trip days are not shown: %1$d.', 'Minione dni wyjazdu nie są pokazane: %1$d.'),
'trip_over': ('Поїздка вже минула.', 'The trip is over.', 'Wyjazd już się skończył.'),
'trip_truncated': ('Показано перші %1$d днів.', 'The first %1$d days are shown.', 'Pokazano pierwsze %1$d dni.'),
# 0.14.42 TRIP-03: a compact packing suggestion.
'trip_suggest': ('Запропонувати речі', 'Suggest items', 'Zaproponuj rzeczy'),
'trip_suggest_add': ('Додати %1$d речей — тоді образ буде на %2$d з %3$d днів, з повторним використанням речей:',
    'Add %1$d items — then %2$d of %3$d days have an outfit, reusing items:', 'Dodaj %1$d rzeczy — wtedy zestaw będzie na %2$d z %3$d dni, z ponownym użyciem rzeczy:'),
'trip_suggest_enough': ('Додавати нічого не треба: речей у капсулі вистачає на %2$d з %3$d днів.',
    'Nothing to add: the capsule’s items cover %2$d of %3$d days.', 'Nie trzeba nic dodawać: rzeczy w kapsule wystarczą na %2$d z %3$d dni.'),
'trip_suggest_gaps': ('Деяким дням образу бракує навіть з усього гардероба — причини видно в картках днів.',
    'Some days lack an outfit even from the whole wardrobe — the day cards show why.',
    'Niektórym dniom brakuje zestawu nawet z całej szafy — powody widać na kartach dni.'),
'trip_suggest_hint': ('Речі додаються до капсули як додані вручну; зібране позначається окремо.',
    'The items are added to the capsule as added by hand; packing is marked separately.',
    'Rzeczy są dodawane do kapsuły jako dodane ręcznie; spakowanie zaznacza się osobno.'),
'trip_suggest_confirm': ('Додати %1$d', 'Add %1$d', 'Dodaj %1$d'),
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
