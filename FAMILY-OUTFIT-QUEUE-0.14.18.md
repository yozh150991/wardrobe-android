# FAM-03 — черга спільних образів, 0.14.18 / code 48

Статус: реалізовано, verify до CI цього PR і спільного native-приймання.
База: main після PR #15, commit 4ae5525. Room/backup 10.

## Поведінка

- До 10 підготовлених публікацій образів на спільний гардероб, одна на outfitId.
  Сумарно до 512 MiB active publication ZIPs; чинний ліміт окремого ZIP — 128 MiB.
  Тимчасові архіви та orphan-файли потребують додаткового місця; GC — окрема задача.
- Список черги з вибором образу, підготовка наступного або повернення до незавершеного
  редактора. Одночасно з чергою зберігається одна незавершена форма на гардероб;
  відкриття іншого образу не перезаписує її. Спершу завершити або явно відкинути форму.
- Відправляється лише вибраний head. Наступні ZIP містять повні snapshot, тому після
  зміни серверної revision потрібен явний rebase. Він змінює лише цей образ у свіжому
  snapshot, зберігаючи позицію в черзі, інші образи, історію й packing linked capsules.
- Submitted retry зберігає точні archiveId/base/hash/size і перевіряє receipt до ZIP.
  Невідомий результат не дозволяє відкинути чи перебазувати запит без підтвердженого
  відхилення. Rebase доступний і до першого надсилання.
- Надсилання, видалення й rebase одного образу зберігають решту черги та незавершену
  форму. Generation guards/expected draft блокують пізнє autosave і повторні дії,
  які могли б зачепити інший вибір. Підтвердження прив’язане до покоління редактора.
- Чинне підтверджене оновлення дубліката через LookEditor збережене; воно не може
  замінити вже підготовлену публікацію цього ж образу.

## Сховище та міграція

FamilyOutfitQueue — pure FIFO/CAS policy + codec. FamilyOutfitStore використовує
один account-private AtomicFile `<remote>-state.json` v2 для form + ordered drafts.
Підготовка атомарно додає ZIP до черги та споживає точну очікувану форму: після crash
збережено або форму, або публікацію, без проміжного стану з обома.

Якщо v2 відсутній, читаються старі v1 form/draft. Старий publication має пріоритет
над superseded form — як у старому клієнті. Перша мутація записує v2, потім видаляє
старі journals. Навіть після перерваного cleanup v2 має пріоритет. Пошкоджений v2
не дозволяє fallback і відновлення застарілих даних. Empty v2 зберігається як checkpoint.
Downgrade до старого клієнта після міграції не підтримується.

CAS порівнює publication, submitted та всі збережені поля зміни. Додаткові display-поля
LookPiece не входять у journal і перевизначаються зі shared snapshot під час rebase;
вони не руйнують CAS після перезапуску. Чужі account/remote/owner/wardrobe, duplicate
outfit/archive, невідомі версії, malformed або завеликі форми відхиляються.

Без змін приватних даних, Room/backup, API/rules/signing/package чи live Firebase.
Черги metadata/add/photo/outfit поки незалежні; серверний CAS узгоджує конкурентні
зміни. Спільний scheduler, batch для ZIP-операцій і opt-in background sync — далі.

## Перевірки

18 нових JVM tests: FIFO, bounds, CAS, head-only/repeated action, form promotion,
незалежне збереження форми, canonical persisted fields, codec/identity/legacy receipt.
5 нових instrumentation tests (разом 10 store): restart, atomic promotion, tail cleanup,
legacy draft/form upgrade і corrupt-v2 fail-closed. На пристрої не виконані.
Host runner: 23 suites pass; missing client Firebase config skipped; backend 148 pass.
Android CI цієї гілки перевірити перед передачею.
Попередній PR #15: CI 40128849612 pass, 191 JVM, lint/schema/preview APK;
ці докази не поширюються на поточні зміни.

## Спільне native-приймання

1. Підготувати 3 різні образи, відкрити кожен зі списку, перезапустити застосунок.
2. Почати четвертий, вибрати queued образ, надіслати/видалити його, повернутися
   до незавершеної форми: всі вибори, назва, layout і background мають зберегтись.
3. Надіслати head, явно перебазувати наступний, надіслати його; попередні образи,
   wears/plans і capsule packing не втрачені. Перевірити confirmed duplicate update.
4. Restart старого v1 form, submitted draft і v2 після promotion; втрата мережі/відповіді,
   missing ZIP після успішного commit, повторні кнопки та запізнілий autosave.
5. 10/11 чернеток, bytes/disk limits, account switch/revoke/viewer/expired lease.
6. UA/EN/PL, 320 dp, 200% font, TalkBack і розгортання довгої черги.

Далі: спільне планування черг, opt-in фонова синхронізація; потім FAM-04.
