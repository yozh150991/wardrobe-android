#!/usr/bin/env python3
"""DEV-04 guard: the screenshot tour keeps reaching every screen, CI keeps collecting the pictures, and the test-only
activity never becomes reachable from outside the app."""
import base64
import importlib.util
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


tour = (root / 'app/src/androidTest/java/com/familywardrobe/app/ScreenshotTourTest.kt').read_text(encoding='utf-8')
ui = (source / 'ui/Ui.kt').read_text(encoding='utf-8')
catalog = (source / 'ui/CatalogScreen.kt').read_text(encoding='utf-8')
top_bar = (source / 'ui/WardrobeTopBar.kt').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
debug_manifest = (root / 'app/src/debug/AndroidManifest.xml').read_text(encoding='utf-8')
main_manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
workflow = (root / '.github/workflows/ci.yml').read_text(encoding='utf-8')

# The tour: four configurations, every main screen, the real app on a seeded account.
for config, language, scale in (('uk', 'uk', '1.0f'), ('uk-large', 'uk', '1.3f'), ('en', 'en', '1.0f'), ('pl', 'pl', '1.0f')):
    check(f'tour("{config}", "{language}", {scale})' in tour, f'the tour runs the {config} configuration')
screens = ['1-today', '2-wardrobe', '3-want-to-buy', '4-outfits', '5-profile', '6-item', '7-new-item']
positions = [tour.find(f'shot(config, "{name}")') for name in screens]
check(all(p > 0 for p in positions) and positions == sorted(positions), 'the tour captures the seven screens in order')
check('WardrobeApp(vm, appearance, identity.email, {}, {})' in tour and 'WardrobeViewModel(app, session)' in tour,
      'the tour drives the production WardrobeApp and view model')
check('session.close(signedOut = false)' in tour, 'the tour closes its account without signing anyone out')
check('shell("wm size 1080x2340")' in tour and 'shell("wm size reset")' in tour and '@AfterClass' in tour,
      'the phone-sized screen is set for the tour only and restored afterwards')
check('Log.i(TAG, "WSHOT|$name|${index + 1}|${parts.size}|$part")' in tour and 'const val TAG = "WardrobeShots"' in tour,
      'pictures go to logcat as WSHOT lines under the WardrobeShots tag')
check(re.search(r'CHUNK = (\d+)', tour) and int(re.search(r'CHUNK = (\d+)', tour).group(1)) <= 3500,
      'a logcat line stays under the 4 000-byte limit')

# Every tag the tour taps exists in the app.
for tag, text in (('nav-catalog', 'Modifier.testTag("nav-$screen")'), ('garment-card', 'testTag("garment-card")'),
                  ('catalog-add', 'testTag("catalog-add")'), ('add-method-manual', 'testTag("add-method-$method")')):
    check(f'"{tag}"' in tour, f'the tour taps {tag}')
check('Modifier.testTag("nav-$screen")' in ui, 'tabs carry nav-<screen> tags')
check('testTag("garment-card")' in catalog and 'testTag("catalog-add")' in catalog, 'catalog cards and «Додати річ» carry tags')
check('testTag("add-method-$method")' in top_bar and '"manual" to R.string.shop_manual' in top_bar,
      'add-item methods carry tags, manual among them')

# The test-only activity: debug builds only, never exported, never in the main manifest.
check('debugImplementation("androidx.compose.ui:ui-test-manifest")' in gradle
      and 'implementation("androidx.compose.ui:ui-test-manifest")' not in gradle.replace('debugImplementation("androidx.compose.ui:ui-test-manifest")', ''),
      'ui-test-manifest is a debug-only dependency')
check('androidTestImplementation("androidx.compose.ui:ui-test-junit4")' in gradle, 'Compose UI tests are an androidTest dependency')
check('android:name="androidx.activity.ComponentActivity" android:exported="false" tools:replace="android:exported"' in debug_manifest,
      'the empty ComponentActivity is not exported in debug builds')
check('androidx.activity.ComponentActivity' not in main_manifest, 'release builds do not declare the test activity')

# CI collects the pictures whether the tests pass or not.
check("adb logcat -G 32M" in workflow and 'status=$?' in workflow and 'adb logcat -d -v raw -s WardrobeShots:I > screenshots.log' in workflow
      and 'exit $status' in workflow, 'the emulator step dumps the WardrobeShots log and keeps the test result')
step = workflow[workflow.find('- name: Screenshots for review (DEV-04)'):]
check(step.startswith('- name: Screenshots for review (DEV-04)\n        if: always()')
      and 'python3 tools/ci_screenshots.py screenshots.log app/build/outputs/androidTest-results screenshots' in step,
      'CI always turns the log into pictures')
check('name: screenshots-${{ github.sha }}' in workflow, 'the pictures are uploaded as an artifact')

# The decoder: complete screens only, later runs win, nothing outside the expected shape.
spec = importlib.util.spec_from_file_location('ci_screenshots', root / 'tools/ci_screenshots.py')
decoder = importlib.util.module_from_spec(spec); spec.loader.exec_module(decoder)
picture = base64.b64encode(b'\xff\xd8' + bytes(range(256)) * 30 + b'\xff\xd9').decode()
pieces = [picture[i:i + 3000] for i in range(0, len(picture), 3000)]
lines = [f'11-23 01:00:00.000 1 2 I WardrobeShots: WSHOT|uk-1-today|{i + 1}|{len(pieces)}|{p}' for i, p in enumerate(pieces)]
lines += ['WSHOT|uk-2-wardrobe|1|2|' + pieces[0], 'WSHOT|Bad Name|1|1|AAAA', 'WSHOT|uk-3-x|2|1|AAAA', 'noise']
shots, expected = decoder.collect(lines)
check(sorted(shots) == ['uk-1-today', 'uk-2-wardrobe'], 'only well-formed screens are collected')
check(''.join(shots['uk-1-today'][i] for i in sorted(shots['uk-1-today'])) == picture, 'a screen is reassembled exactly')
check(sorted(shots['uk-2-wardrobe']) != list(range(1, expected['uk-2-wardrobe'] + 1)), 'a screen with missing parts is incomplete')

if failures:
    for message in failures:
        print(f'FAIL: {message}')
    sys.exit(1)
print('Screenshot tour: four configurations, seven screens, tags, debug-only activity, CI collection, decoder — pass')
