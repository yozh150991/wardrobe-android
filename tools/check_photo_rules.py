#!/usr/bin/env python3
"""Run the exact production Java photo rules. Requires JDK 17; no SDK/Gradle required."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = shutil.which('java')
if not java:
    raise SystemExit('JDK 17 must be available on PATH.')
# 0.14.37: the model sees a contrasting backdrop instead of transparency; the garment's interior is protected.
analyzer = (root / 'app/src/main/java/com/familywardrobe/app/photo/PhotoAnalyzer.kt').read_text(encoding='utf-8')
cutout = (root / 'app/src/main/java/com/familywardrobe/app/rules/PhotoCutoutRules.java').read_text(encoding='utf-8')
failures = []
if 'PhotoCutoutRules.modelInput(sourcePixels) { workContext.ensureActive() }' not in analyzer or 'InputImage.fromBitmap(shown ?: bitmap, 0)' not in analyzer:
    failures.append('the model input replaces transparency with a contrasting backdrop')
if 'PhotoCutoutRules.refine(sourcePixels, bitmap.width, bitmap.height, confidence, stronger)' not in analyzer:
    failures.append('the cutout keeps the source pixels, not the model input')
if cutout.count('protect(alpha, source, width, height, confidence, background, distinct, fabric, queue, visited, checkpoint);') != 1:
    failures.append('every mask protects the garment interior')
if 'if ((gentle[i] & 255) < (alpha[i] & 255)) alpha[i] = gentle[i];' not in cutout:
    failures.append('the stronger action never keeps more than the default')
if failures: raise SystemExit('FAIL photo cutout:\n' + '\n'.join(failures))
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-tests-') as output:
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    str(root / 'app/src/main/java/com/familywardrobe/app/rules/PhotoRules.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoRulesCases.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/rules/PhotoGeometry.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoGeometryCases.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/rules/PhotoCutoutRules.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/rules/PhotoBackdropRules.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoCutoutCases.java')], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.PhotoRulesCases'], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.rules.PhotoGeometryCases'], check=True)
    subprocess.run([java, '-Xmx256m', '-cp', output, 'com.familywardrobe.app.rules.PhotoCutoutCases'], check=True)
