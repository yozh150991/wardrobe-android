#!/usr/bin/env python3
"""QA regression suites (QA-REPORT-0.14.13.md) on the host, without the Android SDK.

1. Compiles every production Java rule and the technique-tagged Qa*Cases, then runs each suite
   (equivalence classes, boundary values, state transitions, pairwise, error guessing).
2. Client/server parity: the phone verdicts (TextLimits, AuthRules, CategoryRules) are compared with the
   family server rules (schema.visible_name, membership.email, custom-label validation) on a Unicode corpus.
3. Source guards for Kotlin screens that the JVM suites cannot execute.

Open bugs are strict expected failures: they keep this check green while they reproduce and fail it once
fixed, so the regression assertion is promoted with the fix. Kotlin JUnit (QaRegressionTest, QaFamilyWireTest)
runs in the Android CI job; backend unittest (tests/test_qa_techniques.py) runs in check_family_backend.py.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile
import unicodedata

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main/java/com/familywardrobe/app'
tests = root / 'app/src/test/java/com/familywardrobe/app'
SUITES = ['QaTextInputCases', 'QaShopImportCases', 'QaWardrobeDomainCases', 'QaStateTransitionCases',
          'QaPairwiseCases', 'QaFamilyClientCases']
failures = []
reproduced = set()


def known_bug(bug, reproduces, message):
    """Strict expected failure for source guards and parity checks."""
    if reproduces:
        reproduced.add(bug)
    else:
        failures.append(f'{bug} no longer reproduces ({message}); promote it to a regular check and update QA-REPORT-0.14.13.md')


env = dict(os.environ)
env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '') + ' -XX:-UsePerfData -Dstdout.encoding=UTF-8').strip()
java = ['java']

with tempfile.TemporaryDirectory(prefix='wardrobe-qa-') as output:
    sources = sorted(str(p) for p in main.glob('*.java')) + sorted(str(p) for p in tests.glob('Qa*.java'))
    subprocess.run(java + ['-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-nowarn', '-encoding', 'UTF-8',
                           '-d', output, *sources], check=True, env=env)
    for suite in SUITES:
        completed = subprocess.run(java + ['-cp', output, f'com.familywardrobe.app.{suite}'], env=env, text=True,
                                   capture_output=True, encoding='utf-8')
        print((completed.stdout + completed.stderr).strip())
        if completed.returncode != 0:
            failures.append(f'{suite} failed')
        reproduced.update(re.findall(r'QA-\d+', completed.stdout.split('open bugs still reproduced:')[-1])
                          if 'open bugs still reproduced:' in completed.stdout else [])

    # ---------- Client/server parity on a Unicode corpus ----------
    sys.path.insert(0, str(root / 'cloud/family-functions'))
    from family.errors import FamilyError  # noqa: E402
    from family.membership import email as server_email  # noqa: E402
    from family.schema import text_length, visible_name  # noqa: E402

    names = ['Shirt', 'Сукня', 'Ὀδυσσεύς', 'İstanbul', 'Straße', '\U0001F600', '\U0001F468‍\U0001F469‍\U0001F467',
             '\U0001F1FA\U0001F1E6', 'é', '́', '️', '⃝', '​', '‍', ' ', '　', ' ',
             '‎', '‏', '­', 'ㅤ', '⠀', 'ᅟ', '᠎', '\U000e0041', '', 'ＡＢ',
             'ﬁ', 'ﷺ', 'a‮b', '⁦x⁩', ' Ink  blue ', 'м’ята', 'a' * 60, 'a' * 61, 'ﷺ' * 4,
             'Tab\tname', 'Line\nname', '\x07bell']
    emails = ['a@b.co', 'A@B.CO', ' a@b.co ', 'ivan@пошта.укр', 'ivan@ПОШТА.укр', 'x@straße.de', 'x@münchen.de',
              'x@ドメイン.テスト', 'x@ex。com', 'x@ＥＸ.com', 'a@b.co ', 'a..b@b.co', 'a@-b.co', 'a@b-.co',
              'a@b', 'a@1.23', 'ä@b.co', 'a@b.co.', 'a@xn--80a1acn3a.xn--j1amh', 'a@xn--.xn--', 'a+b@b.co',
              'a' * 64 + '@b.co', 'a@' + 'b' * 63 + '.co', 'a@' + 'b' * 64 + '.co']
    corpus = names + emails
    lines = '\n'.join(' '.join(format(ord(c), 'x') for c in value) for value in corpus) + '\n'
    completed = subprocess.run(java + ['-cp', output, 'com.familywardrobe.app.QaParityDump'], input=lines.encode('utf-8'),
                               capture_output=True, env=env, check=True)
    rows = completed.stdout.decode('utf-8').splitlines()
    assert len(rows) == len(corpus), (len(rows), len(corpus))

    def decode(field):
        return ''.join(chr(int(h, 16)) for h in field.split(' ')[1:])

    def server_mail(value):
        try:
            return server_email(value)
        except FamilyError:
            return ''

    def server_label(name):
        """customCategories/customColors row as the family server validates it (schema.row_shape)."""
        clean = ' '.join(unicodedata.normalize('NFKC', name).split())
        return (0 < text_length(name.strip()) <= 60 and visible_name(name) and name == clean
                and not any(unicodedata.category(c) in ('Cc', 'Cf') for c in name))

    visible_mismatch, email_mismatch, label_mismatch = [], [], []
    for value, row in zip(corpus, rows):
        phone_visible, phone_email, phone_clean, phone_label = row.split('\t')
        if (phone_visible == 'true') != visible_name(value):
            visible_mismatch.append(value)
        if decode(phone_email) != server_mail(value):
            email_mismatch.append((value, decode(phone_email), server_mail(value)))
        cleaned = decode(phone_clean)
        if cleaned and (phone_label == 'true') != server_label(cleaned):
            label_mismatch.append(value)
    checks = len(corpus) * 3
    if visible_mismatch:
        failures.append(f'TextLimits.hasVisibleText differs from schema.visible_name for {[ascii(v) for v in visible_mismatch]}')
    if email_mismatch:
        failures.append(f'AuthRules.serverEmail differs from membership.email: {[ascii(v) for v in email_mismatch]}')
    # QA-02: invisible-only custom labels pass on the phone and are refused by the server.
    expected_label_gap = {'́', '️', '⃝'}
    unexpected = [v for v in label_mismatch if v not in expected_label_gap]
    if unexpected:
        failures.append(f'new custom-label parity gap: {[ascii(v) for v in unexpected]}')
    known_bug('QA-02', expected_label_gap <= set(label_mismatch), 'custom label parity')
    print(f'PASS: client/server parity on {len(corpus)} Unicode samples ({checks} comparisons); '
          f'known label gap: {sorted(ascii(v) for v in label_mismatch)}')

# ---------- Source guards for Compose screens (error guessing) ----------
def source(name):
    return (main / name).read_text(encoding='utf-8')

# QA-03: names saved locally must satisfy the same visibility rule as the family server, otherwise one
# invisible name (ZWSP, combining mark) aborts the whole shared-wardrobe transfer.
def body(name, signature):
    """Text of one Kotlin member function: from its signature to the next member declaration."""
    text = source(name); start = text.index(signature)
    ends = [i for i in (text.find('\n    suspend fun ', start + 1), text.find('\n    fun ', start + 1)) if i > 0]
    return text[start:min(ends) if ends else len(text)]

# A form is fixed when its screen or the repository write behind it applies the visibility rule.
name_forms = {
    'wish name': [source('WishlistScreen.kt')],
    'look name': [source('OutfitEditor.kt') + source('DailyScreen.kt'), body('Data.kt', 'suspend fun saveLook(')],
    'capsule/trip name': [source('CapsuleScreen.kt'), source('CapsuleOperations.kt')],
    'wardrobe name': [source('WardrobeManagement.kt'), body('Data.kt', 'suspend fun renameWardrobe(')],
}
missing = [label for label, places in name_forms.items() if not any('hasVisibleText' in place for place in places)]
known_bug('QA-03', bool(missing), 'name forms validate visible text')
# Regular guard: the forms already fixed in 0.14.13 keep the visibility rule.
for file in ['FamilyAddition.kt', 'FamilyEditDialog.kt', 'FamilyAccessControl.kt']:
    if 'hasVisibleText' not in source(file):
        failures.append(f'{file} lost TextLimits.hasVisibleText (BUG-12 regression)')
if 'hasVisibleText(garment.name)' not in source('Data.kt'):
    failures.append('Data.kt garment save lost the visible-name rule (BUG-12 regression)')
# QA-09: prefilled custom label names are cut with String.take (half emoji, bidi kept) instead of TextLimits.input.
raw_take = [f for f in ['CategoryControls.kt', 'ColorControls.kt'] if re.search(r'\bquery\.take\(', source(f))]
known_bug('QA-09', bool(raw_take), 'custom label prefill uses TextLimits.input')
# Regular guard: no text field change handler goes back to it.take(N) (BUG-05 regression).
for path in sorted(main.glob('*.kt')):
    if re.search(r'\{\s*\w+\s*=\s*it\.take\(\d+\)', path.read_text(encoding='utf-8')):
        failures.append(f'{path.name}: text field uses it.take(N) instead of TextLimits.input (BUG-05 regression)')
# QA-06 (fallback path): shared text used as a product name is cut with take() but bidi controls are kept.
known_bug('QA-06', 'TextLimits.take(sharedText, 120)' in source('ShopImportScreen.kt'), 'shared-text fallback name now strips bidi controls')

print(f'PASS: source guards; open bugs still reproduced: {", ".join(sorted(reproduced, key=lambda b: (len(b), b)))}')
if failures:
    for failure in failures:
        print('FAIL:', failure)
    raise SystemExit(1)
print('NOT RUN here: Kotlin QA JUnit (Android CI job), backend tests/test_qa_techniques.py (check_family_backend.py), device UI.')
