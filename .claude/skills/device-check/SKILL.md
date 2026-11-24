---
name: device-check
description: Verify a change on the owner's real phone - what to ask him to build and install, how to collect logcat or photo captures, and how to drive the phone through scrcpy when it is connected. Use when a fix needs confirming on the device or a bug is reported from the phone.
---

# Checking on the phone

## What the owner runs (Windows PowerShell, in the repository folder)

```powershell
git fetch origin
git checkout <branch>
git pull
.\build-apk.ps1 assembleDebug
$adb = "d:\data\downloads\scrcpy-win64-v5.0.1\scrcpy-win64-v5.0.1\adb.exe"
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

- `-r` installs over the existing app and keeps its data. The profile screen shows the version at the bottom.
- Never send him the CI preview APK: it is a separate `.ci` package without Firebase config, sign-in fails.
- Give exact steps to reproduce, in Ukrainian, and say what he should see.

## Evidence

- **Logs:** `& $adb logcat -d -s WardrobePhoto > "D:\Data\Downloads\My projects\Wardrobe\logs\<name>.txt"` for photo
  processing; for anything else `& $adb logcat -d --pid=(& $adb shell pidof -s com.familywardrobe.app) > …`.
  A failure the app swallows silently needs a `Log.w` with its stage first — add it, then ask for the log.
- **Photo captures** (debug builds): `& $adb pull /sdcard/Android/data/com.familywardrobe.app/files/photo-debug
  "D:\Data\Downloads\My projects\Wardrobe\logs\photo-debug"`. Only the newest 8 are kept.
- He attaches the file or connects the `logs` folder; ask for access to that folder only. His photos stay out of the
  repository and out of documents.

## Driving the phone yourself (only when he has connected it)

- The phone is mirrored by scrcpy on his Windows PC; use computer use on the scrcpy window. Ask for access to scrcpy
  only. Take a screenshot before every tap sequence; coordinates differ between sessions.
- Cyrillic and symbols cannot be typed into the phone: type ASCII letters key by key or tap the on-screen keyboard.
- Work only in a test wardrobe that you create and delete afterwards. The owner's real wardrobes are view-only.
  Never enter a password or PIN, never open his gallery beyond the photos he named, never tap delete on his data.
- Android may stop the app while the camera or another app is in front: test that path on purpose for any form.
- Release the computer-use lock at the end of the turn.

## Reporting

State separately what was confirmed on the phone and what only by host checks or CI. A finding gets an id in the
release document (as QA-61-xx in docs/releases/QA-EXPLORATORY-0.14.61.md) with steps, expected and actual.
