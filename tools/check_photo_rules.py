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
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-tests-') as output:
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    str(root / 'app/src/main/java/com/familywardrobe/app/PhotoRules.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/PhotoRulesCases.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/PhotoGeometry.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/PhotoGeometryCases.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/PhotoCutoutRules.java'),
                    str(root / 'app/src/main/java/com/familywardrobe/app/PhotoBackdropRules.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/PhotoCutoutCases.java')], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.PhotoRulesCases'], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.PhotoGeometryCases'], check=True)
    subprocess.run([java, '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoCutoutCases'], check=True)
