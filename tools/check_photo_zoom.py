#!/usr/bin/env python3
"""Run production viewer geometry. This does not execute Compose gestures or Android UI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-zoom-') as output:
    files = [source / name for name in ['rules/PhotoGeometry.java', 'rules/PhotoViewportRules.java', 'rules/PhotoZoomRules.java']]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoZoomCases.java')
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    *map(str, files)], check=True)
    subprocess.run(['java', '-cp', output, 'com.familywardrobe.app.rules.PhotoZoomCases'], check=True)
