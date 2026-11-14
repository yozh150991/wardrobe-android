# R1: каталог та імпорт магазину — 0.12.0

7 жовтня 2026. `0.12.0-release`, code 18, `com.familywardrobe.app`, Room/backup 8.
Це пакет вихідного коду; нового APK немає. Статус UX-01–02/IMP-01–03: verify.

## Зміни

- Заголовок поєднує розділ і вибір гардероба; статистика/черга в меню. Одна
  основна дія додавання відкриває камеру, галерею, пакет, магазин або ручний редактор.
- Android Share приймає `text/plain` через cold start/onNewIntent. Локальна
  черга до 10 посилань переживає перезапуск; авторизований акаунт прив’язується
  до запиту, непідписаний запит приймається після входу. Активний редактор не затирається.
- Імпорт вручну починається з меню додавання або «Хочу купити». Відкриті HTTPS
  metadata: JSON-LD Product/Offer, Open Graph/title. Кілька товарів/фото дають вибір.
  Без точної підтриманої валюти/ціни сума порожня; діапазон не стає ціною.
- Перед Save можна змінити поля/фото, гардероб, обрати власну річ/бажаний товар,
  доповнити наявну потребу. Її ID, бюджет, вимоги та історія зберігаються.
  URL-збіги в обраному гардеробі пропонуються для перегляду; копія потребує підтвердження.
- Власна імпортована річ та source Wish/history записуються однією транзакцією;
  повтор Save не дублює річ. URL/ціна/фото використовують чинні backup/sync структури.
  Фото власної речі проходить наявну обробку з можливістю ручного виправлення.
- Чернетка зберігає URL, отримані варіанти, фото, вибір гардероба/потреби й ціль.
  UA/EN/PL та теми збережені; нові елементи без фіксованої висоти тексту.

## Межі цієї реалізації

Тільки Share тексту/URL: `image/*`/`EXTRA_STREAM` — наступна доробка IMP-01.
Зображення можна додати через поточний системний picker. JS-only, приватні та
заблоковані магазини можуть не надати metadata; є ручний шлях. Не виконується
код сторінки, не використовуються cookie/login магазину й не оформлюються замовлення.
HTTP URL можна зберегти вручну; автоотримання лише HTTPS/default 443.

HTML ≤2 MiB, зображення ≤16 MiB, до 5 запитів з redirect, connect/read timeout
10 с та перевірка загального 45-секундного бюджету між операціями читання.
DNS/блокувальне системне читання можуть затримати скасування до повернення операції.
Некоректне фото не блокує редактор. Незавершена чернетка/черга не входять до portable backup.

## Фактично виконано

`python3 tools/run_host_checks.py`: 14/14 host-наборів, включно з 34 assertions
production URL/metadata Java. `check_project.py`: 3434 source/SQL, 11 XML,
821 localized strings, 67 DAO. Java скомпільовано через `jdk.compiler` JDK 17.
JSON-звіт: `docs/history/HOST-VALIDATION-0.12.0.json`. Це не компіляція Kotlin/Compose.

`bash gradlew --version`: Gradle 8.11.1 bootstrap не вдався —
`java.net.SocketException: Network is unreachable`. Доказ:
`docs/history/release-evidence/0.12.0/gradle-bootstrap.log`. Gradle JUnit/lint, Kotlin/Compose,
Android/Firebase, APK, UI та Windows/PowerShell у цій версії не виконані.

Підготовлено 8 нових instrumented tests: п’ять Share/чернетка/account boundaries
у `ShopImportFlowTest`, три atomic-save/retry/conflict у `PurchaseFlowTest`;
`EditorDraftStoreTest` доповнено shop-kind. Вони ще не запускалися.

## Обов’язкове приймання після збірки

Усі рядки нижче **не виконані** для 0.12.0. Докази прикріпити до release-звіту.

| Сценарій | Очікувана перевірка |
| --- | --- |
| Каталог | Порожній/200+ речей, довга назва гардероба, пошук/фільтри/позиція після Back; усі старі дії доступні |
| Доступність | UA/EN/PL, 320/360/412 dp, 100/200%, Light/Dark, TalkBack, клавіатура, прокрутка меню |
| Share | Cold start, відкритий застосунок, signed out → login, duplicate intent, process death до/після claim |
| Активний редактор | Share не змінює поля; після Save/discard запит доступний; акаунт B не бачить запит A |
| Metadata | Один/кілька товарів і URL, variant query, unsupported currency, range/old price, malformed JSON, без metadata |
| Мережа/фото | Offline, redirect chain, timeout, cancel, приватні адреси, пошкоджене/надвелике фото; ручний fallback |
| Редактори | Обидві цілі, need conversion із бюджетом, інший гардероб, URL-збіг → перегляд, повтор/скасування |
| Надійність | Restart до Save/після commit, retry, rollback при помилці, cleanup тимчасових фото, stale analysis |
| Перенос | Backup/restore та same-account cloud повертають джерело/ціну/кінцеве фото/історію; друга UID ізольована |
| Оновлення | CheckOnly/повтор/конфлікт/часткове оновлення у PowerShell; SDK, ключі, client config, порт 8088 збережені |

Запуск у налаштованому оригінальному проєкті:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\build-apk.ps1 testDebugUnitTest lintDebug assembleDebug
.\build-apk.ps1 connectedDebugAndroidTest
```

Встановлення поверх попередньої версії лише APK з тим самим signing key.
До приймання `verify` не змінюється на `done`. Наступні роботи —
`WARDROBE-ROADMAP.md`; модель сімейного доступу — `ADR-FAMILY-WARDROBES.md`.
