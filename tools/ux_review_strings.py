"""Labels for 0.14.47 (UX review: delete an outfit, optional item name, season and style in the main form).

New keys are appended to every locale's strings.xml; existing keys are left as they are.
"""
STRINGS = {
'outfit_delete': ('Видалити образ', 'Delete outfit', 'Usuń zestaw'),
'outfit_delete_title': ('Видалити образ «%1$s»?', 'Delete the outfit “%1$s”?', 'Usunąć zestaw „%1$s”?'),
'outfit_delete_hint': ('Речі лишаться в гардеробі, а відмічені носіння — в історії речей. Образ зникне з капсул і поїздок.',
    'The items stay in the wardrobe and the wear already marked stays in their history. The outfit leaves capsules and trips.',
    'Rzeczy zostaną w garderobie, a zaznaczone noszenia w ich historii. Zestaw zniknie z kapsuł i wyjazdów.'),
'outfit_delete_plans': ('Плани в календарі з цим образом теж видаляться: %1$d.', 'Calendar plans with this outfit are deleted too: %1$d.',
    'Plany w kalendarzu z tym zestawem też zostaną usunięte: %1$d.'),
'item_name_optional': ('Назва — необов’язково', 'Name — optional', 'Nazwa — opcjonalnie'),
'item_name_auto': ('Без назви річ називатиметься «%1$s».', 'Without a name the item is called “%1$s”.', 'Bez nazwy rzecz będzie się nazywać „%1$s”.'),
'item_season_style_hint': ('Сезон і стиль визначають, коли річ пропонується: за погодою й нагодою.',
    'Season and style decide when the item is suggested: by the weather and the occasion.',
    'Sezon i styl decydują, kiedy rzecz jest proponowana: według pogody i okazji.'),
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
    print(f'{len(STRINGS)} UX-review strings × 4 locales')
