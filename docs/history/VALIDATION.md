# Wardrobe Android 0.11.0-scenarios

## Поточний реліз 0.11.1 — 3 жовтня 2026

Debug/Release реально зібрані; 43 JVM, 71 Android, 6 Firebase emulator, 13 host наборів пройшли. UI-навігацію потрібно прийняти окремо: System UI емулятора повторно зависає. Поточний обсяг, межі ручного/production приймання та команди — `RELEASE-VALIDATION-0.11.1.md`, `SCENARIO-COVERAGE-0.11.1.md`, `CLOUD-SETUP-UK.md` і `../README-UK.md`. Збережено Room/backup 8 і ваш клієнтський Firebase-конфіг; version code 16. Нижче — історія попередніх версій.

Version code 15; Room 8; portable backup format 8. The complete seven-block coverage and device acceptance are in SCENARIO-COVERAGE-0.11.0.md. Cloud setup and rules are in CLOUD-SETUP-UK.md and cloud/.

New behavior includes durable purchase undo, candidate-to-need reversal with retained history and photos, varied inspiration, per-piece availability explanations and Firebase snapshot sync with a persistent offline queue and explicit conflicts. Existing authentication, photo processing, calendar, wear statistics and packing flows are retained.

All 13 host checks passed; results are in HOST-VALIDATION-0.11.0.json. Host tests execute production Java and SQLite, not Kotlin/Android. APK build stopped at the full-JDK check. Kotlin/Compose/KSP, native tests, Firebase/emulator tests and two-device acceptance remain pending. No compiled APK is supplied. Historical sections below apply to their named releases and do not override this section.

# Validation — Wardrobe Android 0.10.0-ux

Executed on 2 October 2026 through `python3 tools/run_host_checks.py`: all 12 scripts passed. Full outputs are in HOST-VALIDATION-0.10.0.json; historical reports retain their original bytes.

- Source/SQL: 3000 checks, 9 XML files, 718 matching nonempty UA/EN/PL/fallback keys and 58 actual DAO queries accepted by SQLite. Source integration and delimiters do not establish Kotlin type correctness.
- UX refinement: 6817 assertions compile and execute actual DailyDiagnostics, PhotoViewportRules, PhotoMaskRules, VerifiedArchiveInput, LookNameRules and ImportReviewRules with their production dependencies. Coverage includes single and combined empty-result constraints, strict capsule/repeat scope, 300 varied zoom/crop/inverse-coordinate cases, all 4096 pixels' RGB preservation, cancellation checkpoints, ZIP read-ahead/tail/skip/digest/limits and same-sized changes, bounded unique Unicode names and five sequential reviews past failed/working entries. This is not a Compose simulation.
- Retained production checks: 132 flexible-outfit/recency/mask/canvas assertions; 46 daily/date/packing/cost assertions; 76 photo-ownership/presentation assertions; 2147 wardrobe, 2035 money/link, 23 selection, 38 auth and 12 photo-rule/7 crop-rotation checks; actual DAO isolation/rollback.
- Real SQLite migration 2→7 preservation, including seeded clothing, wear, saved outfits, pending imports, capsule packing and default repeat/exclusion/layout fields, passes. Earlier purchase/migration/reference checks include 19 real statements. This does not execute Kotlin repositories or generated Room validation.
- Theme: 440 text-color pairings across 11 palettes × 2 modes, minimum 4.52:1; 22 outline checks ≥3:1. Supplied Firebase config shape/package check passes without printing its contents; bytes remain protected. Native accessibility and Firebase behavior remain unverified.

Prepared, NOT EXECUTED: eight ExperiencePersistenceTest instrumentation scenarios, including changed confirmed ZIP rejection before durable writes, zoomed native alpha masking and five imported saves with original cleanup; RefinementRulesTest JUnit wrapper; all previous native/JUnit scenarios. Native UI, saved-instance restoration, gestures, WorkManager/SAF, Android bitmap results, ML/weather/Firebase and TalkBack are pending.

Actual native attempt: `bash build-apk.sh testDebugUnitTest lintDebug assembleDebug` exits 1 at the missing full-JDK check: “Install JDK 17 (or 21) and set JAVA_HOME. A Java runtime alone is insufficient.” Java 17's compiler module runs the standalone checks; no javac executable, Gradle/Android SDK/emulator or PowerShell toolchain is available. No Kotlin/Compose compilation, KSP schema export, lint, APK or device test pass is claimed.

Archive package validation: 70 host-model scenarios across seven source baselines, covering clean/repeat/partial application, CRLF/BOM, independent edits, protected Firebase/SDK/signing files and conflict preflight. PowerShell and Windows rollback remain inspection-only. See archive-level package-validation.json and UX-REFINEMENT-0.10.0.md for device acceptance.

Earlier results below apply only to their named releases.

# Prior validation — Wardrobe Android 0.9.0

Executed on 2 October 2026 through `python3 tools/run_host_checks.py`; all 11 scripts passed. Complete final outputs are in HOST-VALIDATION-0.9.0.json.

- Source/SQL: 2,586 checks, 9 XML files, 617 localized keys in Ukrainian, English, Polish and Ukrainian fallback, 58 production DAO queries accepted by SQLite. Source delimiters are checked; Kotlin type correctness is not established.
- Experience: 132 assertions compile and execute production WardrobeRules, DailyRules, LookSelection, RecommendationRules, PhotoMaskRules and CanvasRules. They cover one-piece outfit branches, multi-layer locks, completeness/limits, repeat-day/leap-year boundaries, exclusions, alpha-only erase/restore and immutable originals, and normalized canvas bounds for all categories.
- Actual production SQL 2→3→4→5→6→7 runs on the original exported schema 2. Existing garment, outfit/part, wear, import and packed-capsule rows are seeded before 6→7 and preserved unchanged; new defaults and per-wardrobe repeat updates are checked. Foreign keys pass. This is SQL execution, not Room-generated validation or Kotlin repository execution.
- Roadmap: 46 production daily/date/packing/cost-per-wear assertions and real 4→5→6 migration/price/reference checks.
- UX/photo ownership: 76 production Java assertions and real 3→4 migration preservation.
- Wardrobe: 2,147 production assertions, including legacy category IDs and legacy five-piece outfits. Counts differ from old random scenarios because the category set now includes one-piece branches.
- Money/links: 2,035 production assertions; collage selection: 23; wishlist migration/reference/rollback: 19 checks.
- Authentication: 38 production identity/validation/cancellation assertions. Management: actual DAO isolation, deletion, rollback and migration checks.
- Photos: 12 production classification/rule and 7 crop/rotation scenarios; no actual native ML or bitmap API is exercised here.
- Theme: 440 text-color pairings across 11 palettes × 2 modes, minimum 4.52:1, and 22 outline checks ≥3:1. Native accessibility remains unverified.
- Auth configuration: supplied client configuration shape and package name pass; values are not printed. Existing configuration, signing settings and old exported schema are retained.

Prepared, NOT EXECUTED: ExperiencePersistenceTest (5 real Android Room/photo/backup/capsule/import scenarios), ExperienceRulesTest JUnit wrapper, updated schema-chain tests, format-7 backup tests and all previous instrumentation/JUnit scenarios. Test source presence is not a test pass. The real schema 7 must be generated by KSP and validated on Android.

The actual `bash build-apk.sh testDebugUnitTest lintDebug assembleDebug` attempt stopped at its full-JDK check: no javac executable/JAVA_HOME toolchain is available. Java 17's jdk.compiler module compiles/runs standalone production Java rules. Kotlin compiler, Gradle, Android SDK, emulator and PowerShell are absent. No 0.9.0 APK was built. No native screen, gesture, TalkBack, WorkManager/provider, model, live weather/Firebase or repository/backup-controller result is claimed.

Package validation uses a host model of the actual updater matching/preflight rules against 0.8.0, 0.7.0, 0.6.1, 0.6.0, 0.5.0 and 0.4.1. It checks clean/repeat/partial application, CRLF/BOM, preserved independent edits, protected Firebase/SDK/signing files and conflict preflight. Details are in archive-level package-validation.json. PowerShell/Windows IO and rollback are inspected, not executed. This source update does not operate on installed phone data. Required native acceptance is listed in EXPERIENCE-UPDATE-0.9.0.md.

Earlier results below apply only to their named releases.

# Prior validation — Wardrobe Android 0.8.0

Executed on 2 October 2026 against the final source through `python3 tools/run_host_checks.py`. Complete outputs are in HOST-VALIDATION-0.8.0.json.

- Source/SQL: 2233 checks; 9 XML files; 532 keys in Ukrainian, English, Polish and Ukrainian fallback; 57 production DAO queries accepted by SQLite. Kotlin/Java delimiters pass. These checks are not Kotlin compilation or Room code generation.
- Roadmap: 46 assertions executing production DailyRules.java and RoadmapRules.java: availability and account/wardrobe boundaries, seasons, determinism, fixed-piece replacement, rejected signatures, missing outerwear, anchored purchase previews including accessories, date/period validity, cost rounding/unknown/zero states, packing deduplication and invalid temperature/price rejection.
- Actual SQL 4→5→6 migrations executed after 2→3→4 on the original exported Room schema 2. Existing garment fields, wear and outfit parts survive; historical purchase price and currency transfer. Unique calendar and packing keys, separate calendar/item wear sources, deduplication, RESTRICT and capsule cascades pass. SQL execution is not execution of the Kotlin repositories.
- UX: 76 production Java photo-result ownership and presentation assertions, plus actual 3→4 wish preservation and source delimiter checks.
- Wardrobe: 2814 production Java domain assertions.
- Money/links: 2035 production assertions; collage selection: 23 production assertions; existing wishlist migration/reference/rollback: 19 checks.
- Authentication: 38 production Java identity/validation/cancellation assertions. Live Firebase session behavior remains unverified.
- Management: actual DAO SQL wardrobe isolation, cascade, rollback and migration checks. New capsule-before-garment deletion ordering is in source and requires the included Android tests.
- Photos: 12 production photo-rule and 7 crop/rotation scenarios. No actual native model or photo processing exercised.
- Theme: 440 text-color pairings across 11 palettes × 2 modes, minimum 4.52:1; all 22 outline checks ≥3:1. Theme constants are retained, and native accessibility is not established.
- Auth configuration: existing client shape and package name pass; values are not printed. Firebase configuration, dependency versions, schema-2 fixtures and build/signing settings are retained.

Included, NOT EXECUTED: ImportFlowTest (4 native queue/bulk scenarios), RoadmapPersistenceTest (6 repository scenarios covering plan/actual wear separation, independent undo, laundry, reconciliation, packing union and full format-6 remapping), RoadmapRulesTest, purchase/import draft reopening regression, updated schema-chain and photo-backup tests, and all earlier Android/JUnit tests.

The direct script invocation first encountered a file permission error; `bash build-apk.sh testDebugUnitTest lintDebug assembleDebug` reached its toolchain check and stopped for missing javac/full JDK. Java 17 with the compiler module runs the standalone host rules. Kotlin compiler, Gradle, Android SDK, emulator and PowerShell are unavailable. A parallel host JVM process encountered a shared performance-file SIGBUS; the final complete sequential run disables JVM performance-file diagnostics and passes all ten scripts. No 0.8.0 APK was built.

NOT EXECUTED: Kotlin/Compose compilation, KSP schema-6 generation and Room validation on Android, Gradle lint/JUnit, APK, instrumentation, native batch processing and process-death recovery, live city/weather HTTPS, Firebase/ML behavior, Android visual layout at 100–200% text, keyboard/focus/TalkBack, or Windows updater execution. BackupStateRules, coroutine controllers and Kotlin transaction code are not exercised by the host Java/SQL assertions. Run the native build and acceptance scenarios in ROADMAP-UPDATE-0.8.0.md before a release.

The package updater covers source versions 0.7.0, 0.6.1, 0.6.0, 0.5.0 and 0.4.1. Archive-level package-validation.json records source matching, repeated/partial application, CRLF/BOM, independent edit preservation and conflict preflight using a host model. PowerShell and Windows IO rollback are inspected, not executed. Existing Firebase, local SDK and signing files are excluded from update plans. The source update does not operate on an installed phone database or its photos.

Earlier validation below applies only to its named release.

# Prior validation — Wardrobe Android 0.7.0

Executed on 2 October 2026 against this source update:

- `check_project.py`: 1708 source/XML/resource/SQL checks; 9 XML files; 406 localized keys; 33 actual DAO queries.
- `check_ux.py`: 76 assertions compiling and executing production PhotoWorkGate.java and PhotoPresentationGeometry.java. Covers late responses after manual changes/save/editor switches, independent owners, transparent bounds, preserved soft edges and proportional centering.
- `check_ux.py`: actual SQL migration 3→4 executed after 2→3 on the original exported Room schema 2; every existing wish field survives and ownedId defaults to empty. Kotlin delimiter scan passed; this does not establish type correctness or compilation.
- `check_wardrobe.py`: 2814 production Java browsing, onboarding and matching assertions.
- `check_auth.py`: 38 production Java validation, identity and cancellation assertions. The new AuthViewModel SDK-restoration flow is not executed by these host rules.
- `check_wishes.py`: 2035 production money/URL assertions, 23 production LookSelection assertions and 19 migration checks on the exported schema.
- `check_management.py`: actual DAO SQL isolation, cascade deletion, rollback and migration 1→2 checks. Its migration parser was restricted to that migration after adding 3→4.
- `check_photo_rules.py`: 12 existing production photo rules and 7 crop/rotation scenarios.
- `check_theme.py`: 440 source-constant text colour pairings across 11 palettes × 2 modes; minimum 4.52:1. All 22 outline/surface pairs ≥3:1. Theme constants are unchanged from 0.6.1; this is not proof of complete UI accessibility.
- `check_auth_config.py`: client configuration shape and application ID; client values are not printed. Firebase configuration and dependency files remain unchanged.

Included but NOT EXECUTED: PurchaseFlowTest (6 instrumentation scenarios: purchase/idempotence/linked outfits/undo, stale edits, wear, category rollback, backup ownership remapping and outfits created after purchase); UXRulesTest; updated photo persistence and backup/migration instrumentation tests. Native coroutine/Room transaction execution, bitmap rendering and SDK callbacks are not established by the host assertions.

The attempted `build-apk.sh testDebugUnitTest lintDebug assembleDebug` stopped because the full JDK executable/toolchain is unavailable. Java 17 and its compiler module suffice for standalone host Java assertions; there is no Kotlin compiler, Gradle, Android SDK, emulator or PowerShell here. No 0.7.0 APK was built.

NOT EXECUTED: Kotlin/Compose compilation, KSP/Room schema-4 generation and Android validation, Gradle lint/JUnit, APK, instrumentation, camera/ML on real photos, Firebase cold-start/expiry/reauthentication, Android visual layout, 100–200% text, TalkBack/focus and Windows PowerShell. Run the Android build and acceptance scenarios in UX-UPDATE-0.7.0.md before releasing this version.

Updater manifests cover 0.6.1, 0.6.0, 0.5.0 and 0.4.1. The release-level host-model results are recorded in package-validation.json in the archive; that model is not execution of PowerShell. The original Firebase config is retained byte-for-byte, with local build/signing files outside the update plan. Source changes do not operate on the user's installed app or photographs.

Earlier validation below applies only to its named version.

# Prior validation — Wardrobe Android 0.6.1

Executed for the colour update on 2 October 2026 (Europe/Warsaw):

- check_theme.py: 440 production text colour pairings across 11 palettes × 2 modes; minimum 4.52:1. All 22 outline/surface pairs have contrast ≥3:1. Source constants are checked; this is not Kotlin execution or proof of complete accessibility.
- check_project.py: 1632 source/XML/resource/SQL checks; 9 XML files; 389 keys in Ukrainian, English, Polish and Ukrainian fallback; 30 actual DAO queries.
- Comparison with the approved previews: 60 background/surface/accent/detail/text role values match exactly. Muted text and strong outline colours were adjusted for readability. The native layout is not claimed to be pixel-identical to the preview.
- check_auth_config.py: existing Android client config shape and package name remain valid; no client values are printed.
- ThemeTest.kt was updated for distinct secondary roles, complete combinations, default palette and all modes; the persistence scenario now uses Burgundy and Linen. These Kotlin/Android tests are INCLUDED, NOT EXECUTED.

This is a source-only update. NOT EXECUTED: Kotlin/Compose compilation, Gradle lint/JUnit, APK build, instrumentation, on-device persistence, Android rendering, 100–200% text, TalkBack or Windows PowerShell. JDK 17 is available; Kotlin compiler, Gradle, Android SDK, emulator and PowerShell are absent here.

Only palette/theme UI, translations, relevant tests, validation tools and version metadata change from 0.6.0. Domain, database, photo processing, account/draft storage and dependency configuration are unchanged. The prior results below are retained as results for 0.6.0, not rerun or relabelled as 0.6.1.

The package updater is verified with a host model for 0.4.1, 0.5.0 and 0.6.0 baselines, full and partial application, repeat runs, CRLF/BOM, independent edits and conflicts. Its detailed results are in package-validation.json in the archive. PowerShell itself remains unexecuted.

# Prior validation — Wardrobe Android 0.6.0

Executed in the task environment on 1 October 2026:

- check_project.py: 1580 source/XML/resource/SQL checks; 9 Android XML files; 376 keys in Ukrainian, English, Polish and Ukrainian fallback; 30 actual DAO queries.
- check_wishes.py: 2035 assertions executing production WishRules.java (integer money, optional amounts, comma/dot, grouped spaces, bounds and safe optional HTTP(S) URLs).
- check_wishes.py: 23 assertions executing production LookSelection.java (locked IDs, category refresh, incomplete wardrobes, seasonal search and ownership reference keys). Native LookRules.generate delegates to this code.
- check_wishes.py: 19 actual migration statements executed on the original exported Room v2 SQL using SQLite. Checks preservation, null unknown prices, stable wish IDs, separate budget/price, rollback and planned-reference SET NULL/CASCADE. The simulated conversion at the SQL boundary is not execution of the Kotlin repository.
- check_wardrobe.py: 2814 existing production domain assertions.
- check_auth.py: 38 existing production validation/identity/cancellation assertions.
- check_photo_rules.py: 12 production recognition/cutout rules and 7 geometry scenarios.
- check_theme.py: 120 existing production colour pairings, minimum 4.66:1.
- check_management.py: existing real DAO wardrobe deletion/isolation, rollback and migration-1-2 checks.
- check_auth_config.py: Android client config shape and package name. No credentials are printed.

New Kotlin tests cover source/weather/availability selection, locks, planned/partial statuses, stable Need → Product conversion, repeated saves, wishlist/photo/mixed-outfit backup round trip, partial-outfit editing, refusal of planned wear logging, account-isolated draft reopening/corruption and migration from the original schema. They are included, not reported as executed.

NOT EXECUTED: Kotlin/Compose compilation, KSP/Room schema-3 generation or Android schema validation, Gradle lint/JUnit, APK build, Android instrumentation, device rendering at 100–200%, TalkBack/focus, live Firebase expiry/relogin, ConnectivityManager transitions, live ML failure and Windows PowerShell. This environment has JDK 17 but no Kotlin compiler, Android SDK, Gradle, emulator or PowerShell. Host tests do not establish that the app compiles or looks exactly like the prototype.

Updater compatibility is checked with a host model against both original 0.4.1 and 0.5.0 sources, repeated/partial application, CRLF/BOM, independent edits and conflicts. The PowerShell runtime itself remains unexecuted.

Retained byte-for-byte: Firebase client config, Gradle/Kotlin/Firebase dependency versions, app ID and schema 2. AccountStorage changes only register migration 2→3. Source changes do not operate on the user's phone database or photographs. Earlier full-build results apply only to their earlier version.
