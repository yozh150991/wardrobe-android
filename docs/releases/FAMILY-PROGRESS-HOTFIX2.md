# FAM-01/03 — виправлення падіння прогресу, 17.10.2026

Android: **0.14.3-family-hotfix2**, versionCode **33**, Room/backup **10**.
Виконавець: Codex. Статус: виправлено в коді, приймання на телефоні відкрите.

## Доказ і причина

Наданий користувачем wardrobe-crash.txt містить два падіння com.familywardrobe.app
17.10 о 19:02:09 та 19:02:51: `IllegalArgumentException: current must not be NaN`
у ProgressBarRangeInfo / Material3 LinearProgressIndicator. Інший запис про
com.google.android.as не стосується Wardrobe.

FamilyTransferControl перевіряв `state.total > 0` при композиції, але callback
індикатора повторно читав delegated mutable state. Після зміни UPLOADING на
COMMITTING контролер публікує bytes=0/total=0. Старий callback ще може бути
викликаний для semantics/drawing: 0f/0 дає NaN; coerceIn не усуває NaN.
Це пояснює наданий stack trace; порядок подій на пристрої після виправлення
поки не перевірено.

## Зміна

- FamilyTransferControl захоплює один immutable snapshot і передає готовий callback.
- FamilyTransferProgress обчислює bounded finite fraction до створення callback;
  відсутній/нульовий/від'ємний total або інший етап означає indeterminate indicator.
- Старий callback не читає нові counters, тому transition більше не дає 0/0.
- Схема даних, локальні ZIP, UID/guard, запити backend та retry не змінені.

## Перевірки

Виконано 19 Kotlin/JVM JUnit-тестів: 15 попередніх coordinator-тестів і 4 нові.
Нові: відтворення старого NaN та безпечний deferred read; unknown total / інший
етап; незмінність старого callback при наступному прогресі; Long boundaries.
Запуск: tools/check_family_transfer.py, Kotlin 2.0.20 / JDK17 / JUnit4.13.2.
Це не Compose/UI-тест: Android SDK/Gradle environment відсутнє; Android build,
lint, APK та приймання на фізичному телефоні агентом НЕ виконано.
PowerShell updater у цьому середовищі НЕ запускався; Python updater перевірено
на dry-run, apply, повторі, конфліктах і збереженні локальних конфігурацій.

## Застосування на Windows

1. Розпакувати wardrobe-family-transfer-hotfix2.zip в окрему папку.
2. Відкрити PowerShell у цій папці та застосувати до оригінального проєкту:

```powershell
.\apply-release-update.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android'
Set-Location 'D:\Data\Downloads\Wardrobe\wardrobe-android'
.\build-apk.ps1
```

Новий APK: app/build/outputs/apk/debug/app-debug.apk. Установити як оновлення
наявного застосунку з тим самим ключем підпису, без uninstall / clear data.
Патч містить повні джерела та накопичувальний payload для 0.14.2 і hotfix1;
скрипт перевіряє конфлікти, зберігає backup та підтримує повторне застосування.
google-services.json, SDK paths та ключі підпису збережено. Firebase deploy
для цього Android-виправлення не потрібен.

## Приймання та незавершена діагностика

На оновленому APK повторити Resume / check, перевірити UPLOADING → COMMITTING →
VERIFYING, завершення або видиме повідомлення без закриття. Закрити/відкрити
та повторити Resume / check; перевірити, що локальні речі й фото залишилися.
Якщо знову буде ACCESS, потрібна перевірка свіжого upload ticket і Storage.

Storage 403 підтверджено Android-логом 17.10 о 18:48. Потім користувач змінював
правила в Firebase Console: відсутній/null/identity contentEncoding та
відсутня/null/порожня metadata. Після останньої зміни отримано це падіння UI.
Це НЕ доводить успішного commit або остаточного усунення 403.
Cloud source rules у цьому Android-патчі не змінені: консольні зміни ще потрібно
окремо перевірити, перенести в cloud/family-storage.rules та протестувати.
Не перезаписувати поточні console rules старим storage deploy під час цієї перевірки.
Backend поки відхиляє truthy content_encoding, у тому числі identity; це окрема
відкрита невідповідність, не підтверджена на фактичному завантаженому об'єкті.
FAM-01/03 лишається partial; shared-cache, FAM-02 і continuous sync не закриті.
