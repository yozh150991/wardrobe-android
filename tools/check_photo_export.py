#!/usr/bin/env python3
"""PHOTO-17 (0.14.46): share a saved photo as a file — the rules' cases, the store, the viewer and the strings."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

store = read('photo/PhotoStore.kt')
body = store.split('suspend fun export(', 1)[1].split('fun newCapture', 1)[0]
check('PhotoExportRules.size(bounds.outWidth, bounds.outHeight, size)' in body, 'the size follows the rules (never enlarged)')
check('drawColor(PhotoExportRules.background(format))' in body, 'JPEG gets a real background')
check('"share/$namespace"' in body and '86_400_000L' in body and 'deleteRecursively' in body, 'exports live in cache/share per account and expire after a day')
check('PhotoExportRules.fileName(title, format)' in body and 'File(root, newId())' in body, 'a safe name in a fresh folder')
check('ExifInterface' not in body and 'directory' not in body, 'pixels only; never the saved photos folder')
check('FileProvider.getUriForFile(app, "${app.packageName}.files", file)' in store and store.count('FileProvider.getUriForFile(') == 1
      and store.count('sharedUri(') == 3, 'every URI is made with the application context (the roots are its cache)')
check('<cache-path name="share" path="share/" />' in (root / 'app/src/main/res/xml/file_paths.xml').read_text(encoding='utf-8'), 'FileProvider exposes share/')
check('<files-path' not in (root / 'app/src/main/res/xml/file_paths.xml').read_text(encoding='utf-8'), 'saved photos are never exposed')
dialog = read('photo/PhotoExportDialog.kt')
check('Intent.FLAG_GRANT_READ_URI_PERMISSION' in dialog and 'ClipData.newRawUri' in dialog and 'Intent.createChooser' in dialog, 'shared through the system chooser with a read grant')
check('PhotoExportRules.offered(ready.width, ready.height)' in dialog and 'R.string.photo_export_no_alpha' in dialog, 'only smaller sizes; says when PNG has no transparency')
check('ActivityNotFoundException' in dialog and 'R.string.photo_export_failed' in dialog, 'failures are explained')
viewer = read('photo/PhotoViewer.kt')
check('PhotoExportDialog(photoId, title, photos' in viewer and 'Icons.Outlined.Share' in viewer, 'the viewer has the share button')

from photo_export_strings import STRINGS
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    for key in STRINGS:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.(photo_export_\w+)', dialog + viewer)):
    check(name in STRINGS, f'R.string.{name} is not in photo_export_strings.py')
if failures: raise SystemExit('FAIL photo export:\n' + '\n'.join(failures))

java = shutil.which('java') or 'java'
with tempfile.TemporaryDirectory(prefix='wardrobe-export-') as output:
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, str(source / 'rules/PhotoExportRules.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoExportRulesCases.java')], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.PhotoExportRulesCases'], check=True)
print(f'PASS photo-export guards: store, FileProvider share/, chooser, {len(STRINGS)} strings × 4 locales')
