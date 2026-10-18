# Перевірки 0.13.7

11 жовтня 2026. PHOTO-09–10 — verify; code 26, Room/backup 8.
18 host-наборів pass: 4024 source/SQL, 11 XML, 958 рядків локалізації,
68 DAO-запитів. Докази: HOST-VALIDATION-0.13.7.json та машинний release-звіт.

108013 нових Java assertions виконують production правила роздільності,
семплювання, меж пам’яті, fit без збільшення та масок. PhotoCutoutRules також
обробляє synthetic 2048² під -Xmx256m; фактичний host time у звіті.
Це не замір Android/ML. 14 перевірок виконують SQL заміни фото на SQLite,
включно зі stale/repeat, метаданими, іншим гардеробом/акаунтом і видаленням.

12 нових Android-тестів підготовлено (9 черги + 3 роздільності), не виконано.
Змінено старе очікування crop для маленького фото, яке тепер не збільшується
штучно. Kotlin/Compose/KSP, Gradle/JUnit/lint/assemble, PNG/AtomicFile/Room
на Android, жести, accessibility, peak memory, ML-якість та backup-профілювання
залишаються на погоджений користувачем спільний етап. Нового APK немає;
нової спроби Gradle bootstrap цього релізу не було.

Матриця: PHOTO-QUALITY-0.13.7.md → PHOTO-INSPECTION-0.13.6.md → PHOTO-01–05/R1/R2.
Історичні native/Gradle/Git Safety докази не вважаються новим запуском.
Package-validation — Python-моделювання updater, а не PowerShell/Windows.
