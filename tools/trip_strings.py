"""Labels for 0.14.41 TRIP-02: the days of a trip and whether the packing covers them.

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
