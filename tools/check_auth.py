#!/usr/bin/env python3
"""Runs production auth primitives with JDK 17. Does not simulate Firebase or claim Android E2E."""
from pathlib import Path
import shutil
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
java=shutil.which('java')
if not java: raise SystemExit('JDK 17 required.')
sources=[root/'app/src/main/java/com/familywardrobe/app'/name for name in ('AuthRules.java','AuthAttemptGate.java')]
sources.append(root/'app/src/test/java/com/familywardrobe/app/AuthRulesCases.java')
with tempfile.TemporaryDirectory(prefix='wardrobe-auth-tests-') as output:
    subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,sources)],check=True)
    subprocess.run([java,'-cp',output,'com.familywardrobe.app.AuthRulesCases'],check=True)
