# Спільні зміни, які застосовує сервер (дельта-модель v1) — 0.14.21-family-changes

29 жовтня 2026 · Claude · 0.14.21-family-changes / code 51 · Room/backup 10 (схема не змінена).
Основа: 0.14.20-audit-fixes (PR #18). Пункт роадмапу: FAM-03, «дельта-модель спільних змін» (AUDIT-0.14.19 B-01, довгострокова частина).

## Що змінилося для користувача

- **Додавання речі** (копія своєї речі або нова річ із галереї/камери) і **заміна фото** спільної речі більше не збирають і не завантажують
  повний ZIP спільного гардероба. Надсилається лише нове фото (один файл у маленькому ZIP) і короткий опис зміни.
- Така чернетка **не застаріває**: сервер застосовує її до найновішої версії. Коміт іншого члена сім'ї між підготовкою й надсиланням більше
  не вимагає «Оновити основу». У чернетці замість «основа: ревізія N» показано «Застосується до найновішої версії».
- Кнопки «Оновити основу» для таких чернеток немає. Якщо зміну неможливо застосувати (власна категорія/колір із такою назвою, але іншою базою;
  річ, якій міняли фото, видалили), показується пояснення, і чернетку можна видалити.
- Пакетне надсилання: спершу образ (він досі повний архів), потім додавання й фото, потім правки полів. Пакет «образ + кілька додавань +
  кілька фото + правки» тепер проходить повністю, без «PARTIAL».

**Сумісність.** Клієнт вмикає новий шлях, лише коли сервер повідомляє ознаку `change1` (поле `features` у `family_resume`). Поки
family functions не задеплоєно, усе працює як у 0.14.20 (повний архів). Старі чернетки з черг (повні архіви) надсилаються як раніше;
«Оновити основу» для них на новому сервері перетворює їх на чернетки нового типу.

## Сервер (`cloud/family-functions`, потрібен деплой)

| Що | Деталі |
| --- | --- |
| `family_begin_change` | `remoteId, changeId, sha256, size` (≤ 12 MiB + 64 KiB). Запис `uploads/{changeId}` з `kind: 'change'`; та сама денна квота, що й для архівів. Повтор тим самим автором оновлює `baseRevision` і `expiresAt`, тож чинні Storage rules (`u.baseRevision == root().revision`) приймають завантаження без змін правил. Прибирає прострочені staging-об'єкти автора. |
| `family_apply_change` | `remoteId, changeId, change` (JSON-рядок ≤ 64 KiB; рядок, а не об'єкт, щоб Android не загортав числа в Int64Value). Текст прив'язується до `changeId` при першому виклику; повтор з іншим текстом — `already-exists`. Сервер читає staging (рівно один запис `photos/{id}.{ext}`), бере поточний head, застосовує зміну, повністю валідує результат і публікує новий знімок `snapshots/{uuid}.zip` транзакцією. Якщо між читанням і публікацією head змінився — повторює до 3 разів на новішому head, потім `CHANGE_BUSY`. Повтор після коміту повертає той самий receipt. |
| Додавання | Рядки `garment`, `customCategory`, `customColor` у форматі backup/manifest. Мітка з тим самим `nameKey` перевикористовується (як `FamilyAddition.merge` на телефоні); інша база — `CHANGE_LABEL_CONFLICT`. Колізія ID речі/мітки/фото — `already-exists`. |
| Заміна фото | `garmentId, fingerprint, photo`. Річ зникла — `CHANGE_ITEM_MISSING`. Старе фото лишається в архіві, якщо на нього ще посилаються бажання, історія чи undo покупки; інакше видаляється. |
| `features` | `family_resume`/`family_create` повертають `features: ['change1']`. |
| Межі | `begin_upload` не відновлює `change`-записи, `commit` їх не приймає. Образи досі йдуть повним архівом. |

Файли: `family/applying.py` (новий), `family/service.py`, `family/callable_wire.py`, `main.py` (`family_apply_change`: 1 GiB / 540 с).

## Телефон

| Що | Файли |
| --- | --- |
| `FamilyRemote.features` (необов'язковий список, ≤ 32 назви); причина помилки сервера в `FamilyTransferException.reason` (`ARCHIVE_MISSING` не плутається з `CHANGE_ITEM_MISSING`) | `FamilyTransfer.kt`, `FamilyBackend.kt` |
| `FamilyChange`: JSON зміни з рядків `Backup.encode`, перевірка чернетки, preview, запити/ticket/receipt, `publishFamilyChange` (receipt-first: apply → лише за `ARCHIVE_MISSING` upload → apply), `definite` — коли можна видалити надіслану чернетку | `FamilyChange.kt` (новий) |
| `FamilyAddDraft.change`; журнал черг v3 (v1/v2 читаються; ліміт журналу 1 MiB) | `FamilyAddStore.kt`, `FamilyMediaQueue.kt` |
| ZIP з одним STORED-фото з тими самими перевірками зображення, що й для повного архіву | `FamilySnapshotZip.kt` |
| `stageChange` для додавання, `stage(change = true)` для фото; preview чернетки з JSON; rebase старої чернетки → нова на сервері з `change1` | `FamilyAddArchive.kt`, `FamilyPhotoChange.kt` |
| Контролери обирають шлях за `features`; для чернетки-зміни немає rebase, «можна видалити» лише після остаточної відмови | `FamilyAddController.kt`, `FamilyPhotoController.kt`, `FamilyAddDialog.kt`, `FamilyPhotoDialog.kt` |
| Пакети: зміна завжди `Send`, публікація повертає ревізію з receipt (вона стає `owned`, і правки полів після неї отримують новий base); fingerprint включає текст зміни (у старих чернеток не змінився); порядок `OUTFIT, ADD, PHOTO, EDIT` | `FamilySyncEngine.kt`, `FamilySyncPlan.kt` |
| Рядки UA/EN/PL: `family_change_latest`, `family_change_conflict`; оновлено `family_sync_hint` | `tools/family_change_strings.py` |

## Тести

- Backend: `tests/test_changes.py` — 15 тестів (нова річ з новою/наявною міткою, конфлікт мітки, колізії ID, коміт іншого члена між
  підготовкою й застосуванням, гонка під час публікації, replay і зміна тексту, фото: старе лишається/видаляється, відсутня річ, viewer,
  вміст staging, прострочення й квота, повтор begin, `begin_upload`/`commit` не беруть change-записи, Int64Value, порожній гардероб).
  Мутації (без перевикористання мітки, без повтору на новішому head, видалення фото без перевірки посилань, begin без оновлення ревізії) —
  кожну ловить хоча б один тест.
- Емулятор: `tests/emulator_changes.py` (CI job `firebase`): editor через callable — `features`, `ARCHIVE_MISSING` до upload, коміт власника
  між begin і apply, оновлений `baseRevision`, receipt і replay, видалений staging, новий head містить фото й правку власника.
- JVM: `FamilyChangeTest` (12), `FamilyMediaQueueTest` (+2: журнал v3, межі), `FamilySyncPlanTest` (+2: зміни після образу без PARTIAL,
  зміна після чужого коміту + зупинка правки; порядок і fingerprint оновлено).
- Instrumentation: `FamilyChangeStageTest` (2: ZIP з одним STORED-фото, JSON, preview, локальний конфлікт мітки; фото без спільних фото,
  відсутня річ); `FamilySyncStoreTest` — новий порядок.

## Перевірки в цьому середовищі

- Backend unittest — 170 pass (155 + 15).
- Локальний Kotlin 2.0.21 (з Gradle) + JUnit: `FamilySyncPlanTest`, `FamilyMediaQueueTest`, `FamilyChangeTest`, `FamilyPublicationTest` —
  67/67 pass на production-файлах `FamilySyncPlan/EditQueue/Transfer/TransferModels/Publication/MediaQueue/Change.kt` (заглушки лише для
  Android-класів).
- Host checks — pass. Компіляція Android, lint, instrumentation і Firebase-емулятори — у CI PR.

## Не виконано / обмеження

- **Деплой family functions** потрібен, інакше клієнт лишається на повному архіві. Правила Firestore/Storage не змінювалися.
- Образи досі публікуються повним архівом; правки полів — `edit_item` з CAS (сервер). Дельта для образів — наступний крок.
- Нова річ досі потребує свіжого спільного знімка на телефоні (вибір міток і локальна перевірка), але завантаження — лише фото.
- Пристрій: додавання й заміна фото на новому сервері, коміт іншого члена між підготовкою й надсиланням, видалена річ під час заміни фото,
  пакет «образ + додавання + фото + правки», стара чернетка → «Оновити основу» → нова.
