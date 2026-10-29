"""Labels for 0.14.25 FAM-04: ownership transfer and full account deletion.

New keys are appended; keys listed in UPDATED replace an existing value in every language.
"""
STRINGS = {
'family_transfer': ('Передати власність', 'Transfer ownership', 'Przekaż własność'),
'family_transfer_hint': ('Власність можна передати лише редакторові. Новий власник керуватиме учасниками й зберігатиме копію; ви залишитеся редактором.',
    'Ownership can be transferred only to an editor. The new owner manages members and saves copies; you stay an editor.',
    'Własność można przekazać tylko edytorowi. Nowy właściciel zarządza członkami i zapisuje kopie; Ty pozostajesz edytorem.'),
'family_transfer_confirm': ('Передати власність «%1$s» користувачу %2$s? Ви станете редактором. Керувати учасниками й зберігати копію зможе лише новий власник. Скасувати це може тільки новий власник, передавши власність назад.',
    'Transfer ownership of “%1$s” to %2$s? You become an editor. Only the new owner can manage members and save copies. Only the new owner can undo this by transferring it back.',
    'Przekazać własność „%1$s” użytkownikowi %2$s? Staniesz się edytorem. Tylko nowy właściciel będzie mógł zarządzać członkami i zapisywać kopie. Cofnąć to może tylko nowy właściciel, przekazując własność z powrotem.'),
'family_owner_transferred': ('Власність передано. Тепер ви редактор цього гардероба.', 'Ownership transferred. You are now an editor of this wardrobe.',
    'Własność przekazana. Jesteś teraz edytorem tej szafy.'),
'account_delete': ('Видалити акаунт', 'Delete account', 'Usuń konto'),
'account_delete_title': ('Видалення акаунта', 'Delete account', 'Usuwanie konta'),
'account_delete_intro': ('Буде остаточно видалено акаунт %1$s: його копію в хмарі, усі речі, фото, образи й історію цього акаунта на цьому телефоні, а також ще не надіслані зміни спільних гардеробів.',
    'The account %1$s will be deleted for good: its cloud copy, all items, photos, outfits and history of this account on this phone, and unsent changes to shared wardrobes.',
    'Konto %1$s zostanie trwale usunięte: jego kopia w chmurze, wszystkie rzeczy, zdjęcia, stylizacje i historia tego konta na tym telefonie oraz niewysłane zmiany wspólnych szaf.'),
'account_delete_keeps': ('Збережені раніше ZIP-копії у вибраних тобою папках залишаться. Інші акаунти на цьому телефоні не зміняться.',
    'ZIP copies you saved earlier in folders you chose stay. Other accounts on this phone are not changed.',
    'Zapisane wcześniej kopie ZIP w wybranych przez Ciebie folderach pozostaną. Inne konta na tym telefonie nie zmienią się.'),
'account_delete_owned': ('Твої спільні гардероби', 'Your shared wardrobes', 'Twoje wspólne szafy'),
'account_delete_no_owned': ('Спільних гардеробів, якими ти володієш, немає.', 'You own no shared wardrobes.', 'Nie jesteś właścicielem żadnej wspólnej szafy.'),
'account_delete_no_editors': ('Редакторів немає — гардероб буде видалено для всіх учасників.', 'No editors: the wardrobe will be deleted for all members.',
    'Brak edytorów: szafa zostanie usunięta dla wszystkich członków.'),
'account_delete_single': ('Новим власником стане єдиний редактор: %1$s.', 'The only editor becomes the new owner: %1$s.', 'Nowym właścicielem zostanie jedyny edytor: %1$s.'),
'account_delete_choose': ('Обери нового власника серед редакторів:', 'Choose the new owner among the editors:', 'Wybierz nowego właściciela spośród edytorów:'),
'account_delete_choose_all': ('Обери нового власника для кожного гардероба з кількома редакторами.', 'Choose the new owner of every wardrobe with several editors.',
    'Wybierz nowego właściciela każdej szafy z kilkoma edytorami.'),
'account_delete_memberships': ('Ти вийдеш зі спільних гардеробів інших власників: %1$d.', 'You will leave shared wardrobes of other owners: %1$d.',
    'Opuścisz wspólne szafy innych właścicieli: %1$d.'),
'account_delete_understood': ('Я розумію, що видалення остаточне й не скасовується.', 'I understand that deletion is final and cannot be undone.',
    'Rozumiem, że usunięcie jest ostateczne i nie można go cofnąć.'),
'account_delete_action': ('Видалити назавжди', 'Delete forever', 'Usuń na zawsze'),
'account_deleted': ('Акаунт видалено разом з його даними на цьому телефоні.', 'The account was deleted together with its data on this phone.',
    'Konto zostało usunięte razem z jego danymi na tym telefonie.'),
'account_delete_wrong_password': ('Неправильний пароль.', 'Wrong password.', 'Nieprawidłowe hasło.'),
'account_delete_reauth': ('Сервер не прийняв підтвердження входу. Введи пароль ще раз.', 'The server did not accept the sign-in confirmation. Enter the password again.',
    'Serwer nie przyjął potwierdzenia logowania. Wpisz hasło ponownie.'),
'account_delete_changed': ('Учасники спільних гардеробів змінилися. Перевір нового власника й повтори.', 'Members of a shared wardrobe changed. Check the new owner and try again.',
    'Członkowie wspólnej szafy zmienili się. Sprawdź nowego właściciela i spróbuj ponownie.'),
'account_delete_email': ('Спочатку підтвердь email посиланням із листа.', 'First confirm your email with the link from the message.',
    'Najpierw potwierdź email linkiem z wiadomości.'),
'account_delete_config': ('Сервер ще не підтримує видалення акаунта. Потрібне оновлення серверної частини.', 'The server does not support account deletion yet. The server part needs an update.',
    'Serwer nie obsługuje jeszcze usuwania konta. Potrzebna jest aktualizacja części serwerowej.'),
'account_delete_network': ('Не вдалося отримати відповідь сервера. Видалення могло вже початися: повтори, коли з’явиться мережа.',
    'No answer from the server. Deletion may have started already: try again when the network is back.',
    'Brak odpowiedzi serwera. Usuwanie mogło się już rozpocząć: spróbuj ponownie, gdy wróci sieć.'),
'account_delete_retry': ('Не вдалося видалити акаунт. Спробуй ще раз.', 'The account could not be deleted. Try again.', 'Nie udało się usunąć konta. Spróbuj ponownie.'),
}
UPDATED = {}
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
