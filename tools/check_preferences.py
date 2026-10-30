#!/usr/bin/env python3
"""PERS-01: run the production preference rules and the daily generator with their cases (JDK 17)."""
from pathlib import Path
import shutil, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
# The cases list every context; they must be the app's own (LookRules.kt).
look = (source / 'LookRules.kt').read_text(encoding='utf-8')
cases = (root / 'app/src/test/java/com/familywardrobe/app/PreferenceRulesCases.java').read_text(encoding='utf-8')
for name in ('OCCASIONS', 'WEATHERS'):
    app_list = look.split(f'val {name} = listOf(', 1)[1].split(')', 1)[0]
    case_list = cases.split(f'List<String> {name} = List.of(', 1)[1].split(')', 1)[0]
    if app_list != case_list: raise SystemExit(f'FAIL: PreferenceRulesCases.{name} differs from LookRules.{name}')
with tempfile.TemporaryDirectory(prefix='wardrobe-preferences-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'DailyRules.java', 'PreferenceRules.java')]
    files += [root / 'app/src/test/java/com/familywardrobe/app/PreferenceRulesCases.java']
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.PreferenceRulesCases'], check=True)
