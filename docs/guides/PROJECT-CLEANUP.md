# DEV-02 — очищення проєкту

База: **0.14.4-photo-hotfix3 / code34**, Room/backup10. Це source-only пакет.
Якщо у вас 0.14.2 або family-hotfix2, спочатку застосуйте photo hotfix3.
Не копіюйте повне дерево поверх наявного проєкту: застосуйте updater з кореня цього пакета.

## Що зроблено

- Видалено невикористаний OfflineScreen.kt і лише offline_title/offline_session_hint у генераторі та чотирьох strings.xml. Інша offline-поведінка збережена.
- Source scan: app/src, tools, кореневі *.kts і app/*.kts; бекапи, generated/build, node_modules та symlink не обходяться. app/build.gradle.kts перевіряється.
- 73 історичні файли перенесено в docs/history, включно зі старими звітами й evidence 0.12.0–0.13.5 та парою ui-failure.png/xml. Посилання оновлено; JSON/бінарні докази не переписані.
- README: 282 → 42 рядки. Роадмап: 155018 → приблизно 75 КБ. Старий README і повний журнал рішень збережені у CHANGELOG. Усі попередні task ID збережені.
- Поточні звіти 0.14.2, FAMILY-HOTFIX2, PHOTO-HOTFIX3, package-validation.json збережені. Нові host reports пишуться в release-evidence/host-runs з повною версією та UTC timestamp.

## Поправки до початкового аналізу

137 МБ gradle ZIP уже входять у 276 МБ .tools; їх не можна додавати двічі.
Залежність від .tools може бути в локальному .idea/gradle.xml, Gradle settings або середовищі — updater перевіряє ці посилання й залишає каталог, якщо вони є.
Генератор перекладів відстає від деяких ручних доповнень, тому повну регенерацію не виконано: вилучені лише два ключі.
Фактичних локальних кешів, 24 бекапів і старих updater-файлів у доступному source-пакеті немає; їхні розміри тут не підтверджені.
connect-firebase.ps1 та можливі дублікати README-UK.md/wardrobe-windows-guide-uk.md автоматично не видаляються: локальні копії не були доступні для порівняння.

## Windows

Розпакуйте пакет окремо від проєкту. Закрийте Android Studio, збірки й Firebase emulators.
З кореня розпакованого пакета запустіть попередній перегляд:

```powershell
.\apply-cleanup.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android' -ArchiveLocal -PurgeCaches
```

Застосування того самого набору дій:

```powershell
.\apply-cleanup.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android' -ArchiveLocal -PurgeCaches -Apply
```

Потрібен Python 3.9+. Без -Apply нічого не записується.
За потреби напряму: `py -3 apply-cleanup.py --project-path 'D:\Data\Downloads\Wardrobe\wardrobe-android' --archive-local --purge-caches --apply`.

## macOS/Linux

```bash
bash ./apply-cleanup.sh --project-path '/Volumes/SSD/Wardrobe/wardrobe-android' --archive-local --purge-caches
bash ./apply-cleanup.sh --project-path '/Volumes/SSD/Wardrobe/wardrobe-android' --archive-local --purge-caches --apply
```

Шлях потрібно замінити фактичним. Можна застосувати лише зміни джерел: пропустити --archive-local і --purge-caches.

## Локальні файли та відкат

- --archive-local: переносить .tools (якщо не використовується), старі apply-auth/design/editor/photo/wardrobe-update.ps1, їхні wardrobe-*-update-data.json, wardrobe-*.patch, prerequisites/wardrobe-management-update-0.1.1.zip та connection-validation.json поза проєкт. Файли з посиланнями в активних скриптах/конфігурації залишає.
- Переносить .wardrobe-*-backup-* поза проєкт, залишаючи два останні за timestamp у назві. Резервна копія самого очищення також зберігається зовні.
- Архів розташований поруч: `<назва-проєкту>-cleanup-archives/<timestamp-id>/`. Збережено початкові байти файлів, manifest, файли видаленого екрана й шлях відкату. Архів не відправляється у Git чи хмару автоматично.
- --purge-caches видаляє лише app/build, .gradle, .kotlin, cloud/family-functions/__pycache__ та cloud/firestore-debug.log. Наступна збірка буде довшою й може потребувати мережі. Глобальний ~/.gradle не чіпає.
- Архівування зменшує робочу папку, але не звільняє місце на диску. Фізично місце звільняється лише від очищених кешів. Скрипт показує ці два числа окремо.
- Усі зміни джерел перевіряються до запису за SHA-256 (для тексту з урахуванням CRLF/BOM); невідомі локальні зміни зупиняють updater. Повторне застосування підтримується. Символічні посилання/junction у цілях відхиляються.
- Локальні конфігурації, ключі підпису, Firebase rules, база й фото користувача не змінюються. App version/schema не підвищуються заради очищення.

Відкат спочатку показує план. Візьміть точний шлях архіву з результату:

```powershell
.\apply-cleanup.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android' -Restore 'ПОВНИЙ_ШЛЯХ_АРХІВУ'
.\apply-cleanup.ps1 -ProjectPath 'D:\Data\Downloads\Wardrobe\wardrobe-android' -Restore 'ПОВНИЙ_ШЛЯХ_АРХІВУ' -Apply
```

Відкат відмовиться перезаписувати нові зміни. Видалені generated-кеші не відновлюються — їх створить збірка.
За конфлікту не застосовуйте файли примусово; збережіть повідомлення й узгодьте різницю.

## Перевірка після застосування

```powershell
Set-Location 'D:\Data\Downloads\Wardrobe\wardrobe-android'
py -3 tools/check_project.py
.\build-apk.ps1 testDebugUnitTest lintDebug assembleDebug
```

Фактично виконані перевірки: CLEANUP-VALIDATION.json. Повна Android-компіляція й lint тут не виконані: build-apk.sh зупинився на відсутньому javac/full JDK launcher. Java compiler module доступний для host tests, але це не Android build. Native Windows PowerShell/macOS запуск також не виконаний.
Поточні проблеми фото й сімейної синхронізації не оголошуються виправленими цим очищенням.
