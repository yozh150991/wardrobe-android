"""Labels for 0.14.21 shared changes applied by the server (delta model v1).

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'family_change_latest': ('Застосується до найновішої версії спільного гардероба — оновлювати чернетку не потрібно.', 'Will be applied to the newest version of the shared wardrobe — no refresh needed.', 'Zostanie zastosowane do najnowszej wersji wspólnej szafy — odświeżanie szkicu nie jest potrzebne.'),
'family_change_conflict': ('Цю зміну не можна застосувати: у спільному гардеробі вже є власна категорія чи колір з такою назвою, але з іншою базою, або такий запис уже існує. Видали чернетку й підготуй її знову.', 'This change can\'t be applied: the shared wardrobe already has a custom category or colour with this name but a different base, or the record already exists. Discard the draft and prepare it again.', 'Tej zmiany nie można zastosować: wspólna szafa ma już własną kategorię lub kolor o tej nazwie, ale z inną bazą, albo taki wpis już istnieje. Odrzuć szkic i przygotuj go ponownie.'),
}
UPDATED = {
'family_sync_hint': ('Підтверди список уже збережених змін. Нові чернетки не додаються до цього пакета. Зміни надсилаються послідовно. Нові речі й фото застосовуються до найновішої версії; поля речей після власних змін цього пакета оновлюються автоматично. Образ (а також додавання чи фото, підготовлені до оновлення сервера) на старішій версії лишається в черзі для оновлення. Чужа зміна на сервері зупиняє пакет для перегляду. Незавершені форми не надсилаються.', 'Confirm the list of saved changes. New drafts are not added to this batch. Changes are sent in order. New items and photos are applied to the newest version; item details are updated automatically after this batch\'s own changes. An outfit (or an addition or photo prepared before the server update) built on an older version stays queued for refresh. Someone else\'s change on the server pauses the batch for review. Unfinished forms are not sent.', 'Potwierdź listę zapisanych zmian. Nowe szkice nie są dodawane do tej partii. Zmiany są wysyłane kolejno. Nowe rzeczy i zdjęcia są stosowane do najnowszej wersji; dane rzeczy są aktualizowane automatycznie po własnych zmianach tej partii. Stylizacja (a także dodanie lub zdjęcie przygotowane przed aktualizacją serwera) na starszej wersji pozostaje w kolejce do odświeżenia. Zmiana innej osoby na serwerze wstrzymuje partię do sprawdzenia. Niedokończone formularze nie są wysyłane.'),
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
