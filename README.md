# Wardrobe Android

Поточна база: **0.14.4-photo-hotfix3**, code **34**, Room/backup **10**.
Очищення DEV-02 змінює структуру документації й інструменти перевірки та прибирає невикористаний екран. Нового APK у пакеті немає.

## Почати тут

- [Роадмап і поточні статуси](WARDROBE-ROADMAP.md)
- [Правила роботи з проєктом](AGENTS.md)
- [Очищення та застосування](PROJECT-CLEANUP.md)
- [Історія змін](CHANGELOG.md) · [Історичні звіти](docs/history/README.md)
- [Firebase](CLOUD-SETUP-UK.md) · [Git Safety](GIT-SAFETY.md)

## GitHub і локальні білди

[GITHUB-WORKFLOW-UK.md](GITHUB-WORKFLOW-UK.md) описує commit/push, гілки, теги, CI та звичайні локальні збірки.
GitHub workflow підготовлено; віддалений запуск і Android-приймання ще не підтверджені.

## Поточний стан

Делікатне очищення фото: [PHOTO-CLEANUP-HOTFIX3.md](PHOTO-CLEANUP-HOTFIX3.md).
Сімейний перенос: [FAMILY-ANDROID-0.14.2.md](FAMILY-ANDROID-0.14.2.md),
[callable hotfix1](FAMILY-CALLABLE-HOTFIX1.md), [progress hotfix2](FAMILY-PROGRESS-HOTFIX2.md).
Shared cache, запрошення й постійна сімейна синхронізація ще потрібні.
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
