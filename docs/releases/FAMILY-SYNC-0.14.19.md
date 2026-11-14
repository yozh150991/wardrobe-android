# FAM-03 — підтверджені пакети та фонове надсилання, 0.14.19 / code 49

Статус: реалізовано, verify до CI цього PR і спільного native-приймання.
База — main після злиття PR #16, commit 0576c48. Room/backup 10 незмінні.

## Обсяг

«Надсилання спільних змін» у shared reader показує підготовлені metadata/add/photo/outfit
операції з назвами. Користувач підтверджує точний список, до 130 операцій на гардероб,
і обирає ручне надсилання або фон для цього пакета. Нові чернетки та незавершені форми
не входять автоматично. За замовчуванням фон вимкнений. Один активний план на гардероб.

Планувальник зберігає FIFO кожного типу, спершу відновлює submitted heads; серед решти
стабільний пріоритет metadata → additions → photos → outfits. Це порядок типів,
не глобальний порядок за часом створення. Підтвердження прив’язане до request ID та
SHA-256 усіх збережених полів операції; submitted flag може змінюватись без зміни payload.
Зміна/видалення/rebase підготовленої операції потребує нового підтвердження пакета.

WorkManager запускається тільки для явно підтвердженого фонового пакета, через
UNMETERED network (зазвичай Wi-Fi). Немає periodic polling чи автоматичного підключення
нових чернеток. До 10 операцій за запуск, продовження з durable cursor. Тимчасові
помилки мають exponential backoff від 30 секунд; після вичерпання ліміту спроб (attempt
index >= 5 у RETRY) потрібен користувач. Android може відкласти/перервати роботу.

Ручне надсилання працює у поточній сесії. Після переривання без фонового дозволу
новий контролер позначає такий план paused; автоматичного надсилання немає.
Зупинка зберігає чернетки, але не скасовує вже прийняту сервером зміну. Після завершення
треба оновити shared reader. Перегляд назв і нове підтвердження потребують чинного
owner/editor view; зупинка власного плану доступна і після закінчення read lease.

## Конфлікти й повтори

Координатор НЕ виконує автоматичний rebase, не створює ZIP і не відкриває приватний Room.
Перед кожним новим запитом перевіряє акаунт, owner/editor та baseRevision. Якщо серверна
версія вже змінилась, пакет зупиняється до mark-submitted. Часто після першої зміни
наступний ZIP/metadata має стару base: потрібно переглянути/оновити чернетку через
її редактор, а тоді підтвердити новий пакет. Чинний окремий metadata batch з field-aware
refresh збережений. Безперервний автоматичний drain усіх видів черг ще не реалізовано.

Submitted запит повторюється точно з початковими ID/base/hash/size/fields. Historical
receipt перевіряється навіть при новішому head; без доступу сервер не дозволяє повтор.
Неоднозначна відповідь не дозволяє видалення. Після валідного receipt план спочатку
записує acknowledged=true, потім прибирає лише відповідний outbox head, потім пересуває
cursor. Crash між цими кроками не надсилає повторно вже підтверджений запит і не видаляє
сусідню чернетку. Receipt, втрачений до локального ack, відновлюється тим самим запитом.

Account-wide Mutex спільний для чотирьох редакторів і координатора: prepare/send/rebase/
discard не змагаються з worker. План можна зупинити без очікування цього lock. Epoch,
job cancellation, current auth key, plan ID/status та exact-head guards перевіряються
між кроками. Sign-out скасовує WorkManager tag акаунта й зупиняє активні durable плани;
повторний login не вмикає їх самостійно. Серверні role/CAS guards залишаються остаточними.

## Дані й файли

Нові FamilySyncPlan/Store/Engine/Schedule/Controller/Dialog + тести та UA/EN/PL strings.
Плани — account-private AtomicFile `family-sync/<account>/<remote>.json` v1 у noBackup:
ID, fingerprints, режим, статус, cursor, ack; до 100 планів на акаунт, 128 KiB/план.
Немає credentials або payload фото у плані. Пошкоджені/чужі/unknown-version журнали
відхиляються; CAS не дозволяє пізньому worker перезаписати скасований план.
Нових endpoints, rules, backend deployment, Room/backup migration чи signing змін немає.

## Перевірки

23 JVM tests: FIFO/type order, exact approval/fingerprint, boundaries, fixed-prefix,
bounded worker continuation, role rejection, unknown outcome, ack/remove crash recovery,
cancel/replacement plan, malformed journals. 6 Android storage/integration tests:
restart, stale-worker CAS, sign-out pause, account isolation, corruption, всі чотири
outboxes зі збереженням unfinished outfit form. Instrumentation не виконані.
Host runner: 23 suites pass; backend 148 pass; missing client Firebase config skipped.
Android CI поточної гілки перевірити перед передачею.
PR #16 CI 40130329782: 209 JVM, lint/schema/APK pass; це докази лише попередньої бази.

## Native-приймання разом

1. Створити metadata, addition, photo й outfit drafts; перевірити назви, порядок,
   точний список і те, що unfinished outfit form не включається.
2. Підтвердити фон offline; перезапустити app/device; з появою unmetered мережі
   виконується лише підтверджений пакет. Нові чернетки не надсилаються автоматично.
3. Втрата відповіді після commit, restart після ack/before removal/after removal:
   немає дублювання й втрати сусідніх чернеток. Missing local ZIP + historical receipt.
4. Stale revision зупиняє пакет; явний rebase/оновлення й нове підтвердження. Одночасний
   foreground editor/worker, rapid confirm/stop, зупинка під час upload/commit.
5. Sign-out → інший UID → повторний login; revoke/viewer/email/config; жодного чужого
   read/write. Перевірити Android force-stop, Doze, retry limit і constraint loss.
6. UA/EN/PL, 320 dp, 200% font, TalkBack; після send оновити reader.

Далі: узгоджена політика автоматичного refresh/rebase та continuous sync, після неї
FAM-04 backup/migration/role acceptance. GC/native/live fixes залишаються відкритими.
