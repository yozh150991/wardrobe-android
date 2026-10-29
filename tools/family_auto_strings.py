"""Labels for 0.14.23 automatic sending of shared changes.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'family_sync_auto': ('Надсилати зміни автоматично', 'Send changes automatically', 'Wysyłaj zmiany automatycznie'),
'family_sync_auto_hint': ('Для цього спільного гардероба: кожна збережена зміна надсилається у фоні через будь-яку мережу, навіть після закриття застосунку. Конфлікт чи інша проблема зупиняє надсилання, доки ти не розв\'яжеш її в редакторі або не надішлеш вручну. Вихід з акаунта зупиняє надсилання.', 'For this shared wardrobe: every saved change is sent in the background over any network, even after closing the app. A conflict or another problem pauses sending until you resolve it in the editor or send by hand. Signing out stops sending.', 'Dla tej wspólnej szafy: każda zapisana zmiana jest wysyłana w tle przez dowolną sieć, także po zamknięciu aplikacji. Konflikt lub inny problem wstrzymuje wysyłanie, dopóki nie rozwiążesz go w edytorze albo nie wyślesz ręcznie. Wylogowanie zatrzymuje wysyłanie.'),
'family_sync_auto_waiting': ('Автоматичне надсилання чекає на тебе: відкрий зміну, на якій воно зупинилося (оновити основу, видалити або надіслати ще раз), або надішли список вручну.', 'Automatic sending is waiting for you: open the change it stopped at (refresh, discard or send again) or send the list by hand.', 'Automatyczne wysyłanie czeka na Ciebie: otwórz zmianę, na której się zatrzymało (odśwież, odrzuć lub wyślij ponownie), albo wyślij listę ręcznie.'),
'family_auto_queued': ('Увімкнено автоматичне надсилання: цю зміну буде надіслано у фоні. Стан — у вікні «Надсилання».', 'Automatic sending is on: this change will be sent in the background. See its status in the Sending window.', 'Automatyczne wysyłanie jest włączone: ta zmiana zostanie wysłana w tle. Stan — w oknie Wysyłanie.'),
}
UPDATED = {
'family_sync_background': ('Надіслати цей пакет у фоні через будь-яку мережу, навіть після закриття застосунку. Вихід з акаунта зупиняє пакет.', 'Send this batch in the background over any network, even after closing the app. Signing out stops the batch.', 'Wyślij tę partię w tle przez dowolną sieć, także po zamknięciu aplikacji. Wylogowanie zatrzymuje partię.'),
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
