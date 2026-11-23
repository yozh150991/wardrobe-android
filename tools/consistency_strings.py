"""Text consistency for 0.14.61 (TXT-01…TXT-08): one voice and one set of names in Ukrainian, English and Polish.

Run once after an edit to strings.xml files; it is idempotent. The rules are checked by tools/check_consistency.py.

TXT-01  the default locale (values) is Ukrainian, like values-uk; 24 photo-editor strings were left in English there.
TXT-02  Ukrainian speaks to the user as «ти» everywhere (the app mostly did; ~90 strings used «Ви»).
TXT-03  no gendered verbs for the user: «Вдягнув сьогодні», «Знайшов товар» → «Вдягаю сьогодні», «Додати товар»;
        one verb for wearing (вдягати, not одягати), one label for wearing today, «вдягнено» for a recorded wear.
TXT-04  one name per thing: «образ» (not «комплект»), «outfit» (not «look»), «item» for a garment (not «piece»),
        «zestaw» (not «stylizacja»), «garderoba» (not «szafa»), and the tab «Хочу купити» / “Want to buy” /
        „Chcę kupić” instead of a wish list, shopping list or lista zakupów.
TXT-05  numbers never stand before a noun that must agree with them («1 гардеробів», «1 items»): «гардеробів: 1».
TXT-06  button names in running text are quoted in every language: «…» in Ukrainian, “…” in English, „…” in Polish,
        and the quoted name is the label the button really has.
TXT-07  British English throughout (colour, favourite, catalogue, recognised), typographic apostrophes (’).
TXT-08  wording fixes found on the way (the Polish title of «Що вдягнути сьогодні», email without a hyphen).
"""
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
LOCALES = [('values-uk', 0), ('values-en', 1), ('values-pl', 2)]

# Full new values; None keeps the language as it is (after the rules below).
UPDATED = {
# TXT-03: gender-neutral wear labels. The action is the one «Сьогодні» already had («Вдягаю сьогодні»), the same on
# the item page, the outfit and the widget; a recorded wear is «вдягнено» / “worn” / „noszone”.
'wear': ('Вдягаю сьогодні', 'Wearing it today', 'Zakładam dziś'),
'wear_outfit': ('Вдягаю сьогодні', 'Wearing it today', 'Zakładam dziś'),
'undo_outfit': ('Скасувати відмітку образу', 'Undo today’s wear', 'Cofnij dzisiejsze noszenie'),
'widget_wear': ('Вдягаю сьогодні', 'Wearing it today', 'Zakładam dziś'),
'widget_description': ('Запланований на сьогодні образ активного гардероба з кнопкою «Вдягаю сьогодні».',
    'Today’s planned outfit from the active wardrobe, with a “Wearing it today” button.',
    'Zaplanowany na dziś zestaw z aktywnej garderoby z przyciskiem „Zakładam dziś”.'),
'calendar_confirm_wear': ('Вдягнено', 'Worn', 'Noszone'),
'calendar_planned': ('Заплановано · ще не вдягнено', None, 'Zaplanowane · jeszcze nie noszone'),
'calendar_hint': ('Плануй тиждень наперед. Лічильник носіння змінюється лише після підтвердження «Вдягнено».',
    'Plan the week ahead. Wear counts change only after confirming “Worn”.',
    'Zaplanuj tydzień. Licznik użyć zmienia się dopiero po potwierdzeniu „Noszone”.'),
# On the phone «Знайдено товар» under «Шукаю» read as a state («already found»); the button adds the product it found.
'found_candidate': ('Додати товар', 'Add a product', 'Dodaj produkt'),
# The catalog card counted «Носінь», the item page and statistics «днів носіння»; a wear is one per day.
'wear_count': ('Днів носіння: %1$d', None, None),
'wish_kind_hint': ('Конкретний товар уже знайдено чи ще шукаєш потрібну річ?', None, None),
'i_bought_it': ('Уже куплено', None, None),
# Statistics on the phone: «Не носив 90+ днів», «Ще не носив». The 90-day list is «Давно не вдягали» everywhere else.
'statistics_forgotten': ('Давно не вдягали', 'Not worn for a while', 'Dawno nienoszone'),
'statistics_never': ('Ще не вдягали', None, None),
'family_read_counts': ('Ревізія %1$d · речей: %2$d · образів: %3$d · у «Хочу купити»: %4$d',
    'Revision %1$d · items: %2$d · outfits: %3$d · in “Want to buy”: %4$d',
    'Wersja %1$d · rzeczy: %2$d · zestawy: %3$d · w „Chcę kupić”: %4$d'),
'style_mixed': ('Стилі змішані: %1$s — речей: %2$d із %3$d', 'Mixed styles: %1$s — items: %2$d of %3$d',
    'Style mieszane: %1$s — rzeczy: %2$d z %3$d'),
'cloud_changed': ('Дані змінилися під час вибору дії. Онови синхронізацію й переглянь актуальні версії.', None, None),
'compatibility_owned_hint': ('Товар залишається в «Хочу купити». Його можна буде відмітити як вдягнений після покупки.',
    'The product stays in “Want to buy”. It can be marked as worn after purchase.',
    'Produkt pozostaje w „Chcę kupić”. Użycie oznaczysz po zakupie.'),
# TXT-04: «образ», not «комплект».
'match': ('Підібрати образ', None, None),
'outfit_builder': ('Твій образ', None, None),
'no_complete_outfit': ('Для повного образу бракує сумісних речей. Додай верх, низ або взуття, перевір сезон і доступність.', None, None),
'outfit_hint': ('Відмітка стосується всіх речей образу.', None, None),
'empty_outfits': ('Ще немає образів. Відкрий річ і натисни «Підібрати образ».',
    'No outfits yet. Open an item and tap “Build an outfit”.',
    'Brak zestawów. Otwórz rzecz i wybierz „Dobierz zestaw”.'),
# TXT-04: the tab name instead of a wish list or a shopping list.
'wardrobe_contents': ('Речей: %1$d, з них в архіві: %2$d. Образів: %3$d. У «Хочу купити»: %4$d.',
    'Items: %1$d, including %2$d archived. Outfits: %3$d. In “Want to buy”: %4$d.',
    'Rzeczy: %1$d, w tym w archiwum: %2$d. Zestawy: %3$d. W „Chcę kupić”: %4$d.'),
'delete_wardrobe_warning': ('Буде видалено цей гардероб, його речі (включно з архівними), фото, образи, історію носіння та «Хочу купити». Інші гардероби залишаться. Відновлення можливе лише зі збереженої ZIP-копії.',
    'This deletes this wardrobe, its items (including archived items), photos, outfits, wear history and “Want to buy”. Other wardrobes remain. Recovery requires a saved ZIP backup.',
    'Ta operacja usuwa tę garderobę, jej rzeczy (także archiwalne), zdjęcia, zestawy, historię noszenia i „Chcę kupić”. Inne garderoby pozostaną. Przywrócenie wymaga zapisanej kopii ZIP.'),
'empty_wishes': ('У «Хочу купити» поки нічого немає.', 'Nothing in “Want to buy” yet.', 'W „Chcę kupić” nie ma jeszcze nic.'),
'advice_owned_only': ('У цьому варіанті лише твої речі. Товар залишається в «Хочу купити».',
    'This version uses only your own items. The product stays in “Want to buy”.',
    'Ta wersja zawiera tylko własne rzeczy. Produkt pozostaje w „Chcę kupić”.'),
'advice_deferred': ('Товар залишено в «Хочу купити».', 'The product stays in “Want to buy”.', 'Produkt pozostaje w „Chcę kupić”.'),
'purchase_undo_done': (None, 'Purchase undone. The item is back in “Want to buy”.', 'Zakup cofnięty. Rzecz wróciła do „Chcę kupić”.'),
'purchase_undo_confirm': (None, 'The item will return to “Want to buy”. Purchase history and saved outfit contents will be kept.',
    'Rzecz wróci do „Chcę kupić”. Historia zakupu i skład zapisanych zestawów zostaną zachowane.'),
'purchase_undo_conflict': ('Річ уже змінено або позначено носіння. Повернення до «Хочу купити» скасовано, щоб зберегти ці дані.',
    'The item has been edited or worn. The move back to “Want to buy” was stopped to keep those changes.',
    'Rzecz została już zmieniona lub oznaczona jako noszona. Nie przeniesiono jej z powrotem do „Chcę kupić”, aby zachować te dane.'),
'unavailable_deleted': ('Запис у «Хочу купити» видалено.', 'The “Want to buy” entry was deleted.', 'Wpis w „Chcę kupić” został usunięty.'),
'item_delete_purchase': ('Її куплено з «Хочу купити», і запис про покупку посилається на неї.',
    'It was bought from “Want to buy”, and the purchase record refers to it.',
    'Została kupiona z „Chcę kupić” i zapis zakupu się do niej odnosi.'),
# TXT-04: the tab is «Гардероб» now, not «Каталог»; a wardrobe, not a collection.
'photo_reprocess_finished': ('Чергу завершено. Нові фото можна вибрати на вкладці «Гардероб».',
    'Queue complete. Select more photos in the “Wardrobe” tab.',
    'Kolejka zakończona. Kolejne zdjęcia wybierzesz na karcie „Garderoba”.'),
'daily_rain_hint': ('Перевір водостійкість взуття й верхнього одягу: у картці речі цих властивостей немає.',
    'Check that shoes and outerwear resist rain; item details do not record these properties.',
    'Sprawdź odporność butów i okrycia na deszcz; karta rzeczy nie zapisuje tych cech.'),
'rotation_archive_text': ('В архіві річ не пропонується в образах і не показується на вкладці «Гардероб». Повернути її можна будь-коли.',
    'An archived item isn’t suggested in outfits or shown in the “Wardrobe” tab. You can bring it back at any time.',
    'Rzecz w archiwum nie jest proponowana w zestawach ani pokazywana na karcie „Garderoba”. Można ją przywrócić w każdej chwili.'),
'shop_review_hint': ('Нічого не додається до гардероба до натискання «Зберегти» в редакторі.',
    'Nothing is added to your wardrobe until you tap “Save” in the editor.',
    'Nic nie zostanie dodane do garderoby, dopóki nie wybierzesz „Zapisz” w edytorze.'),
'daily_no_replacement': (None, 'No other available item can replace this one. Check laundry, season or try another outfit.', None),
# TXT-05: the noun before the number.
'owned_subtitle': (None, 'Items: %1$d · everything you already own', None),
'capsule_progress': ('Спаковано: %1$d із %2$d', 'Packed: %1$d of %2$d', 'Spakowano: %1$d z %2$d'),
'cloud_local_counts': ('На цьому пристрої — гардеробів: %1$d, речей: %2$d, образів: %3$d',
    'This device — wardrobes: %1$d, items: %2$d, outfits: %3$d',
    'To urządzenie — garderoby: %1$d, rzeczy: %2$d, zestawy: %3$d'),
'cloud_remote_counts': ('У хмарі — гардеробів: %1$d, речей: %2$d, образів: %3$d',
    'Cloud — wardrobes: %1$d, items: %2$d, outfits: %3$d',
    'Chmura — garderoby: %1$d, rzeczy: %2$d, zestawy: %3$d'),
'family_sync_confirm': ('Надіслати підготовлені зміни (%1$d) у вибраному режимі? Під час зупинки вже прийняті сервером зміни не скасовуються. Після втрати відповіді повтор перевіряє результат того самого запиту.',
    'Send the prepared changes (%1$d) in the selected mode? Stopping does not undo changes already accepted by the server. After a lost response, retry checks the same request.',
    'Wysłać przygotowane zmiany (%1$d) w wybranym trybie? Zatrzymanie nie cofa zmian już przyjętych przez serwer. Po utracie odpowiedzi ponowienie sprawdza to samo żądanie.'),
'trip_suggest_add': ('Додати речей: %1$d — тоді днів з образом: %2$d із %3$d, з повторним використанням речей:',
    'Add %1$d more — then days with an outfit: %2$d of %3$d, reusing items:',
    'Dodaj rzeczy: %1$d — wtedy dni z zestawem: %2$d z %3$d, z ponownym użyciem rzeczy:'),
'trip_suggest_enough': ('Додавати нічого не треба: речей у капсулі вистачає — днів з образом: %2$d із %3$d.',
    'Nothing to add: the capsule’s items are enough — days with an outfit: %2$d of %3$d.',
    'Nie trzeba nic dodawać: rzeczy w kapsule wystarczą — dni z zestawem: %2$d z %3$d.'),
'prefs_warmth_default': (None, 'As forecast. After %1$d “Too warm” answers it becomes one step lighter automatically.', None),
# TXT-06: quoted, real button names.
'auth_wrong_account': (None, 'Use the same account, or choose “Switch account”.', 'Użyj tego samego konta lub wybierz „Zmień konto”.'),
'photo_edit_notice': (None, 'Apply the edit, then tap “Save” in the item form. The previous photo will be replaced without keeping an original.',
    'Zastosuj zmiany, a następnie wybierz „Zapisz” w formularzu rzeczy. Poprzednie zdjęcie zostanie zastąpione bez zachowania oryginału.'),
'photo_save_hint': (None, 'You can correct the fields manually. To keep changes, tap “Save” at the bottom of the item form.',
    'Pola możesz poprawić ręcznie. Aby zachować zmiany, wybierz „Zapisz” na dole formularza rzeczy.'),
'photo_model_requested': ('Модель ще може завантажуватися. Залиш інтернет увімкненим і за хвилину спробуй ще раз.',
    'The model may still be downloading. Keep your connection on and try again in a minute.',
    'Model może być nadal pobierany. Pozostaw połączenie włączone i spróbuj ponownie za minutę.'),
'mark_laundry': (None, 'Mark “In the wash”', 'Oznacz „W praniu”'),
'capsule_reset_hint': (None, 'Clear all “packed” checks? Items and outfits remain.', 'Usunąć wszystkie oznaczenia „spakowane”? Rzeczy i zestawy pozostaną.'),
'mask_hint': (None, None, None),  # handled by QUOTE_LABELS below
'look_select_hint': (None, 'Tap an item for replacement, pinning or details. Open “Composition” to move it.',
    'Dotknij rzeczy, aby ją zastąpić, przypiąć lub zobaczyć szczegóły. Otwórz „Kompozycja”, aby ją przesunąć.'),
'canvas_reset_confirm': (None, 'Reset all item positions? You can undo this with “Undo last change”.',
    'Zresetować położenie wszystkich rzeczy? Możesz to cofnąć przyciskiem „Cofnij ostatnią zmianę”.'),
'cloud_conflict_hint': ('Автоматична заміна призупинена. Обери, які дані зберегти. «Зберегти обидві версії» додасть хмарні гардероби окремо.',
    'Automatic replacement is paused. Choose what to keep. “Keep both versions” adds the cloud wardrobes separately.',
    'Automatyczna zamiana jest wstrzymana. Wybierz dane do zachowania. „Zachowaj obie wersje” doda garderoby z chmury osobno.'),
'photo_save_category': (None, 'Choose a category to save. Tap “Save” to go to the field.', 'Wybierz kategorię, aby zapisać. Naciśnij „Zapisz”, aby przejść do pola.'),
'photo_history_geometry_hint': (None, '“Undo” and “Redo” cover cropping and rotation. Switching to background applies these edits and starts a new history.',
    '„Cofnij” i „Ponów” obejmują kadrowanie i obrót. Przejście do tła stosuje te zmiany i rozpoczyna nową historię.'),
'photo_history_unified_hint': ('«Назад» і «Вперед» працюють для всіх мазків, обрізань і поворотів до завершення редагування.',
    '“Undo” and “Redo” cover every stroke, crop and rotation until you finish editing.',
    '„Cofnij” i „Ponów” obejmują wszystkie pociągnięcia, kadrowania i obroty aż do zakończenia edycji.'),
'photo_crop_commit_hint': (None, 'Release a corner or slider to apply the crop. You can undo it with “Undo”.',
    'Puść narożnik lub suwak, aby zastosować kadrowanie. Możesz je cofnąć przyciskiem „Cofnij”.'),
'photo_region_pending': (None, 'Confirm removal or clear the selection before applying the photo. “Undo” can restore the removed area.',
    'Potwierdź usunięcie lub anuluj zaznaczenie przed zastosowaniem zdjęcia. „Cofnij” przywraca usunięty obszar.'),
'family_read_changed': (None, 'The server copy changed during download. Tap “Refresh from server”.',
    'Kopia na serwerze zmieniła się podczas pobierania. Wybierz „Odśwież z serwera”.'),
'family_edit_no_draft': (None, 'No pending edit. Open an item and choose “Edit fields”.', 'Brak niezakończonej edycji. Otwórz rzecz i wybierz „Edytuj pola”.'),
'family_auto_queued': ('Увімкнено автоматичне надсилання: цю зміну буде надіслано у фоні. Стан — у вікні «Надсилання спільних змін».',
    'Automatic sending is on: this change will be sent in the background. See its status in “Send shared changes”.',
    'Automatyczne wysyłanie jest włączone: ta zmiana zostanie wysłana w tle. Stan — w oknie „Wysyłanie wspólnych zmian”.'),
# TXT-08 and the gendered «ви обрали».
'prefs_trends_hint': ('Трохи частіше пропонує пари кольорів із вибраних тобою дописів і кольори сезону: %1$s. Оцінка образу не змінюється.', None, None),
'prefs_rotation_hint': (None, None, 'Karta w „Co dziś założyć” z rzeczą nienoszoną od 90+ dni. Rzecz, którą już pokazała, przez następny tydzień ustępuje innym.'),
# TXT-04: «zestaw», not «stylizacja».
'category_base_hint': (None, None, 'Wybierz typ, do którego należy kategoria, aby prawidłowo dobierać zestawy.'),
'family_confirm': (None, None, 'Utworzyć kopię tej garderoby na serwerze z rzeczami, zestawami, historią i gotowymi zdjęciami? Inne garderoby nie zostaną skopiowane. Dane lokalne pozostaną na urządzeniu.'),
'family_look_title': (None, None, 'Wspólny zestaw'),
'family_look_open': (None, None, 'Utwórz zestaw / wznów szkic'),
'family_look_edit': (None, None, 'Edytuj zestaw'),
'family_look_hint': (None, None, 'Zestaw zostanie zapisany we wspólnej garderobie po potwierdzeniu wysłania.'),
'family_look_draft_hint': (None, None, 'Jeden szkic zestawu na wspólną garderobę. Niedokończony szkic otwiera się pierwszy. Zapis przygotowuje zmiany do wysłania.'),
'family_look_synced': (None, None, 'Wspólny zestaw został zapisany.'),
'family_look_send': (None, None, 'Wyślij zestaw'),
'family_look_send_confirm': (None, None, 'Zapisać ten zestaw we wspólnej garderobie? Zmiany będą widoczne dla jej uczestników.'),
'family_look_rebase_confirm': (None, None, 'Pobrać aktualną garderobę i zastosować szkic? Wysłanie zastąpi bieżącą wersję tego zestawu. Inne zestawy zostaną zachowane.'),
'family_look_missing': (None, None, 'Zestaw lub wybrana rzecz zostały usunięte. Otwórz aktualną garderobę i sprawdź skład zestawu.'),
'family_look_validation': (None, None, 'Nie udało się przygotować zestawu. Sprawdź nazwę, dostępność rzeczy i zgodność sezonów. Szkic zachowano.'),
'family_look_add_another': (None, None, 'Przygotuj kolejny zestaw'),
'family_look_resume_form': (None, None, 'Wznów niedokończony zestaw'),
'family_look_queue_hint': (None, None, 'Do 10 przygotowanych zestawów i 512 MB archiwów na garderobę. Wysyłaj je po kolei; jawnie odśwież kolejny szkic po zmianie wersji na serwerze. Jeden niedokończony formularz edytora jest zachowywany osobno od kolejki.'),
'family_sync_hint': (None, None, 'Potwierdź listę zapisanych zmian. Nowe szkice nie są dodawane do tej partii. Zmiany są wysyłane kolejno. Nowe rzeczy, zdjęcia i zestawy są stosowane do najnowszej wersji; dane rzeczy są aktualizowane automatycznie po własnych zmianach tej partii. Szkic przygotowany przed aktualizacją serwera na starszej wersji pozostaje w kolejce do odświeżenia. Zmiana innej osoby w tym samym zestawie lub przed edycją danych rzeczy wstrzymuje partię do sprawdzenia. Niedokończone formularze nie są wysyłane.'),
'family_sync_outfit': (None, None, 'Zestaw'),
'family_sync_partial': (None, None, 'Część zmian została wysłana. Dodania, zdjęcia lub zestawy przygotowane na poprzedniej wersji pozostały w kolejce: odśwież je w ich edytorze i potwierdź nową partię.'),
'account_delete_intro': (None, None, 'Konto %1$s zostanie trwale usunięte: jego kopia w chmurze, wszystkie rzeczy, zdjęcia, zestawy i historia tego konta na tym telefonie oraz niewysłane zmiany wspólnych garderób.'),
'prefs_colours_hint': (None, None, 'Bez analizy zdjęć: zaznacz kolory, które lubisz nosić, i te, które wolisz nosić rzadziej. Rzeczy w tych kolorach pojawiają się w doborze trochę częściej lub rzadziej — żadna nie znika. Ocena zestawu na 10 się nie zmienia: wynika z zasad łączenia kolorów.'),
'item_stat_outfits': (None, None, 'w zestawach'),
}
UPDATED['prefs_trends_hint'] = (UPDATED['prefs_trends_hint'][0], None,
    'Nieco częściej proponuje pary kolorów z wybranych przez Ciebie postów i kolory sezonu: %1$s. Ocena zestawu się nie zmienia.')
del UPDATED['mask_hint']
# TXT-06: keys whose English/Polish only need a button name quoted.
QUOTE_LABELS = {
    'mask_hint': [(1, 'Restore brings back', '“Restore” brings back'), (2, 'Przywracanie odzyskuje', '„Przywróć” odzyskuje')],
    'photo_history_mask_hint': [(1, 'Redo restores', '“Redo” restores'), (2, 'Ponów przywraca', '„Ponów” przywraca')],
    'family_edit_keep_confirm': [(1, 'review it, then send changes.', 'review it, then tap “Send changes”.'),
                                 (2, 'sprawdź go i wyślij zmiany.', 'sprawdź go i wybierz „Wyślij zmiany”.')],
}

W_UK = '[А-Яа-яЇїІіЄєҐґ’\']'
# TXT-02: «Ви» → «ти». Only whole words; every listed form was found in the strings.
INFORMAL = {
 'додайте': 'додай', 'перевірте': 'перевір', 'спробуйте': 'спробуй', 'закрийте': 'закрий', 'заповніть': 'заповни',
 'перейдіть': 'перейди', 'оберіть': 'обери', 'перегляньте': 'переглянь', 'завершіть': 'заверши', 'відкрийте': 'відкрий',
 'скопіюйте': 'скопіюй', 'змініть': 'зміни', 'виберіть': 'вибери', 'зробіть': 'зроби', 'завантажте': 'завантаж',
 'повторіть': 'повтори', 'залиште': 'залиш', 'збережіть': 'збережи', 'натисніть': 'натисни', 'розведіть': 'розведи',
 'пересувайте': 'пересувай', 'вмикайте': 'вмикай', 'перевіряйте': 'перевіряй', 'замініть': 'заміни', 'збільшіть': 'збільш',
 'скасуйте': 'скасуй', 'відпустіть': 'відпусти', 'торкніться': 'торкнись', 'скористайтеся': 'скористайся', 'зменште': 'зменш',
 'виправте': 'виправ', 'підтвердьте': 'підтверди', 'рухайте': 'рухай', 'пропустіть': 'пропусти', 'перевідкрийте': 'перевідкрий',
 'звузьте': 'звузь', 'пом’якшіть': 'пом’якши', 'застосуйте': 'застосуй', 'очистьте': 'очисть', 'продовжте': 'продовж',
 'відкиньте': 'відкинь', 'введіть': 'введи', 'оновіть': 'онови', 'увійдіть': 'увійди', 'позначте': 'познач',
 'любите': 'любиш', 'носите': 'носиш', 'їдете': 'їдеш', 'залишитеся': 'залишишся', 'станете': 'станеш',
 'ваш': 'твій', 'ваша': 'твоя', 'ваше': 'твоє', 'ваші': 'твої', 'вашу': 'твою', 'вашого': 'твого', 'вашої': 'твоєї',
 'вашому': 'твоєму', 'вашим': 'твоїм', 'вашими': 'твоїми', 'ваших': 'твоїх', 'ви': 'ти', 'вас': 'тебе', 'вам': 'тобі',
}
INFORMAL_RE = re.compile(rf'(?<!{W_UK})(' + '|'.join(sorted(map(re.escape, INFORMAL), key=len, reverse=True)) + rf')(?!{W_UK})', re.I)

# TXT-04: English garment and outfit words, only in the strings where they mean that (not «a piece of background»).
LOOK_KEYS = {'daily_hint', 'daily_saved_name', 'daily_rotation', 'daily_no_replacement', 'calendar_add', 'calendar_empty',
    'open_outfit', 'statistics_build', 'capsule_hint', 'capsule_empty', 'capsule_outfits', 'capsule_unique_hint',
    'capsule_planned_warning', 'capsule_no_items', 'capsule_no_owned_outfits', 'capsule_reset_hint', 'capsule_remove_hint',
    'compatibility_empty', 'compatibility_save', 'daily_no_outerwear', 'wish_category_in_outfits'}
PIECE_KEYS = {'remove_missing_pieces', 'wishlist_hint', 'photo_privacy', 'no_complete_outfit', 'find_item', 'empty_laundry',
    'empty_active', 'undo_availability_conflict', 'look_source', 'look_inspiration_hint', 'look_source_removed',
    'look_collage_hint', 'replace_locked_hint', 'manual_picker_hint', 'look_no_candidates', 'refresh_unlocked',
    'look_inspiration_reason', 'look_match_reason', 'look_unavailable_hint', 'planned_wear_hint', 'daily_hint',
    'daily_no_replacement', 'flex_locked_conflict', 'canvas_duplicate_title', 'canvas_duplicate_hint',
    'daily_empty_availability', 'look_existing_opened', 'look_contents_mode', 'picker_parts', 'calendar_actual_composition',
    'advice_replacement_scope', 'widget_ideas'}
SZAFA = {'szafa': 'garderoba', 'szafy': 'garderoby', 'szafie': 'garderobie', 'szafę': 'garderobę', 'szafą': 'garderobą',
         'szaf': 'garderób', 'szafom': 'garderobom', 'szafami': 'garderobami', 'szafach': 'garderobach'}
W_PL = '[A-Za-ząćęłńóśźżĄĆĘŁŃÓŚŹŻ]'
SZAFA_RE = re.compile(rf'(?<!{W_PL})(' + '|'.join(sorted(SZAFA, key=len, reverse=True)) + rf')(?!{W_PL})', re.I)
SZAFA_KEEP = {'location_hint'}  # «Szafa → druga półka» is a real cupboard


def keep_case(word, new):
    return new[0].upper() + new[1:] if word[0].isupper() else new


def rule(key, value, column):
    if column == 0:
        value = INFORMAL_RE.sub(lambda m: keep_case(m.group(1), INFORMAL[m.group(1).lower()]), value)
        value = value.replace("'", '’')
    elif column == 1:
        if key in LOOK_KEYS:
            value = re.sub(r'\b([Aa]) look\b', r'\1n outfit', value)
            value = re.sub(r'\blook(s?)\b', r'outfit\1', value); value = re.sub(r'\bLook(s?)\b', r'Outfit\1', value)
        if key in PIECE_KEYS:
            value = re.sub(r'\b([Aa]) piece\b', r'\1n item', value)
            value = re.sub(r'\bpiece(s?)\b', r'item\1', value); value = re.sub(r'\bPiece(s?)\b', r'Item\1', value)
        value = re.sub(r'\b([Cc])olor', r'\1olour', value)
        value = re.sub(r'\b([Ff])avorite', r'\1avourite', value)
        value = re.sub(r'\b([Cc])atalog\b', r'\1atalogue', value)
        value = re.sub(r'([Rr])ecogniz', r'\1ecognis', value)
        value = re.sub(r'«([^«»]*)»', r'“\1”', value)
        value = value.replace("'", '’')
    else:
        if key not in SZAFA_KEEP:
            value = SZAFA_RE.sub(lambda m: keep_case(m.group(1), SZAFA[m.group(1).lower()]), value)
        value = re.sub(r'\be-mail', 'email', value)
        value = re.sub(r'«([^«»]*)»', r'„\1”', value)
    for edit_column, old, new in QUOTE_LABELS.get(key, []):
        if edit_column == column and old in value:
            value = value.replace(old, new)
    if key in UPDATED and UPDATED[key][column] is not None:
        value = UPDATED[key][column]
    return value


def decode(raw):
    return (raw.replace("\\'", "'").replace('\\"', '"').replace('&lt;', '<').replace('&gt;', '>').replace('&amp;', '&'))


def encode(value):
    return escape(value).replace("'", "\\'").replace('"', '\\"')


ENTRY = re.compile(r'(<string name="(\w+)"[^>]*>)(.*?)(</string>)', re.S)


def main():
    changed = 0
    for folder, column in LOCALES:
        path = ROOT / folder / 'strings.xml'; text = path.read_text(encoding='utf-8')
        known = set(ENTRY_NAME.findall(text))
        missing = [k for k in UPDATED if k not in known]
        assert not missing, (folder, missing)

        def apply(m):
            nonlocal changed
            new = encode(rule(m.group(2), decode(m.group(3)), column))
            if new != m.group(3): changed += 1
            return m.group(1) + new + m.group(4)
        path.write_text(ENTRY.sub(apply, text), encoding='utf-8')
    # TXT-01: the default locale is the Ukrainian one, string for string.
    (ROOT / 'values' / 'strings.xml').write_text((ROOT / 'values-uk' / 'strings.xml').read_text(encoding='utf-8'), encoding='utf-8')
    print(f'consistency strings: {changed} values changed; default locale = values-uk')


ENTRY_NAME = re.compile(r'<string name="(\w+)"')

if __name__ == '__main__':
    main()
