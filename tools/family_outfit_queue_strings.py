"""Targeted strings for the shared outfit publication queue."""
STRINGS = {
'family_look_add_another': ('Підготувати ще один образ', 'Prepare another outfit', 'Przygotuj kolejną stylizację'),
'family_look_resume_form': ('Продовжити незавершений образ', 'Resume unfinished outfit', 'Wznów niedokończoną stylizację'),
'family_look_queue_hint': ('До 10 підготовлених образів і 512 МБ архівів на гардероб. Надсилай їх по черзі; після зміни серверної версії явно онови наступну чернетку. Одна незавершена форма редактора зберігається окремо від черги.', 'Up to 10 prepared outfits and 512 MB of archives per wardrobe. Send them in order; explicitly refresh the next draft after the server version changes. One unfinished editor form is preserved separately from the queue.', 'Do 10 przygotowanych stylizacji i 512 MB archiwów na garderobę. Wysyłaj je po kolei; jawnie odśwież kolejny szkic po zmianie wersji na serwerze. Jeden niedokończony formularz edytora jest zachowywany osobno od kolejki.'),
}
if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = root / folder / 'strings.xml'; text = path.read_text()
        for key, values in STRINGS.items():
            if f'name="{key}"' in text: continue
            value = escape(values[column]).replace("'", "\\'").replace('"', '\\"')
            text = text.replace('</resources>', f'    <string name="{key}">{value}</string>\n</resources>', 1)
        path.write_text(text)
