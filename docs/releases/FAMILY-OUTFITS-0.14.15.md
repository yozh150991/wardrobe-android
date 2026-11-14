# FAM-03 — спільні образи, 0.14.15 / code 45

Статус: реалізовано, **verify** до Android CI та спільного приймання на пристрої.

## Поведінка

У перегляді спільного гардероба видно збережені образи з колажами. Owner/editor
може створити образ або редагувати наявний через чинний LookEditor: налаштування,
автоматичний і ручний підбір, кілька закріплень, заміна частини, компонування,
масштаб, фон. Viewer має лише перегляд. Режими owned/mixed/inspiration збережені;
речі та бажання беруться тільки зі спільного snapshot. Власні фото/Room не змінюються.

На гардероб є одна незавершена чернетка образу. Вона відкривається першою навіть
при виборі іншого образу; спочатку її слід завершити або явно відкинути.
Зміни форми зберігаються в account-private AtomicFile. «Зберегти образ» готує
незмінний ZIP. Окреме підтвердження надсилає його через чинний begin/commit API.
Втрата відповіді залишає той самий archiveId для перевірки receipt і retry.
Невизначений результат не дозволяє відкинути/перебазувати відправлений запит.
Конфлікт потребує явного оновлення/підтвердження; інші записи нового snapshot
зберігаються. Видалений образ не створюється повторно під час rebase.

Редагування зберігає ID/createdAt, фактичні носіння та worn-плани. Пов’язані
капсули оновлюють склад пакування; ручні додатки та packed-позначки збережених
речей не скидаються. Дублікати signature не перезаписуються мовчки.

## Файли й сумісність

- FamilyOutfitChange: перевірка та застосування однієї зміни до shared snapshot.
- FamilyOutfitStore/Controller/Dialog: форма, publication, retry/rebase, account/lease guards.
- LookEditorPort і LocalLookPhotos: повторне використання конструктора та ізольованих фото.
- FamilyReaderDialog: перегляд і дії; дії/пошук прокручуються разом зі списком.
- UA/EN/PL + fallback. Room і формат backup залишаються **10**.
- Нових backend endpoints або rules немає; цей блок не потребує окремого деплою.
  Потреба розгорнути попередні серверні виправлення 0.14.13–14 залишається.
- Для JVM-тестів canvas додано test-only org.json; APK-залежності не змінені.

## Перевірки

- Host runner: 23 набори pass; client Firebase config явно skipped (не наданий).
- Backend: 148 tests pass, з них 7 нових для shared outfits: create/edit, retry,
  viewer/revoke, concurrent commit, missing reference, packing/history.
- 12 нових JVM domain-тестів — виконання в Android CI цього PR.
- 5 Android instrumented tests чернеток — підготовлені, **не виконані**.
- Локальний Android build зупинився до Gradle: немає повного JDK 17/21 і Android SDK.
- CI: перевірити unit/lint/assembleDebug та Kotlin у PR; не переносити успіх main
  797daad на новий код. Live Firebase і фізичні пристрої не перевірялися.

## Спільне приймання на пристрої

1. Owner/editor: новий owned, mixed і inspiration образ; ручний/авто підбір,
   закріплення, заміна, колаж і фон; друга особа бачить результат після refresh.
2. Viewer: лише читання. Revoke/роль під час редагування й upload, закінчення lease,
   background/resume і зміна акаунта не залишають доступного редактора/фото.
3. Ввести форму, закрити/перезапустити; відновити назву, keys, locks і layout.
   Перезапустити після prepare/send; retry не створює другий образ.
4. Паралельна правка: явний conflict/rebase; інша річ/образ не губиться.
   Видалення образу/речі та дубль комбінації дають помилку без silent overwrite.
5. Редагувати образ у капсулі/календарі: packed/manual та історія носіння збережені.
6. UA/EN/PL, 320/360/412 dp, 100/150/200% шрифт, TalkBack, клавіатура й поворот.
7. Регресія приватного LookEditor: чернетки, дублікати, всі джерела й фото.

Далі: multi-operation queue і continuous sync, потім FAM-04. Email/history
запрошень, orphan GC, live deployment та native-приймання залишаються відкритими.

### Publication checkpoint — 23 October 2026

Branch `feat/family-outfits-0.14.15`, based on main
`797daadb2f173627e8f05ef3c8db4aa20afc10af`. GitHub write authorization was restored
and verified by a successful create-tree request on 23 October 2026, after earlier
403 failures. Publication proceeds through a draft PR; Android/JVM CI results
must be verified for that PR before marking this work validated. Instrumentation
and device acceptance remain pending; no live Firebase deployment is included.
