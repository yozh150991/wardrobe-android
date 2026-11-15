# Wardrobe Android

Поточна база: **0.14.50-packages**, code **80**, Room **11**, формат копії **11** (лише з даними поїздки; інакше 10).
Останні зміни: [CHANGELOG.md](CHANGELOG.md); документи релізів — у [docs/releases](docs/releases).
Підтверджені пакети всіх сімейних черг і opt-in фонове надсилання — [FAMILY-SYNC-0.14.19.md](docs/releases/FAMILY-SYNC-0.14.19.md).
Черга спільних образів та атомарне збереження форми/публікації — [FAMILY-OUTFIT-QUEUE-0.14.18.md](docs/releases/FAMILY-OUTFIT-QUEUE-0.14.18.md).
Черги додавання речей та заміни фото: кілька чернеток, FIFO, явний rebase — [FAMILY-MEDIA-QUEUE-0.14.17.md](docs/releases/FAMILY-MEDIA-QUEUE-0.14.17.md).
Черга правок кількох спільних речей, FIFO і явне пакетне надсилання — [FAMILY-QUEUE-0.14.16.md](docs/releases/FAMILY-QUEUE-0.14.16.md).
Спільні образи: створення/редагування через наявний конструктор, чернетка та явне надсилання — [FAMILY-OUTFITS-0.14.15.md](docs/releases/FAMILY-OUTFITS-0.14.15.md).

Попередньо виправлено 13 багів QA-регресії: [QA-FIXES-0.14.14.md](docs/releases/QA-FIXES-0.14.14.md) (звіт — [QA-REPORT-0.14.13.md](docs/releases/QA-REPORT-0.14.13.md)); потрібен деплой family functions.
FAM-03: нова річ прямо в спільний гардероб із галереї/камери — [FAMILY-NEW-0.14.11.md](docs/releases/FAMILY-NEW-0.14.11.md).
FAM-03: заміна фото спільної речі — [FAMILY-PHOTO-0.14.10.md](docs/releases/FAMILY-PHOTO-0.14.10.md).
FAM-03: додавання копії власної речі з фото до спільного гардероба, чернетки/retry/CAS; попередній редактор полів збережено. Нового APK у пакеті немає.
Багфікси за аудитом 0.14.6: [AUDIT-FIXES-0.14.8.md](docs/releases/AUDIT-FIXES-0.14.8.md); FAM-03 додавання інтегровано поверх них як 0.14.9.

Деталі поточного обсягу: [FAMILY-ADD-0.14.8.md](docs/releases/FAMILY-ADD-0.14.8.md).

## Почати тут

- [Роадмап і поточні статуси](WARDROBE-ROADMAP.md)
- [Правила роботи з проєктом](AGENTS.md)
- [Очищення та застосування](docs/guides/PROJECT-CLEANUP.md)
- [Історія змін](CHANGELOG.md) · [Історичні звіти](docs/history/README.md)
- [Firebase](docs/guides/CLOUD-SETUP-UK.md) · [Git Safety](docs/guides/GIT-SAFETY.md)

## Структура проєкту

| Де | Що |
| --- | --- |
| `README.md`, `AGENTS.md`, `CHANGELOG.md`, `WARDROBE-ROADMAP.md` | Вхід у проєкт, правила, історія змін і роадмап |
| `docs/releases/` | Документ кожного релізу й виправлення (`НАЗВА-версія.md`) |
| `docs/guides/` | Посібники: Firebase, вхід, Git Safety, робота з GitHub, обробка фото, специфікація UX |
| `docs/history/` | Історичні звіти до 0.13.5 |
| `release-evidence/` | Звіти перевірок: `host-runs/` (кожен запуск host checks), `validation/` (звіти релізів і старого оновлювача) |
| `app/` | Android-застосунок: код, ресурси, тести, схеми Room |
| `app/src/main/java/com/familywardrobe/app/` | Код за пакетами: `rules` (правила на Java), `family`, `ui`, `photo`, `data`, `outfits`, `auth`, `backup`, `cloud`, `importing`; у корені — лише точки входу, база Room і фонові задачі. Докладно: [docs/releases/PACKAGES-0.14.50.md](docs/releases/PACKAGES-0.14.50.md) |
| `cloud/` | Правила Firebase і family functions |
| `tools/` | Host-перевірки, тексти інтерфейсу, Git Safety |

## GitHub і локальні білди

[GITHUB-WORKFLOW-UK.md](docs/guides/GITHUB-WORKFLOW-UK.md) описує commit/push, гілки, теги, CI та звичайні локальні збірки.
GitHub CI працює: main 797daad успішно пройшов unit/lint/preview APK. Для нового коду дивись CI відповідного PR; native-приймання окреме.

## Поточний стан

Делікатне очищення фото: [PHOTO-CLEANUP-HOTFIX3.md](docs/releases/PHOTO-CLEANUP-HOTFIX3.md).
Сімейний перенос: [FAMILY-ANDROID-0.14.2.md](docs/releases/FAMILY-ANDROID-0.14.2.md),
[callable hotfix1](docs/releases/FAMILY-CALLABLE-HOTFIX1.md), [progress hotfix2](docs/releases/FAMILY-PROGRESS-HOTFIX2.md).
Ізольований перегляд серверної копії реалізований: [FAMILY-READER-0.14.5.md](docs/releases/FAMILY-READER-0.14.5.md).
Запрошення, discovery, roles/revoke/leave реалізовані: [FAMILY-ACCESS-0.14.6.md](docs/releases/FAMILY-ACCESS-0.14.6.md).
Перший shared metadata editor/outbox/CAS реалізовано: [FAMILY-EDIT-0.14.7.md](docs/releases/FAMILY-EDIT-0.14.7.md). Фото, додавання та образи реалізовані; далі — черга кількох операцій і постійна сімейна синхронізація. Листи та історія завершених запрошень ще потрібні.
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
Після clone установити hooks за [docs/guides/GIT-SAFETY.md](docs/guides/GIT-SAFETY.md).
