#!/usr/bin/env python3
"""Execute production browsing/onboarding/matching rules with JDK 17; no Android runtime implied."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = shutil.which('java')
if not java:
    raise SystemExit('JDK 17 is required.')
sources = [root / 'app/src/main/java/com/familywardrobe/app/WardrobeRules.java',
           root / 'app/src/test/java/com/familywardrobe/app/WardrobeRulesCases.java']
with tempfile.TemporaryDirectory(prefix='wardrobe-rules-') as output:
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    *map(str, sources)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.WardrobeRulesCases'], check=True)
