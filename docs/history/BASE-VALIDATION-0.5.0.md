# Validation — Wardrobe Android 0.5.0

Executed on 1 October 2026 against this package:

- `check_project.py`: 1168 structural, XML, resource and SQL checks; 9 XML files, 276 localized keys, 24 DAO queries.
- `check_wardrobe.py`: 2814 assertions running production Java browsing, onboarding and outfit-selection rules. Covers legacy outfits, laundry/archive exclusion, new categories, optional anchors, seasonal compatibility, wear-day deduplication and 160 generated wardrobe fixtures.
- `check_auth.py`: 38 production assertions for existing authentication validation, identity and cancellation.
- `check_management.py`: existing actual DAO SQL checks for account-local wardrobe isolation, deletion, rollback and migration.
- `check_photo_rules.py`: 12 existing production photo-rule scenarios and 7 crop/rotation scenarios.
- `check_theme.py`: 120 existing production colour pairings; minimum 4.66:1. No claim is made for every photograph or newly rendered control.
- `check_auth_config.py`: client configuration shape and application ID.

The 0.4.1 Firebase configuration, Gradle/Kotlin/Firebase versions, Android manifest, account-storage code, Room entities and exported schema v2 are preserved. No phone database, picture or existing account was edited by this work.

Not executed for 0.5.0: Kotlin/Compose compilation, Gradle lint/JUnit, APK generation, Android instrumentation tests, real UI layout, TalkBack, Windows PowerShell or Firebase/ML flows. This environment has JDK 17 but lacks Android SDK, Gradle and PowerShell. Source/XML checks and production domain tests are not a substitute for those checks.

Run `build-apk.ps1 testDebugUnitTest lintDebug assembleDebug` in the updated original project, then the device checks in `DESIGN-UPDATE-0.5.0.md`. The former full-build result is archived as `BASE-VALIDATION-0.4.1.md` and is not evidence that this new version has compiled.
