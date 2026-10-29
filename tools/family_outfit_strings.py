"""Labels for 0.14.22 outfit changes applied by the server.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {}
UPDATED = {
'family_sync_hint': ('Підтверди список уже збережених змін. Нові чернетки не додаються до цього пакета. Зміни надсилаються послідовно. Нові речі, фото й образи застосовуються до найновішої версії; поля речей після власних змін цього пакета оновлюються автоматично. Чернетка, підготовлена до оновлення сервера, на старішій версії лишається в черзі для оновлення. Зміна іншої людини в тому самому образі чи чужа зміна перед правкою полів зупиняє пакет для перегляду. Незавершені форми не надсилаються.', 'Confirm the list of saved changes. New drafts are not added to this batch. Changes are sent in order. New items, photos and outfits are applied to the newest version; item details are updated automatically after this batch\'s own changes. A draft prepared before the server update that is built on an older version stays queued for refresh. Someone else\'s change to the same outfit, or before an item-detail edit, pauses the batch for review. Unfinished forms are not sent.', 'Potwierdź listę zapisanych zmian. Nowe szkice nie są dodawane do tej partii. Zmiany są wysyłane kolejno. Nowe rzeczy, zdjęcia i stylizacje są stosowane do najnowszej wersji; dane rzeczy są aktualizowane automatycznie po własnych zmianach tej partii. Szkic przygotowany przed aktualizacją serwera na starszej wersji pozostaje w kolejce do odświeżenia. Zmiana innej osoby w tej samej stylizacji lub przed edycją danych rzeczy wstrzymuje partię do sprawdzenia. Niedokończone formularze nie są wysyłane.'),
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
