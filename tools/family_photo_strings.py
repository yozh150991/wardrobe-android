"""FAM-03 shared photo replacement (0.14.10) and new shared items (0.14.11): Ukrainian / English / Polish.
Run directly to append missing keys to the four resource sets (make_strings.py is stale)."""
STRINGS = {
'family_photo_open': ('Замінити фото', 'Replace photo', 'Zmień zdjęcie'),
'family_photo_title': ('Заміна фото спільної речі', 'Replace photo of a shared item', 'Zmiana zdjęcia wspólnej rzeczy'),
'family_photo_hint': ('Нове фото спершу зберігається як чернетка на цьому пристрої й надсилається окремим підтвердженням. Інші речі та фото спільного гардероба не змінюються.', 'The new photo is first saved as a draft on this device and is sent only after a separate confirmation. Other items and photos of the shared wardrobe stay unchanged.', 'Nowe zdjęcie najpierw zapisuje się jako szkic na tym urządzeniu i jest wysyłane dopiero po osobnym potwierdzeniu. Inne rzeczy i zdjęcia wspólnej garderoby pozostają bez zmian.'),
'family_photo_item': ('Річ: %1$s', 'Item: %1$s', 'Rzecz: %1$s'),
'family_photo_current': ('Зараз на сервері', 'Currently on the server', 'Obecnie na serwerze'),
'family_photo_new': ('Нове фото (чернетка)', 'New photo (draft)', 'Nowe zdjęcie (szkic)'),
'family_photo_import_note': ('Фото з галереї чи камери додається як є — без вирізання фону. Щоб надіслати оброблене фото, спершу збережи його у своїй речі й вибери «Фото з моєї речі».', 'A gallery or camera photo is added as is, without background removal. To send a processed photo, save it in your own item first and choose “Photo from my item”.', 'Zdjęcie z galerii lub aparatu jest dodawane bez zmian, bez usuwania tła. Aby wysłać obrobione zdjęcie, najpierw zapisz je we własnej rzeczy i wybierz „Zdjęcie z mojej rzeczy”.'),
'family_photo_from_item': ('Фото з моєї речі', 'Photo from my item', 'Zdjęcie z mojej rzeczy'),
'family_photo_choose': ('Вибери свою річ — візьметься її готове фото. Сама річ не зміниться.', 'Choose one of your items: its final photo is used. The item itself does not change.', 'Wybierz swoją rzecz: zostanie użyte jej gotowe zdjęcie. Sama rzecz się nie zmieni.'),
'family_photo_saved': ('Нове фото збережено на цьому пристрої. Воно ще не надіслане. Закриття вікна зберігає чернетку; одна незавершена заміна фото на спільний гардероб.', 'The new photo is saved on this device and has not been sent yet. Closing keeps the draft; one unfinished photo replacement per shared wardrobe.', 'Nowe zdjęcie zapisano na tym urządzeniu i jeszcze nie wysłano. Zamknięcie zachowuje szkic; jedna niedokończona zmiana zdjęcia na wspólną garderobę.'),
'family_photo_other_draft': ('Уже є незавершена заміна фото для «%1$s». Надішли або видали її, щоб почати іншу.', 'There is already an unfinished photo replacement for “%1$s”. Send or delete it to start another one.', 'Istnieje już niedokończona zmiana zdjęcia dla „%1$s”. Wyślij ją lub usuń, aby zacząć kolejną.'),
'family_photo_send': ('Замінити фото в спільному гардеробі', 'Replace photo in the shared wardrobe', 'Zmień zdjęcie we wspólnej garderobie'),
'family_photo_send_confirm': ('Замінити фото цієї речі для всіх учасників? Старе фото зникне зі спільної копії, якщо більше ніде не використовується.', 'Replace this item’s photo for all members? The old photo leaves the shared copy if nothing else uses it.', 'Zmienić zdjęcie tej rzeczy dla wszystkich uczestników? Stare zdjęcie zniknie ze wspólnej kopii, jeśli nic innego go nie używa.'),
'family_photo_synced': ('Сервер підтвердив нове фото.', 'The server confirmed the new photo.', 'Serwer potwierdził nowe zdjęcie.'),
'family_photo_conflict': ('Спільний гардероб змінився. Нове фото збережене локально. Онови основу чернетки й надішли ще раз.', 'The shared wardrobe has changed. The new photo is kept locally. Update the draft base and send it again.', 'Wspólna garderoba się zmieniła. Nowe zdjęcie jest zapisane lokalnie. Zaktualizuj podstawę szkicu i wyślij ponownie.'),
'family_new_open': ('Нова річ із фото (галерея/камера)', 'New item with photo (gallery/camera)', 'Nowa rzecz ze zdjęciem (galeria/aparat)'),
'family_new_hint': ('Заповни поля й вибери фото з галереї або зніми камерою. Фото додається як є, без вирізання фону. Річ одразу готується як чернетка для спільного гардероба; у твій приватний гардероб вона не потрапляє.', 'Fill in the fields and pick a gallery photo or take one with the camera. The photo is added as is, without background removal. The item is prepared as a draft for the shared wardrobe and is not added to your private wardrobe.', 'Uzupełnij pola i wybierz zdjęcie z galerii lub zrób je aparatem. Zdjęcie jest dodawane bez zmian, bez usuwania tła. Rzecz jest przygotowywana jako szkic dla wspólnej garderoby i nie trafia do Twojej prywatnej garderoby.'),
'family_new_name_first': ('Спершу введи назву (до 120 символів), потім вибери фото.', 'Enter a name first (up to 120 characters), then choose a photo.', 'Najpierw wpisz nazwę (do 120 znaków), potem wybierz zdjęcie.'),
'family_photo_rebase_confirm': ('Завантажити актуальну версію гардероба й застосувати до неї це саме нове фото? Надсилання — окремим кроком.', 'Download the current wardrobe and apply the same new photo to it? Sending is a separate step.', 'Pobrać aktualną garderobę i zastosować do niej to samo nowe zdjęcie? Wysłanie to osobny krok.'),
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
    print(f'{len(STRINGS)} family photo strings present in UA/EN/PL and fallback.')
