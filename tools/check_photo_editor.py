#!/usr/bin/env python3
"""Execute production photo edit history, brush sizes and painting; not Compose UI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-editor-') as output:
    files = [source / name for name in ['rules/PhotoEditHistory.java', 'rules/PhotoBrushRules.java', 'rules/PhotoMaskRules.java', 'rules/PhotoGeometry.java', 'rules/PhotoSessionRules.java', 'rules/PhotoLoupeRules.java', 'rules/PhotoRegionRules.java', 'rules/PhotoInspectionRules.java', 'rules/PhotoPrecisionRules.java']]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoEditorCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoSessionCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoRegionCases.java')
    files.append(root / 'app/src/test/java/com/familywardrobe/app/rules/PhotoPrecisionCases.java')
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    *map(str, files)], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.rules.PhotoEditorCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.rules.PhotoSessionCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.rules.PhotoRegionCases'], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.rules.PhotoPrecisionCases'], check=True)
