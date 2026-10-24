# Wardrobe Android

Поточна база: **0.14.16-family-queue**, code **46**, Room/backup **10**.
Черга правок кількох спільних речей, FIFO і явне пакетне надсилання — [FAMILY-QUEUE-0.14.16.md](FAMILY-QUEUE-0.14.16.md).
Спільні образи: створення/редагування через наявний конструктор, чернетка та явне надсилання — [FAMILY-OUTFITS-0.14.15.md](FAMILY-OUTFITS-0.14.15.md).

Попередньо виправлено 13 багів QA-регресії: [QA-FIXES-0.14.14.md](QA-FIXES-0.14.14.md) (звіт — [QA-REPORT-0.14.13.md](QA-REPORT-0.14.13.md)); потрібен деплой family functions.
FAM-03: нова річ прямо в спільний гардероб із галереї/камери — [FAMILY-NEW-0.14.11.md](FAMILY-NEW-0.14.11.md).
FAM-03: заміна фото спільної речі — [FAMILY-PHOTO-0.14.10.md](FAMILY-PHOTO-0.14.10.md).
FAM-03: додавання копії власної речі з фото до спільного гардероба, чернетки/retry/CAS; попередній редактор полів збережено. Нового APK у пакеті немає.
Багфікси за аудитом 0.14.6: [AUDIT-FIXES-0.14.8.md](AUDIT-FIXES-0.14.8.md); FAM-03 додавання інтегровано поверх них як 0.14.9.

Деталі поточного обсягу: [FAMILY-ADD-0.14.8.md](FAMILY-ADD-0.14.8.md).

## Почати тут

- [Роадмап і поточні статуси](WARDROBE-ROADMAP.md)
- [Правила роботи з проєктом](AGENTS.md)
- [Очищення та застосування](PROJECT-CLEANUP.md)
- [Історія змін](CHANGELOG.md) · [Історичні звіти](docs/history/README.md)
- [Firebase](CLOUD-SETUP-UK.md) · [Git Safety](GIT-SAFETY.md)

## GitHub і локальні білди

[GITHUB-WORKFLOW-UK.md](GITHUB-WORKFLOW-UK.md) описує commit/push, гілки, теги, CI та звичайні локальні збірки.
GitHub CI працює: main 797daad успішно пройшов unit/lint/preview APK. Для нового коду дивись CI відповідного PR; native-приймання окреме.

## Поточний стан

Делікатне очищення фото: [PHOTO-CLEANUP-HOTFIX3.md](PHOTO-CLEANUP-HOTFIX3.md).
Сімейний перенос: [FAMILY-ANDROID-0.14.2.md](FAMILY-ANDROID-0.14.2.md),
[callable hotfix1](FAMILY-CALLABLE-HOTFIX1.md), [progress hotfix2](FAMILY-PROGRESS-HOTFIX2.md).
Ізольований перегляд серверної копії реалізований: [FAMILY-READER-0.14.5.md](FAMILY-READER-0.14.5.md).
Запрошення, discovery, roles/revoke/leave реалізовані: [FAMILY-ACCESS-0.14.6.md](FAMILY-ACCESS-0.14.6.md).
Перший shared metadata editor/outbox/CAS реалізовано: [FAMILY-EDIT-0.14.7.md](FAMILY-EDIT-0.14.7.md). Фото, додавання та образи реалізовані; далі — черга кількох операцій і постійна сімейна синхронізація. Листи та історія завершених запрошень ще потрібні.
Фото на телефоні й повна Android-збірка потребують перевірки.
Живі Storage rules користувач змінював окремо; не розгортати стару копію поверх них.

## Перевірка та збірка

Windows, у наявному проєкті з налаштованими JDK 17/21 та Android SDK:

```powershell
py -3 tools/check_project.py
.\build-apk.ps1 testDebugUnitTest lintDebug assembleDebug
```

macOS/Linux:

```bash
python3 tools/check_project.py
bash build-apk.sh testDebugUnitTest lintDebug assembleDebug
```

Для тестового пристрою/емулятора додатково `connectedDebugAndroidTest`.
Оновлювати встановлений застосунок APK з тим самим ключем; не очищати дані.
Клієнтський Firebase config і локальні SDK-шляхи залишаються у вашому проєкті.
Після clone установити hooks за GIT-SAFETY.md.
