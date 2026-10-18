# Перевірка 0.12.0 — 8 жовтня 2026

14/14 host-наборів пройшли: `HOST-VALIDATION-0.12.0.json`. Зокрема 34 production
Java assertions для імпорту; source/SQL 3434, XML 11, локалізовані рядки 821, DAO 67.
Стан конкретних Android-сценаріїв — **не виконано**: тест у коді не є доказом запуску.

Gradle 8.11.1 bootstrap заблокований мережею (`Network is unreachable`).
Kotlin/Compose/KSP, Gradle JUnit, lint, instrumented tests, Firebase, APK і UI
для цієї версії не перевірено. Старі 0.11.1 результати залишаються історичними.
PowerShell не виконувався; JSON payload перевірено Python-моделлю updater.

Машинний звіт: `RELEASE-VALIDATION-0.12.0.json`.
Хеші джерел: `release-evidence/0.12.0/source-SHA256SUMS.txt`.
Лог Gradle: `release-evidence/0.12.0/gradle-bootstrap.log`.
Матриця приймання й команди: `R1-UPDATE-0.12.0.md`.
Оновлювач: `../package-validation.json`.

Наступний агент починає з `AGENTS.md` та `WARDROBE-ROADMAP.md`, збирає
у налаштованому JDK 17/SDK 35 середовищі та фіксує реальні результати.
До цього R1 лишається `verify`, APK не видається за готовий.
