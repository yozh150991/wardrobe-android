# FAM-03 — спільне редагування полів речі

0.14.7-family-edit, versionCode 37, Room/backup 10. Codex, 19.10.2026.
База: 0.14.6-family-access. Перший завершений у коді обсяг shared editing;
FAM-03 залишається partial, цей обсяг — verify. Багфікси відкладені користувачем.

## Сценарій

Налаштування → Сімейні гардероби та запрошення → гардероб → Переглянути
серверну копію → Редагувати поля. Owner/editor можуть змінювати назву,
розмір, місце та опис наявної речі. Viewer не отримує редактор, а сервер
відхиляє його запит незалежно від UI. Фото й інші сутності зберігаються.

«Зберегти чернетку» записує правку лише на пристрої. Її можна відкрити
кнопкою «Відкрити незавершену правку», зокрема після перезапуску. На один
гардероб — одна незавершена правка, до 100 гардеробів у локальному outbox.
Якщо відкрити іншу річ за наявності чернетки, відкриється існуюча правка;
її назва показана в редакторі. Незбережені у чернетку поля мають підтвердження
при закритті редактора. Вони не обіцяють збереження після аварійного завершення.

«Надіслати зміни» публікує збережену правку. До запиту на диск записується
точний payload/UUID і стан submitted. Після timeout поля блокуються, повтор
надсилає той самий запит. Видалення невизначеного submitted-запиту заблоковане
до відповіді сервера: локальний discard не є скасуванням server commit.
Підтверджені invalid/missing/quota rejection розблоковують чернетку з новим UUID.

Якщо ревізія змінилася, поточний payload не публікується. «Порівняти з сервером»
завантажує свіжу копію, показує локальні та серверні значення змінених полів.
Можна видалити локальну чернетку або явно перенести тільки свої змінені поля
на нову ревізію. Це створює нову чернетку/UUID, яку потрібно окремо надіслати.
Решта полів/сутностей залишається із сервера. Новий конкурентний запис знову
спричинить конфлікт. Автоматичного перезапису чи фонового надсилання немає.

## Дані та доступ

`family_edit_item`: remoteId, editId, garmentId, baseRevision, changes.
Дозволені лише name≤120, size≤40, location≤200, description≤1000 UTF-16 units;
непорожня назва, строгі типи/UUID/цілочисельна ревізія. Немає довільних JSON
paths, зміни ownership, photoId, ціни, статусів чи інших полів.

Backend читає поточний immutable ZIP, перевіряє hash/envelope/relations/photos,
змінює лише запитані поля та повторно перевіряє весь snapshot. Новий ZIP
детермінований і пишеться create-if-absent; фото зберігають точні bytes.
Публікація head — транзакція з повторною role/expiry/CAS перевіркою. Старий
immutable orphan після програного CAS не відкривається читачам.

`familyWardrobes/{remote}/edits/{editId}` зберігає request binding, pending/committed
та receipt. Replay повертає той самий receipt навіть після новішої ревізії,
але щоразу перевіряє актуальний edit-доступ. UUID не можна використати з іншим
payload/актором. Новий edit використовує чинну квоту 120 uploads/добу на UID;
retry не збільшує лічильник. Політика GC для журналів/orphans лишається rollout-задачею.
Сервіс має той самий ZIP-size/resource envelope, що й existing family commit.

Android outbox — AtomicFile в noBackupFilesDir/family-edits/{accountKey}.
Це metadata-only записи, окремі від private Room, photo store і backup/cloud.
Локальні updates/removal перевіряють попередній draft; один lock на шлях
захищає кілька store instances. Пошкоджений журнал не замінюється порожнім.
На відкритті потрібні online verified auth і роль owner/editor. UI ховає поля
у background та після runtime lease до 15 хвилин. Save чернетки офлайн можливий
лише в межах уже перевіреної сесії. Sign-out прибирає state/lease; outbox
зберігається для того самого акаунта, не відкривається без повторної авторизації.
Revoke блокує server commit; локальні pending записи залишаються закритими.

Reader після підтвердженого commit завантажує актуальний head. Успішний receipt
залишається підтвердженням запису, навіть якщо наступне завантаження не вдалося
або інший редактор уже створив новішу ревізію.

## Перевірки й межі

- 83 backend domain tests pass, з них 23 нових: field allowlist/limits, viewer,
  чужий актор, stale revision, replay, two writers, concurrent same UUID,
  expiry/revoke перед publish, storage failure/retry, snapshot writer race,
  corruption, квоти, rebase, збереження photo bytes і всіх інших сутностей.
- 22 host-набори pass; один explicit skip через відсутність особистого
  Firebase client config. Точний звіт у RELEASE-VALIDATION-0.14.7.json.
- 5 Android AtomicFile/outbox integration tests підготовлено, не виконані.
- 1 новий local emulator callable/Firestore/Storage/Auth сценарій підготовлено
  в cloud/family-functions/tests/emulator_editing.py, не виконаний.
- Kotlin/Compose compilation, lint, APK, реальний lifecycle та два пристрої
  не підтверджені. Build зупиняється до компіляції: потрібен повний JDK 17/21.
- Live Firebase deployment не виконувався. Rules, локальні config/signing,
  applicationId і Room/backup не змінені.

Для emulator-перевірки використати встановлені залежності та guarded demo
оточення, описане у FAMILY-BACKEND-0.14.1.md. З папки cloud:

```bash
FAMILY_STORAGE_BUCKET=demo-wardrobe.appspot.com npx firebase emulators:exec \
  --project demo-wardrobe --config firebase.family.example.json \
  --only auth,firestore,storage,functions \
  "family-functions/venv/bin/python family-functions/tests/emulator_editing.py"
```

Android UI потребує нової family_edit_item Functions. Оновлювати лише family
Functions codebase після перевірок. Не перезаписувати живі Storage rules,
які користувач редагував окремо. Пакет сам не робить deploy.

## Наступне

FAM-03: додавання/фото/решта полів і спільні образи з використанням існуючих
редакторів; черга кількох операцій, узгоджене фонове оновлення та continuous sync.
Потім FAM-04 — native multi-account acceptance. FAM-02 email delivery і terminal
invitation history також залишаються відкритими. Багфікси — за пізнішим етапом.
