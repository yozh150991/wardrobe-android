#!/usr/bin/env python3
"""Execute production photo edit history, brush sizes and painting; not Compose UI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-editor-') as output:
    files = [source / name for name in ['PhotoEditHistory.java', 'PhotoBrushRules.java', 'PhotoMaskRules.java', 'PhotoGeometry.java', 'PhotoSessionRules.java', 'PhotoLoupeRules.java', 'PhotoRegionRules.java', 'PhotoInspectionRules.java', 'PhotoPrecisionRules.java']]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/PhotoEditorCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/PhotoSessionCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/PhotoRegionCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/PhotoPrecisionCases.java')
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    *map(str, files)], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoEditorCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoSessionCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoRegionCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoPrecisionCases'], check=True)
