"""Labels for 0.14.49 UX-15 (delete an unused item).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'item_delete': ('Видалити річ', 'Delete item', 'Usuń rzecz'),
'item_delete_title': ('Видалити «%1$s» назавжди?', 'Delete “%1$s” for good?', 'Usunąć „%1$s” na zawsze?'),
'item_delete_hint': ('Річ і її фото зникнуть із гардероба й капсул. Скасувати це не можна.',
    'The item and its photo leave the wardrobe and capsules. This cannot be undone.',
    'Rzecz i jej zdjęcie znikną z garderoby i kapsuł. Tego nie można cofnąć.'),
'item_delete_confirm': ('Видалити', 'Delete', 'Usuń'),
'item_delete_blocked_title': ('Цю річ можна лише перенести до архіву', 'This item can only be archived', 'Tę rzecz można tylko przenieść do archiwum'),
'item_delete_worn': ('Її відмічено як вдягнену, днів: %1$d. Видалення стерло б цю історію.',
    'It is marked as worn, days: %1$d. Deleting it would erase that history.',
    'Jest oznaczona jako noszona, dni: %1$d. Usunięcie wymazałoby tę historię.'),
'item_delete_outfits': ('Вона є у збережених образах: %1$d. Щоб видалити річ, спершу прибери її з них або видали ці образи.',
    'It is in saved outfits: %1$d. To delete the item, first take it out of them or delete those outfits.',
    'Jest w zapisanych zestawach: %1$d. Aby usunąć rzecz, najpierw wyjmij ją z nich albo usuń te zestawy.'),
'item_delete_purchase': ('Її куплено зі списку бажань, і запис про покупку посилається на неї.',
    'It was bought from the wish list, and the purchase record refers to it.',
    'Została kupiona z listy życzeń i zapis zakupu się do niej odnosi.'),
'item_delete_archive_instead': ('В архіві річ не потрапляє в підбір, а історія зберігається.',
    'In the archive the item is not suggested, and its history is kept.',
    'W archiwum rzecz nie trafia do doboru, a historia zostaje.'),
'item_delete_archived': ('Річ уже в архіві й не потрапляє в підбір.', 'The item is already archived and is not suggested.',
    'Rzecz jest już w archiwum i nie trafia do doboru.'),
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
    print(f'{len(STRINGS)} item-delete strings × 4 locales')
