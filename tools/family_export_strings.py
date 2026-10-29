"""Labels for 0.14.24 FAM-04: the owner's copy of a shared wardrobe.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'family_export': ('Зберегти копію спільного гардероба (ZIP)', 'Save a copy of the shared wardrobe (ZIP)', 'Zapisz kopię wspólnej szafy (ZIP)'),
'family_export_hint': ('Лише для власника. Поточна версія з сервера разом зі змінами учасників. Відновлення додасть окремий локальний гардероб; учасники й доступ не переносяться.', 'Owner only. The current version from the server, members\' changes included. Restoring it adds a separate local wardrobe; members and access are not carried over.', 'Tylko dla właściciela. Bieżąca wersja z serwera wraz ze zmianami członków. Przywrócenie doda osobną lokalną szafę; członkowie i dostęp nie są przenoszeni.'),
'family_export_done': ('Копію спільного гардероба збережено й перевірено.', 'The copy of the shared wardrobe was saved and verified.', 'Kopia wspólnej szafy została zapisana i sprawdzona.'),
}
UPDATED = {}
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
