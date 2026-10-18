#!/usr/bin/env python3
"""Standalone production Kotlin coordinator tests. Normal Android route: ./gradlew testDebugUnitTest.
Requires WARDROBE_KOTLIN_LIB (compiler/dependency jars) and WARDROBE_JUNIT_LIB.
Does not compile the native UI, Room, AtomicFile or Firebase SDK adapter.
"""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
jars=list(Path(os.environ['WARDROBE_KOTLIN_LIB']).glob('*.jar'))+list(Path(os.environ['WARDROBE_JUNIT_LIB']).glob('*.jar'))
assert any('kotlin-compiler-embeddable' in p.name for p in jars)
assert any('junit' in p.name for p in jars)
src=root/'app/src/main/java/com/familywardrobe/app'
env=dict(os.environ,JAVA_TOOL_OPTIONS=(os.environ.get('JAVA_TOOL_OPTIONS','')+' -XX:-UsePerfData').strip())
with tempfile.TemporaryDirectory(prefix='wardrobe-transfer-') as output:
 cp=os.pathsep.join([output,*map(str,jars)])
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,str(src/'FamilyRules.java')],check=True,env=env)
 subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',cp,'-d',output,
                 str(src/'FamilyTransferModels.kt'),str(src/'FamilyTransfer.kt'),str(src/'FamilyTransferProgress.kt'),
                 str(root/'app/src/test/java/com/familywardrobe/app/FamilyTransferTest.kt'),
                 str(root/'app/src/test/java/com/familywardrobe/app/FamilyTransferProgressTest.kt')],check=True,env=env)
 subprocess.run(['java','-cp',cp,'org.junit.runner.JUnitCore','com.familywardrobe.app.FamilyTransferTest',
                 'com.familywardrobe.app.FamilyTransferProgressTest'],check=True,env=env)
print('Native UI/Room/AtomicFile/Firebase SDK compilation not covered by this runner.')
