"""Shared media queues: targeted Ukrainian / English / Polish additions."""
STRINGS = {
'family_media_queue_count': ('Чернетки: %1$d із %2$d', 'Drafts: %1$d of %2$d', 'Szkice: %1$d z %2$d'),
'family_media_add_another': ('Підготувати ще одну річ', 'Prepare another item', 'Przygotuj kolejną rzecz'),
'family_media_add_hint': ('Можна підготувати кілька нових речей і надсилати їх по черзі. До 10 чернеток і 512 МБ архівів для надсилання на гардероб. Після зміни серверної версії явно онови наступну чернетку; решта даних збережеться.', 'Prepare several new items and send them in order. Up to 10 drafts and 512 MB of upload archives per wardrobe. When the server version changes, explicitly refresh the next draft; other data is preserved.', 'Przygotuj kilka nowych rzeczy i wysyłaj je po kolei. Do 10 szkiców i 512 MB archiwów do wysłania na garderobę. Po zmianie wersji na serwerze jawnie odśwież kolejny szkic; pozostałe dane zostaną zachowane.'),
'family_media_photo_hint': ('Підготуй нові фото різних речей, відкриваючи кожну зі спільного гардероба. Одна чернетка на річ, до 10 і 512 МБ архівів на гардероб. Фото надсилаються по черзі; після зміни серверної версії потрібне явне оновлення чернетки.', 'Prepare photos for several items by opening each in the shared wardrobe. One draft per item, up to 10 drafts and 512 MB of archives per wardrobe. Photos are sent in order; a changed server version requires an explicit draft refresh.', 'Przygotuj zdjęcia kilku rzeczy, otwierając każdą we wspólnej garderobie. Jeden szkic na rzecz, do 10 szkiców i 512 MB archiwów na garderobę. Zdjęcia są wysyłane po kolei; zmiana wersji na serwerze wymaga jawnego odświeżenia szkicu.'),
'family_media_queue_wait': ('Спершу заверши першу чернетку цієї черги. Її можна вибрати у списку вище.', 'Finish the first draft in this queue first. Select it from the list above.', 'Najpierw zakończ pierwszy szkic w tej kolejce. Wybierz go z listy powyżej.'),
}
if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = root / folder / 'strings.xml'; text = path.read_text()
        for key, values in STRINGS.items():
            if f'name="{key}"' in text: continue
            value = escape(values[column]).replace("'", "\\'").replace('"', '\\"')
            text = text.replace('</resources>', f'    <string name="{key}">{value}</string>\n</resources>', 1)
        path.write_text(text)
