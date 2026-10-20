#!/usr/bin/env python3
"""Compile real Java production rules. Check new Kotlin integration as source only."""
from pathlib import Path
import re, shutil, subprocess, tempfile, xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-refinement-') as output:
    names=['WardrobeRules','DailyRules','RecommendationRules','DailyDiagnostics','PhotoGeometry','PhotoViewportRules','PhotoMaskRules','VerifiedArchiveInput','LookNameRules','ImportReviewRules','TextLimits']
    sources=[source/(name+'.java') for name in names]+[root/'app/src/test/java/com/familywardrobe/app/RefinementRulesCases.java']
    subprocess.run([shutil.which('java'),'-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,sources)],check=True)
    subprocess.run([shutil.which('java'),'-cp',output,'com.familywardrobe.app.RefinementRulesCases'],check=True)
ui=(source/'Ui.kt').read_text();vm=(source/'WardrobeViewModel.kt').read_text();backup=(source/'Backup.kt').read_text()
assert 'screenState.SaveableStateProvider' in ui and 'returnStack.last()' in ui
assert 'fun back() { navigate("__back") }' in ui
assert 'OperationKind.EXPORT, cancellable = true' in vm and 'DocumentFile.fromSingleUri' in vm
assert 'failure?.let' in ui and 'error_message' not in ui
assert 'expectedFingerprint != null && fingerprint != expectedFingerprint' in backup
guard=backup.index('if (expectedFingerprint != null')
assert guard<backup.index('copied.add(target)')<backup.index('repo.db.withTransaction')
daily=(source/'DailyScreen.kt').read_text();look=(source/'OutfitEditor.kt').read_text()
assert 'generatedFor by rememberSaveable' in daily and 'lookIds by rememberSaveable' in daily
assert 'DailyDiagnostics.explain' in daily and 'ModalBottomSheet' in daily
assert 'editable = composing' in look and 'onGestureStart = { checkpoint()' in look
assert 'Modifier.weight(1f).verticalScroll' in look
assert (source/'PhotoEditor.kt').read_text().count('Dialog(onDismissRequest')==1
assert 'Dialog(onDismissRequest' not in (source/'PhotoMaskEditor.kt').read_text()
selection=(source/'SelectionControls.kt').read_text();assert 'onCheckedChange = null' in selection and 'role = Role.Checkbox' in selection
locale_sets=[]
for locale in ['values','values-uk','values-en','values-pl']:
    values={s.attrib['name']:s.text or '' for s in E.parse(root/f'app/src/main/res/{locale}/strings.xml').getroot()}
    locale_sets.append(set(values));assert all(v.strip() for v in values.values())
    assert 'three' not in values['start_three'] and 'трьох' not in values['start_three'] and 'trzech' not in values['start_three']
assert all(s==locale_sets[0] for s in locale_sets)
print('PASS: source integration guards for contextual navigation/errors, cancellable export, fingerprint before restore writes, saved daily state, explicit collage mode, single photo dialog and full-row selection; UA/EN/PL keys match.')
print('NOT RUN: Kotlin compilation, native navigation/state restoration, touch, WorkManager/SAF, TalkBack, APK or device UX.')
