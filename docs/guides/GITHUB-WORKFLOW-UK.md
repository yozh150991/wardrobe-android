# DEV-03 — GitHub, локальні білди та версії

Поточний код: **0.14.4-photo-hotfix3 / code34**, Room/backup10.
Налаштування GitHub не змінює версію встановленого застосунку.

## Локальні білди залишаються

Windows, у своїй папці проєкту:

```powershell
.\build-apk.ps1 testDebugUnitTest lintDebug assembleDebug
```

Mac/Linux:

```bash
bash build-apk.sh testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Для оновлення встановленої версії потрібен той самий ключ підпису.
На іншому ПК збережіть свій google-services.json, local.properties і ключ окремо від Git.
Без мережі збірка можлива лише за наявності потрібних залежностей/SDK у кеші.
Push чи GitHub Actions для локальної збірки не потрібні.

## Перед першим GitHub push

Спочатку застосуйте DEV-02 cleanup, потім цей пакет через apply-github-update.
Коренем репозиторію має бути саме wardrobe-android: app/, cloud/, tools/ та .github/ на одному рівні.
Не ініціалізуйте Git у батьківській папці з ZIP, резервними копіями й особистими файлами.

У своєму проєкті перевірте `git status`. Якщо репозиторій уже є, збережіть його історію й remote; повторний початковий import не потрібен.
Для проєкту без Git:

```powershell
git init -b main
py -3 tools/git_safety.py install
git add --all
py -3 tools/git_safety.py check
git diff --cached --stat
git commit -m "chore: import Wardrobe 0.14.4 with GitHub CI"
py -3 tools/git_safety.py audit
```

Перед commit Git має знати ваше ім'я й email; установіть свої значення через локальний `git config user.name` / `git config user.email`, якщо їх ще немає.
Якщо guard виявив справжній секрет у старій історії, зупиніться й виправте причину за GIT-SAFETY.md; не використовуйте --no-verify або примусовий push.

Створіть порожній приватний репозиторій GitHub (без нового README/gitignore/license), якщо його ще немає.
Використайте його справжній URL; приклад містить заповнювач, не готовий remote:

```powershell
git remote -v
git remote add origin https://github.com/YOUR_ACCOUNT/YOUR_REPOSITORY.git
git push -u origin main
```

Якщо origin уже є, не виконуйте remote add і не замінюйте адресу автоматично.
Публікацію та перший GitHub CI-run потрібно підтвердити окремо; на момент підготовки доступ до GitHub не підключений.

## Робота на Windows і Mac

Один Git-каталог на SSD теж можливий, але завершуйте Git/build операції перед від'єднанням диска.
На кожному окремому clone встановіть hooks: `py -3 tools/git_safety.py install` або `python3 tools/git_safety.py install`.
Local config та signing key не приходять через git pull.

Приклад наступного завдання, з чистого main:

```bash
git switch main
git pull --ff-only
git switch -c feature/family-cache
# зміни, тести, оновлення роадмапу
git add --all
git commit -m "feat: isolate family wardrobe cache"
git push -u origin feature/family-cache
```

Потім Pull Request → перевірки → merge. Для виправлень використовуйте `fix/...`.
Не використовуйте ZIP-патчі поверх новіших Git-змін без перевірки конфліктів.

## Версії та теги

Єдине джерело Android-версії — app/build.gradle.kts.
Для звичайних commit версію підвищувати не потрібно. Для наступного APK-релізу:
оновити versionName, збільшити versionCode, записати зміни/межі перевірки в CHANGELOG і роадмап.
Різний versionName з тим самим або меншим code перевірка відхиляє при порівнянні з базою.
CI автоматично порівнює версію з базою Pull Request або попереднім commit push, коли ця база є.

```bash
python3 tools/check_version.py
python3 tools/check_version.py --base-ref main
```

Release tag має точно дорівнювати `v` + versionName. Поточна відповідність —
`v0.14.4-photo-hotfix3`, code34. Тег не створений автоматично: Android/native-приймання ще відкрите.
Після перевіреної збірки й commit можна створити annotated tag і явно відправити його:

```bash
git tag -a v0.14.4-photo-hotfix3 -m "Wardrobe 0.14.4 photo hotfix3"
git push origin v0.14.4-photo-hotfix3
```

Не пересувайте опублікований тег на інший commit. Наступний реліз отримує нову версію.
Тег запускає CI, але не публікує GitHub Release чи production APK автоматично.

## Що виконує CI

`.github/workflows/ci.yml`: push гілки/тега v*, Pull Request і ручний запуск.

1. Source and Git safety: повна доступна Git-історія/index, тести hooks, source scan,
   перевірка версії/тега й 22 host-набори. Відсутній особистий Firebase config явно позначається skipped_not_configured;
   пошкоджений config або будь-яка інша помилка залишаються failure.
2. Android debug preview: JDK17, Android SDK35, testDebugUnitTest, lintDebug, assembleDebug.
3. JSON-звіти, Android reports і APK зберігаються як Actions artifacts на 14 днів.

CI APK збирається з `-PciPreview=true`: applicationId **com.familywardrobe.app.ci**,
суфікс versionName **-ci**, окремі дані та тимчасовий runner debug key.
Він не замінює вашу встановлену com.familywardrobe.app. Firebase client config не підключений,
тому цей APK не підтверджує вхід, cloud sync чи family transfer. Це compile/test preview.
Через новий runner key CI APK різних запусків можуть вимагати перевстановлення лише тестового `.ci` застосунку.
Основний застосунок і його дані не видаляйте.

Workflow має лише contents:read, не використовує ваш signing key чи Firebase secrets і не виконує deploy.
Dependabot перевіряє версії GitHub Actions щотижня. Офіційні action major versions перевірені 18.10.2026:
[checkout](https://github.com/actions/checkout/releases), [setup-python](https://github.com/actions/setup-python),
[setup-java](https://github.com/actions/setup-java), [upload-artifact](https://github.com/actions/upload-artifact).
Після першого успішного запуску можна зробити Source and Git safety та Android debug preview обов'язковими перевірками main у налаштуваннях репозиторію.

## Межі перевірки цього пакета

Фактичні локальні результати — GITHUB-READINESS-VALIDATION.json.
GitHub workflow ще не запускався на GitHub; YAML/static checks не підтверджують Gradle build.
Android compile/lint/APK, native macOS/Windows та emulator integration після правки тестового пароля ще потребують виконання.
Повний локальний JDK/SDK у середовищі підготовки відсутній. Старі звіти не зараховані як нові успішні збірки.
