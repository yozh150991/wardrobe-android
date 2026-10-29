"""Labels for the 0.14.20 audit fixes (A-04 waiting shared actions, L-01 partial batches).

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'family_waiting': ('Чекає завершення іншої спільної операції. Можна скасувати.', 'Waiting for another shared operation to finish. You can cancel.', 'Oczekiwanie na zakończenie innej wspólnej operacji. Możesz anulować.'),
'family_sync_partial': ('Частину змін надіслано. Додавання, фото чи образи, підготовлені на попередній версії, лишилися в черзі: онови їх у відповідному редакторі й підтвердь новий пакет.', 'Some changes were sent. Additions, photos or outfits prepared on the previous version stay queued: refresh them in their editor and approve a new batch.', 'Część zmian została wysłana. Dodania, zdjęcia lub stylizacje przygotowane na poprzedniej wersji pozostały w kolejce: odśwież je w ich edytorze i potwierdź nową partię.'),
}
UPDATED = {
'family_sync_hint': ('Підтверди список уже збережених змін. Нові чернетки не додаються до цього пакета. Зміни надсилаються послідовно. Поля речей після власних змін цього пакета оновлюються автоматично; додавання, фото чи образ на старішій версії лишаються в черзі для оновлення. Чужа зміна на сервері зупиняє пакет для перегляду. Незавершені форми не надсилаються.', 'Confirm the list of saved changes. New drafts are not added to this batch. Changes are sent in order. Item details are updated automatically after this batch\'s own changes; an addition, photo or outfit built on an older version stays queued for refresh. Someone else\'s change on the server pauses the batch for review. Unfinished forms are not sent.', 'Potwierdź listę zapisanych zmian. Nowe szkice nie są dodawane do tej partii. Zmiany są wysyłane kolejno. Dane rzeczy są aktualizowane automatycznie po własnych zmianach tej partii; dodanie, zdjęcie lub stylizacja na starszej wersji pozostają w kolejce do odświeżenia. Zmiana innej osoby na serwerze wstrzymuje partię do sprawdzenia. Niedokończone formularze nie są wysyłane.'),
}
if __name__ == '__main__':
    import re
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
        for key, values in UPDATED.items():
            pattern = re.compile(rf'(<string name="{key}">)(.*?)(</string>)', re.S)
            assert len(pattern.findall(text)) == 1, (folder, key)
            text = pattern.sub(lambda m: m.group(1) + encode(values[column]) + m.group(3), text)
        path.write_text(text)
