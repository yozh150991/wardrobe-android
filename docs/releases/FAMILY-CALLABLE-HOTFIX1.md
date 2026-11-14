# FAM-01/03 — виправлення callable Long, 16.10.2026

Android залишається 0.14.2 / versionCode 32, Room/backup 10.
Це серверне виправлення для вже встановленого APK.

## Причина й межі підтвердження

FamilyBackend.begin передає record.archiveSize як Kotlin Long. Firebase Android
Serializer кодує Long об’єктом @type/value (Int64Value). Python callable handler
може передати цей об’єкт без перетворення в request.data. Наш strict integer()
у begin_upload відхиляв його до завантаження ZIP. Це відтворено локально з
гардеробом із однієї речі та PNG; після виправлення той самий запит проходить
begin → upload у memory adapter → commit → replay → resume.

Журнал реальної невдалої спроби користувача недоступний: це підтверджений дефект
коду, але не підтверджений журналом діагноз конкретної спроби зі скриншота.

## Зміни

- family/callable_wire.py: суворе декодування Int64Value/UInt64Value лише для
  size/baseRevision запиту begin_upload. Уже декодовані int також підтримуються.
- main.py: виклик decoder всередині наявної межі FamilyError; повідомлення про
  відмови в server log без payload, UID, токенів і вмісту гардероба.
- Перевірки розміру, ревізії, прав, ZIP і checksum залишаються чинними.
- 6 нових host-тестів, у тому числі відтворення старого збою й повний service flow.
- Callable emulator scenario тепер використовує Android-подібний wrapped size.

## Перевірки

35 backend host-тестів PASS (29 попередніх + 6 нових).
Журнал: release-evidence/family-callable-hotfix1.log.
Firebase emulators, live Firebase й тест на телефоні цього разу НЕ запускалися.
Android-код і APK не змінюються; Android build не запускався.
Python updater перевірено на базі 0.14.2, повторному застосуванні та конфлікті.
PowerShell updater не запускався: pwsh недоступний у середовищі.

## Застосування (Windows)

Розпакуйте ZIP в окрему папку. Відкрийте PowerShell у цій папці:

```powershell
.\apply-release-update.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android'
Set-Location 'D:\Data\Downloads\Wardrobe\wardrobe-android\cloud'
firebase deploy --only functions:family --config firebase.family.example.json --project wardrobe-75fea
```

Скрипт оновлює тільки визначені файли, перевіряє конфлікти, створює backup.
.env.wardrobe-75fea, venv, Firebase config і ключі підпису не змінює.
Пакет розрахований на базу 0.14.2; старіші бази спочатку оновити до 0.14.2.
Не копіюйте повне дерево поверх локального проєкту вручну.

Після успішного deploy натисніть Resume / check для потрібного гардероба.
Якщо програма повідомить про зміну даних після підготовки, натисніть Prepare again.
Ця дія готує нову копію незавершеного перенесення, локальні речі залишаються.
Якщо та сама помилка повториться — потрібен server log; новий запис починається
з family validation: й містить method/code/reason/message.
Сімейні запрошення й постійна shared sync поки не реалізовані.

## Джерела протоколу (перевірено 16.10.2026)

- https://firebase.google.com/docs/functions/callable-reference
- https://raw.githubusercontent.com/firebase/firebase-android-sdk/master/firebase-functions/src/main/java/com/google/firebase/functions/Serializer.kt
- https://raw.githubusercontent.com/firebase/firebase-functions-python/main/src/firebase_functions/https_fn.py
