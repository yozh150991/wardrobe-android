# FAM-01 — основа даних сімейного гардероба

Дата: 2026-10-13. Версія: 0.14.0-release, code 30. Room/backup: 10.
Статус: FAM-01 **partial**; цей етап реалізовано у вихідному коді для перевірки.

## Що доступно в інтерфейсі

1. Налаштування → «Зберегти один гардероб».
2. Знайти й вибрати гардероб; список показує кількість речей і образів.
3. Вибрати місце ZIP. Підтвердження з’являється після запису та перевірки
   прочитаних байтів. При помилці інше місце зберігає вибраний гардероб.
4. Відновити ZIP через наявний екран backup: додається новий гардероб із
   новими ID, узгодженими посиланнями та власними копіями фото.

Копія містить речі, кінцеві фото, образи, бажання/потреби, історію покупки,
purchase receipt, носіння, плани, feedback, капсули, власні категорії й кольори,
включно з невикористаними назвами. Вибіркова копія не оновлює показник останньої
успішної повної копії акаунта. Повний/плановий backup працюють як раніше.
П’ять нових рядків UA/EN/PL/fallback; наявні теми, пошук і доступний селектор.

## Що реалізовано всередині

- WardrobeScope перевіряє граф володіння; WardrobeSnapshots адаптує всі
  16 portable-колекцій. Зберігаються ID та лише потрібні фото; неправильний
  UUID/відсутній батько/дублікат/міжгардеробне посилання відхиляються.
  Unknown/deleted selection не перетворюється на експорт усіх гардеробів.
- FamilyEnvelope — окремий маніфест `family-wardrobe-scoped` v1 із content v10.
  Очікувані remote/wardrobe/owner/baseRevision задаються зовні; всі ключі
  оболонки й photoFiles перевіряються. Це ще не сімейний ZIP transport.
- FamilyRules — клієнтський контракт owner/viewer/editor, owner-only export,
  CAS без тихого перезапису, безпечний archive path і 15-хвилинний lease в
  тій самій runtime-сесії. Серверне застосування цих правил ще потрібне.
- FamilyMigrationStore — account-private AtomicFile журнал зі сталими
  remote/archive UUID та PREPARED→STAGED→UPLOADED→VERIFIED. Перевіряє identity,
  checksum, ревізії, повтор і пошкодження; не експортується та не надає прав.
- Retry експорту зберігає scope; request/session token захищає від пізнього
  callback іншого запиту чи акаунта. Процесний restart не відновлює довільний
  експорт за втраченою VM-операцією.

Нових Room-полів немає. Старі категорії, кольори, фото й зв’язки не мігруються.
Private cloud лишається UID-only та формату 10 (читання 8/9/10).
Запрошення, міжаккаунтний доступ, server migration, shared cache та enforcement
ролей ще не підключені. Live Firebase не змінено. Деталі — ADR-FAMILY-WARDROBES.md.

## Фактично виконані перевірки

21 host-набір pass; HOST-VALIDATION-0.14.0.json. Новий check_family.py компілює
та виконує production FamilyRules/WardrobeScope через Java 17: 70 assertions.
Два гардероби з усіма типами рядків, спільний photo ID, порожній гардероб,
500 додаткових речей, порядок рядків, дублікат/цикл/відсутні посилання,
міжгардеробні ребра, небезпечні photo/path IDs, ролі/відкликання, lease boundary,
інша сесія/відкат часу, CAS/overflow. Інші host-набори також повторно пройшли,
включно з категоріями/кольорами, SQL migrations/guards і photo regression.
4264 source/SQL checks, 11 XML, 1011 localized strings, 72 DAO queries.

Підготовлено 8 FamilyFoundationTest native-методів: повний граф/порожній scope,
реальний ZIP/відновлення/remap/photos, відмова до запису destination,
family-envelope identity/format/extra photos, reopening/idempotent migration,
непідтверджені/stale callbacks, інший акаунт, пошкодження journal.
JUnit wrapper для Java cases також підготовлений.

**Не виконано:** Kotlin/Compose/KSP/Room compile, Gradle unit/lint/assemble,
connectedDebugAndroidTest, Android AtomicFile/ZIP/UI/lifecycle, Firebase emulator,
live deployment, Windows/PowerShell updater. Host-перевірки не доводять ці
сценарії. Нового Gradle bootstrap не запускали, нового APK немає.

## Спільне native-приймання

| Сценарій | Очікуваний результат |
| --- | --- |
| Повний та один гардероб, в обох є різні фото/категорії/кольори | Один ZIP містить лише вибір; повний — всі власні дані; counts і readback правильні |
| Порожній гардероб; >200 речей; unused definitions | Коректні копії та пошук; без зайвих фото/даних |
| Bought item зі зміненим фото, історія, плани й капсули | Старі history photos та нове фото збережені; restore remap узгоджений |
| Вибір, Back/cancel, відмова сховища, retry в інше місце | Без імпорту/зміни даних; retry зберігає scope; часткова копія не рахується успіхом |
| Обраний гардероб видалений до callback | Відмова перед записом; відсутній fallback до всього акаунта |
| Поворот, process death, logout/account switch під час picker/export | Пізній callback не виконує інший export і не бере інші дані |
| Full backup health → one-wardrobe success/failure/cancel | Last full-success/attempt/failure не змінюються вибірковою копією |
| Повний/плановий backup і старий restore | Попередня поведінка та підтримувані формати збережені |
| UA/EN/PL, 320–412 dp, 200% шрифт, теми/TalkBack | Видимі підписи/підказка; селектор прокручується й шукає; touch targets ≥48 dp |
| Journal reopen/repeat/corruption/foreign account | Стабільні UUID, ніяких дублікатів, fail-closed без тихого reset |

Запуск на актуальній збірці: testDebugUnitTest/lintDebug/assembleDebug та
connectedDebugAndroidTest, потім ця матриця разом із COL-01/CAT-01/PHOTO/R1/R2.
Користувач відклав device-приймання на спільний етап; це не блокує наступний код.
Наступний блок — довірений backend migration/membership/CAS, потім FAM-02 UI.
