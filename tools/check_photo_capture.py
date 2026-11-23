#!/usr/bin/env python3
"""PHOTO-19 (0.14.63): the debug capture of the cutout rules' input never runs in a release build and cannot
change the analysis."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/familywardrobe/app/photo'
capture, analyzer = (SRC / 'PhotoDebugCapture.kt').read_text(encoding='utf-8'), (SRC / 'PhotoAnalyzer.kt').read_text(encoding='utf-8')
failures = []


def check(ok, message):
    if not ok:
        failures.append(message)


check('ApplicationInfo.FLAG_DEBUGGABLE != 0' in capture, 'the capture is for debuggable builds only')
save = capture.split('fun save(', 1)[1].split('internal fun encode(', 1)[0]
check(save.index('if (!enabled(context)) return') < save.index('getExternalFilesDir'), 'nothing is written before the debuggable check')
check('catch (e: Exception)' in save, 'a failed capture never fails the analysis')
check('dropLast(KEEP)' in save and 'const val KEEP = 8' in capture, 'only the newest captures are kept')
check('mask.get(start + i)' in capture, 'the SDK buffer is read with absolute indexes')
check(analyzer.count('PhotoDebugCapture.') == 2 and 'if (PhotoDebugCapture.enabled(context)) withContext(Dispatchers.IO)' in analyzer,
      'the analyzer captures once, off the computing thread, and only when enabled')
check(analyzer.index('PhotoDebugCapture.save(') > analyzer.index('PhotoCutoutRules.refine('), 'the capture comes after the rules ran')
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
check('WRITE_EXTERNAL_STORAGE' not in manifest and 'MANAGE_EXTERNAL_STORAGE' not in manifest, 'no storage permission is added')
if failures:
    raise SystemExit('FAIL photo capture guards:\n  - ' + '\n  - '.join(failures))
print('PASS photo capture guards: debug builds only, cannot fail the analysis, newest 8 kept, no new permission')
