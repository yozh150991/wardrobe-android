"""Targeted labels for explicit family queue scheduling."""
STRINGS = {
'family_sync_title': ('Надсилання спільних змін', 'Send shared changes', 'Wysyłanie wspólnych zmian'),
'family_sync_hint': ('Підтверди список уже збережених змін. Нові чернетки не додаються до цього пакета. Зміни надсилаються послідовно; конфлікт зупиняє пакет для перегляду. Незавершені форми не надсилаються.', 'Confirm the list of saved changes. New drafts are not added to this batch. Changes are sent in order; a conflict pauses the batch for review. Unfinished forms are not sent.', 'Potwierdź listę zapisanych zmian. Nowe szkice nie są dodawane do tej partii. Zmiany są wysyłane kolejno; konflikt wstrzymuje partię do sprawdzenia. Niedokończone formularze nie są wysyłane.'),
'family_sync_progress': ('Завершено: %1$d із %2$d', 'Completed: %1$d of %2$d', 'Ukończono: %1$d z %2$d'),
'family_sync_done': ('Підтверджений пакет надіслано. Онови спільний гардероб, щоб побачити зміни.', 'The approved batch was sent. Refresh the shared wardrobe to see the changes.', 'Potwierdzona partia została wysłana. Odśwież wspólną garderobę, aby zobaczyć zmiany.'),
'family_sync_paused': ('Надсилання зупинене. Збережені чернетки залишаються доступними.', 'Sending is stopped. Saved drafts remain available.', 'Wysyłanie jest wstrzymane. Zapisane szkice pozostają dostępne.'),
'family_sync_retrying': ('Очікування повтору. Запит із невідомим результатом зберігає початкові дані.', 'Waiting to retry. A request with an unknown outcome keeps its original data.', 'Oczekiwanie na ponowienie. Żądanie o nieznanym wyniku zachowuje pierwotne dane.'),
'family_sync_queued': ('Пакет очікує або надсилається. Фоновий режим може чекати мережі та дозволу Android.', 'The batch is waiting or sending. Background mode may wait for a network and Android scheduling.', 'Partia oczekuje lub jest wysyłana. Tryb w tle może czekać na sieć i harmonogram Androida.'),
'family_sync_stop': ('Зупинити надсилання', 'Stop sending', 'Zatrzymaj wysyłanie'),
'family_sync_review': ('Черга або серверна версія змінилася. Відкрий відповідну чернетку, перевір конфлікт або онови її, потім підтверди новий пакет. Автоматичного перезапису немає.', 'The queue or server version changed. Open the relevant draft, review the conflict or refresh it, then approve a new batch. Changes are not overwritten automatically.', 'Kolejka lub wersja na serwerze uległa zmianie. Otwórz odpowiedni szkic, sprawdź konflikt lub odśwież go, a następnie potwierdź nową partię. Zmiany nie są automatycznie nadpisywane.'),
'family_sync_pending': ('Підготовлених змін: %1$d', 'Prepared changes: %1$d', 'Przygotowane zmiany: %1$d'),
'family_sync_background': ('Надіслати цей пакет у фоні через нетарифіковану мережу (зазвичай Wi-Fi), навіть після закриття застосунку. Вихід з акаунта зупиняє пакет.', 'Send this batch in the background over an unmetered network (usually Wi-Fi), even after closing the app. Signing out stops the batch.', 'Wyślij tę partię w tle przez sieć bez limitu danych (zwykle Wi-Fi), także po zamknięciu aplikacji. Wylogowanie zatrzymuje partię.'),
'family_sync_send': ('Підтвердити надсилання списку', 'Approve sending this list', 'Potwierdź wysłanie listy'),
'family_sync_confirm': ('Надіслати %1$d підготовлених змін у вибраному режимі? Під час зупинки вже прийняті сервером зміни не скасовуються. Після втрати відповіді повтор перевіряє результат того самого запиту.', 'Send %1$d prepared changes in the selected mode? Stopping does not undo changes already accepted by the server. After a lost response, retry checks the same request.', 'Wysłać %1$d przygotowanych zmian w wybranym trybie? Zatrzymanie nie cofa zmian już przyjętych przez serwer. Po utracie odpowiedzi ponowienie sprawdza to samo żądanie.'),
'family_sync_edit': ('Поля речі', 'Item details', 'Dane rzeczy'),
'family_sync_add': ('Додавання речі', 'Add item', 'Dodanie rzeczy'),
'family_sync_photo': ('Заміна фото', 'Replace photo', 'Zmiana zdjęcia'),
'family_sync_outfit': ('Образ', 'Outfit', 'Stylizacja'),
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
