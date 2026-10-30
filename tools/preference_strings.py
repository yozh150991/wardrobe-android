"""Labels for 0.14.26 PERS-01: reasons for turning down a suggested outfit, their scope and undo.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'feedback_open': ('Не підходить…', 'Not right…', 'Nie pasuje…'),
'feedback_title': ('Чому не підходить?', 'Why is it not right?', 'Dlaczego nie pasuje?'),
'feedback_uncomfortable': ('Незручно', 'Uncomfortable', 'Niewygodne'),
'feedback_formal': ('Надто офіційно', 'Too formal', 'Zbyt oficjalne'),
'feedback_combination_scope': ('Цей набір речей більше не пропонуватиметься в жодних умовах. Самі речі — як і раніше.',
    'This set of items will not be suggested again in any conditions. The items themselves stay as before.',
    'Ten zestaw rzeczy nie będzie już proponowany w żadnych warunkach. Same rzeczy — jak dotąd.'),
'feedback_uncomfortable_scope': ('Обрана річ пропонуватиметься рідше в усіх умовах, але не зникне з пропозицій. Прибрати її повністю — «%1$s» у меню речі.',
    'The chosen item will be suggested less often in all conditions but will not disappear. To remove it completely, use “%1$s” in the item’s menu.',
    'Wybrana rzecz będzie proponowana rzadziej we wszystkich warunkach, ale nie zniknie. Aby ją całkiem usunąć — „%1$s” w menu rzeczy.'),
'feedback_formal_scope': ('Для нагоди «%1$s» цей набір більше не пропонуватиметься, а його речі — рідше. Інші нагоди не зміняться.',
    'For “%1$s” this set will not be suggested again and its items less often. Other occasions do not change.',
    'Na okazję „%1$s” ten zestaw nie będzie już proponowany, a jego rzeczy — rzadziej. Inne okazje się nie zmienią.'),
'feedback_warm_scope': ('Для погоди «%1$s» цей набір більше не пропонуватиметься, а його речі — рідше.',
    'For “%1$s” weather this set will not be suggested again and its items less often.',
    'Przy pogodzie „%1$s” ten zestaw nie będzie już proponowany, a jego rzeczy — rzadziej.'),
'feedback_warm_step': ('Умови зміняться на «%1$s».', 'The conditions change to “%1$s”.', 'Warunki zmienią się na „%1$s”.'),
'feedback_which': ('Що саме незручно?', 'What exactly is uncomfortable?', 'Co dokładnie jest niewygodne?'),
'feedback_done': ('Враховано: %1$s', 'Noted: %1$s', 'Uwzględniono: %1$s'),
'feedback_already': ('Це вже було враховано раніше.', 'This was already noted before.', 'To zostało już wcześniej uwzględnione.'),
'feedback_ok': ('Зрозуміло', 'Got it', 'Rozumiem'),
'feedback_count': ('Збережені відгуки щодо образів: %1$d', 'Saved outfit feedback: %1$d', 'Zapisane opinie o zestawach: %1$d'),
'feedback_reset_confirm': ('Скинути всі відгуки щодо образів цього гардероба? Приховані набори знову з’являться, а незручні речі пропонуватимуться як раніше.',
    'Reset all outfit feedback of this wardrobe? Hidden sets come back and uncomfortable items are suggested as before.',
    'Zresetować wszystkie opinie o zestawach tej szafy? Ukryte zestawy wrócą, a niewygodne rzeczy będą proponowane jak dawniej.'),
}
UPDATED = {
'daily_dislike': ('Не подобається поєднання', 'Do not like the combination', 'Nie podoba mi się zestawienie'),
'daily_hot': ('Надто тепло', 'Too warm', 'Za ciepło'),
'daily_reset_feedback': ('Скинути відгуки щодо образів', 'Reset outfit feedback', 'Zresetuj opinie o zestawach'),
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
