"""0.14.7 audit fixes; UA / EN / PL. Appended to existing resources (not regenerated)."""
STRINGS = {
'operation_wait': ('Зачекай, поки завершиться поточна операція.', 'Please wait until the current operation finishes.', 'Poczekaj, aż zakończy się bieżąca operacja.'),
'daily_no_outerwear': ('Для цієї погоди немає доступного верхнього одягу, тому образи підібрано без нього. Додай куртку чи пальто, щоб вони з’являлися в підборі.', 'No outerwear is available for this weather, so looks are suggested without it. Add a jacket or coat to include it.', 'Brak dostępnej odzieży wierzchniej na tę pogodę, więc zestawy dobrano bez niej. Dodaj kurtkę lub płaszcz, aby się pojawiała.'),
'family_snapshot_hint': ('Серверна копія — знімок на момент переносу. Пізніші зміни цього гардероба учасники поки не бачать: оновлення копії з’явиться разом зі спільним редагуванням.', 'The server copy is a snapshot from the transfer. Members do not see later changes yet: updating the copy arrives with shared editing.', 'Kopia na serwerze to stan z chwili przeniesienia. Uczestnicy nie widzą jeszcze późniejszych zmian: aktualizacja kopii pojawi się razem ze wspólną edycją.'),
'wish_category_in_outfits': ('Ця річ уже є в збережених образах, і нова категорія зробить їх неможливими (наприклад, два верхи або сукня з брюками). Спершу зміни або прибери її з цих образів.', 'This item is already in saved looks, and the new category would make them impossible (for example two tops, or a dress with trousers). Change or remove it in those looks first.', 'Ta rzecz jest już w zapisanych zestawach, a nowa kategoria uczyniłaby je niemożliwymi (np. dwie góry albo sukienka ze spodniami). Najpierw zmień ją lub usuń z tych zestawów.'),
}

if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = res / folder / 'strings.xml'
        text = path.read_text(encoding='utf-8')
        lines = []
        for key, values in STRINGS.items():
            if f'name="{key}"' in text: continue
            value = escape(values[column]).replace("'", "\\'").replace('"', '\\"')
            lines.append(f'    <string name="{key}">{value}</string>')
        if lines:
            text = text.replace('</resources>', '\n'.join(lines) + '\n</resources>', 1)
            path.write_text(text, encoding='utf-8')
    print(f'{len(STRINGS)} audit strings present in UA/EN/PL and fallback.')
