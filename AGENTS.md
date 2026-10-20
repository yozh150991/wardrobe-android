## Current handoff — FAM-03 photo replacement, 20 October 2026

0.14.10-family-photo/code 40, Room/backup 10. Read FAMILY-PHOTO-0.14.10.md. Owner/editor replaces the photo of an existing shared item from gallery/camera (imported as is) or an own item's final photo; same durable draft/receipt/CAS flow as additions in its own folder. No backend change. Migration tests: RoomMigrationSchemaTest (audit №13). Next FAM-03: direct shared camera/gallery for new items, shared outfits, multi-operation queue, background sync; then FAM-04. Work in branches and merge via PR; CI compiles Kotlin.

## Current handoff — 0.14.9-family-add integration, 20 October 2026

0.14.9-family-add/code 39, Room/backup 10 = Codex 0.14.8-family-add (FAMILY-ADD-0.14.8.md) rebased onto 0.14.8-audit-fixes (AUDIT-FIXES-0.14.8.md) and renumbered because code 38 was already released. FamilyAddController now queues actions and prepare() copies shared photos/ZIP outside the private write lock. No backend change besides tests, so no functions deploy is needed for additions. Future Codex packages: build on current main and bump versionCode above main; apply in a branch and merge via PR — do not run updaters directly on main.

## Current handoff — audit fixes, 20 October 2026

0.14.8-audit-fixes/code 38, Room/backup 10, on top of 0.14.7-family-edit. Read AUDIT-FIXES-0.14.8.md. On user request («Можеш виправити все?») the 0.14.6 audit findings were fixed as bugfixes only; №2/№13/№14 and parts of №4/№9/№11/№15 remain open (listed there). Host suites and backend tests pass; verify the Android CI job and a device run. main.py family_commit memory/timeout needs a manual functions deploy; no live deploy was run by the agent. Use tools/audit_strings.py-style targeted edits for strings. Then continue FAM-03 per FAMILY-EDIT-0.14.7.md.

## Current handoff — FAM-03, 19 October 2026

0.14.8-family-add/code38, Room/backup10. Read FAMILY-ADD-0.14.8.md and RELEASE-VALIDATION-0.14.8.json. Owner/editor can copy one saved private item with its final photo/custom category/color into a shared wardrobe. Explicit draft/preview/send; immutable account-private ZIP/journal; general base>=1 begin/commit, historical receipt retry and explicit rebase preserving other data. No private Room/backup writes. One pending addition per remote wardrobe, separate from metadata edits. Backend95 /host22 pass; new JVM7 and Android8 prepared, not run. No APK/live deploy; full JDK unavailable. Next: replace shared item photos and shared outfit editing, multi-operation queue/continuous sync, then FAM-04. Direct shared camera/gallery remains open. User deferred bugfixes and joint native acceptance.

# Wardrobe — інструкція для продовження роботи

Цей файл стосується проєкту `wardrobe-android`. Прямі вказівки користувача та правила середовища мають перевагу.

## Почати тут

1. Прочитай [WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md), особливо швидкий старт, поточні статуси й останню контрольну точку. Це головний беклог; старі `ROADMAP-UPDATE-*` — історія релізів.
2. Звір `app/build.gradle.kts`, `README.md`, локальні зміни та останній release-звіт. Поточна база — 0.14.10-family-photo, code 40, Room/backup 10 (FAM-03 заміна фото поверх 0.14.9; Codex 0.14.8-family-add поверх 0.14.8-audit-fixes; AUDIT-FIXES-0.14.8.md); фото-зміни описані нижче. FAM-01/03 partial: Android initial transfer/UI підключено до family backend; див. FAMILY-ANDROID-0.14.2.md та RELEASE-VALIDATION-0.14.2.json. Це початкова серверна копія зі збереженням приватного локального гардероба. Read-only shared cache/lease реалізовано у 0.14.5, FAM-02 invite/accept/role/revoke у 0.14.6. У 0.14.7 metadata edits, у 0.14.8 копіювання однієї речі/фото; наступне — заміна shared-фото, образи, multi-operation queue і continuous sync. Оновлений Android потребує backend 0.14.2 (actor binding і structured recovery errors). Не запускати live deploy автоматично; firebase.family.example.json окремий від приватної конфігурації. Native-приймання спільне; production GCS, GC, App Check і витрати ще потребують перевірки.
3. За прямою вказівкою користувача 10.10.2026 («Тестувати будемо все разом») PHOTO-12–14 реалізовані у 0.13.8, PHOTO-01–10 збережені; native-приймання перенесене на спільний етап. Воно не блокує подальше погоджене впровадження; автоматичні перевірки виконувати. Спільне native-приймання має включати FAM-01 foundation (FAMILY-FOUNDATION-0.14.0.md: 8 Android-тестів, вибір/retry/акаунти/backup health), COL-01/CAT-01 (COLORS-0.13.10.md/CATEGORIES-0.13.9.md) та PHOTO-12–14 (PHOTO-PRECISION-0.13.8.md: краї/ласо/захист, перезапуск/Undo/Redo/Apply/Discard, пошкодження/акаунти/cleanup), PHOTO-09–10 (PHOTO-QUALITY-0.13.7.md: роздільність, пам’ять/час/backup, вибрана черга, перезапуск/скасування/конфлікти/акаунти), PHOTO-06–08 (PHOTO-INSPECTION-0.13.6.md), PHOTO-05 (лупа/спільна історія/м’якість і сила, PHOTO-TOOLS-0.13.5.md), PHOTO-04 (історія/пензель, PHOTO-EDITOR-0.13.4.md), PHOTO-03 (очищення/фон, PHOTO-CLEANUP-0.13.3.md), PHOTO-02 (зум фото, PHOTO-ZOOM-0.13.2.md) та PHOTO-01: причини збоїв фото, ручне завершення й видиму валідацію; див. PHOTO-RECOVERY-0.13.1.md. Погоджений порядок: компактний каталог → імпорт із магазину через Android Share → розширений помічник покупки → окремі сімейні акаунти → персоналізація → віджет/забуті речі → подорожі. Поточний наступний ID і статус завжди бери з роадмапу.

## Поточний Android photo hotfix3 (17.10.2026)

PHOTO-03 / verify: 0.14.4-photo-hotfix3/code34, Room/backup10. Прочитай
PHOTO-CLEANUP-HOTFIX3.md. Автоматичний default — gentle (0.15–0.70),
colour flood лише за explicit stronger. Clamp дрібного float32 overshoot
і fail-whole-mask для NaN/Inf/значного порушення діапазону. Стара production
функція падає на новій synthetic rounding regression; після виправлення
61 mask/backdrop assertion pass. Це не доказ точного походження сітки на
телефоні: ML-маска користувача недоступна, потрібен новий імпорт оригіналу.
Gradle wrapper blocked: Network is unreachable; Android/Compose/APK та
PowerShell не виконані. Старі family hotfix1/2 збережені; живі правила не
змінювати. Наступне: native PHOTO-03 acceptance, потім чинний сімейний беклог.

## Попередній Android hotfix2 (17.10.2026)

Фактична версія: 0.14.3-family-hotfix2/code33; Room/backup 10. Прочитай
FAMILY-PROGRESS-HOTFIX2.md. Виправлено deferred progress callback NaN після
переходу UPLOADING → COMMITTING; 19 Kotlin/JVM-тестів pass. Android/Compose
build і телефонне приймання ще не виконані. Live Storage 403 не вважати
остаточно усуненим. Console rules користувач змінював окремо, їхні guards
ще не перенесені в cloud source rules; не перезаписувати їх старим deploy.

## Попередній серверний hotfix1 (16.10.2026)

FAM-01/03: Android залишається 0.14.2/code32. Прочитай FAMILY-CALLABLE-HOTFIX1.md.
Виправлено callable Long size; 35 host tests pass. Деплой і приймання на телефоні
не виконані агентом; до підтвердження не вважати випадок користувача закритим.

## Під час роботи

- Використовуй наявні редактори, чернетки й операції збереження. Календар, статистика, капсули та базова сумісність покупки вже є; потрібні розширення, а не дублікати.
- Фіксуй task-ID, виконавця, змінені файли й контрольну точку. Погоджена задача не потребує повторного узгодження звичайних оборотних кроків.
- Зберігай `com.familywardrobe.app`, дані користувача, ключ підпису, client Firebase config, локальні SDK/Gradle/порти. У користувача Jenkins на 8080; Firestore запропоновано перенести на 8088.
- Нові постійні поля перевіряй у Room migration, backup, cloud, історії покупок і посиланнях образів. Не використовуй destructive migration або тиху заміну даних.
- Ізоляція акаунтів перевіряється і при асинхронній роботі. Сімейний доступ не можна реалізувати відкриттям всього ZIP акаунта іншому UID.
- Зберігай UA/EN/PL, теми, доступність, великий шрифт і відсутність горизонтального обрізання. Розрізняй власні речі, бажані товари та натхнення.
- Оригінальні фото не зберігай постійно. Незавершені чернетки й потрібні кінцеві референси історії обробляй без втрати даних.
- Запускай релевантні перевірки за роадмапом. Успіх старого релізу не переноситься на новий. Source checks не замінюють компіляцію чи перевірку екрана на Android.

## Перед передачею

- Онови статуси й журнал у `WARDROBE-ROADMAP.md`: що зроблено, які докази є, що не перевірено, який блокер і точний наступний крок.
- Не пиши «готово» для сценарію лише тому, що код або тест існує. Відділяй реалізацію, виконані автоматичні перевірки й приймання на пристрої.
- Включи актуальні `AGENTS.md` і `WARDROBE-ROADMAP.md` у наступний пакет/репозиторій та механізм оновлення. Окремий примірник роадмапу має відповідати вмісту пакета.
- Не видавай старий APK за новий. У короткому звіті користувачу вкажи результат, межі перевірки й посилання на збережені файли.

<!-- WARDROBE-GIT-SAFETY -->
## Git Safety — DEV-01

- Перед commit/push прочитай `GIT-SAFETY.md`; установи hooks у кожному clone.
- Зберігай `.gitignore`, `.gitattributes`, `.githooks`, інсталятори та `tools/git_safety*`.
- Не додавай локальні ключі/Firebase config/дані до Git. Не обходь блокер
  секрету через автоматичний untrack, переписування історії або `--no-verify`.
- Тести guard: `python3 tools/test_git_safety.py`; справжні credentials не використовувати.
- Два винятки для локальних Firebase emulator-тестів прив’язані до SHA-256
  точного файла та одного literal-warning. Зміна потребує перегляду;
  не розширюй винятки на каталоги, токени чи приватні ключі.
- Після оновлення guard повтори installer: активна копія зберігається локально
  в Git-каталозі й працює навіть на гілках без версійованого guard.
<!-- END WARDROBE-GIT-SAFETY -->

## DEV-02 — очищення, 18.10.2026

Поточна структура: README → WARDROBE-ROADMAP → CHANGELOG/docs/history.
Прочитай PROJECT-CLEANUP.md та CLEANUP-VALIDATION.json. Старі JSON докази незмінні.
Не відновлюй OfflineScreen або його два видалені рядки зі старого payload.
Для нових змін використовуй очищене дерево; старі updater-пакети можуть конфліктувати.
Версія Android лишається 0.14.4/code34; Android build/lint ще потребують запуску.

## DEV-03 — GitHub preparation, 18.10.2026

Прочитай GITHUB-WORKFLOW-UK.md і GITHUB-READINESS-VALIDATION.json.
CI використовує read-only permissions, без live deploy/signing/Firebase secrets.
CI preview: -PciPreview=true → com.familywardrobe.app.ci, локальний default незмінний.
Release tag має збігатися з v + versionName; code збільшується для нового релізу.
Перед GitHub push повтори audit локального index/history; hooks установлюються на кожному clone.
Не оголошувати remote/CI підключеними без підтвердження.
