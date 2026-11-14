# FAM-02 — сімейні запрошення та доступ

0.14.6-family-access, versionCode36, Room/backup10. Автор: Codex, 18.10.2026.
База — 0.14.5-family-reader. За вказівкою користувача багфікси відкладаємо;
цей пакет продовжує функціональність сімейних гардеробів.

## Що доступне в коді

Налаштування → «Сімейні гардероби та запрошення» → «Оновити з сервера».
Власник бачить свої серверні гардероби, зокрема створені до цього оновлення.
Запрошений учасник після прийняття бачить гардероб на будь-якому пристрої
з тим самим акаунтом. Перегляд використовує ізольований cache 0.14.5.

«Керувати доступом» відкриває учасників і pending-запрошення. Власник задає
назву для учасників, email і роль viewer/editor; viewer є початковим вибором.
Одержувач бачить власника, назву, роль і кінцеву дату та приймає або відхиляє.
Власник змінює роль, скасовує запрошення чи відкликає членство; учасник виходить.
Ownership не переноситься. Editor уже має серверне право на commit, але
Android-екран сімейних речей поки лише для перегляду. Спільний редактор — далі.

Запрошення створюється **в застосунку**, діє 7 днів, лист не надсилається.
Після прийняття доступ діє до відкликання/виходу. UI прямо повідомляє про спосіб
доставки й показує email власника. Підтвердження прийняття пояснює, що власник
побачить email учасника. Потрібні окремі акаунти з підтвердженою адресою.

## Контракт і захист

Вісім нових callable-функцій:

| Метод | Обсяг |
| --- | --- |
| family_directory | Власні roots і прийняті членства, перевірені за поточним root/member |
| family_invitations | Pending inbox лише для перевіреної email-адреси з токена |
| family_members | Список активних учасників і pending-запрошень, лише owner |
| family_invite | UUID, remoteId, email, viewer/editor, name; лише owner |
| family_respond_invite | inviteId, accept/decline; перевірена адреса й потім UID |
| family_cancel_invite | Pending → revoked; лише owner |
| family_change_member | UID, роль/revoked, expectedVersion, requestId; owner-only CAS |
| family_leave | Лише поточний UID; власник не може вийти |

`Actor.email` береться з перевіреного Firebase ID token після перевірки
revocation/disabled. Клієнтське поле email дозволене лише як адресат запрошення,
воно ніколи не є ідентичністю виконавця. `expectedActorUid` збережено.
Після прийняття членство належить UID, зміна або повторне використання адреси
іншим акаунтом не дає йому вже прийняте членство.

`familyInvites/{UUID}` зберігає незмінні параметри запрошення й terminal status.
`familyWardrobes/{remote}/inviteSlots/{emailHash}` не допускає двох pending
запрошень на ту саму адресу. `members/{uid}` сумісний із чинними role/expiry
Storage guards; version/requestId додають CAS та ідемпотентність зміни.
`expiresAt=9007199254740991` означає членство до явного відкликання, без
автоматичного річного поновлення.

`familyInboxes/{emailHash}`, `familyDirectories/{uid}`, `familyAccess/{remote}` —
обмежені індекси. Вони не надають доступу: кожний результат повторно звіряється
з authoritative root/member/invitation. Старі власні roots знаходяться equality
query ownerUid без міграції. Старі вручну створені member-документи не
індексуються автоматично: для таких тестових записів потрібна окрема міграція.

Ліміти: 50 активних членів на гардероб,50 pending на гардероб/адресу,
50 прийнятих гардеробів на UID плюс50 власних roots;30 нових запрошень/добу
на owner. Прострочені pending entries прибираються з індексів при новому invite;
terminal invite/member docs зберігаються для захисту повторів. GC/quota cleanup
залишається rollout-задачею. Пошук акаунтів за адресою не виконується.
Підтримується ASCII email з нормалізацією регістру/зовнішніх пробілів; крапки
та plus-аліаси не об’єднуються. Непідтримувані адреси відхиляються явно.

Усі транзакції спершу читають, потім записують. Accept/cancel мають одного
переможця. Revoke/leave закриває прийняте запрошення, прибирає індекси й
забороняє старому invite повторно надати доступ. Новий invite після revoke
створюється явно, з новим UUID. Повтор старого role-запиту після пізнішої зміни
дає конфлікт замість відкату актуальної ролі.

Android зберігає metadata лише в account-scoped ViewModel. Coroutine/session
checks перевіряються до/після SDK await. Refresh скидає runtime read lease;
leave прибирає cached view до мережевого запиту. На іншому пристрої сервер
відхиляє нові запити одразу після revoke, а вже завантажений read-only cache
може залишатися видимим до закінчення 15-хв lease. Це явно написано в confirm UI.
Push-відкликання немає. Приватні Room, backup і сховища фото не змінені.

## Перевірки

- 60 Python backend tests pass, з них 25 нових FAM-02. Виконується реальний
  domain service з serializable memory adapter і забороною read-after-write.
  Покрито identity/forgery, owner/editor/viewer, retries, TTL, cancel/accept race,
  role CAS, revoke/reinvite, leave, bounds/quotas, poisoned indexes та old roots.
- 22 host-набори pass, 1 explicit skip через відсутній особистий client config.
  Повний звіт у release-evidence/host-runs, точний шлях у release JSON.
- 7 Kotlin response/parser-тестів підготовлено у FamilyAccessWireTest; не виконані.
- 2 нові Auth/Firestore/Functions emulator-сценарії підготовлено у
  tests/emulator_membership.py; не виконані. Це **не** підтверджений live rollout.
- `bash build-apk.sh testDebugUnitTest lintDebug assembleDebug` зупиняється до
  компіляції: Install a full JDK 17 or 21 and set JAVA_HOME. APK немає.
- Compose compilation, lint, Android lifecycle/navigation UI, Firebase adapter
  integration, два телефони, Windows/macOS updater ще не перевірені.

Після налаштування тестового середовища запустити звичайні host/native checks.
Для двох нових emulator-сценаріїв з `cloud`, після встановлення залежностей за
FAMILY-BACKEND-0.14.1.md:

```bash
FAMILY_STORAGE_BUCKET=demo-wardrobe.appspot.com npx firebase emulators:exec \
  --project demo-wardrobe --config firebase.family.example.json \
  --only auth,firestore,storage,functions \
  "family-functions/venv/bin/python family-functions/tests/emulator_membership.py"
```

Adapter використовує офіційний Python Firestore `FieldFilter` та
`query.stream(transaction=...)`; контракт:
https://docs.cloud.google.com/python/docs/reference/firestore/latest/google.cloud.firestore_v1.query.Query

## Застосування та наступне

Updater цього пакета застосовується після 0.14.5; default — preview,
`--apply` — зміни після перевірки всіх hashes, backup поруч із проєктом,
rollback перевіряє незмінність застосованих файлів. Локальні config/signing/Git
не перезаписуються. Старі звіти залишаються історичними.

Для роботи нового Android UI потрібно оновити **лише family Functions codebase**
у вже налаштованому Firebase проєкті. Потрібний наявний FAMILY_STORAGE_BUCKET.
Прямий Firestore доступ залишається deny-all для family metadata; Storage rules
вже перевіряють members. У цьому пакеті rules не змінені. Live console rules
користувач редагував окремо: не перезаписувати їх старим повним Firebase deploy.
Агент нічого не деплоїв і не надсилав реальних запрошень.

FAM-02 залишається partial: окремий UI історії accepted/expired/revoked та
email delivery ще відкриті. Приймання нової функціональності — verify.
Наступний функціональний обсяг FAM-03: mutable shared model, ізольовані
чернетки/editor, outbox і CAS-конфлікти; після нього FAM-04. Багфікси — пізніше.
