#!/usr/bin/env python3
"""FAM-04 (0.14.25): account deletion erases every account-scoped file this app writes.

The old global storage has no per-account folder, so AccountErasure lists its files and preference names.
This guard fails when a new account store appears that the list does not cover.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main/java/com/familywardrobe/app'
erasure = (main / 'AccountDeletion.kt').read_text(encoding='utf-8')
files_source = (main / 'AccountFiles.kt').read_text(encoding='utf-8')
def listed(name):
    match = re.search(rf'val {name} = listOf\(([^)]*)\)', files_source, re.S)
    assert match, name
    return set(re.findall(r'"([^"]+)"', match.group(1)))
preferences, files, prefixes = listed('LEGACY_PREFERENCES'), listed('LEGACY_FILES'), listed('LEGACY_PREFIXES')
APP_PREFERENCES = {'shop-share-inbox'}  # app-wide inbox of shared shop links, not tied to an account
APP_FILES = {'account-storage-v1.json', 'account-erasure-v1.json'}  # the registry and the erasure marker
failures = []
sources = {path.name: path.read_text(encoding='utf-8') for path in sorted(main.glob('*.kt'))}
constants = {}
for text in sources.values():
    constants.update(dict(re.findall(r'const val (\w+) = "([^"]+)"', text)))
for name, text in sources.items():
    for pref in re.findall(r'getSharedPreferences\("([^"]+)"', text):
        if pref not in preferences | APP_PREFERENCES:
            failures.append(f'{name}: preferences "{pref}" are neither erased with an account nor app-wide')
    for raw in re.findall(r'File\((?:context\.|session\.context\.)?filesDir,\s*("([^"$]+)|(\w+))', text):
        value = raw[1] or constants.get(raw[2], raw[2])
        if value in APP_FILES or value in files or any(value.startswith(p) for p in prefixes):
            continue
        failures.append(f'{name}: filesDir entry "{value}" is not erased with the old global account')
    for path in re.findall(r'File\(context\.noBackupFilesDir,\s*"([^"]+)"', text):
        if not re.fullmatch(r'[\w-]+/\$\{(account\.key|context\.location\.key)\}.*', path) and not re.fullmatch(r'\$folder/\$\{account\.key\}', path):
            failures.append(f'{name}: noBackupFilesDir path "{path}" is not <kind>/<account key>/..., so erasure would miss it')
# The erasure must cover every place: database, per-account folders, caches, no-backup journals, registry.
for needle in ('dirs.deleteDatabase(location.databaseName)', 'File(dirs.files, "accounts/$key")', 'File(dirs.cache, "accounts/$key")',
               'File(dirs.cache, "camera/$key")', 'dirs.noBackup.listFiles()', 'startsWith("account-$key-")'):
    if needle not in files_source:
        failures.append(f'AccountFiles.erase lost: {needle}')
for needle in ('app.noBackupFilesDir', 'app.accountRegistry.forget(key)', 'AccountFiles.erase(', 'CloudSchedule.enabled(app, key, false)',
               'BackupSchedule.update(app, key, false)', 'FamilySyncSchedule.cancelAll(app, key)'):
    if needle not in erasure:
        failures.append(f'AccountErasure.erase lost: {needle}')
if failures:
    for failure in failures: print('FAIL:', failure)
    raise SystemExit(1)
print(f'PASS: account erasure covers {len(preferences)} legacy preference files, {len(files)} legacy files, '
      f'{len(prefixes)} prefixes and every no-backup journal')
