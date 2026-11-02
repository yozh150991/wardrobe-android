"""Labels for 0.14.27 PERS-02: visible, changeable preferences for the daily outfits.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'prefs_open': ('Мої вподобання', 'My preferences', 'Moje preferencje'),
'prefs_title': ('Мої вподобання', 'My preferences', 'Moje preferencje'),
'prefs_empty': ('Поки що немає відгуків і налаштувань: образи підбираються за звичним стилем нагоди та погодою. Відгуки «Не підходить…» і вибір нижче поступово налаштують підбір.',
    'No feedback or settings yet: outfits follow the usual style of the occasion and the weather. “Not right…” feedback and the choices below gradually tune them.',
    'Brak jeszcze opinii i ustawień: zestawy dobierane są według zwykłego stylu okazji i pogody. Opinie „Nie pasuje…” i wybór poniżej stopniowo dostroją dobór.'),
'prefs_personal': ('Ці вподобання — лише ваші: вони зберігаються в цьому акаунті для цього гардероба й не потрапляють до спільних гардеробів і їхніх копій.',
    'These preferences are yours only: they stay in this account for this wardrobe and never go into shared wardrobes or their copies.',
    'Te preferencje są tylko Twoje: zostają na tym koncie dla tej szafy i nie trafiają do wspólnych szaf ani ich kopii.'),
'prefs_style_title': ('Стиль для нагод', 'Style per occasion', 'Styl na okazje'),
'prefs_auto': ('Авто', 'Auto', 'Auto'),
'prefs_style_default': ('Типово для цієї нагоди: %1$s.', 'Usual for this occasion: %1$s.', 'Zwykle na tę okazję: %1$s.'),
'prefs_style_auto': ('Автоматично: %1$s — відгуків «Надто офіційно»: %2$d.', 'Automatic: %1$s — “Too formal” feedback: %2$d.', 'Automatycznie: %1$s — opinii „Zbyt oficjalne”: %2$d.'),
'prefs_style_choice': ('Ваш вибір: %1$s.', 'Your choice: %1$s.', 'Twój wybór: %1$s.'),
'prefs_style_avoid_formal': ('Офіційні речі тут пропонуються рідше.', 'Formal items are suggested less often here.', 'Rzeczy oficjalne są tu proponowane rzadziej.'),
'prefs_warmth_title': ('Тепло', 'Warmth', 'Ciepło'),
'prefs_warmth_forecast': ('Як у прогнозі', 'As forecast', 'Jak w prognozie'),
'prefs_warmth_lighter': ('На крок легше', 'One step lighter', 'O krok lżej'),
'prefs_warmth_warmer': ('На крок тепліше', 'One step warmer', 'O krok cieplej'),
'prefs_warmth_default': ('Як у прогнозі. Після %1$d відгуків «Надто тепло» стане на крок легше автоматично.',
    'As forecast. After %1$d “Too warm” feedback it becomes one step lighter automatically.',
    'Jak w prognozie. Po %1$d opiniach „Za ciepło” automatycznie zrobi się o krok lżej.'),
'prefs_warmth_auto': ('Автоматично: на крок легше за прогноз — відгуків «Надто тепло»: %1$d.',
    'Automatic: one step lighter than the forecast — “Too warm” feedback: %1$d.',
    'Automatycznie: o krok lżej niż prognoza — opinii „Za ciepło”: %1$d.'),
'prefs_warmth_choice': ('Ваш вибір: %1$s.', 'Your choice: %1$s.', 'Twój wybór: %1$s.'),
'prefs_warmth_hint': ('Діє лише на погоду з прогнозу; вручну вибрані умови не змінюються.',
    'Applies to the forecast only; manually chosen conditions are not changed.',
    'Dotyczy tylko pogody z prognozy; ręcznie wybrane warunki się nie zmieniają.'),
'prefs_weather_shifted': ('Прогноз: %1$s. З урахуванням ваших вподобань: %2$s.', 'Forecast: %1$s. With your preferences: %2$s.', 'Prognoza: %1$s. Z Twoimi preferencjami: %2$s.'),
'prefs_uncomfortable_title': ('Незручні речі: %1$d', 'Uncomfortable items: %1$d', 'Niewygodne rzeczy: %1$d'),
'prefs_uncomfortable_scope': ('Пропонується рідше в усіх умовах.', 'Suggested less often in all conditions.', 'Proponowana rzadziej we wszystkich warunkach.'),
'prefs_hidden_title': ('Приховані поєднання: %1$d', 'Hidden combinations: %1$d', 'Ukryte zestawienia: %1$d'),
'prefs_scope_everywhere': ('%1$s — в усіх умовах.', '%1$s — in all conditions.', '%1$s — we wszystkich warunkach.'),
'prefs_scope_occasion': ('%1$s — для нагоди «%2$s».', '%1$s — for “%2$s”.', '%1$s — na okazję „%2$s”.'),
'prefs_scope_weather': ('%1$s — для погоди «%2$s».', '%1$s — for “%2$s” weather.', '%1$s — przy pogodzie „%2$s”.'),
'prefs_none': ('Немає.', 'None.', 'Brak.'),
'prefs_restore': ('Повернути', 'Restore', 'Przywróć'),
'prefs_unknown_item': ('Річ видалено', 'Item deleted', 'Rzecz usunięta'),
'prefs_reset_all': ('Скинути всі вподобання', 'Reset all preferences', 'Zresetuj wszystkie preferencje'),
'prefs_hidden_shown': ('Усі варіанти для цих умов раніше приховані вашими відгуками — показано найкращі з них. Повернути чи скинути їх можна в «Мої вподобання».',
    'Every variant for these conditions was hidden by your feedback earlier, so the best of them are shown. Restore or reset them in “My preferences”.',
    'Wszystkie warianty na te warunki zostały wcześniej ukryte Twoimi opiniami — pokazano najlepsze z nich. Przywrócić lub zresetować je można w „Moje preferencje”.'),
}
UPDATED = {
'feedback_reset_confirm': ('Скинути всі вподобання цього гардероба? Приховані поєднання знову з’являться, незручні речі пропонуватимуться як раніше, а стиль і тепло повернуться до автоматичних.',
    'Reset all preferences of this wardrobe? Hidden combinations come back, uncomfortable items are suggested as before, and style and warmth return to automatic.',
    'Zresetować wszystkie preferencje tej szafy? Ukryte zestawienia wrócą, niewygodne rzeczy będą proponowane jak dawniej, a styl i ciepło wrócą do automatycznych.'),
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
