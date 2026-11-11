"""Labels for 0.14.35 WEAR-01: «Вдягаю сьогодні» on daily variants, wear on another day and the calendar's worn items.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'wear_look_today': ('Вдягаю сьогодні', 'Wearing it today', 'Zakładam dziś'),
'wear_today_marked': ('Відмічено на сьогодні', 'Marked for today', 'Oznaczone na dziś'),
'wear_today_title': ('Сьогодні вдягнено', 'Worn today', 'Dziś noszone'),
'wear_today_hint': ('Враховано в статистиці, правилі повторів і «Давно не вдягали». Зберігати образ для цього не потрібно.',
    'Counted in the statistics, the repeat rule and “Not worn for a while”. You don’t need to save the outfit for this.',
    'Uwzględnione w statystykach, regule powtórzeń i „Dawno nienoszone”. Nie trzeba do tego zapisywać zestawu.'),
'wear_other_day': ('Вдягнено іншого дня…', 'Worn on another day…', 'Noszone innego dnia…'),
'wear_history_title': ('Останні носіння', 'Recent wear', 'Ostatnie noszenie'),
'wear_remove_day': ('Прибрати носіння за %1$s?', 'Remove the wear on %1$s?', 'Usunąć noszenie z dnia %1$s?'),
'calendar_worn_title': ('Вдягнено цього дня', 'Worn this day', 'Noszone tego dnia'),
'calendar_worn_empty': ('Носіння за цей день не відмічено.', 'No wear recorded for this day.', 'Brak zapisanego noszenia tego dnia.'),
'calendar_wear_item': ('Відмітити вдягнені речі', 'Mark worn items', 'Oznacz noszone rzeczy'),
}
if __name__ == '__main__':
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
        path.write_text(text)
