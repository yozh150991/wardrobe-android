"""Labels for 0.14.46 PHOTO-17 (share a photo as a file).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'photo_export_open': ('Поділитися фото', 'Share photo', 'Udostępnij zdjęcie'),
'photo_export_format': ('Формат', 'Format', 'Format'),
'photo_export_png': ('PNG — прозорий фон', 'PNG — transparent background', 'PNG — przezroczyste tło'),
'photo_export_white': ('JPEG — білий фон', 'JPEG — white background', 'JPEG — białe tło'),
'photo_export_cream': ('JPEG — кремовий фон', 'JPEG — cream background', 'JPEG — kremowe tło'),
'photo_export_no_alpha': ('У цього фото фон не вирізано, тож PNG буде без прозорості.',
    'This photo’s background was not removed, so the PNG has no transparency.',
    'Tło tego zdjęcia nie zostało usunięte, więc PNG nie będzie przezroczysty.'),
'photo_export_size': ('Розмір', 'Size', 'Rozmiar'),
'photo_export_original': ('Як збережено — %1$d × %2$d', 'As saved — %1$d × %2$d', 'Jak zapisano — %1$d × %2$d'),
'photo_export_pixels': ('%1$d × %2$d', '%1$d × %2$d', '%1$d × %2$d'),
'photo_export_note': ('Фото ніколи не збільшується. У файлі лише зображення — без геоданих та інших даних знімка.',
    'The photo is never enlarged. The file holds only the image — no location or other camera data.',
    'Zdjęcie nigdy nie jest powiększane. Plik zawiera tylko obraz — bez lokalizacji i innych danych aparatu.'),
'photo_export_preparing': ('Готуємо файл…', 'Preparing the file…', 'Przygotowujemy plik…'),
'photo_export_share': ('Поділитися', 'Share', 'Udostępnij'),
'photo_export_failed': ('Не вдалося підготувати файл. Спробуй ще раз.', 'Could not prepare the file. Try again.',
    'Nie udało się przygotować pliku. Spróbuj ponownie.'),
'photo_export_no_app': ('Немає застосунку, щоб поділитися файлом.', 'No app can receive the file.',
    'Brak aplikacji, która może odebrać plik.'),
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
    print(f'{len(STRINGS)} photo-export strings × 4 locales')
