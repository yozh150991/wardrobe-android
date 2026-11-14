# FAM-03 — додавання власної речі з фото, 0.14.8

19 жовтня 2026 · Codex · 0.14.8-family-add/code38 · Room/backup10.
Реалізаційний обсяг verify; FAM-03 загалом partial. Багфікси відкладені користувачем.

## Сценарій

Налаштування → Сімейні гардероби та запрошення → гардероб → Переглянути
серверну копію → **Додати власну річ / відкрити чернетку**.

Owner/editor вибирає збережену неархівовану річ зі свого активного приватного
гардероба. Вибір готує локальну копію з новими ID речі й фото. Видимий preview
показує кінцеве фото та поля; окреме підтвердження надсилає копію для сім’ї.
Одна незавершена копія на спільний гардероб; її можна закрити й відновити після
перезапуску на тому самому акаунті. Прямий новий імпорт із галереї/камери у shared
editor ще не реалізований: спочатку створити річ чинним приватним редактором.

Копіюються поля вибраної речі, її кінцевий JPEG/PNG, вибрана custom category/color.
Категорія/колір з однаковим nameKey та сумісним base повторно використовуються.
Несумісний base відхиляється; потрібна зміна категорії/кольору у вихідній речі.
Власна річ залишається без змін. Приватні outfits/wears/wishes/plans/capsules,
історія покупок та інші фото не копіюються. Уже наявні спільні записи зберігаються.

## Збереження і повтор

- `FamilyAddStore`: AtomicFile у account-private noBackupFilesDir/family-additions.
  Незмінний ZIP та метадані містять точну identity/base/hash/size/archiveId.
  Максимум 100 незавершених гардеробів на акаунт. JSON до 8 KiB, ZIP до 128 MiB,
  до 5000 фото; чинні обмеження фото 12 MiB/8192 px/16 Mi pixels.
- `FamilyAddition`: явний відбір лише однієї речі, remap ID й merge до свіжого
  server snapshot. Не викликає Room insert/update чи приватний backup import.
- `FamilySnapshotZip`: спільний bounded writer початкового переносу й додавання;
  використовує ізольований PhotoStore та FamilyEnvelope, перевіряє MIME/dimensions.
- Перед мережею журнал позначається submitted. `FamilyPublication` окремий від
  initial-migration record з base0. Чинні begin_upload/commit працюють з base>=1.
- Повтор спочатку перевіряє receipt; завантаження лише після ARCHIVE_MISSING.
  Історичний receipt перевіряється за identity, base, archive ID/hash/size/author,
  навіть якщо інший учасник уже опублікував наступну ревізію. Дублі не створюються.
- Невідома відповідь/обрив залишає запит незмінним. Malformed callable map тепер
  RETRY, а не ARCHIVE: невідомий результат не дозволяє перебудувати запит.
  Якщо локальний ZIP втрачений, збережений journal усе ще дозволяє запитати receipt.
- Після підтвердженого conflict/expiry/rejection можна видалити чернетку або
  явно оновити її основу. Завантажується свіжа копія, додається лише вибрана річ
  зі збереженого ZIP, зберігаються її ID/фото, створюється новий archiveId.
  Надсилання після rebase потребує окремого підтвердження. Automatic overwrite немає.
- Свіжі owner/editor checks до читання/надсилання та серверні role/expiry/CAS
  перевірки до остаточної публікації. Account/session guard на асинхронних межах.
  UI приховує дані у background/після 15-хв lease; перезапуск вимагає мережі.
- Видалення/підтвердження чистить лише відповідну локальну addition directory.
  Журнал має compare-and-set; інша сесія не може затерти новішу чернетку.

## Виконані перевірки

95 backend domain/ZIP tests pass, з них 12 нових addition tests: незмінність інших
записів/фото, lost reply після пізнішої правки, explicit rebase, viewer/stranger/
unverified denial, revoke/downgrade, expiry, UUID binding, missing/unreferenced
photo, foreign wardrobe, competing writer та concurrent same-ID commit.
22 host-набори pass; personal Firebase config — один явний skip.
Нові Android тести: FamilyAdditionTest4, FamilyAddStoreTest4; JVM
FamilyPublicationTest7. Підготовлені, **не виконані**. Не прирівнювати їх до pass.

`bash build-apk.sh testDebugUnitTest lintDebug assembleDebug` зупиняється до
компіляції: потрібен full JDK17/21 та JAVA_HOME. Kotlin/Compose compilation,
Android instrumentation/lint/APK та новий emulator integration не виконані.
Детальні машинні докази — RELEASE-VALIDATION-0.14.8.json.

## Межі та наступне

Це явне додавання **копії власної речі з готовим фото**. Не повний редактор shared
photos: заміна фото наявної спільної речі, прямий shared camera/gallery, спільні
образи, multi-operation queue та continuous/background sync залишаються в FAM-03.
Публікація поки завантажує повний scoped snapshot; це впливає на час/трафік/диск.
Orphan directories після process death до journal commit й серверні orphan ZIP
потребують окремого GC; підготовка може тимчасово займати ZIP + усі розпаковані
фото. Native acceptance: два акаунти/пристрої, втрата мережі на всіх межах,
перезапуск, зміна ролі/акаунта, великий гардероб, TalkBack/200%/320dp.

Нові серверні endpoints або правила не потрібні. Потрібен чинний backend із
begin_upload/commit; backend0.14.7 потрібен також для попереднього editor.
Live deploy не виконувався; не перезаписувати console Storage rules старими файлами.
Наступний обсяг: заміна фото наявної shared-речі й shared outfits, потім черга та
фонова синхронізація; FAM-02 листи/історія запрошень і FAM-04 ще відкриті.
