# Wardrobe 0.13.8 — фактична валідація

PHOTO-12–14 реалізовано у джерелах, статус verify. Code 27, Room/backup 8.
18 host-наборів pass; 4147 source/SQL checks, 11 XML, 987 рядків,
68 DAO. Нові Java production-правила: 588864 assertions. Oracle перевіряє
alpha-ерозію/дилатацію/blur; polygon tests — межі, geometry/protection/cancellation.
Synthetic 2048² проходить -Xmx256m; фактичний час у HOST-VALIDATION-0.13.8.json.
Це не native benchmark. Існуючі набори фото та 14 SQLite guards також пройшли.

Вісім нових Android-тестів і один JUnit wrapper підготовлені, не виконані.
Kotlin/Compose/KSP/Gradle/JUnit/lint/APK/телефон/емулятор у цьому релізі
не запускалися. Користувач відклав спільне приймання. Нової Gradle-спроби немає,
попередня 0.13.5 bootstrap-помилка не є доказом поточного релізу.

Механізм оновлення перевіряється окремо у ../package-validation-0.13.8.json
через Python-моделювання. Windows/PowerShell не виконувалися. Наступний native
етап: PHOTO-PRECISION-0.13.8.md та попередні PHOTO/R1/R2 на одній Android-збірці.
