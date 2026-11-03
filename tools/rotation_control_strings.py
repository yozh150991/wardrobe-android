"""Labels for 0.14.30 ROT-02: postponing items, reasons for not wearing them, and how often the card appears.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'rotation_snooze': ('Відкласти…', 'Postpone…', 'Odłóż…'),
'rotation_snooze_title': ('Відкласти «%1$s»', 'Postpone “%1$s”', 'Odłóż „%1$s”'),
'rotation_snooze_hint': ('Річ не з’являтиметься в «Давно не вдягали» до обраної дати. Повернути її раніше можна в «Мої вподобання».',
    'The item won’t appear in “Not worn for a while” until the chosen date. You can bring it back earlier in “My preferences”.',
    'Rzecz nie pojawi się w „Dawno nienoszone” do wybranej daty. Wcześniej przywrócić ją można w „Moje preferencje”.'),
'rotation_snooze_week': ('На тиждень', 'For a week', 'Na tydzień'),
'rotation_snooze_month': ('На місяць', 'For a month', 'Na miesiąc'),
'rotation_snooze_season': ('На 3 місяці', 'For 3 months', 'Na 3 miesiące'),
'rotation_why': ('Чому не вдягаю?', 'Why don’t I wear it?', 'Dlaczego jej nie noszę?'),
'rotation_reason_size': ('Не той розмір або більше не подобається', 'Wrong size or no longer liked', 'Zły rozmiar lub już się nie podoba'),
'rotation_reason_size_scope': ('Запропонуємо перенести її в архів.', 'We’ll offer to move it to the archive.', 'Zaproponujemy przeniesienie jej do archiwum.'),
'rotation_reason_waiting': ('Чекаю нагоди чи сезону', 'Waiting for an occasion or season', 'Czekam na okazję lub sezon'),
'rotation_reason_waiting_scope': ('Відкладемо на 3 місяці.', 'We’ll postpone it for 3 months.', 'Odłożymy ją na 3 miesiące.'),
'rotation_reason_no_match': ('Немає з чим поєднати', 'Nothing to wear it with', 'Nie mam z czym jej połączyć'),
'rotation_reason_no_match_scope': ('Відкладемо на місяць і відкриємо ручне складання образу з нею.',
    'We’ll postpone it for a month and open manual outfit building with it.',
    'Odłożymy ją na miesiąc i otworzymy ręczne składanie zestawu z nią.'),
'rotation_reason_later': ('Без причини', 'No reason given', 'Bez podania powodu'),
'rotation_archive_title': ('Перенести «%1$s» в архів?', 'Move “%1$s” to the archive?', 'Przenieść „%1$s” do archiwum?'),
'rotation_archive_text': ('В архіві річ не пропонується в образах і не показується в каталозі. Повернути її можна будь-коли.',
    'Archived items aren’t suggested in outfits or shown in the catalogue. You can bring it back at any time.',
    'Rzeczy w archiwum nie są proponowane w zestawach ani pokazywane w katalogu. Można ją przywrócić w każdej chwili.'),
'rotation_snooze_instead': ('Лише відкласти на 3 місяці', 'Just postpone for 3 months', 'Tylko odłóż na 3 miesiące'),
'rotation_snoozed': ('«%1$s» відкладено до %2$s.', '“%1$s” is postponed until %2$s.', '„%1$s” odłożono do %2$s.'),
'rotation_hide_today': ('Сховати на сьогодні', 'Hide for today', 'Ukryj na dziś'),
'rotation_hidden': ('«Давно не вдягали» сховано до завтра.', '“Not worn for a while” is hidden until tomorrow.', '„Dawno nienoszone” ukryto do jutra.'),
'rotation_show': ('Показати', 'Show', 'Pokaż'),
'rotation_frequency_link': ('Як часто показувати', 'How often to show', 'Jak często pokazywać'),
'rotation_daily': ('Щодня', 'Every day', 'Codziennie'),
'rotation_weekly': ('Раз на тиждень', 'Once a week', 'Raz w tygodniu'),
'rotation_off': ('Вимкнено', 'Off', 'Wyłączone'),
'prefs_rotation_hint': ('Картка на «Що вдягнути сьогодні» з річчю, яку не вдягали 90+ днів. Річ, яку вона вже показувала, наступного тижня поступається іншим.',
    'A card on “What to wear today” with an item not worn for 90+ days. An item it has already shown gives way to others for the next week.',
    'Karta w „Co założyć dziś” z rzeczą nienoszoną od 90+ dni. Rzecz, którą już pokazała, przez następny tydzień ustępuje innym.'),
'prefs_rotation_postponed': ('Відкладені речі: %1$d', 'Postponed items: %1$d', 'Odłożone rzeczy: %1$d'),
'prefs_rotation_until': ('До %1$s · %2$s', 'Until %1$s · %2$s', 'Do %1$s · %2$s'),
}
UPDATED = {
'feedback_reset_confirm': ('Скинути всі вподобання цього гардероба? Приховані поєднання знову з’являться, незручні речі пропонуватимуться як раніше, стиль і тепло повернуться до автоматичних, а відкладені речі й частота «Давно не вдягали» — до типових.',
    'Reset all preferences of this wardrobe? Hidden combinations come back, uncomfortable items are suggested as before, style and warmth return to automatic, and postponed items and the “Not worn for a while” frequency return to the defaults.',
    'Zresetować wszystkie preferencje tej szafy? Ukryte zestawienia wrócą, niewygodne rzeczy będą proponowane jak dawniej, styl i ciepło wrócą do automatycznych, a odłożone rzeczy i częstotliwość „Dawno nienoszone” — do domyślnych.'),
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
