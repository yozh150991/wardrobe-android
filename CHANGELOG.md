## 0.14.15-family-outfits — 23 October 2026 (code 45)

FAM-03: show shared outfit collages and create/edit looks with the existing constructor,
including owned/mixed/inspiration, pins, replacements and canvas controls. Account-private
editor autosave and immutable publication; explicit send, receipt-first retry and confirmed
CAS rebase. Preserve historical wears/plans; reconcile capsule packing. No private Room
writes or new backend endpoints/rules. Host 23 pass (client config skipped), backend 148 pass;
12 JVM tests added for CI, 5 instrumented tests pending. See FAMILY-OUTFITS-0.14.15.md.

# 0.14.5-family-reader — 18 October 2026

## 0.14.14-qa-fixes2 — 22 October 2026 (code 44)
All 13 bugs of the QA regression report (QA-REPORT-0.14.13.md) fixed; their strict expected
failures are now regular regression checks. Family backend (deploy needed): invitation names
limited to 80 UTF-16 units like the phone, legacy over-long names hidden from lists, RFC e-mail
rule (same on the phone), family_edit_item gets the commit's 1 GiB / 540 s. App: every saved
name (wish, item, look, capsule, wardrobe, custom label) needs a visible character; imported
shop names lose bidi controls; IDN shop links work; http/https and www share a duplicate key;
purchase advice no longer demands optional outerwear; NFC look names; password minimum in
characters; canvas layers no longer overlap outerwear. Backups with older labels still
restore. Backend 141 tests, host suites and Kotlin QA JUnit pass; Android in CI. See QA-FIXES-0.14.14.md.

## 0.14.13-qa-fixes — 20 October 2026 (code 43)
All 16 bugs from the 0.14.12 QA report fixed, each with a regression check. Shop import:
locale quotes/ellipsis and brackets no longer break shared links, JSON-LD after many scripts
is read, pages over 2 MiB are parsed instead of refused, entities decoded once (+ named
entities), grapheme-safe truncation (also in form fields), duplicate keys ignore default
port/empty query/fragment, http links fetched over https. Family backend (deploy needed):
.укр/punycode e-mails, Java final-sigma label keys, CRLF in edits, bidi/invisible names refused.
App: look names trimmed per grapheme, category definition trigger, invite e-mail checked like
the server, canonical UUID canvas keys, order-independent scope depth. Backend 113 tests and
host suites pass; Kotlin/Android in CI. See QA-FIXES-0.14.13.md.
## 0.14.12-family-labels — 20 October 2026 (code 42)

FAM-03: the new shared item form can use the shared wardrobe's own custom categories and
colours (only existing ones matching the chosen base); merge reuses the label by nameKey,
no duplicates. JVM tests extended. No backend change. See FAMILY-NEW-0.14.11.md.

## 0.14.11-family-new — 20 October 2026 (code 41)

FAM-03: create a new item directly in a shared wardrobe — name, base category/colour,
size, location, description, season, style and a gallery/camera photo imported as is —
without a private copy. Reuses the addition draft/send/retry/rebase flow; no backend
change. Unreadable photos now get their own message in the add and photo dialogs.
See FAMILY-NEW-0.14.11.md.

## 0.14.10-family-photo — 20 October 2026 (code 40)

FAM-03: replace the photo of an existing shared item (owner/editor) from the gallery,
the camera (as is, no background removal) or the final photo of one of your own items.
Only that item's photoId/photoFingerprint change; the old photo leaves the shared copy
only when nothing else uses it. Durable isolated draft, explicit send, receipt-first
retry and CAS rebase via the existing begin_upload/commit; no backend change or deploy.
99 backend tests (4 new) and host suites pass; Android CI in the PR; device checks open.
See FAMILY-PHOTO-0.14.10.md.

## 0.14.9-family-add — 20 October 2026 (code 39)

Codex package 0.14.8-family-add (19.10, code 38) integrated on top of
0.14.8-audit-fixes and renumbered, because code 38 was already released.
Same audit fixes applied to the new code: FamilyAddController queues actions
instead of dropping them (№10) and prepare() no longer holds the private write
lock while copying shared photos and writing the ZIP (№4). See FAMILY-ADD-0.14.8.md.

## 0.14.8-family-add — 19 October 2026 (Codex package, released as 0.14.9 code 39)

FAM-03: copy a selected saved private item and final photo to a shared wardrobe.
Remap only its item/photo/custom category/color; preserve all existing shared data.
Durable isolated ZIP/journal, explicit preview/send, historical receipt retry and
CAS rebase. Existing backend begin/commit reused; no new endpoint/rules/deploy.
95 backend tests and 22 host suites pass. JVM7/Android8 prepared, not run; native
build blocked before compilation by missing full JDK. No APK. Photo replacement,
outfits, multi-operation outbox and background sync remain. See FAMILY-ADD-0.14.8.md.

## 0.14.8-audit-fixes — 20 October 2026 (code 38)

Bugfixes from the 0.14.6 technical audit on top of 0.14.7-family-edit (no new features,
Room/backup 10 unchanged): collage crash on inspiration keys, Back on session opening
no longer signs out, Activity close keeps background sync, export snapshot under lock
with ZIP outside it and 60 s dirty debounce, CLOUD operations allow browsing, daily
looks without outerwear, photo fingerprint kept on edit, VERIFIED family transfer no
longer fails, queued family access actions, family_commit 1 GiB/540 s (needs manual
deploy), busy notice, reserved IP ranges, synchronized photo cleanup, member wardrobe
title, wish category vs outfits, cached rescue files. Host suites and backend tests
pass; Kotlin build and device checks not run by the author. See AUDIT-FIXES-0.14.8.md.

## 0.14.7-family-edit — 19 October 2026 (code 37)

FAM-03 metadata editor for existing shared items: name/size/location/description,
account-isolated durable drafts, explicit sync, stable UUID replay, authoritative
role/expiry/CAS checks, comparison and explicit rebase. Photos/other entities
preserved. 83 backend tests and 22 host suites pass; native build, Android5 and
emulator1 tests not run. No APK/live deployment. Full shared editing and continuous
sync remain partial. See FAMILY-EDIT-0.14.7.md; bugfixes deferred.

## 0.14.6-family-access — 18 October 2026 (code 36)

FAM-02: callable-only in-app invitations by verified email; owner/member discovery,
accept/decline/cancel; owner-only role CAS/revoke and member leave. Android UI in
UA/EN/PL, account/session guards, lease invalidation, bounded inbox/directory.
60 backend tests and 22 host suites pass; native Kotlin/Compose build, emulator 2
and wire 7 tests not run. No APK or live deployment. Email delivery and terminal
invitation history remain open. Next: shared editing/CAS; bugfixes deferred.
See FAMILY-ACCESS-0.14.6.md and RELEASE-VALIDATION-0.14.6.json.

FAM-01/03 partial, reader verify. Added isolated read-only server-copy browsing, authenticated download/head recheck, bounded ZIP validation and runtime lease. Preserves private data and all DEV-03/photo hotfix changes. Native build/tests remain unverified. See FAMILY-READER-0.14.5.md and RELEASE-VALIDATION-0.14.5.json.

# Wardrobe — історія змін

Це історичні записи; актуальні статуси й наступні кроки — у [роадмапі](WARDROBE-ROADMAP.md). Результати тестів належать указаним релізам.

## 2026-10-18 — DEV-03, підготовка GitHub

Git guard false positive усунено генерацією тимчасового пароля. Додано CI й окремий Android preview, перевірку версій, PR template та інструкцію. Android versionName/code і локальний build default не змінено. Перший GitHub run та Android build ще не підтверджені.

## Історичні перевірки

- **0.11.1:** у `docs/history/RELEASE-VALIDATION-0.11.1.md` зафіксовані Debug/Release build, 43 JVM-тести, 71 Android-тест, 6 Firebase emulator-тестів, 13 host-наборів. У фінальному повідомленні також зафіксовано 42 перевірки оновлювача. Це результати попереднього релізу.
- **0.11.1 UI:** перевірка навігації не завершилася через ANR System UI. Успіх не зараховано. Живий Firebase, фізичні телефони й повні 52 сценарії не підтверджені як прийняті.
- **0.11.2:** попередній `package-validation.json` фіксує 3305 перевірок джерел/SQL, 11 XML, 791 рядок локалізації, 67 DAO-запитів і Python-моделювання оновлення. Завантаження Gradle тоді було недоступне. Це не компіляція, не запуск PowerShell і не перевірка UI.
- **Редакція 1:** документація й доставка оновлювачем; історичний `../roadmap-package-validation.json`. **0.12.0 / редакція 2:** 14 host-наборів, 3434 source/SQL перевірки й 34 Java-перевірки імпорту; докази R1 у `docs/history/HOST-VALIDATION-0.12.0.json`, `docs/history/RELEASE-VALIDATION-0.12.0.json` та `../package-validation-0.12.0.json`.
- **0.13.0 / редакція 3:** 15 host-наборів, 3620 source/SQL перевірок, 866 локалізованих рядків, 76 Java assertions R2 і 34 імпорту. Поточні докази — `docs/history/HOST-VALIDATION-0.13.0.json`, `docs/history/RELEASE-VALIDATION-0.13.0.json`, `../package-validation.json`.
- У `SCENARIO-COVERAGE-0.11.1.md` є застаріла загальна фраза про невиконані Android/Firebase-перевірки. Читати конкретні результати release-звіту й журнали, а виконання кроків UI відстежувати окремо (`DOC-01`).


## Попередній журнал рішень


| Дата | Рішення / результат |
| --- | --- |
| 2026-10-07 | Користувач погодив каталог + імпорт із магазину як найближче оновлення, помічник перед покупкою як наступне та окремий сімейний блок. Усі ідеї мають бути в документі, доступному наступному агенту. |
| 2026-10-07 | Звірено поточні джерела 0.11.2: базова сумісність уже є, Share і сімейні ролі відсутні, feedback поки обмежений. Уточнено межі попереднього тестування. |
| 2026-10-07 | Створено цей roadmap і `AGENTS.md`; додано до пакета та updater. Версія застосунку, Room/backup, runtime-код і конфігурація не змінені. Наступне завдання — UX-01. |

Формат запису після кожної змістовної зміни:

```text
Дата / виконавець:
Task ID / попередній → новий статус:
Базова версія / commit або хеш пакета:
Що змінено / точні файли:
Перевірки: команда, середовище, результат, шлях до доказу:
Що не перевірено / блокер:
Зміни даних, backup, cloud і сумісності:
Де збережено результат:
Наступна конкретна дія:
```

Нові ідеї додавати зі стабільним ID, джерелом, пріоритетом і залежностями. Не видаляти завершені записи; зберігати рішення й причини відкладення. Якщо стан коду суперечить цьому документу, спочатку звірити докази й оновити статус, а не продовжувати за застарілим припущенням.


### Контрольна точка R1 — 8 жовтня 2026, Codex

- База: 0.11.2-release/code 17 → 0.12.0-release/code 18. Повний список змінених файлів і перевірки updater — `../package-validation.json`.
- `UX-01–02`, `IMP-01–03`: реалізація URL/тексту → `verify`. `DOC-01`: `done`. `FAM-01`: `partial`, рішення в ADR; фінальна оцінка витрат/інфраструктури перед R3.
- Реально виконано: `python3 tools/run_host_checks.py`, 14/14; Java parser 34 assertions, source/SQL 3434, XML 11, локалізовані ресурси 821, DAO 67. Пряма Java-компіляція — модулем `jdk.compiler` JDK 17. Це не компіляція Kotlin/Compose.
- Блокер `REL-01`: `bash gradlew --version` не завантажив Gradle 8.11.1, `java.net.SocketException: Network is unreachable`. Лог: `docs/history/release-evidence/0.12.0/gradle-bootstrap.log`. Немає нового APK, Gradle JUnit/lint, Android/Firebase/UI/Windows запусків.
- Формати даних і cloud protocol незмінні: Room 8, backup 8, owner-only snapshot. URL/ціна власної імпортованої речі записуються в наявний Wish/history у тій самій транзакції з Garment. Черга та незавершена чернетка локальні, не переносні.
- **Точний наступний крок:** застосувати updater з `-CheckOnly`, потім у налаштованому проєкті JDK 17/SDK 35 виконати `build-apk.ps1 testDebugUnitTest lintDebug assembleDebug`, `connectedDebugAndroidTest`. Виконати матрицю `R1-UPDATE-0.12.0.md`, зберегти результати/хеш APK/підпис і тільки після цього змінити `verify` на `done`.

**Відкриті доробки та ідеї R1 (не загубити):**

1. `IMP-01`: прямий Share фото/`EXTRA_STREAM`, URI-grants, cold start і відновлення дозволів; поточний контракт тільки текст/URL. Умови сумісності джерела перевіряти до оголошення MIME-фільтра.
2. `IMP-02`: відтворювані HTTPS fixtures для redirect, повільної відповіді, offline/cancel, зіпсованого/надвеликого фото; контрольна добірка реальних магазинів. Не додавати web scraping із входом чи обхід блокувань. За попитом — безпечна підтримка нових валют і форматів метаданих; без підміни невідомої валюти PLN.
3. `IMP-03`: native-тести ViewModel/Compose для Share поверх відкритого редактора, crash між claim/draft/complete, зміни destination wardrobe, повтору після commit і backup/cloud round trip історії власної імпортованої речі. Наявні instrumented tests покривають нижчі рівні й не замінюють ці сценарії.
4. `UX-01–02`: перевірити довгі назви/URL, 200% шрифт, вузькі екрани, клавіатуру та повернення із вибраного збігу іншого гардероба. За результатами прибрати лише підтверджені незручності; не повертати дублікати постійних кнопок.
5. `SHOP-01–03`: наступний функціональний етап після приймання R1 — пояснювана схожість і користь покупки на основі вже наявного CompatibilityScreen. Решта пріоритетів R3–R6 і всі FUT-01–10 збережені вище.


### Контрольна точка R2 — 8 жовтня 2026, Codex

- Після команди користувача «Далі» повторено Gradle bootstrap; та сама мережева помилка. R2 продовжено на існуючих збережених/ручних товарах, без прихованого закриття R1-приймання.
- База: 0.12.0-release/code 18 → 0.13.0-release/code 19. `SHOP-01–03`: partial/planned → `verify`. Активних фонових завдань після передачі немає.
- Production-правила: `PurchaseAdviceRules.java`; адаптер Snapshot/LookPiece: `PurchaseCompatibility.kt`; UI: `CompatibilityScreen.kt`; перехід у purchase-review: `Ui.kt`; 45 нових ресурсів UA/EN/PL/fallback.
- Реально виконано: `python3 tools/run_host_checks.py`, 15/15. У тому числі 76 assertions R2, 34 assertions імпорту, 3620 source/SQL, 11 XML, 866 localized strings, 67 DAO. Останню правку підписів і Material3 opt-in перевірено повторним `check_project.py` з окремою датою в host-звіті.
- Виправлений під час перевірок крайовий сценарій: наявність усіх категорій із несумісними сезонами не пояснюється як необхідність купити відсутній одяг.
- Підготовлено 7 JVM/JUnit методів (1 Java wrapper із host-fixtures, 6 Kotlin adapter tests) і 1 Android flow test. Gradle JUnit і Android **не виконані**. Flow test перевіряє planned/owned save, повторне збереження, purchase/undo та збереження джерела/бюджету.
- Ключові рішення: тимчасові ознаки перевірки не змінюють Wish; невідомий сезон явно позначений. Заміна — функціональна за категорією/сезонами, не твердження про однаковий колір/посадку. Архівна річ не зникає з порівняння, щоб не створювати штучний аргумент за покупку. Ціни різних валют не порівнюються арифметично.
- База/backup залишаються 8, немає нових permissions, сервісів, платежів або міграції. Збереження й purchase/undo використовують наявні repository-транзакції. Повний список файлів і перевірки updater — `../package-validation.json`.
- **Точний наступний крок:** REL-01/QA-01–02 — зібрати 0.13.0, виконати `testDebugUnitTest lintDebug assembleDebug`, `connectedDebugAndroidTest` і native-матриці R1/R2. Успіх старого 0.11.1 не переноситься. Після цього завершити FAM-01 (провайдер/вартість/ліміти/lease) і перейти до FAM-02–04.

**Відкриті доробки R2 (збережені під чинними ID):**

1. `SHOP-01`: контрольна добірка хибних збігів, різних ракурсів і багатомовних назв підкатегорій. Зараз підкатегорія збігається за нормалізованим текстом, без перекладу/синонімів; це свідоме обмеження. Якщо потрібні постійні ознаки товару — окремо Room/backup/cloud migration, не приховане дописування полів.
2. `SHOP-01`: візуальна схожість/embeddings залишається експериментом після оцінки якості й витрат, не частиною поточних заяв про порівняння фото.
3. `SHOP-02`: native acceptance для 200+ речей, швидкої зміни ознак/погоди, повороту екрана, Back після перегляду речі, 200% шрифту й пізнього результату. Аналіз працює поза UI thread; застарілий результат не пропонується для Save, але це ще слід перевірити на пристрої.
4. `SHOP-02–03`: перевірити підстановку виключеної з рекомендацій, але доступної речі (дозволена як ручний вибір), зміну availability під час перегляду, збережені дублі й planned → purchase → undo → backup/cloud round trip. Наявні транзакції відхиляють невалідні/застарілі записи.
5. `SHOP-03`: якщо з’явиться потреба в окремому відкладеному статусі чи нагадуванні, проєктувати явно; поточна дія лише лишає товар у списку. Не підміняти її автоматизацією або повідомленнями.


### Контрольна точка PHOTO-01 — 8 жовтня 2026, Codex

- База 0.13.0/code 19 → 0.13.1/code 20. PHOTO-01: нова P0-задача → verify. Всі попередні 40 ID збережені; тепер 41 ID.
- Скарга: під час перевірки 11 фото деякі можна лише відкласти; немає зрозумілої причини. На скриншоті фото видно, категорії не видно. Це не доводить збій ML: невибрана категорія — можливий блокер. Без версії APK/відтворення не називати його встановленою причиною.
- Підтверджено в джерелах: BatchImporter викидав статус аналізу й виняток, зберігаючи лише review/failed. Поля й помилки були в прокручуваній області під фото, а великі нижні дії відбирали видиму висоту. Фото без визначеної категорії не можна зберегти до ручного вибору.
- Змінено `PhotoDiagnostics.kt`, `PhotoAnalyzer.kt`, `PhotoStore.kt`, `PhotoProcessingPanel.kt`, `BatchImporter.kt`, `BatchImportScreen.kt`, `Ui.kt`, `WardrobeViewModel.kt`, `ImportOperations.kt`, `OperationState.kt`, ресурси UA/EN/PL/fallback.
- Розділені успішне видалення фону й розпізнавання категорії. Причини: модель відсутня, Play services, об’єкт не виділено, timeout, немає доступу/файлу, декодування, >25 МБ, read/write, підтверджений ENOSPC, невідома причина. Не вгадувати «темне фото», offline або брак місця без доказу.
- Результат пакетної обробки зберігається в account-private preferences за ID черги/фото. Немає URL, сирих exception messages, фото або cache paths у діагностиці. Після Save/Skip запис прибирається; після recover очищаються сироти. Старі черги не отримують вигаданої причини. Деталі ручного повтору в редакторі залишаються session state; якщо їх немає після рестарту, показується відсутність деталей.
- Фото й поля не відкидаються при ML-помилці. Ручний Save не вимагає успішного аналізу. Failed-рядок можна відкрити й завершити з придатним фото; repository перевіряє status/wardrobe/ID, формат фото, поля та повторний commit. ViewModel перевіряє читабельність кожного фінального імпортованого фото до Save.
- Назва/категорія/фото/сума/посилання перевіряються до діалогу дубліката. Біля Save є конкретний блокер; натискання прокручує до поля. Другорядні дії перенесені всередину прокрутки, основний Save залишається видимим. Native layout ще не прийнятий.
- Фактично виконано: 15 host-наборів, production Java R2 76 assertions, importer 34 assertions. Перевірки Kotlin-джерел/XML/SQL пройшли; деталі в HOST-VALIDATION-0.13.1.json. Це не запуск нового Kotlin-функціоналу.
- Підготовлено 5 Android-методів у `PhotoRecoveryTest.kt`: ізоляція й відновлення діагностики, пошкоджені/старі дані, cutout без категорії, завершення failed з фото/повтор, непридатна заміна без втрати черги. Не виконані.
- Gradle bootstrap повторено: Network is unreachable. Kotlin/Compose/KSP, JUnit/lint, Android/Firebase, APK, UI й Windows/PowerShell не виконані. Room/backup/cloud schema залишаються 8, міграції немає; діагностика черги не експортується.
- **Наступний крок:** REL-01 збірка 0.13.1 → PHOTO-01 native-матриця → QA-01–02 і приймання R1/R2. Потім FAM-01–04. Перед native-прийманням не ставити done.

**Незавершені покращення PHOTO-01 / FUT-05:**

1. PHOTO-01: перевірити на реальній проблемній добірці (сам скриншот не є оригінальними фото), різних форматах і слабкому телефоні; окремо зафіксувати помилки читання, ML і валідації.
2. PHOTO-01: перевірити TalkBack, 320–412 dp, 100/150/200% шрифт, клавіатуру, поворот, restart, pause/retry, зміну акаунта й пізні результати. Конкурентний Retry/Save не створює дубль або запис чужого гардероба.
3. PHOTO-01: за результатами приймання — окремий діагностичний код/експорт журналу без фото, URI й персональних даних. Не додавати автоматичне надсилання логів.
4. FUT-05: контрольний набір для якості сегментації/розпізнавання; розмитість, освітлення, кілька предметів і невпевненість показувати як припущення лише після появи перевірюваного детектора. Не обіцяти, що повторний аналіз покращить той самий знімок.
5. PHOTO-01: за потреби зберігати також результат ручного повторного аналізу чернетки з коректною прив’язкою до версії фото. Зараз довготривало зберігається результат batch; при заміні/редагуванні старий статус не приписується новому фото.


### Контрольна точка PHOTO-02 — 8 жовтня 2026, Codex

- Запит: збільшувати вже додане фото, бо деталі одягу погано видно на картці. База 0.13.1/code 20 → 0.13.2/code 21. PHOTO-02: in_progress → verify. Збережено всі 41 попередні ID; тепер 42.
- `PhotoViewer.kt`: натискання фото або області з лупою відкриває повноекранний діалог. Підключено `CatalogScreen.kt`, `Ui.kt` Detail, `WishlistScreen.kt` cards/WishDetail. У каталозі вибір кількох речей має пріоритет: натискання фото продовжує вибирати річ, а зум недоступний у цьому режимі. Натискання текстової частини картки зберігає навігацію до деталей.
- Pinch 1–5×, переміщення тільки в межах зображення, подвійний дотик 3×/fit, кнопки зменшення/збільшення/fit, Close і системний Back. Зум прив’язаний до центру жесту; картинка не їде довільно від пальців. Розмір нового viewport скидає перегляд до fit; закриття/повторне відкриття починає з цілого фото.
- Прозорі вирізки показуються на нейтральному сірому тлі для видимості темних/світлих речей. Loading, missing photo й Retry визначені. Назва обрізається візуально, але доступна accessibility; є кнопки 48 dp та TalkBack actions для зуму/руху. UA/EN/PL/fallback збережено.
- Production-геометрія `PhotoZoomRules.java` повторно використовує `PhotoViewportRules.java`; `PhotoZoomCases.java`/`PhotoZoomRulesTest.java`, runner `tools/check_photo_zoom.py`. Реально виконано 225 assertions: прив’язка точки, рух, min/max/reset, portrait/landscape/тонкі фото, захист країв і невалідних координат. JVM/JUnit wrapper підготовлено, Gradle runner не виконано.
- Пройшли 16 host-наборів; source/SQL 3706, XML 11, локалізаційні рядки 886, DAO 67. 76 purchase та 34 import Java assertions також пройшли. Докази docs/history/HOST-VALIDATION-0.13.2.json і RELEASE-VALIDATION-0.13.2.json.
- Повторний Gradle bootstrap: Network is unreachable. Kotlin/Compose/KSP, Gradle JUnit/lint, Android/Firebase, UI, APK, Windows/PowerShell не виконані. Тести Java-геометрії не доводять коректність розпізнавання touch-жестів на Android.
- Перегляд читає повну наявну версію з PhotoStore; не створює файли, не змінює фото, чернетки, базу, backup чи cloud. Оригінал не зберігається заново, зум не відновлює втрачену роздільність. PhotoStore/дані/маніфест/permissions не змінені. Room/backup 8.
- **Точний наступний крок:** REL-01 — зібрати 0.13.2, виконати PHOTO-ZOOM-0.13.2.md на пристрої, потім PHOTO-01 і R1/R2/QA-01–02. Статус verify зберігати до приймання. Сімейний блок не загублено.

**Доробки/ідеї PHOTO-02:** перевірити низьку роздільність старих фото й 200% шрифт у ландшафті; за результатами приймання вирішувати потребу в більших кінцевих файлах (без постійного оригіналу) та додаткових точках відкриття з образів. Свайп між кількома фото, AI-upscale і нова модель зберігання не входять у поточний запит/реалізацію.


### Контрольна точка PHOTO-03 — 9 жовтня 2026, Codex

- Запит: видаляти більше залишків фону; показувати білий фон, а для білих речей — кремовий. 0.13.2/code 21 → 0.13.3/code 22; Room/backup 8. PHOTO-03: in_progress → verify. Попередні 42 ID збережено, тепер 43.
- `PhotoAnalyzer.kt` бере confidence-mask замість готового bitmap. `PhotoCutoutRules.java` використовує м’який alpha-поріг, видаляє малі компоненти без відкидання другої значної частини (пара взуття), очищує суміжні залишки від підтвердженого однорідного фону. Для схожого кольору предмета й фону колірне очищення вимикається. RGB тканини й попередні прозорі ділянки не підміняються; порожній/суцільний результат відхиляється.
- `PhotoProcessingPanel.kt`, `WardrobeViewModel.kt`: «Очистити фон сильніше», повернення до звичайного режиму; повтор у відкритому редакторі бере тимчасове джерело до першого cutout. Gate перевіряє поточний editor/photo ID; помилка лишає попередній показаний знімок. Після збереження й очищення оригіналу відновити вже видалені деталі неможливо — замінити фото або використати доступні ручні інструменти.
- `PhotoBackdropRules.java`/`PhotoBackdrop.kt`: білий `#FFFFFF`, кремовий `#EEE5D5`; вибір за непрозорими пікселями, ручне значення «білий» має пріоритет. `Ui.kt`, `PhotoViewer.kt`, `CatalogScreen.kt`, `WishlistScreen.kt`, `PhotoEditor.kt`, `PhotoMaskEditor.kt`: узгоджений фон, опційна сітка прозорості у пензлі. Оригінал/невдалу сегментацію не видавати за очищений фон. Фон застосовується при відображенні; PNG/backup зберігає alpha для колажів, flatten/export окремого фото не додано.
- UA/EN/PL: 5 нових рядків; 891 загалом. Без нових платних сервісів/моделі/полів даних.
- Виконано: 16 host-наборів, 3731 source/SQL checks, 11 XML, 67 DAO; 40 нових Java assertions маски/фону, включно з камерним кадром 1600×1200 під `-Xmx256m`, скасуванням, NaN/malformed masks, дірками, лямкою, парою взуття, білим на білому. JUnit wrapper додано, Gradle JUnit не запущено.
- Збірка через Gradle wrapper: exit 1, `UnknownHostException: services.gradle.org`, до компіляції. Нового APK немає. Це не доказ якості реального ML або роботи Compose.
- Доставка: накопичувальний ZIP, payload від 0.13.2 та попередніх баз 0.8.0–0.13.1, захищена локальна конфігурація/backup/conflict checks. Python-моделювання updater — окремий доказ, не виконання PowerShell/Windows.
- **Точний наступний крок:** зібрати 0.13.3 на ПК з JDK/SDK і виконати матрицю `PHOTO-CLEANUP-0.13.3.md` на фото користувача; потім PHOTO-02/PHOTO-01/R1/R2 і сімейний блок. До реального приймання зберігати verify.
- **Подальша доробка за результатами приймання:** контрольна добірка реальних фото й вимірювання якості країв/втрат деталей; якщо загальна модель стабільно залишає високовпевнений фон або людину разом з одягом, оцінити спеціалізовану garment-модель. Платні API, нова модель і ручне виділення кількох предметів не впроваджені.


### Контрольна точка PHOTO-04 — 9 жовтня 2026, Codex

- Прямий запит: крок назад/вперед у ручному редагуванні, тонший пензель зі збереженням великого. 0.13.3/code 22 → 0.13.4/code 23, Room/backup 8. PHOTO-04: in_progress → verify. Попередні 43 ID збережені, тепер 44.
- PhotoEditHistory.java, PhotoHistoryControls.kt, PhotoMaskHistory.kt: immutable-команди/курсор, undo/redo, нова гілка лише після завершення мазка, saveable-стан без bitmap; повернення джерела теж undoable. Обмеження 128 команд/160000 символів і 2048 сегментів не видаляє старі дії мовчки.
- PhotoMaskEditor.kt: tap/drag, одна команда на мазок, скасування цілого pending-жесту, другий палець не комітить мазок, без малювання по затиснутому краю. PhotoBrushRules.java/PhotoMaskRules.java: діаметр 1 пкс–16% короткої сторони, logarithmic slider, −/+1, presets, повна роздільність preview/save, alpha-only erase/restore.
- PhotoEditor.kt: undo/redo руху кута, повзунків, поворотів і reset; незавершена геометрія не зберігається. Перемикання фон ↔ геометрія застосовує правки перед передачею наступному інструменту, історія нового етапу починається заново. Це не глобальне undo через уже застосовані етапи; межа описана у видимих підказках.
- UA/EN/PL/fallback: 11 рядків, 902 загалом. Чинні PhotoStore/trackDraft, очищення, ownership і Room/backup без нових полів; оригінали постійно не додаються.
- Виконано 17 host-наборів: 1774 нові production Java assertions, 3782 source/SQL, 11 XML, 67 DAO; старі mask/backdrop/zoom/import/purchase перевірки також pass. PhotoEditorTest.kt і 5 PhotoEditorHistoryTest.kt Android-тестів підготовлено, Gradle runner не виконано. Докази docs/history/HOST-VALIDATION-0.13.4.json та RELEASE-VALIDATION-0.13.4.json.
- Gradle wrapper exit 1 до компіляції: UnknownHostException: services.gradle.org. APK, Kotlin/Compose/KSP, Android-тести/жести/конфігураційне відновлення, TalkBack, Windows/PowerShell не підтверджені. Java-тести не доводять правильність touch-взаємодії на телефоні.
- Доставка: накопичувальний ZIP із контекстами 0.8.0–0.13.3, README, захистом локальної конфігурації та моделюванням повторного/часткового застосування й конфліктів. Історичні machine-звіти збережено без зміни. Роадмап і UX-специфікація оновлюються зі збереженням ідентичності.
- **Точний наступний крок:** зібрати 0.13.4 на налаштованому JDK/SDK і виконати PHOTO-EDITOR-0.13.4.md; далі PHOTO-01–03/R1/R2 та FAM-01–04. До native-приймання статус verify. За результатами перевірити продуктивність довгих мазків на найбільших допустимих фото та зручність 1-піксельного пензля при 8×.

<!-- WARDROBE-GIT-SAFETY -->
### Контрольна точка DEV-01 — 9 жовтня 2026, Codex

- Прямий запит: додати винятки до `.gitignore` та перевірку перед GitHub push
  з автоматичним додаванням нових локальних/секретних файлів. DEV-01: done
  для реалізації та Linux/Git-інтеграції; native Mac/Windows acceptance — verify.
- Додано `tools/git_safety.py`, точний allowlist emulator-fixtures,
  `.githooks/pre-commit`/`pre-push`, інсталятори Mac/Windows, `.gitattributes`,
  `GIT-SAFETY.md`, інтеграційні тести та окремий validation report.
- Pre-commit читає index, pre-push — snapshots усіх вихідних комітів/ref-ів;
  deleted/renamed/history/tag/multi-ref/force-push не приховують credential.
  Нові локальні credential-файли отримують точне правило `.gitignore`;
  вихідний код із підозрілим значенням блокується без автоматичного ignore.
- Жодних автоматичних видалень, змін індексу, commit, push чи rewrite історії.
  Installer не замінює інші hooks і зберігає активний guard поза робочою гілкою.
  Винятки для двох demo-wardrobe emulator-файлів — точний шлях/хеш/правило.
- Перевірки: 31 Git integration tests із тимчасовими репозиторіями й bare remotes;
  пакетний updater перевірено на повторне застосування, збереження custom rules,
  локальної конфігурації й відмову при конфлікті. Деталі: GIT-SAFETY-VALIDATION.json.
- На комп’ютері користувача нічого не активовано дистанційно. Після застосування
  малого пакета installer активує hooks у наявному репозиторії; після нового clone
  запустити installer ще раз. GitHub remote не створено й push не виконано.
- Межі: локальний hook, не гарантія всіх форматів секретів/персональних даних;
  перевіряються файли, не commit/tag messages. Native macOS/Windows/PowerShell
  не виконані; Python і shell/Git перевірені в Linux. APK/Android не збиралися.
- Android 0.13.4/code 23, Room/backup 8 і продуктова черга не змінені.
  Наступний крок: застосувати/активувати Git Safety на ПК/Mac, потім REL-01
  збірка 0.13.4 та PHOTO-04 → PHOTO-01–03/R1/R2 → FAM-01–04.
<!-- END WARDROBE-GIT-SAFETY -->


### Контрольна точка PHOTO-05 — 10 жовтня 2026, Codex

- Після запиту додаткових покращень погоджено лупу, спільну історію і м’якість/силу пензля; користувач попросив продовжити. 0.13.4/code 23 → 0.13.5/code 24, Room/backup 8. PHOTO-05: in_progress → verify. Усі попередні 45 ID, включно з DEV-01, збережені; разом із PHOTO-05–11 тепер 52.
- PhotoSessionRules.java/PhotoSession.kt: одна послідовність stroke/crop/rotate/source, однаковий replay для preview та PNG, узгоджене перетворення пікселів відновлення, cancellation. Наступна команда може використати попередній frame тільки з тим самим PhotoStore та source; undo/redo відтворюють історію. Preview «до» також прив’язано до поточного frame, щоб при зміні фото/акаунта не показувався попередній кешований кадр.
- PhotoEditor.kt/PhotoMaskEditor.kt: одна rememberSaveable-історія й фіксовані undo/redo для всіх інструментів; зміна інструмента не створює файл і не обнуляє гілку. Завершений crop-жест одразу вписує нові межі; source/reset undoable. Налаштування пензля зберігаються між інструментами; незавершені жести не комітяться.
- PhotoLoupeRules.java: лупа 3× над пальцем із перенесенням біля країв. PhotoMaskRules.java/PhotoMaskHistory.kt: м’якість 0–100%, сила 1–100%, максимальне покриття пікселя за один мазок, плавні краї, RGB без змін. Раніше збережені 6-польові сегменти читаються як чіткі/100%.
- PhotoStore.kt/WardrobeViewModel.kt: новий editPhotoSession записує PNG тільки на Apply, порожній результат відхиляється, невдалий кандидат очищується; наявні draft/ownership правила збережені. Постійні оригінали, Room/backup, дозволи й модель ML не змінені.
- UA/EN/PL/fallback: 9 нових рядків, 911 загалом. Інші пропозиції збережено як PHOTO-06–11/deferred, вони не заявлені реалізованими.
- Виконано 17 host-наборів: 3824 source/SQL, 11 XML, 67 DAO, 22649 нових Java assertions і 1774 попередніх редактора. Soft alpha, частота подій, змішані undo/redo, reset, RGB, геометрія, лупа та cancellation; камера 1600×1200 під -Xmx256m. PhotoSessionTest (4 методи) і PhotoSessionPersistenceTest (5 Android-тестів) підготовлено, не запущено. Докази docs/history/HOST-VALIDATION-0.13.5.json/RELEASE-VALIDATION-0.13.5.json.
- Gradle bootstrap exit 1 до компіляції: UnknownHostException: services.gradle.org. Kotlin/Compose/KSP, Gradle JUnit/lint, Android/PNG/жести/відновлення стану/TalkBack, ML на реальних фото та APK не підтверджені. Source/Java перевірки не замінюють native-приймання.
- Злито актуальний DEV-01 без втрати паралельного оновлення: збережені guard/allowlist/інсталятори/документи та початкова контрольна точка. Hooks і remote не змінювалися; Git Safety payload 1.0.0 лишився незмінним. Для вже застосованого addon є окремий baseline updater.
- Доставка: накопичувальний ZIP, повне дерево, JSON/patch від 0.8.0–0.13.4 та 0.13.4-git-safety. Повтор, часткове застосування, CRLF, конфлікти й локальні налаштування перевірені моделюванням; Windows/PowerShell не виконані. Історичні machine-звіти не переписано; специфікація 1.19 й окремий роадмап відповідають пакету.
- **Точний наступний крок:** REL-01 — зібрати 0.13.5 на налаштованому JDK/SDK й виконати PHOTO-TOOLS-0.13.5.md; далі PHOTO-01–04/R1/R2 з актуальною поведінкою історії та FAM-01–04. На кожному clone перевірити активацію Git Safety за DEV-01. До native-доказів PHOTO-05 лишається verify.


### Початок PHOTO-06–08 — 10 жовтня 2026, Codex

Користувач: «Продовж. Тестувати будемо все разом». Продовжуємо три запропоновані інструменти: видалення ділянки дотиком, контрастний фон для країв, повзунок до/після. Native-приймання переноситься на спільний етап після реалізації; автоматичні перевірки виконуємо зараз. База 0.13.5/code 24, ціль 0.13.6/code 25; Room/backup 8.


### Контрольна точка PHOTO-06–08 — 10 жовтня 2026, Codex

- Прямий запит: продовжити наступні запропоновані інструменти, «Тестувати будемо все разом». Native-приймання перенесено на спільний етап; не вимагати його перед звичайним продовженням погодженого коду. 0.13.5/code 24 → 0.13.6/code 25, Room/backup 8. PHOTO-06–08: deferred → in_progress → verify; усі 52 ID збережені.
- PhotoRegionRules.java: 4-connected fixed-seed selection, tolerance, незмінна BitSet-маска, черга без рекурсії, прозорі бар’єри, RGB без змін, cancellation, stride ALPHA_8. PhotoSessionRules.java/PhotoSession.kt додають region-команду, однакове відтворення для preview/export, undo/redo й пікселі restore після геометрії.
- PhotoMaskEditor.kt: підсвічування й підтвердження/очищення вибору, змінна чутливість, помилки/повтор, блокування повного стирання, tap без drag/multitouch. Маска прив’язана до frame/координат/допуску; застаріле обчислення не можна застосувати. PhotoEditor.kt чекає рішення про pending selection; undo/інший інструмент скасовує незастосований вибір. Файли не створюються до Apply.
- PHOTO-07: auto/білий/кремовий/темний/фіолетовий/сітка — тимчасова підкладка. PHOTO-08: clip-порівняння джерела до ручних alpha-правок і результату, однакові crop/rotate та зум; PhotoInspectionRules.java розраховує роздільник по видимій області. Це не відновлення початкового фото до ML. Нових оригіналів, схем, дозволів, API чи моделі немає.
- UA/EN/PL/fallback: 24 нові рядки, 935 загалом. 17 host-наборів pass, 3925 source/SQL, 11 XML, 67 DAO; 52357 нових production Java assertions, 22649 попередніх сесії, 1774 редактора. Незалежний union-find oracle для 512 масок 3×3 × усі seeds, RGB/alpha/прозорі розриви, градієнт, undo/redo/restore, cancellation, камера 1600×1200 під -Xmx256m і divider. Докази docs/history/HOST-VALIDATION-0.13.6.json/RELEASE-VALIDATION-0.13.6.json.
- PhotoRegionTest: 4 JUnit-методи; PhotoRegionPersistenceTest: 4 Android-тести (codec/redo, PNG, ALPHA_8 stride, порожній результат) підготовлено, не запущено. Kotlin/Compose, Gradle JUnit/lint/assemble, UI/жести/TalkBack/пристрої ще не перевірені. Нового APK немає. Gradle bootstrap цього релізу не запускали; попередній UnknownHostException належить 0.13.5.
- Накопичувальний пакет 0.13.6 з повними джерелами, JSON/patch 0.8.0–0.13.5 і 0.13.4-git-safety, локальна конфігурація/backup/conflicts/repeat збережені. Пакет перевірено Python-моделюванням, Windows/PowerShell не виконані. DEV-01 та історичні machine-звіти незмінні; роадмап і специфікація 1.20 узгоджені.
- Наступне приймання: один актуальний білд, PHOTO-INSPECTION-0.13.6.md та PHOTO-01–05/R1/R2. PHOTO-09–11 (роздільність, повторна обробка старих фото, інтерактивна ML-модель) лишаються deferred. FAM-01–04 збережено. Статуси verify не підвищувати до done лише за host-перевірками.


### Контрольна точка PHOTO-09–10 — 11 жовтня 2026, Codex

- Пряме продовження «Давай далі» / «Далі», native-тестування відкладено користувачем. 10–11.10.2026: deferred → in_progress (Codex) → verify. 0.13.6/code 25 → 0.13.7/code 26; Room/backup 8. Усі 52 ID збережені.
- PhotoResolutionRules.java, PhotoStore.kt, PhotoAnalyzer.kt, PhotoViewer.kt: очищене полотно до 2048², 8% margin, без збільшення доступної предметної частини. Heap tiers 1024/1536/2048, source long side 1800/2560, pixel caps. Power-of-two bounds decode також для старого великого фото. In-memory preview до 768, окремий full viewer; JPEG import quality 92. Порожній cutout відхиляється.
- PhotoReprocessStore.kt/PhotoReprocessor.kt/PhotoReprocessScreen.kt + CatalogScreen/Ui/ViewModel/Data: вибір до 500 речей одного гардероба, одна послідовна обробка і кандидат, перегляд/зум/розмір файла/повтор/зберегти/пропустити/скасувати. Старий кінцевий знімок є джерелом; видалений оригінал та деталі не відновлюються. Метадані й fingerprint не перезаписуються.
- Атомарний account-private файл черги: allocation до PNG, відновлення review, розпізнавання вже виконаного DB commit, durable retired IDs для очищення. SQL змінює тільки photoId за збігом item/wardrobe/source; stale кнопка/Retry прив’язані до entry/token. Джерело захищене чергою й редакторною чернеткою, після звільнення проходить звичайне прибирання. CloudArchive не застосовує інший snapshot поверх черги. БД/backup/cloud snapshot формат не змінений.
- Перевірено 4024 source/SQL, 11 XML, 958 локалізованих рядків (23 нових), 68 DAO. 18 host-наборів, 108013 нових Java assertions, 14 фактичних SQLite CAS checks. Source/SQL й host synthetic 2048² не замінюють native/ML вимірювання. docs/history/HOST-VALIDATION-0.13.7.json і docs/history/RELEASE-VALIDATION-0.13.7.json містять фактичні результати.
- 9 Android-тестів черги + 3 роздільності підготовлені, crop-тест малих джерел оновлений; не запущені. Kotlin/Compose/KSP, JUnit/lint/assemble, native-пам’ять/PNG/життєвий цикл/зум/TalkBack/ML та backup-профіль ще не підтверджені. Нового APK і нової Gradle-спроби немає.
- Накопичувальний ZIP/повні джерела та updater JSON/patch для 0.8.0–0.13.6 і 0.13.4-git-safety; конфлікти, повтор, локальна конфігурація й історичні докази збережені. Перевірка часткового оновлення з 0.13.5 виявила можливість пропуску старих потрібних файлів: payload тепер перевіряє комплектність усіх історично відмінних файлів через ensure-entries, не перезаписує конфлікти. DEV-01 без змін. Роадмап редакції 10 і UX 1.21 узгоджені.
- Наступний крок: спільне native-приймання PHOTO-QUALITY-0.13.7.md → PHOTO-INSPECTION-0.13.6.md → PHOTO-01–05/R1/R2. PHOTO-11 лишається deferred: дослідити інтерактивний ML для потрібного предмета, сумісність/якість/розмір/витрати. FAM-01–04 збережено. verify не підвищувати до done лише за host-перевірками.


### Контрольна точка PHOTO-12–14 — 11 жовтня 2026, Codex

- Пряме погодження «Роби», продовження «Далі»: in_progress → verify. 0.13.7/code 26 → 0.13.8/code 27. Room/backup 8, application ID, підпис, Firebase/локальна конфігурація не змінені. До попередніх 52 ID додані PHOTO-12–17, разом 58. Решта запропонованих ідей збережена як deferred; FAM-01–04 і DEV-01 лишаються.
- PhotoPrecisionRules.java: bounded alpha morphology −8…+8 пкс, separable feather 0…6, лінійні проходи й cancellation; even-odd pixel-centre polygon до 128 точок. Розширення/відновлення лише в межах доступного джерела, без генерації RGB.
- PhotoSessionRules.java/PhotoSession.kt: edges/polygon/protection команди, захист пензля та регіону, crop/rotate маски захисту, replay/undo/redo. PhotoPrecisionEditor.kt/PhotoProtection.kt: tap-полігон, координатні повзунки, масштаб/переміщення, підкладки, перегляд до кроку, підтвердження/очистити, видимий захист. PhotoMaskEditor показує захист і використовує той самий replay для preview.
- PhotoSessionDraft.kt/EditorDraftStore/ViewModel/PhotoEditor/Ui/PhotoStore: історія й redo у наявній приватній чернетці, атомарний запис разом із cleanup ledger, збереження після кожного завершеного кроку/Undo/Redo. Autosave полів зберігає фотоісторію тільки для тієї самої речі/фото. Відновлення бере актуальну чернетку, а не старий SavedInstanceState. Немає додаткової постійної копії оригіналу; source/reset/redo references захищені до Apply/Discard/завершення чернетки. Apply записує один PNG, фіксує новий photo ID і видаляє recipe одним commit. Allocation до PNG; owner/photo guard, orphan cleanup при помилці/скасуванні. Батьківська форма пропонує Resume й вимагає розв’язати незавершені правки перед Save; заміну фото/автообробку заблоковано до цього.
- Незавершений жест/непідтверджений preview та зум/параметри пензля не є завершеними кроками і не обіцяються після cold restart. Захист не забороняє crop чи reset source. Після Apply/Discard історія сесії завершена; фінальне фото не зберігає захисний overlay. Пошкоджена recipe показує помилку й потребує явного discard, без тихого перезапису.
- Фактично виконано 18 host-наборів, 4147 source/SQL, 11 XML, 987 рядків (29 нових UA/EN/PL/fallback), 68 DAO. 588864 нових production Java assertions (незалежний raster oracle, polygon boundary, захист/геометрія/cancellation), synthetic 2048² під -Xmx256m. Існуючі photo suites і 14 SQLite guards також pass; деталі у HOST/RELEASE-VALIDATION-0.13.8. Це не Android benchmark.
- PhotoDraftRecoveryTest: 8 підготовлених native-сценаріїв (реальні prefs/PNG/Room, redo/fields/акаунти/пошкодження/джерела/cleanup/preview-export). Доданий JUnit wrapper PhotoPrecisionTest. Kotlin/Compose/KSP, Gradle/JUnit/lint/assemble й Android/UI тести не запускалися, APK відсутній. Попередню Gradle-помилку не видавати за нову спробу.
- Накопичувальний updater 0.8.0–0.13.7 + 0.13.4-git-safety; повні джерела, конфлікти/повтор/часткове оновлення/локальна конфігурація перевіряються моделюванням, PowerShell/Windows не виконувалися. Історичні докази не змінено. Роадмап редакції 11 і UX 1.22 узгоджені.
- Наступний крок: спільний native-run PHOTO-PRECISION-0.13.8.md → PHOTO-QUALITY-0.13.7.md → попередні PHOTO/R1/R2. PHOTO-15–17 можна продовжувати після нової вказівки; PHOTO-11 дослідити окремо. verify не підвищувати до done без потрібних доказів.


### Контрольна точка CAT-01 — 12 жовтня 2026, Codex

- Прямий запит: можливість додавати власні категорії, знаходити їх, пошук підкатегорій. in_progress → verify. 0.13.8/code 27 → 0.13.9/code 28. Додано один ID CAT-01, збережено всі 58 попередні; разом 59.
- Підтверджена база: текстовий пошук уже містив subcategory, але фільтра й видимого поля не було; категорії були фіксовані. CategoryControls/SelectionControls/CatalogScreen/Ui додають створення категорії з базовим типом, власні назви на картках, видиме поле підкатегорії та вибір наявних, окремий searchable filter. CategoryRules.java нормалізує case/Unicode spaces/apostrophes; усі слова запиту шукаються по назві, custom/base title, subcategory, опису й місцю.
- CategoryData/Data: CustomCategory з UUID/wardrobe/name/key/base; nullable Garment.customCategoryId FK. 8→9 без destructive migration, old=null, unique key/wardrobe; SQL guards для всіх DAO writers. Підбір продовжує користуватися стандартним base category. Категорії без речей теж зберігаються. Перейменування/видалення та окремий довідник порожніх підкатегорій поза цим запитом.
- WardrobeViewModel/Ui: flow, збереження, чернетка, copy previous, ML не заміняє custom category. Ручна зміна категорії очищує залежну підкатегорію. Фільтри категорії/підкатегорії обмежено поточним гардеробом; інші фільтри не скидаються при зміні категорії.
- Backup/BackupStateRules/ScenarioData: version9, перенос custom IDs разом із garment та purchase receipt, validation до restore. 1–8 і старі receipts підтримані з null. CloudArchive/CloudBackend: 8/9 читання, version9 запис, stable IDs, dirty triggers. Firestore rules приймають 8/9 й відхиляють downgrade; owner guard не змінено. Deployment правил і оновлення обох телефонів потрібні до cloud-приймання; live deployment не виконано.
- Виконано 19 host-наборів, 31 production Java assertion, SQL 8→9 із seeded outfit/wear/item references, 7 відхилених записів, uniqueness/wardrobe isolation/reopen/rollback/FK/dirty/echo. Source/XML/70 DAO queries pass; 998 локалізованих рядків (11 нових UA/EN/PL/fallback). Докази HOST/RELEASE-VALIDATION-0.13.9.json.
- Шість CategoryPersistenceTest методів (Room8→9, wardrobe isolation, backup/receipt, cloud, v8 compatibility, bad reference) та JUnit wrapper підготовлено, не виконано. Firebase emulator format-upgrade/downgrade test додано, не виконано. Kotlin/Compose/KSP/Room generation, Gradle unit/lint/assemble, native/UI/APK і Windows/PowerShell не виконані; нового Gradle bootstrap не запускали. Старий APK не входить до нового пакета.
- Накопичувальний updater 0.8.0–0.13.8 + 0.13.4-git-safety, історичні звіти незмінні, DEV-01 збережено. Конфлікти/повтор/часткові оновлення й локальна конфігурація перевіряються окремим Python-моделюванням. README, AGENTS, roadmap редакції 12 та UX 1.23 узгоджено.
- Наступний крок: поточний Android build + CATEGORIES-0.13.9.md і погоджене спільне PHOTO/R1/R2-приймання. CAT-01 лишається verify до native-доказів; FAM-01–04 й PHOTO-15–17 не загублені.

### 12 жовтня 2026 — COL-01, Codex, in_progress → verify

Пряма вимога користувача: додавання своїх кольорів. Обсяг: власні назви кольорів гардероба, редактор речі, пошук і фільтр, Room migration, backup/cloud і purchase receipt. Native-приймання залишається спільним.

- Реалізовано 0.13.10/code 29. Додано COL-01, усі 59 попередні ID збережено; разом 60.
- Змінені ColorData/ColorRules/ColorControls, Data/Ui/CatalogScreen/WardrobeViewModel, Backup/BackupStateRules/ScenarioData/CloudArchive/CloudBackend, локалізації, cloud rules/tests, host_schema/run_host_checks і нові тести.
- Власні назви й пошук у селекторі; гардеробний scope, нормалізація/повтори; базовий колір для підбору; деталі/чернетки/копіювання. ML не стирає вибір. Загальний пошук за власним/базовим кольором, точний custom-фільтр і базовий із відтінками.
- Додаткова Room 9→10, nullable customColorId, FK/SQL guards. Backup 10 remap/receipt; backup 1–9 без customColorId. Cloud 8/9/10, запис 10, dirty/echo, без downgrade та розширення прав. Rules підготовлені, не розгорнуті.
- 20 host-наборів pass: 22 нові production Java assertions; реальна SQLite 9→10 з категоріями/речами/образами/носінням, 9 заборонених записів, reopen/rollback/FK/dirty/echo. 4235 source/SQL перевірок, 11 XML, 1006 рядків локалізації, 72 DAO-запити. Докази HOST/RELEASE-VALIDATION-0.13.10.json.
- 6 ColorPersistenceTest методів та JVM wrapper підготовлені; emulator-тест форматів оновлено. Kotlin/Compose/KSP/Room generation, Gradle unit/lint/assemble, Android/UI/APK, Firebase emulator/live deployment та PowerShell/Windows не виконані. Нового Gradle bootstrap не запускали.
- Накопичувальний updater 0.8.0–0.13.9 перевіряється окремим Python-моделюванням; збережено локальну конфігурацію, історичні звіти й DEV-01. README/AGENTS/роадмап 13/UX 1.24 узгоджено.
- Наступна дія: актуальна Android-збірка та COLORS-0.13.10.md разом із CAT-01/PHOTO/R1/R2. Спільне native-приймання лишається домовленістю; COL-01 — verify. FAM-01–04 та PHOTO-15–17 залишаються в беклозі.

### FAM-01 — 13 жовтня 2026, Codex, in_progress → partial

Продовження погодженого роадмапу: підготовка даних сімейних гардеробів, ізольований перенос одного гардероба, модель ролей/ревізій, відновлюваний журнал міграції; без надання міжаккаунтного доступу до реалізації FAM-02–04.


- Реліз 0.14.0/code 30, Room/backup 10 без нової Room-міграції. Усі 60 task ID збережені; COL-01/CAT-01/PHOTO/R1/R2 залишаються verify до спільного native-приймання.
- Змінено Backup, WardrobeViewModel, Ui, SingleWardrobeBackupControl, WardrobeScope, WardrobeSnapshots, FamilyRules, FamilyEnvelope, FamilyMigrationStore, 5 рядків UA/EN/PL/fallback, host runner і нові тести. Права live Firebase не змінено.
- Копія одного гардероба проходить існуючий ZIP/readback verification; unknown/deleted scope відхиляється до запису. Retry зберігає scope, застарілий callback не стає повним експортом. Вибіркова копія не оновлює показник успішного backup всього акаунта.
- Журнал виділяє сталі remote/archive UUID до початку операцій, переживає повторне відкриття, перевіряє account identity, ревізію й checksum; пошкодження зупиняє повтор. Це локальний компонент майбутнього переносу, без виконаної server migration.
- Фактично виконано 21 host-набір: 4264 source/SQL, 11 XML, 1011 локалізованих рядків, 72 DAO; 70 нових production Java assertions (дві незалежні групи, спільне фото, 500 додаткових речей, неправильні посилання, ролі, lease, CAS і paths). Докази HOST/RELEASE-VALIDATION-0.14.0.json.
- 8 FamilyFoundationTest методів і JUnit wrapper підготовлено, не виконано. Kotlin/Compose/KSP/Room, Gradle unit/lint/assemble, Android AtomicFile/ZIP/UI, Firebase emulator/live та PowerShell/Windows не запускали; нового APK немає. Source/Java checks не підтверджують ці сценарії.
- Накопичувальний updater 0.8.0–0.13.10 має 20 баз; Python-моделювання перевіряє повтор, CRLF, конфлікти, змішані бази та збереження локальної конфігурації. README/AGENTS/ADR/роадмап 14/UX 1.25 узгоджені. Історичні release-звіти збережені.
- Точна наступна реалізація: FAM-01/03 — backend create/resume/commit з перевіреним UID, envelope/ZIP validation, membership та CAS/ідемпотентністю на емуляторах; далі FAM-02 — запрошення/роль/відкликання через цей API. Email-провайдер, billing/ліміти та оцінка пілоту потрібні перед живою доставкою/розгортанням. Native-приймання всього поточного коду лишається спільним і не блокує погоджене продовження коду.


### FAM-01/03 — 13–14 жовтня 2026, Codex, in_progress → partial

- Продовження погодженого роадмапу. 0.14.0/code 30 → 0.14.1/code 31;
  Room/backup 10, усі 60 ID та попередні функції збережені.
- `cloud/family-functions`: service, schema, ZIP/photo validator, Firebase
  adapters/runtime та 4 callable endpoints. `family-storage.rules` і
  `firebase.family.example.json` окремі від чинного private deployment.
- Auth UID/email_verified/revocation, без ролі з request; reservation,
  upload binding/expiry/limits, CAS/ідемпотентний commit з повторною перевіркою
  membership; strict 16 collections/receipts/photos та immutable archive.
- Виконано 29 Python domain tests, 20 Firebase integration tests (18 сценаріїв
  на реальних Firestore transaction, Storage adapter та callable/Auth flow),
  6 Firestore/Storage rules tests і 22 host-набори: 21 pass, перевірка Android client config не виконана (файл app/google-services.json відсутній у пакеті). Source/SQL, Java, XML,
  SQLite та backend мають окремі докази. Details: RELEASE-VALIDATION-0.14.1.json.
- Storage emulator не доводить production generation preconditions: виявлений
  повторний GCS upload, доданий read/verify fast replay path і збережений
  if_generation_match=0 для першого create; потрібний production smoke test.
  Фінальні об'єкти не копіюють metadata staging і не містять download tokens.
- Новий source-only Mac/Linux updater (Bash → Python), CheckOnly, backup,
  rollback, заборона symlink/protected files, перевірені хеші відомих версій;
  PowerShell підтримує той самий payload. Історичні context patches для
  незмінених частин збережені. Фактичні updater тести — package-validation.json.
  Windows/PowerShell не запускалися; перевірка Linux не є native Mac-прийманням.
- Kotlin/Compose/KSP, Android/Gradle/UI/APK і live Firebase не виконані.
  Android код змінив лише номер релізу; нового family UI ще немає.
- Документація: AGENTS/README/ADR/FAMILY-BACKEND/роадмап 15/UX 1.26.
  Native-статуси verify не підвищено до done.
- Точний наступний крок: Android scoped ZIP + authenticated transport,
  account/runtime guard, FamilyMigrationStore та новий archive ID після
  expiry; ізоляція shared data від private/scheduled backup. Далі FAM-02
  invite/accept/decline/role/revoke та UI. Перед live — GC, App Check, бюджет,
  email provider, GCS smoke test. Живого deployment або надсилання листів немає.


### FAM-01/03 — 16 жовтня 2026, Codex, in_progress → partial

- Реалізовано Android-перенос одного гардероба: `FamilyBackend`, `FamilyTransferEngine`, `FamilyArchive`, `FamilyTransferController`, `FamilyTransferControl`; підключення в ViewModel/Settings і firebase-functions.
- Збережено ZIP/журнал за акаунтом. Перевірка попереднього commit до повторного upload; recovery без локального ZIP, structured expiry з новим archive ID у тому самому remote ID, явний restart лише за порожнього head.
- Server-head readback перевіряє identity/hash/path/size/revision; локальна ревізія перевіряється під write lock. Зміни після snapshot не видаються за синхронізовані. Session lifetime/UID guards та expectedActorUid захищають асинхронну зміну акаунта.
- UI: вибір і підтвердження, прогрес, resume/retry, restart, верифікація email, помилки UA/EN/PL. Локальний гардероб збережено; це початкова копія, не постійна сімейна синхронізація.
- 15 JVM-тестів production coordinator і 3 Android storage-тести додані; backend callable integration перевіряє actor binding та ARCHIVE_MISSING. Точний виконаний результат і межі: `RELEASE-VALIDATION-0.14.2.json`, `HOST-VALIDATION-0.14.2.json`, `FAMILY-ANDROID-0.14.2.md`.
- Room/backup 10, старі функції та всі task ID збережені. APK не передається, live deployment не виконано. Backend також слід оновити перед запуском Android-функції.
- Наступна реалізація: окремий shared cache/DB/photo ownership, виключення чужих даних із private/portable/scheduled backup; FAM-02 invite/accept/role/revoke; далі continuous shared sync/conflicts. Native-приймання залишається спільним за домовленістю.

- 16.10: виправлено наданий користувачем build blocker 0.14.1: `PhotoEditor` захоплює maxHeight до входу в ColumnScope; literal 0%/100% у `photo_brush_soft_hint` мають `formatted="false"` в UA/EN/PL/fallback.

- Виконано: 15 Kotlin/JVM coordinator-тестів (standalone Kotlin 2.0.20), 29 backend domain tests, 20 Firebase integration tests, 6 rules tests; 22 host-набори: 21 pass, client config NOT CONFIGURED. Повна Android-компіляція не підтверджена: Gradle blocked на infrastructure/cache lock; native та Windows PowerShell-приймання не виконано.


### 2026-10-16 — FAM-01/03, callable hotfix1 — Codex



- family/callable_wire.py: суворе декодування Int64Value/UInt64Value лише для
  size/baseRevision запиту begin_upload. Уже декодовані int також підтримуються.
- main.py: виклик decoder всередині наявної межі FamilyError; повідомлення про
  відмови в server log без payload, UID, токенів і вмісту гардероба.
- Перевірки розміру, ревізії, прав, ZIP і checksum залишаються чинними.
- 6 нових host-тестів, у тому числі відтворення старого збою й повний service flow.
- Callable emulator scenario тепер використовує Android-подібний wrapped size.

#### Перевірки

35 backend host-тестів PASS (29 попередніх + 6 нових).
Журнал: release-evidence/family-callable-hotfix1.log.
Firebase emulators, live Firebase й тест на телефоні цього разу НЕ запускалися.
Android-код і APK не змінюються; Android build не запускався.
Python updater перевірено на базі 0.14.2, повторному застосуванні та конфлікті.
PowerShell updater не запускався: pwsh недоступний у середовищі.


### 2026-10-17 — FAM-01/03, Android progress hotfix2 — Codex

- Доказ: наданий wardrobe-crash.txt, два падіння com.familywardrobe.app о 19:02:09/19:02:51, ProgressBarRangeInfo current must not be NaN. Старий lambda міг читати нульові counters після перевірки total>0; standalone regression відтворює NaN.
- FamilyTransferProgress.kt та FamilyTransferControl.kt: один immutable state snapshot, callback із попередньо обчисленим finite fraction; total<=0/не upload → indeterminate. Counter clamp до float conversion. Збережено транспорт, auth guards, retries, Room/backup10 та локальні дані.
- Версія app/build.gradle.kts: 0.14.3-family-hotfix2/code33. Новий тест FamilyTransferProgressTest.kt, оновлено tools/check_family_transfer.py, AGENTS/README/FAMILY-PROGRESS-HOTFIX2.md. Пакет включає повне дерево й накопичувальний updater для 0.14.2 та hotfix1.
- Виконано 19 Kotlin/JVM JUnit tests (15 coordinator + 4 progress), Kotlin2.0.20/JDK17. Python updater: dry-run/apply/reapply/parity/protected config/conflict/CRLF. Android SDK/Gradle environment відсутнє; Android/Compose/KSP build, lint, APK, native та PowerShell не виконано. Це не UI-приймання.
- Користувач розгорнув hotfix1 functions; morning commit logs доходять до ARCHIVE_MISSING із auth VALID. Android18:48 підтвердив Storage403. Після console metadata-null guard надійшло падіння UI. Немає доказу остаточного усунення403 чи успішного commit. Точні timings поза crash є з наданих користувачем журналів, не з доступу агента до Firebase.
- Console rules drift: missing/null/identity contentEncoding та missing/null/empty metadata guards ще не перенесені до cloud/family-storage.rules. Не redeploy старих storage rules; після перевірки синхронізувати й виконати rules tests. Backend не приймає truthy content_encoding (включно з identity), production значення ще невідоме.
- Наступна дія: оновлення APK тим самим ключем без uninstall/clear data → Resume / check через upload/commit/verify → підтвердити результат та збереження локального гардероба. За ACCESS перевірити свіжий upload ticket і rules. FAM-01/03 partial, решта roadmap ID/статусів збережені.


### 2026-10-17 — PHOTO-03, делікатне очищення / hotfix3 — Codex

- Прямий запит користувача: зменшити силу автоматичного видалення, надані скриншот і посилання на оригінал; сорочка однотонна, сітка з’явилася після обробки.
- PhotoCutoutRules.java: пороги gentle 0.15–0.70, колірний flood лише stronger; tiny overshoot ±0.00001 clamp, NaN/Inf/значні out-of-range відхиляють маску цілком. RGB та наявна alpha не збільшуються/не вигадуються.
- PhotoCutoutCases.java/PhotoCutoutTest.kt: float32 regression спочатку впала на старому production-коді; після правки pass в обох режимах. Додані gentle dominance, malformed rejection і збереження ручної alpha. UA/EN/PL/fallback: «Очистити делікатно» й пояснення компромісу та заміни збереженої пошкодженої вирізки оригіналом.
- Виконано 4 релевантні host-набори: photo rules (12+7 сценаріїв та 61 assertions), editor/session/region/precision, resolution/14 SQLite guards, 4390 source/SQL checks. Журнали release-evidence/0.14.4-photo-hotfix3; результати RELEASE-VALIDATION-PHOTO-HOTFIX3.json.
- Gradle wrapper зупинився до компіляції: Network is unreachable при завантаженні Gradle8.11.1. Kotlin/Compose, Gradle JUnit, lint, APK, Android runtime/ML і PowerShell не виконано. Сітку на телефоні остаточно не пояснено без фактичної маски; synthetic regression підтверджує дефект у нашій обробці чисел.
- Повні джерела, актуальні AGENTS/README/роадмап і накопичувальний payload зберігають family hotfix1/2. Python updater: застосування/повтор/часткове оновлення/CRLF/conflicts/protected config; див. package-validation-hotfix3.json. Firebase rules та live backend не змінювалися.
- Наступний крок: зібрати code34 тим самим ключем, замінити фото оригіналом, порівняти gentle/stronger і перевірити деталі на телефоні. Якщо сітка лишилася — діагностика raw mask/PNG/display. Потім повернутися до FAM-01/03 shared-cache та FAM-02. Усі попередні ID/статуси збережені.

## Архів попереднього README

# Android 0.14.4-photo-hotfix3 — делікатне видалення фону

Code 34, Room/backup 10. Автоматичний режим зберігає більше деталей тканини;
сильніше очищення — окрема дія. Похибка маски біля 1.0 більше не створює
прозорі дірки. 61 mask/backdrop assertion pass; причина сітки саме на телефоні
потребує повторної перевірки оригіналу. Gradle build заблокований мережею,
нового APK немає. [Інструкція, докази та приймання](PHOTO-CLEANUP-HOTFIX3.md).
Збережені family hotfix1/2; для фото-виправлення Firebase deploy не потрібен.

# Android 0.14.3-family-hotfix2 — виправлення падіння прогресу

Code 33. Виправлено `current must not be NaN` після Resume / check:
callback індикатора використовує безпечне значення з одного snapshot стану.
19 Kotlin/JVM-тестів pass. Потрібно зібрати й установити новий APK; Android/UI
приймання ще відкрите. [Інструкція та межі перевірки](FAMILY-PROGRESS-HOTFIX2.md).
Firebase deploy для цього виправлення не потрібен.

# Попередній серверний hotfix1 для Android 0.14.2

Виправлено приймання Android Long у family_begin_upload. APK перебудовувати
не потрібно. Інструкція: [FAMILY-CALLABLE-HOTFIX1.md](FAMILY-CALLABLE-HOTFIX1.md).
35 host-тестів pass; live перевірка залишається відкритою.

# Wardrobe Android 0.14.2

## Поточне оновлення 0.14.2 — Android family transfer

Code 32, Room/backup 10. Налаштування → «Сімейний перенос»: вибір гардероба,
підтвердження, scoped ZIP, upload/commit/readback, durable retry і expiry,
захист акаунтів та повідомлення UA/EN/PL. Локальні дані залишаються;
постійна сімейна синхронізація й запрошення — наступні. Оновіть family backend
разом із клієнтом. Деталі: [FAMILY-ANDROID-0.14.2.md](FAMILY-ANDROID-0.14.2.md),
фактичні перевірки: RELEASE-VALIDATION-0.14.2.json. Виправлено build blocker
PhotoEditor (maxHeight) і literal % у підказці пензля. Повна Android-компіляція
не підтверджена; без live deployment та нового APK.


## Попереднє оновлення 0.14.1 — серверна частина сімейних гардеробів

Code 31, Room/backup 10. Додано довірений Firebase API переносу одного
гардероба, перевірку ZIP/фото/зв’язків, ролей і ревізій, повтор після збою та
захист від конкурентного перезапису. 29 backend domain tests, 20 Firebase
integration tests і 6 правил доступу; 22 host-набори: 21 pass, перевірка Android client config не виконана (файл app/google-services.json відсутній у пакеті). Деталі й фактичні
обмеження: [FAMILY-BACKEND-0.14.1.md](FAMILY-BACKEND-0.14.1.md).

**FAM-01/03 partial:** Android family transport, запрошення та shared UI
ще не підключені. Вихідний код, без нового APK і без live deployment.
Чинний private cloud і локальна конфігурація збережені.

## Попереднє оновлення 0.14.0 — основа сімейних гардеробів

Code 30, Room/backup 10. Налаштування → «Зберегти один гардероб»:
пошук і вибір гардероба, усі його кінцеві фото/образи/історія/зв’язки та
власні категорії/кольори. Існуюче відновлення додає новий гардероб.
Повтор після помилки зберігає вибір; ця копія не замінює повний backup.

FAM-01 — partial: у коді додано ізоляцію даних, family envelope, контракт
ролей/ревізій/lease і локальний журнал переносу. Запрошення, серверний
перенос і міжаккаунтний доступ ще не підключені; private cloud не розкрито.
Наступне — довірений backend і FAM-02–03.

21 host-набір pass, 70 нових production Java assertions. Вісім Android-тестів
підготовлені, не виконані. **Вихідний код, нового APK немає.** Приймання
залишається спільним. Деталі та матриця: [FAMILY-FOUNDATION-0.14.0.md](FAMILY-FOUNDATION-0.14.0.md),
архітектурне рішення: [ADR-FAMILY-WARDROBES.md](ADR-FAMILY-WARDROBES.md).

## Попереднє оновлення 0.13.10 — власні кольори

Code 29, Room/backup 10. «Колір → Додати власний колір» у редакторі
й фільтрах каталогу; власна назва та базовий колір для підбору образів.
Пошук за кольорами, точний фільтр відтінку, деталі, чернетка й копіювання
попереднього поля. Власні категорії/підкатегорії 0.13.9 збережені.

20 host-наборів pass, зокрема 22 Java assertions та SQLite 9→10/guards.
**Вихідний код, нового APK немає.** Native-приймання лишається спільним.
Для cloud потрібні включені Firestore rules (8/9/10, без downgrade)
та оновлення обох клієнтів. Живі правила тут не розгортали.
[COLORS-0.13.10.md](COLORS-0.13.10.md) містить деталі та матрицю приймання.

## Попереднє оновлення 0.13.9 — власні категорії й пошук підкатегорій

Code 28, Room/backup 9. Створення власної категорії у каталозі та редакторі,
базовий тип для підбору образів, окремий пошук/фільтр підкатегорії,
підкатегорія у видимій частині форми. Пошук за назвами стандартної/власної
категорії й підкатегорії, Unicode-нормалізація, назви на картках.
Категорії прив’язані до гардероба; backup/cloud та purchase receipts оновлені.

19 host-наборів pass; 31 production Java assertion і міграція 8→9 із
перевіркою захисту даних виконані. **Вихідний код, нового APK немає.**
Шість native-тестів підготовлені, не запущені. Для cloud потрібні оновлення
обох клієнтів і deployment нових `cloud/firestore.rules`; живий Firebase тут
не змінювали. Інструкція й матриця: [CATEGORIES-0.13.9.md](CATEGORIES-0.13.9.md).

## Попереднє оновлення 0.13.8 — краї, ласо та відновлення редактора

Code 27, Room/backup 8. «Краї та ласо»: звуження/розширення маски, згладжування,
полігон із підтвердженням, захист ділянок у всіх alpha-інструментах. Спільні
Undo/Redo, фінальне зображення без overlay. Підтверджені кроки зберігаються
разом із чернеткою речі та відновлюються після закриття/перезапуску. Доступні
Apply, повернення до форми зі збереженням і явне відкидання правок.

18 host-наборів і 588864 нових Java assertions пройшли; 8 Android-тестів
підготовлені, не виконані. **Вихідний код, нового APK немає.** Kotlin/Compose,
Android-пам’ять, UI та lifecycle перевіримо на спільному етапі, як домовлено.
Деталі: [PHOTO-PRECISION-0.13.8.md](PHOTO-PRECISION-0.13.8.md). PHOTO-12–14 — verify.

## Попереднє оновлення 0.13.7 — якість і вибрана переобробка фото

Code 26, Room/backup 8. Очищені фото до 2048×2048 залежно від джерела й heap;
малі предмети не збільшуються. Картки читають окремий preview в пам’яті.
У каталозі вибір речей → групові дії → «Переобробити фото»: один кандидат,
перегляд до/після, зум, розмір файла, зберегти/пропустити/скасувати.
Черга відновлюється після перезапуску; фото замінюється лише після підтвердження
і збігу поточного джерела. Метадані, спільні фото й чернетки захищені.

18 host-наборів, 108013 нових Java assertions і 14 SQL перевірок пройшли.
PHOTO-09–10 — verify. **Вихідний код, нового APK немає.** Android-приймання
відкладене користувачем на спільний етап. Деталі: [PHOTO-QUALITY-0.13.7.md](PHOTO-QUALITY-0.13.7.md).
PHOTO-11 потребує дослідження; попередні PHOTO-01–08/R1/R2/DEV-01 включені.

## Попереднє оновлення 0.13.6 — ділянки фону й перевірка результату

Code 25, Room/backup 8. Виділення суміжного фону дотиком із чутливістю,
підсвічуванням і підтвердженням; один крок undo. Шість підкладок для країв та
повзунок до/після ручного очищення. Фото й RGB не перефарбовуються; постійного
оригіналу немає. [PHOTO-INSPECTION-0.13.6.md](PHOTO-INSPECTION-0.13.6.md).

17 host-наборів і 52357 нових Java assertions пройшли. PHOTO-06–08 — verify.
За вказівкою користувача тестування Android виконаємо разом на одному білді.
**Новий APK не зібрано.** Попередні фотофункції, R1/R2 і DEV-01 збережені.
PHOTO-09–11 залишаються подальшими ідеями.

## Попереднє оновлення 0.13.5 — лупа, спільна історія, м'який пензель

Code 24, Room/backup 8. Лупа 3× показує край під пальцем. Пензель має м'якість
0–100% і силу 1–100%. Undo/redo спільні для стирання, відновлення, обрізання,
повороту й повернення джерела: зміна інструмента більше не скидає історію.
Файл створюється один раз через «Застосувати». Деталі та native-матриця:
[PHOTO-TOOLS-0.13.5.md](PHOTO-TOOLS-0.13.5.md).

17 host-наборів, 22649 нових Java assertions і 1774 попередніх пройшли.
**Вихідний код, без нового APK.** Gradle bootstrap: UnknownHostException:
services.gradle.org. Android/Compose, жести, PNG та відновлення стану ще не
прийняті; PHOTO-05 — verify. Усі попередні PHOTO-01–04/R1/R2 збережені.
Git Safety DEV-01 включено без зміни guard; перед commit/push див. GIT-SAFETY.md.
Решта запропонованих фотофункцій — PHOTO-06–11 у роадмапі, не частина цього коду.

## Попереднє оновлення 0.13.4 — історія редагування й точний пензель

Code 23, Room/backup 8. «Назад/Вперед» для цілих мазків, обрізання й повороту;
діаметр пензля від 1 пкс до попереднього великого розміру, повзунок, −/+ і швидкі значення.
Дотик без перетягування теж працює. Скасований жест зберігає redo; новий мазок його очищує.
Перехід між фоном і геометрією застосовує правки й починає нову історію для нового джерела.
Деталі та матриця: [PHOTO-EDITOR-0.13.4.md](PHOTO-EDITOR-0.13.4.md).

17 host-наборів і 1774 нові Java assertions пройшли. **Джерела, без нового APK.**
Gradle bootstrap: UnknownHostException: services.gradle.org. Android/Compose і жести
ще потребують перевірки; PHOTO-04 — verify. Усі попередні PHOTO-01–03/R1/R2 включені.

## Попереднє оновлення 0.13.3 — очищення фону

Code 22, Room/backup 8. Покращено маску: м’які краї, очищення залишків,
два режими — звичайний та «Очистити фон сильніше». У картках, зумі й редакторах
фон білий, а для білих речей кремовий. Прозорий PNG зберігається для пензля
й колажів. Для старих фото відкрийте редактор і запустіть аналіз/сильніше очищення.
Оригінал не зберігається постійно. [PHOTO-CLEANUP-0.13.3.md](PHOTO-CLEANUP-0.13.3.md).

16 host-наборів і 40 нових Java assertions пройшли. **Джерела, без нового APK.**
Gradle bootstrap: `UnknownHostException: services.gradle.org`. Kotlin/Compose,
Android і якість на реальних фото ще не перевірені; PHOTO-03 — verify.
Пакет накопичувальний і містить PHOTO-01–02/R1/R2.



## Попереднє оновлення 0.13.2 — збільшення збережених фото

Code 21, Room/backup 8. Натисніть фото з лупою у каталозі/«Хочу купити» або
на екрані деталей: повний екран, pinch 1–5×, рух, double tap 3×/fit,
кнопки +/−/fit та Close/Back. Режим вибору речей у каталозі збережено.
Перегляд не змінює фото й не додає оригінал. [PHOTO-ZOOM-0.13.2.md](PHOTO-ZOOM-0.13.2.md).

16 host-наборів пройшли; 225 Java-перевірок геометрії. **Джерела, без нового APK.**
Gradle bootstrap заблокований мережею; Kotlin/Compose, Android-жести й UI
не перевірено. Докази docs/history/RELEASE-VALIDATION-0.13.2.json; PHOTO-02 — verify.
У пакеті всі попередні зміни R1/R2/PHOTO-01.


## Попереднє оновлення 0.13.1 — причини збоїв фото

Code 20, Room/backup 8. Збережені причини пакетної обробки, ручне завершення,
заміна фото в failed-записі, явна причина біля Save і прокрутка до потрібного поля.
Успіх видалення фону не означає, що тип одягу розпізнано. Повторний аналіз
не обов’язковий для збереження придатного фото з ручною категорією.
Матриця приймання: [PHOTO-RECOVERY-0.13.1.md](PHOTO-RECOVERY-0.13.1.md).

15 host-наборів пройшли; 5 нових Android-тестів підготовлено, але не виконано.
**Джерела, без нового APK.** Gradle bootstrap блокує мережа; Kotlin/Compose,
JUnit/lint, Android і UI не перевірено. Докази: `docs/history/RELEASE-VALIDATION-0.13.1.json`.
Пакет накопичувальний: містить R1/R2 та всі попередні зміни.


## Попереднє оновлення 0.13.0 — помічник покупки

Code 19, Room/backup 8. Схожі власні речі з фото/причинами та мітками доступності;
до 20 прикладів із закріпленим товаром; заміна на власну річ і збереження без
покупки; ціна/бюджет без конвертації; явні невідомі ознаки та відкладення рішення.
Стан R1 і R2: verify до native-приймання. Подробиці: [R2-PURCHASE-0.13.0.md](R2-PURCHASE-0.13.0.md).

15 host-наборів пройшли, включно з 76 assertions R2 і 34 імпорту. **Нового APK немає.**
Повторний Gradle bootstrap заблокований мережею; Kotlin/Compose, Gradle JUnit,
lint, Android/Firebase і UI не виконані. Поточні докази: `docs/history/RELEASE-VALIDATION-0.13.0.json`.
Наступне: REL-01/QA-01–02 і матриці R1/R2; далі сімейний блок за [WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md).

## Попереднє оновлення 0.12.0 — каталог та імпорт магазину

Version code 18; Room/backup 8. Компактний заголовок і меню способів додавання,
Android Share URL/тексту з відновлюваною чергою, JSON-LD/Open Graph та ручна
перевірка у наявних редакторах. Джерело/ціна власної речі зберігаються в історії покупки.
Деталі, обмеження й native-матриця — [R1-UPDATE-0.12.0.md](R1-UPDATE-0.12.0.md).

14 host-наборів пройшли; 34 перевірки імпорту. **Це джерела, без нового APK.**
Gradle bootstrap заблоковано мережею; Kotlin/Compose, lint, Android-тести,
UI та PowerShell не виконані. `docs/history/RELEASE-VALIDATION-0.12.0.json` описує межі доказів.
Наступний крок — REL-01/QA-01 і приймання R1 за [WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md).
Сімейний доступ лише спроєктовано в [ADR-FAMILY-WARDROBES.md](ADR-FAMILY-WARDROBES.md).

## Попереднє оновлення 0.11.2 — 7 жовтня 2026

Фільтри категорій переносяться на новий рядок за доступною шириною;
кнопку списку перейменовано на «Категорії» зі стрілкою. Version code 17.
Виконано перевірки вихідних файлів/ресурсів; Gradle недоступний у цьому
середовищі, тому новий APK ще потрібно зібрати. Подробиці й перевірки:
`docs/history/CATEGORY-LAYOUT-0.11.2.md`. Нижче наведено результати попередніх версій.

## Попередній реліз 0.11.1 — 3 жовтня 2026

Debug/Release реально зібрані; 43 JVM, 71 Android, 6 Firebase emulator, 13 host наборів пройшли. UI-навігацію потрібно прийняти окремо: System UI емулятора повторно зависає. Поточний обсяг, межі ручного/production приймання та команди — `docs/history/RELEASE-VALIDATION-0.11.1.md`, `SCENARIO-COVERAGE-0.11.1.md`, `CLOUD-SETUP-UK.md` і `../README-UK.md`. Збережено Room/backup 8 і ваш клієнтський Firebase-конфіг; version code 16. Нижче — історія попередніх версій.

Version code 15; Room 8; portable backup format 8. The complete seven-block coverage and device acceptance are in SCENARIO-COVERAGE-0.11.0.md. Cloud setup and rules are in CLOUD-SETUP-UK.md and cloud/.

New behavior includes durable purchase undo, candidate-to-need reversal with retained history and photos, varied inspiration, per-piece availability explanations and Firebase snapshot sync with a persistent offline queue and explicit conflicts. Existing authentication, photo processing, calendar, wear statistics and packing flows are retained.

All 13 host checks passed; results are in HOST-VALIDATION-0.11.0.json. Host tests execute production Java and SQLite, not Kotlin/Android. APK build stopped at the full-JDK check. Kotlin/Compose/KSP, native tests, Firebase/emulator tests and two-device acceptance remain pending. No compiled APK is supplied. Historical sections below apply to their named releases and do not override this section.

# Wardrobe Android 0.10.0-ux — all 14 UX refinements

All approved UX-01–UX-14 findings are implemented in Android source: contextual Back and retained state, recovery messages and labelled/cancellable work, neutral onboarding, daily-result hierarchy and specific empty reasons, explicit composition mode and Undo, sequential import review, precise crop/mask controls, searchable visual selectors, backup status and verified restore preview, compact Need cards, full-row selection and localized dates.

Read docs/history/UX-REFINEMENT-0.10.0.md for the implementation map and required native acceptance. docs/history/VALIDATION.md and docs/history/HOST-VALIDATION-0.10.0.json distinguish executed production Java/SQLite/source checks from pending native work. Earlier release notes and reports remain historical.

Version code 14. Room schema 7, registered 1→2→3→4→5→6→7 migrations, backup format 7, application ID, account storage, dependencies, Firebase and signing configuration retain their existing contracts. Formats 1–7 restore additively into new wardrobes. Pending queues, source URIs, folder grants/scheduling settings, city settings, drafts and transient view state are not portable. Original exported schema 2 remains; KSP must generate and validate genuine schema 7 during the Android build.

Build in the original project with its existing signing key:

```powershell
.\build-apk.ps1 testDebugUnitTest lintDebug assembleDebug
.\build-apk.ps1 connectedDebugAndroidTest
```

Linux/macOS: `bash build-apk.sh testDebugUnitTest lintDebug assembleDebug`, then `bash build-apk.sh connectedDebugAndroidTest` with a test device/emulator.

Install `app/build/outputs/apk/debug/app-debug.apk` over the previous app using the same signing key, after exporting a backup. This package contains source and no new APK. The attempted build stopped at the missing full-JDK check. Native compilation, generated Room validation, UI/gestures, providers/WorkManager, live services and TalkBack are pending for 0.10.0-ux.

## Актуальний роадмап і продовження роботи — 7 жовтня 2026

[WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md) містить погоджені пріоритети,
стан реалізації, залежності, критерії приймання та наступний крок.
[AGENTS.md](AGENTS.md) — точка входу для будь-якого наступного агента.
Старі розділи «наступний реліз» вище є історичними. Поточний наступний ID —
REL-01/QA-01–02: зібрати й прийняти 0.13.0 (R1 + R2); потім FAM-01–04. Історичні розділи вище стосуються лише названих у них версій.

<!-- WARDROBE-GIT-SAFETY -->
## Перевірка перед Git push — 9 жовтня 2026

Додано Git Safety 1.0.0: розширений `.gitignore`, автоматичне виявлення нових
локальних credentials, перевірки index та історії вихідних комітів.
На кожному ПК після clone запусти `sh install-git-safety.sh` (Mac/Linux)
або `.\install-git-safety.ps1` / `py -3 tools/git_safety.py install` (Windows).
Потрібні Git і Python 3.9+. Усі кроки, виправлення блокерів та межі перевірки —
[GIT-SAFETY.md](GIT-SAFETY.md). Android залишається 0.13.4/code 23;
це оновлення інструментів розробки, нового APK немає.
<!-- END WARDROBE-GIT-SAFETY -->

## Попередні короткі контрольні точки

**Попередня контрольна точка hotfix2:** 17 жовтня 2026, Codex, FAM-01/03 partial/verify. Журнал телефона підтвердив `current must not be NaN` у LinearProgressIndicator після Resume / check. Виправлено delayed callback, що читав скинуті bytes/total=0/0, додано snapshot-bound progress і 4 regression tests. 19 Kotlin/JVM-тестів pass. Версія 0.14.3-family-hotfix2/code33; потрібний новий APK. Native/Compose build не виконано. Storage 403 ще не закрито: консольні null/missing metadata guards користувач змінював окремо; cloud source rules не синхронізовані. Точний наступний крок: застосувати hotfix2, зібрати/оновити APK тим самим ключем, повторити Resume / check і підтвердити server head. Див. FAMILY-PROGRESS-HOTFIX2.md.

**Попередня контрольна точка hotfix1:** 16 жовтня 2026, Codex, FAM-01/03 (partial/verify). Відтворено помилку Android Long → Python callable size на гардеробі з однією річчю. Додано scoped decoder і безпечні server logs; 35 host-тестів pass. Android 0.14.2/code32 без змін. Новий пакет `wardrobe-family-transfer-hotfix1.zip` — цільове оновлення бази 0.14.2. Див. FAMILY-CALLABLE-HOTFIX1.md. Наступний крок: користувач розгортає functions:family та повторює Resume / check; реальну причину зі скриншота без server log остаточно не підтверджено. Live deploy та перевірку на телефоні агент не виконував. Після приймання повернутися до shared-cache isolation / FAM-02.

**Контрольна точка 0.14.2:** 16 жовтня 2026, Codex. 0.14.2/code 32, Room/backup 10. FAM-01/03: Android initial transfer реалізовано, без перезапису локальних даних; 15 JVM-сценаріїв виконано; 3 нові native-тести підготовлені, не виконані. Фактичні результати — RELEASE-VALIDATION-0.14.2.json. Наступне: shared-cache isolation, FAM-02 invite/accept/role/revoke, потім shared sync. Усі 60 task ID збережені.

**Попередня контрольна точка:** 11 жовтня 2026, Codex. PHOTO-12–14: краї маски −8…+8 пкс і згладжування 0…6 пкс, полігональне ласо до 128 точок, захист усіх alpha-інструментів, спільна undo/redo-історія в account-private чернетці. Відновлення після закриття/перезапуску, явні Apply/відкласти/відкинути, без нової копії оригіналу. 18 host-наборів pass, 588864 нових Java assertions; 4147 source/SQL, 987 рядків. Вісім нових Android-тестів підготовлені, не виконані. Нового APK немає. Збережено всі 52 попередні ID та DEV-01, додано PHOTO-12–17; разом 58 ID. Native-приймання відкладене користувачем.

