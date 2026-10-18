#!/usr/bin/env python3
"""Run production Java ownership/access logic. Android persistence and UI need native tests."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-family-') as output:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    str(source / 'FamilyRules.java'), str(source / 'WardrobeScope.java'),
                    str(root / 'app/src/test/java/com/familywardrobe/app/FamilyCases.java')], check=True)
    subprocess.run(['java', '-cp', output, 'com.familywardrobe.app.FamilyCases'], check=True)
print('NOT RUN: Kotlin snapshot adapter/envelope, Android AtomicFile/Room/ZIP/UI, Firebase security or transport.')
