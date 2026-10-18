#!/usr/bin/env python3
"""Production archive and lease tests. Android, Firebase and Compose are separate gates."""
from pathlib import Path
import subprocess, tempfile
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-family-cache-') as output:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    *[str(src/name) for name in ['FamilyRules.java','FamilyReadLease.java','FamilyCacheZip.java']],
                    str(root/'app/src/test/java/com/familywardrobe/app/FamilyCacheCases.java')],check=True)
    subprocess.run(['java','-cp',output,'com.familywardrobe.app.FamilyCacheCases'],check=True)
