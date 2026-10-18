# Current addendum — 0.14.1, 14.10.2026

FAM-01/03 partial. The server transport described below is now implemented
in cloud/family-functions, with a separate emulator/deployment example.
Create/resume, upload reservation, scoped ZIP validation, membership guards
and transactional commit have executable emulator evidence. The older ADR
below describes the 0.14.0 foundation; statements about missing backend there
are historical. Android family transport, membership/invite API and shared
UI/cache remain outstanding. Current contract, limits, known emulator
limitations and production gates: FAMILY-BACKEND-0.14.1.md.

# ADR FAM-01 — спільний доступ до конкретного гардероба

Оновлено 2026-10-13. Виконавець: Codex. Клієнт 0.14.0/code 30, Room/backup 10.
Статус: **partial**. Реалізована основа даних та вибірковий portable backup;
сімейний backend, синхронізація, запрошення й серверні правила ще потрібні.

## Одиниця доступу та стан реалізації

Одиниця доступу — гардероб із власним remote UUID та owner UID. Приватний
`users/{uid}/snapshots` містить весь акаунт і ніколи не відкривається іншому UID.
Сімейний доступ добровільний. Передавання ownership не входить до R3.

| Компонент | Стан у 0.14.0 | Призначення |
| --- | --- | --- |
| WardrobeScope + WardrobeSnapshots | Реалізовано | Відібрати рівно один гардероб із 16 portable-колекцій та його фото; зберегти ID; відхилити невалідні/міжгардеробні посилання |
| Backup.export(wardrobeId) + Settings | Реалізовано | Portable ZIP обраного гардероба, перевірка записаних байтів; restore додає копію з новими узгодженими ID |
| FamilyEnvelope | Реалізовано як внутрішній компонент | Окремий маніфест v1, внутрішній content v10; перевірка очікуваних remote/wardrobe/owner/baseRevision |
| FamilyRules | Реалізовано як контракт | Ролі, CAS, шлях архіву, строк кешованого доступу; не замінює серверні перевірки |
| FamilyMigrationStore | Реалізовано як локальний компонент | Account-private AtomicFile checkpoints, повтор без нового remote/archive UUID |
| Transport / server / invite UI | Ще не реалізовано | Довірений перенос, memberships, відправлення/прийняття, захист і конфлікти |

Формат portable ZIP лишається `family-wardrobe`, version 10. Він не містить
членства й не надає доступу; відновлення — незалежна локальна копія. Сімейний
маніфест має інший format і не приймається як приватний account backup.
Shared-дані ще не додаються до локального account Snapshot: перед підключенням
transport потрібно відокремити їх від повного/планового private backup і cloud.

## Схема майбутнього backend

| Об’єкт | Контракт |
| --- | --- |
| `familyWardrobes/{remoteId}` | ownerUid, local wardrobe UUID, revision, formatVersion, archived, headSnapshot; root створює довірений backend |
| `familyWardrobes/{remoteId}/members/{uid}` | active viewer/editor; owner визначається root, його не можна видати записом membership |
| Запрошення | Приватний backend-запис: remoteId, нормалізована перевірена адреса або її hash, роль, expiry, tokenHash, status; іншим учасникам адреси не перелічуються |
| Head | revision, immutable archive path, SHA-256, baseRevision, authorUid; оновлення лише CAS-транзакцією після перевірки вмісту |
| Storage | `familyWardrobes/{remoteId}/snapshots/{archiveId}.zip`; кінцеві фото всередині ZIP, окремого міжгардеробного photo bucket немає |
| Локальне зіставлення | Account-private remoteId ↔ wardrobeId; UUID сутностей зберігаються в family exchange, роль береться з сервера |

На першому сімейному етапі обрано незмінний ZIP одного гардероба: його можна
перевірити повністю та опублікувати одним head. Це повторно використовує
portable граф даних. Ціна цього рішення — передавання всіх кінцевих фото при
новій ревізії. Пізніший incremental photo transport потребуватиме окремого ADR,
контролю посилань і GC; поточний код його не імітує.

Оболонка: `format=family-wardrobe-scoped`, `version=1`, `remoteId`, `wardrobeId`,
`ownerUid`, `baseRevision`, `snapshot`. Внутрішній snapshot має version 10,
один wardrobe ID та точний набір photoFiles. Ролі/токени/запрошення в архіві
не приймаються. Очікувану identity передає майбутній контролер із довіреного
server response; підставляти значення з самого архіву заборонено.

Майбутній transport повинен перевірити SHA-256/ZIP CRC, точний набір файлів,
ліміти, всі посилання й чинне членство перед встановленням head або записом
локальних даних. FamilyEnvelope сам перевіряє маніфест; завантаження та
перевірка сімейних ZIP-байтів у цій версії не реалізовані.

## Ролі, запрошення та офлайн

| Операція | Owner | Editor | Viewer |
| --- | --- | --- | --- |
| Читати відкритий гардероб | Так | Так | Так |
| Змінювати речі/образи/плани | Так | Так | Ні |
| Керувати учасниками, ролями, архівацією | Так | Ні | Ні |
| Експортувати shared-гардероб у portable ZIP | Так | Ні | Ні |

Це контракт для FAM-02–03. Поки в застосунку є тільки власні приватні
гардероби, нова кнопка експорту працює з ними. До появи shared cache всі
шляхи full/scheduled/export/cloud мають отримати серверно підтверджений scope.
Приховування кнопки або виклик FamilyRules не є серверним захистом.

Створення/прийняття/відкликання запрошень — довірений Firebase backend:
перевірений ID token, email_verified, owner/recipient identity, одноразовий
hash токена, expiry, rate limit та ідемпотентний request ID. Стани:
pending/accepted/expired/revoked; відмова одержувача не створює membership.
Посилання саме по собі не надає доступу. Ключ email-провайдера зберігатиметься
тільки на сервері. Провайдер ще не вибраний; реальні листи не надсилалися.

Контракт кешу: читання shared cache до **15 хвилин** після online-підтвердження,
в тій самій авторизованій runtime-сесії; монотонний час, expiry на межі TTL.
Зміна акаунта, перезапуск процесу або відкат часу потребують нової перевірки.
FamilyRules містить цю перевірку; shared cache та її застосування ще відсутні.
Приватні власні гардероби продовжують працювати офлайн. Мутації чужого
гардероба допускаються лише після online-перевірки права редактора; майбутня
черга завжди повторно перевіряється сервером перед commit.

При відкликанні клієнт має скасувати jobs, закрити чужий екран, очистити кеш
і чергу. Невідправлені зміни не переходять до іншого UID. Сервер перевіряє
членство повторно на commit, включно з відкликанням під час upload. Уже
отримані байти на відключеному пристрої неможливо гарантовано миттєво стерти.

## Міграція та відновлення

1. Власник явно обирає гардероб. Перевірити цілісний Snapshot і фото під
   поточним write lock. Локальну базу та старий private ZIP не видаляти.
2. `prepare` атомарно зберігає запропоновані remoteId/archiveId; повтор
   використовує той самий запис. Backend має резервувати цей remoteId для
   автентифікованого owner і відхиляти колізію, а не привласнювати чужий root.
3. Після створення перевіреного family ZIP записати STAGED із localRevision
   і SHA-256. UPLOADED означає завершений transport, не встановлене членство.
4. Backend validate/commit працює для revision 0→1, ідемпотентно для того
   самого archiveId/checksum. Після authenticated readback `confirmServer`
   звіряє identity/path/checksum/revision=1 і незмінність localRevision.
5. Тільки VERIFIED дозволяє майбутньому контролеру перейти до запрошень.
   Локальний файл не є доказом серверного членства; права перечитуються.

Фази PREPARED→STAGED→UPLOADED→VERIFIED зберігає
`filesDir/family-migration-v1.json` конкретного акаунта; до 1000 записів і
1 MiB. Контекст звіряється з AuthIdentity, пошкодження або інший account key
зупиняють операцію. Файл не входить до portable/private-cloud snapshot.
Пізній callback може повторити завершений крок, але не підмінити archive/hash.

Після зміни localRevision або невідомого server outcome поточний компонент
відмовляє у VERIFIED і зберігає checkpoint. Майбутній контролер повинен
спочатку прочитати server head, показати конфлікт/відновлення, узгодити нову
ревізію та staged artifact. Не скидати журнал і не створювати другий remoteId.
Локальний journal сам по собі не є реалізованим server resume/rollback.

## Межі, витрати й наступна робота

Маніфест обмежено 8 MiB. Portable backup додатково обмежує 10 000 фото,
100 000 wear rows і 20 GiB загального вмісту; кожне фото — наявним MAX_PHOTO.
Для сімейного backend потрібні власні серверні quotas, upload/download limits,
retention, idempotency TTL, cleanup staged archives і budget alerts до rollout.
Не використовувати максимальні portable-ліміти як обіцяні сімейні quotas.

Оцінка пілоту має врахувати: гардероби × середні кінцеві фото × версії;
повні завантаження × учасники; server invocation, Firestore reads/writes,
Storage egress та email sends. Фактичні параметри пілоту/провайдер/бюджет
ще не зафіксовані; точний грошовий прогноз не заявляється. До deployment
перевірити актуальні умови [Firebase Functions](https://firebase.google.com/docs/functions/get-started),
[Storage access](https://firebase.google.com/docs/storage/security/rules-conditions)
та [email delivery](https://firebase.google.com/docs/extensions/official/firestore-send-email).
Платні сервіси цим етапом не активовано.

Наступний код: серверні create/resume/validate/commit, membership та CAS,
ізольовані emulator-тести owner/viewer/editor/unrelated/revoked/expired,
escalation/path/ZIP spoofing, replay і конкурентні commit. Далі FAM-02 —
запрошення/прийняття/відмова/роль/відкликання через перевірений API. FAM-04 —
два окремі логіни, переривання переносу, фото/історія, account switch,
portable restore й сумісність старого клієнта. Downgrade shared-даних не
підтримувати тихою заміною private head або destructive migration.

Докази поточного етапу: FAMILY-FOUNDATION-0.14.0.md, HOST/RELEASE-VALIDATION-0.14.0.json.
FAM-01 лишається partial; документи й client helpers не означають готову сімейну синхронізацію.
