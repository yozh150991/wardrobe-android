"""FAM-03 metadata queue: targeted UA / EN / PL resource updates."""
STRINGS = {
'family_edit_resume': ('Черга правок речей', 'Item edit queue', 'Kolejka zmian rzeczy'),
'family_edit_hint': ('Зберігай правки кількох речей, а потім надсилай по одній або всю чергу. Одна чернетка на річ, до 100 на акаунт. Чернетки зберігаються лише на цьому пристрої.', 'Save edits for several items, then send one at a time or the whole queue. One draft per item, up to 100 per account. Drafts are kept on this device only.', 'Zapisuj zmiany kilku rzeczy, a następnie wysyłaj je pojedynczo lub całą kolejkę. Jeden szkic na rzecz, do 100 na konto. Szkice są przechowywane tylko na tym urządzeniu.'),
'family_queue_count': ('Черга правок: %1$d', 'Queued edits: %1$d', 'Zmiany w kolejce: %1$d'),
'family_queue_item': ('%1$d. %2$s', '%1$d. %2$s', '%1$d. %2$s'),
'family_queue_order': ('Правки надсилаються в порядку збереження. Щоб перейти до іншої, спочатку збережи введені поля.', 'Edits are sent in the order they were saved. Save the current fields before selecting another draft.', 'Zmiany są wysyłane w kolejności zapisu. Zapisz bieżące pola przed wybraniem innego szkicu.'),
'family_queue_send': ('Надіслати всю чергу', 'Send whole queue', 'Wyślij całą kolejkę'),
'family_queue_wait': ('Спершу заверши попередні правки або надішли всю чергу.', 'Finish earlier edits first, or send the whole queue.', 'Najpierw zakończ wcześniejsze zmiany lub wyślij całą kolejkę.'),
'family_queue_confirm': ('Надіслати збережені правки цього гардероба по черзі? Незалежні зміни об’єднаються з поточною версією. При зміні того самого поля, втраті зв’язку або доступу черга зупиниться; решта чернеток залишиться на пристрої.', 'Send the saved edits for this wardrobe in order? Independent changes will be combined with the current version. A conflicting field, connection failure or access loss will stop the queue; remaining drafts stay on this device.', 'Wysłać zapisane zmiany tej garderoby po kolei? Niezależne zmiany zostaną połączone z bieżącą wersją. Konflikt pola, błąd połączenia lub utrata dostępu zatrzyma kolejkę; pozostałe szkice zostaną na urządzeniu.'),
'family_queue_progress': ('Оброблено правок: %1$d з %2$d', 'Edits processed: %1$d of %2$d', 'Przetworzono zmian: %1$d z %2$d'),
}

if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    import re
    root = Path(__file__).resolve().parents[1]
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = root / 'app/src/main/res' / folder / 'strings.xml'
        text = path.read_text(encoding='utf-8')
        for key, values in STRINGS.items():
            value = escape(values[column]).replace("'", "\\'").replace('"', '\\"')
            line = f'    <string name="{key}">{value}</string>'
            pattern = rf'    <string name="{key}">.*?</string>'
            if re.search(pattern, text): text = re.sub(pattern, lambda _: line, text)
            else: text = text.replace('</resources>', line + '\n</resources>', 1)
        path.write_text(text, encoding='utf-8')
