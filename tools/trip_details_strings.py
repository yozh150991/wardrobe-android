"""Labels for 0.14.44 TRIP-01 (a trip's own city and saved occasion).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'trip_city_label': ('Місто поїздки', 'Trip city', 'Miasto wyjazdu'),
'trip_city_hint': ('Необов’язково. Коли погоду ввімкнено, дні поїздки підбираються за прогнозом для цього міста.',
    'Optional. When the weather is on, the trip days follow this city’s forecast.',
    'Opcjonalnie. Gdy pogoda jest włączona, dni wyjazdu są dobierane według prognozy dla tego miasta.'),
'trip_city_search': ('Знайти', 'Search', 'Szukaj'),
'trip_city_none': ('Такого міста не знайдено.', 'No such city found.', 'Nie znaleziono takiego miasta.'),
'trip_city_failed': ('Не вдалося знайти місто. Перевір інтернет і спробуй ще раз.', 'Could not search for the city. Check the connection and try again.',
    'Nie udało się wyszukać miasta. Sprawdź połączenie i spróbuj ponownie.'),
'trip_city_selected': ('Місто: %1$s', 'City: %1$s', 'Miasto: %1$s'),
'trip_city_remove': ('Прибрати місто', 'Remove the city', 'Usuń miasto'),
'trip_private_hint': ('Місто й нагода поїздки зберігаються у твоїх резервних копіях і хмарі, але не потрапляють у спільні гардероби.',
    'The trip’s city and occasion are kept in your backups and cloud copy, but never in shared wardrobes.',
    'Miasto i okazja wyjazdu są zapisywane w Twoich kopiach i chmurze, ale nie trafiają do wspólnych garderób.'),
'trip_city_forecast': ('Погода — за прогнозом для %1$s, до %2$d днів наперед. Далі дні без прогнозу.',
    'Weather from the forecast for %1$s, up to %2$d days ahead. Later days have no forecast.',
    'Pogoda według prognozy dla %1$s, do %2$d dni naprzód. Dalsze dni są bez prognozy.'),
'trip_city_loading': ('Завантажуємо прогноз для %1$s…', 'Loading the forecast for %1$s…', 'Wczytujemy prognozę dla %1$s…'),
'trip_city_unavailable': ('Прогноз для %1$s зараз недоступний: образи підібрано без погоди.',
    'The forecast for %1$s is not available right now: outfits are chosen without the weather.',
    'Prognoza dla %1$s jest teraz niedostępna: zestawy dobrano bez pogody.'),
'trip_city_weather_off': ('Погоду не ввімкнено: прогноз для %1$s не завантажується, образи підібрано без неї.',
    'The weather is off: the forecast for %1$s is not loaded and outfits are chosen without it.',
    'Pogoda jest wyłączona: prognoza dla %1$s nie jest wczytywana, zestawy dobrano bez niej.'),
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
    print(f'{len(STRINGS)} trip-detail strings × 4 locales')
