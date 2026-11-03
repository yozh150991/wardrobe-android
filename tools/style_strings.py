"""Labels for 0.14.31 STYLE-01–03: outfit rating, colour pairs, «Добре → Краще» and the new outfit card.

New keys are appended; existing keys of this release are overwritten (rules v2 changed some wording).
"""
STRINGS = {
'style_label_great': ('Чудово', 'Great', 'Świetnie'),
'style_label_good': ('Добре', 'Good', 'Dobrze'),
'style_label_fair': ('Непогано', 'Fair', 'Nieźle'),
'style_label_weak': ('Можна краще', 'Could be better', 'Może być lepiej'),
'style_score_description': ('Оцінка образу: %1$d з 10, %2$s', 'Outfit score: %1$d out of 10, %2$s', 'Ocena zestawu: %1$d na 10, %2$s'),
'style_pair_line': ('%1$s + %2$s: %3$s', '%1$s + %2$s: %3$s', '%1$s + %2$s: %3$s'),
'style_pair_warm_cool': ('тепле з холодним — найбагатше поєднання нейтральних', 'warm with cool — the richest pairing of neutrals',
    'ciepły z zimnym — najbogatsze połączenie neutralnych'),
'style_pair_accent_contrast': ('акцент на нейтральному протилежної температури — як синій із коричневим',
    'an accent on a neutral of the opposite temperature — like blue with brown', 'akcent na neutralnym o przeciwnej temperaturze — jak niebieski z brązem'),
'style_pair_contrast': ('світле з темним — класичний контраст', 'light with dark — a classic contrast', 'jasny z ciemnym — klasyczny kontrast'),
'style_pair_accent': ('нейтральна основа й один акцент', 'a neutral base and one accent', 'neutralna baza i jeden akcent'),
'style_pair_calm': ('спокійні нейтральні — добре, але без виразності', 'calm neutrals — fine, but understated', 'spokojne neutralne — dobrze, ale bez wyrazu'),
'style_pair_tonal': ('тон у тон — цілісно; краще в різних відтінках', 'tone on tone — cohesive; best in different shades', 'ton w ton — spójnie; najlepiej w różnych odcieniach'),
'style_pair_unknown': ('колір не вказано, поєднання не оцінюється', 'no colour given, the pairing is not rated', 'nie podano koloru, połączenie nie jest oceniane'),
'style_pair_same_accent': ('сміливий монохром одного яскравого кольору — краще в різних відтінках', 'a bold monochrome in one bright colour — best in different shades',
    'odważny monochrom jednego jaskrawego koloru — najlepiej w różnych odcieniach'),
'style_pair_black_brown': ('спірне поєднання: допоможуть третій колір і різні відтінки', 'debated: a third colour and different shades help',
    'połączenie dyskusyjne: pomoże trzeci kolor i różne odcienie'),
'style_pair_dark_dark': ('спірне поєднання: відтінки мають явно відрізнятися', 'debated: the shades should be clearly different',
    'połączenie dyskusyjne: odcienie powinny wyraźnie się różnić'),
'style_pair_two_accents': ('два яскраві кольори — решту краще лишити нейтральною', 'two bright colours — keep the rest neutral',
    'dwa jaskrawe kolory — resztę lepiej zostawić neutralną'),
'style_many_colours': ('Кольорів: %1$d — правило трьох кольорів радить не більше трьох, разом із нейтральними',
    'Colours: %1$d — the three-colour rule suggests no more than three, neutrals included',
    'Kolorów: %1$d — zasada trzech kolorów radzi nie więcej niż trzy, łącznie z neutralnymi'),
'style_one_style': ('Один стиль: %1$s', 'One style: %1$s', 'Jeden styl: %1$s'),
'style_one_style_other': ('Один стиль: %1$s (для цієї нагоди зазвичай — %2$s)', 'One style: %1$s (this occasion usually calls for %2$s)',
    'Jeden styl: %1$s (na tę okazję zwykle — %2$s)'),
'style_mixed': ('Стилі змішані: %1$s — %2$d з %3$d речей', 'Mixed styles: %1$s — %2$d of %3$d items', 'Style mieszane: %1$s — %2$d z %3$d rzeczy'),
'style_weather_ok': ('Під погоду «%1$s»', 'Right for “%1$s” weather', 'Pasuje do pogody „%1$s”'),
'style_weather_no_outerwear': ('Для погоди «%1$s» бракує верхнього одягу', 'No outerwear for “%1$s” weather', 'Brak okrycia wierzchniego na pogodę „%1$s”'),
'style_weather_season': ('«%1$s» не за сезоном для погоди «%2$s»', '“%1$s” is out of season for “%2$s” weather', '„%1$s” nie pasuje sezonem do pogody „%2$s”'),
'style_now': ('Зараз %1$d/10', 'Now %1$d/10', 'Teraz %1$d/10'),
'style_better': ('Краще %1$d/10', 'Better %1$d/10', 'Lepiej %1$d/10'),
'style_better_change': ('Краще: «%1$s» замість «%2$s».', 'Better: “%1$s” instead of “%2$s”.', 'Lepiej: „%1$s” zamiast „%2$s”.'),
'style_better_style': ('Так усе буде в одному стилі.', 'That keeps everything in one style.', 'Wtedy wszystko będzie w jednym stylu.'),
'style_better_weather': ('Так образ краще пасуватиме до погоди.', 'That suits the weather better.', 'Wtedy zestaw lepiej pasuje do pogody.'),
'style_apply': ('Замінити', 'Swap', 'Zamień'),
'style_occasion_chip': ('Нагода: %1$s. Змінити', 'Occasion: %1$s. Change', 'Okazja: %1$s. Zmień'),
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
            pattern = re.compile(rf'(<string name="{key}">)(.*?)(</string>)', re.S)
            if pattern.search(text): text = pattern.sub(lambda m: m.group(1) + encode(values[column]) + m.group(3), text)
            else: text = text.replace('</resources>', f'    <string name="{key}">{encode(values[column])}</string>\n</resources>', 1)
        path.write_text(text)
