# FAM-01/03 — серверний перенос, 0.14.1

Дата: 13–14.10.2026. Codex. Код клієнта 31, Room/backup 10.
Стан: реалізований серверний етап; інтеграція з Android та FAM-02 ще потрібні.
Живий Firebase не змінено, нового APK немає.

## Реалізовано

- Python Firebase callable API: `family_create`, `family_resume`,
  `family_begin_upload`, `family_commit`. Довірений UID береться з Auth;
  перевіряються email_verified, disabled/revoked token. UID/роль/owner із тіла
  запиту не приймаються. Користувацькі UID підтримують A–Z/a–z/0–9/_/−,
  1–128 символів; стандартні UID Firebase сумісні.
- Стале резервування remoteId + wardrobeId; повтор того самого запиту
  повертає той самий гардероб. Чужий remoteId не можна привласнити.
- Upload прив'язаний до автора, archiveId, baseRevision, SHA-256, розміру;
  діє 30 хвилин. Після expiry потрібний новий archiveId. Staging незмінний.
- Перевірка ZIP/CRC/SHA-256, точного набору фото, envelope v1 / content v10,
  усіх 16 груп, зв'язків, purchase receipts, категорій/кольорів, plans/wears,
  capsule references, signatures та collage. Чужі/зайві поля, дублікати,
  traversal, symlink, шифрований ZIP, NaN, некоректні фото відхиляються.
  PNG/JPEG повністю декодується із захистом розміру.
- Публікація head у Firestore transaction: CAS, повторна перевірка ролі,
  відкликання/пониження ролі/expiry під час upload або validation. Повтор
  commit повертає збережений результат; паралельні різні commit мають одного
  переможця. Результат повтору може бути історичною ревізією: клієнт після
  нього має викликати resume для актуального head.
- Остаточний об'єкт пише лише backend; шлях, метадані та checksum формуються
  сервером. Клієнтські metadata зі staging не копіюються. Backend не додає
  download tokens або signed URLs. Storage читається авторизованим SDK;
  лише поточний head, активний viewer/editor або owner. Старі snapshot,
  staging, list, update/delete та приватний архів власника учаснику закриті.
- Membership/root/квоти/стан upload клієнт напряму не змінює. Membership
  поки створюється лише в тестах через Admin. Публічного API видачі ролі немає.

## Межі й конфігурація

Сімейний ZIP: до 128 MiB стиснутого / 256 MiB розпакованого, маніфест 8 MiB,
фото 12 MiB, до 16 мегапікселів і сторони 8192, до 5000 фото / 100000 рядків.
Це окремі серверні обмеження, не зменшення можливостей локального backup.
Перевищення не обрізає дані — запит завершується помилкою.
До 50 резервованих гардеробів на UID і 120 нових upload/добу; replay не
витрачає нову квоту. Видалення резервувань/GC та керування квотами ще потрібні.
Functions: europe-west1, 512 MiB, concurrency=1, max_instances=2, timeout=180s.
Ці параметри не є обіцянкою безкоштовного живого використання.

`cloud/firebase.json`, локальні порти, приватні rules і конфігурація клієнта
не змінені. Новий `cloud/firebase.family.example.json` — окремий явний
профіль, Firestore 8088. `family-storage.rules` містить чинні private правила
та нові family правила. Типовий deploy-rules.ps1 сімейний backend не вмикає.

## Відтворення автоматичних перевірок

Потрібні Python 3.12, JDK та сумісний із наявним cloud/package.json Node.
З папки `cloud`:

```bash
python3.12 -m venv family-functions/venv
family-functions/venv/bin/pip install -r family-functions/requirements.txt
npm ci
(cd family-functions && venv/bin/python -m unittest discover -s tests -v)
FAMILY_STORAGE_BUCKET=demo-wardrobe.appspot.com npx firebase emulators:exec \
  --project demo-wardrobe --config firebase.family.example.json \
  --only auth,firestore,storage,functions \
  "family-functions/venv/bin/python family-functions/tests/emulator_family.py && node --test family.rules.test.mjs"
```

Емуляторні тести явно відмовляються працювати без localhost endpoints і
`demo-wardrobe`. AnonymousCredentials доступні runtime лише за
`FUNCTIONS_EMULATOR=true` і цих перевірених demo/local умов; production — ADC.
Жодних service-account ключів до пакета не входить.

## Докази й відомі межі перевірки

Фактичні результати та журнал: `docs/history/RELEASE-VALIDATION-0.14.1.json`,
`docs/history/HOST-VALIDATION-0.14.1.json`, `release-evidence/family-backend-0.14.1.log`.
Виконано 29 domain tests, 20 Firebase integration tests і 6 rules tests.
Із 22 host-наборів 21 pass; check_auth_config.py повернув NOT CONFIGURED,
бо app/google-services.json навмисно не включений до вихідного пакета.
Це не успішна перевірка client Firebase config.
Окремо розрізняються доменні тести, реальні Firestore transaction тести,
Storage adapter та callable/Auth, і Storage/Firestore rules.

Storage emulator 1.1.3 ігнорує create-if-generation=0 у перевіреному
повторному GCS upload: виявлено під час перевірки. Adapter тепер спочатку
читає/звіряє наявний об'єкт і повертає його generation, а для першого create
зберігає GCS if_generation_match=0. Локальний успіх не доводить атомарність
цього GCS precondition у production — потрібен окремий staging smoke test.
Firebase Storage також обробляє зарезервовані download-token metadata
особливо: правила порожніх custom metadata не є доказом блокування такого
поля. Остаточний об'єкт створюється заново backend без копіювання metadata;
окремий тест перевіряє відсутність download token у фінальному об'єкті.

Native Android, live deployment, реальні листи та два фізичні телефони не
перевірені. Історичні Android-докази не переносяться на 0.14.1.

## Точне продовження

1. Android family transport: сформувати scoped ZIP, викликати create/begin,
   upload/commit/resume; прив'язати callback до account/runtime і
   FamilyMigrationStore. Обробити expiry із новим archive ID без втрати локальних
   даних. Не змішувати shared cache із private/full/scheduled backups.
2. FAM-02: довірені invite/accept/decline/role/revoke із перевіреною адресою,
   одноразовим token hash, expiry та ідемпотентністю; UI через цей API.
3. FAM-03: shared-cache lease, conflict UI, account switch і cleanup; FAM-04:
   окремі акаунти на двох пристроях. Універсальний експорт чужого ZIP не додавати.
4. Перед live rollout: GC staging/неопублікованих archive/старих head,
   App Check enrollment/enforcement, пілотний обсяг/вартість/квоти, провайдер
   email, production GCS-precondition smoke test. Немає автоматичного deploy.

Джерела API: https://firebase.google.com/docs/functions/callable,
https://firebase.google.com/docs/functions/manage-functions,
https://docs.cloud.google.com/python/docs/reference/storage/latest/generation_metageneration.
