# Experience update 0.9.0

All six approved additions are implemented in source. This document describes their intended behavior and the native acceptance still required. No new APK or native visual certification is provided.

## 1. Verified portable backups

Profile/settings shows the last successful verified export, a missing/7-day-old reminder and failures. A ZIP can be checked without importing. Choosing a folder requests persistent Android Storage Access Framework read/write permission; enabling daily backup schedules WorkManager and requests an initial copy. Subsequent work is approximately daily and subject to Android scheduling and battery/storage conditions, not an exact wall-clock alarm.

Each scheduled attempt creates a new document. It stages a consistent account snapshot under the shared account write lock, copies it, rereads it and compares SHA-256 before reporting success. Partial scheduled documents are deleted on failure; old complete copies are retained. Stale private staging ZIPs older than a day are removed on the next export. Provider failure, permission revocation, cancellation, folder changes and account changes fail the attempt. The worker checks current account/revision throughout and does not open another signed-out account. Manual export, verification and additive restore remain available. Folder grants and settings are device/account-local, not stored in portable backups. There is no cloud synchronization or automatic retention deletion.

## 2. Faster capture and controlled photo editing

The batch review screen can capture up to 20 pictures per series using the system camera. A completed picture is copied to account-private storage before queueing; a cancelled shot adds no record. The persistent queue retains its 200-item ceiling and individual review before adding ownership. Selected rows can share category, season and style; blank template fields preserve existing values. Explicit categories survive later recognition. New item forms can copy category, color, subcategory, season, style, size and location from the last item without copying its identity, name, photo, price or wear.

Duplicate warnings use exact SHA-256 fingerprints of normalized imported photo bytes within the wardrobe. They permit explicit addition of a separate item. They do not recognize the same garment photographed differently. Existing pre-0.9 items have empty fingerprints until a new photo is saved, so the warning is not exhaustive.

Photo editing adds alpha-only erase/restore brushes, brush size, per-drag undo, a before checkbox and a return-to-background-original action while that original is still available. Pixels are not recolored or generated. Restore returns pixels erased during the current editing session. To correct an automatically cropped cutout, start with its original because the geometries differ. Applying creates a candidate with a separate photo ID; only Save changes ownership. Existing cleanup removes unused originals/variants after commit. The session limit is 2,048 stroke segments; applying and reopening starts another session.

## 3. Flexible wardrobe and outfit structure

Categories include top, bottom, dress, jumpsuit, shoes, layer, outerwear, bag, tights, jewelry, hat, accessory and other. A complete outfit is top+bottom+shoes OR dress+shoes OR jumpsuit+shoes. Dress/jumpsuit cannot coexist with top/bottom or each other. Up to 12 parts are allowed; up to two layers and four jewelry/accessory items, with one of each other category. Manual category/subcategory selection is available; the old recognition model is not upgraded or claimed to infer the new categories.

Matching, first-outfit onboarding, daily rules, manual selection, locks, purchase previews, purchase/undo validation and backup limits use these structures. Availability, wardrobe ownership and seasonal compatibility remain enforced. Partially built outfits remain saveable where previously allowed; invalid saved structures after category edits show replacement-needed status. Purchase and restore operations remap the saved canvas keys.

## 4. Capsule/trip scope

Open an existing capsule or trip and choose outfit generation. Only the capsule's owned-item union is eligible. Trips offer packed-only mode. Readiness, season and user recommendation rules also apply. Saving rechecks the current capsule/packing rows inside the same transaction as the outfit link; it rejects stale or out-of-scope selections. Capsule membership references existing garments and does not clone them. Changing/removing an item or packing check removes its ineligible pin.

## 5. User-controlled recommendations

Daily recommendations offer no-repeat periods 0, 1, 3, 7, 14 and 30 days, saved per wardrobe. With a 7-day period, actual wears on the target day and previous six calendar days are excluded; an item last worn exactly seven days earlier is allowed. Future wear entries are ignored. Planned dates alone do not create wear. Zero disables the recency filter.

An item can be excluded from automatic recommendations and restored from the settings list or its detail page. It remains available for explicit manual composition. Pinning retains the chosen eligible item across recommendation refreshes; replacing a pinned item is disabled until unpinned. Daily pins are view-session state, not durable wardrobe rules or backup data. Availability, scope and exclusion changes clear ineligible pins. Existing manual-editor locks also remain. Empty results suggest changing the rules or scope; unavailable clothes are not silently added.

## 6. Saved outfit canvas

The outfit editor supports drag and pinch resizing, five board backgrounds, bringing a piece to the front, reset and labelled 48dp movement/size buttons. Positions and dimensions use normalized board coordinates, constrained within the board; scale ranges from 0.6 to 1.8 with category-specific fit limits. Whole saved images remain proportional. At large system font sizes, the labelled reading-order list replaces direct canvas manipulation and the buttons remain available.

Canvas state is stored in outfit rows and editor drafts, shown in saved details and included in format-7 backup. Restore remaps owned/wish IDs; removing pieces prunes stale placements. Calendar recommendations/previews use the composition component but do not expose editing unless opening the outfit editor. No device performance or screen-layout claim is made without native testing.

## Required Android acceptance

First run `build-apk.ps1 testDebugUnitTest lintDebug assembleDebug` (or the bash counterpart), then `connectedDebugAndroidTest` with an isolated test account/device. Generate and inspect the genuine KSP schema 7; run the original schema-2 migration fixture and all persistence tests. Never manufacture a Room schema identity hash.

1. Update an installed 0.8 app using the same signing key. Confirm clothes, prices, wear, outfits, pending review and packing survive. Check new defaults and restore a format-6 backup. Export/verify/restore format 7 and compare rules, subcategories, exclusions, background and remapped canvas positions. Reject malformed or missing-photo ZIPs without altering existing rows.
2. Select local and a supported cloud-backed document-tree folder. Enable immediate/daily backups, then simulate provider failure, revoked permission, low storage, process death, folder switch, disable, sign-out and account switch. Confirm last-success changes only after a reread match, partial scheduled documents are removed and older backups remain. Verify account A never exports B's data. WorkManager scheduling depends on OS/provider conditions.
3. Capture/cancel several photos, interrupt/reopen the queue, apply templates to selected queued/working/review rows and save/skip them. Replace a review photo and verify its new fingerprint. Add the same photo twice, accept/cancel the warning and try a different photo of the same garment. Confirm skipped/error rows do not create owned items.
4. Test erase/restore/undo and before/after on opaque and transparent photos. Correct an automatic cutout from the original, cancel, reopen, save and inspect source files. An all-erased image must fail safely; a saved source must not be overwritten before Save. Check phone memory/latency under a long stroke session.
5. Generate top/bottom, dress and jumpsuit outfits; use two layers and multiple accessories. Test season conflicts, locks, laundry/archive, category edits, purchase conversion/undo and backup. Test capsule-only and packed-only generation, then change packing before save. Compare garment counts across capsules and restores.
6. Test no-repeat boundary at exactly seven days, plans versus actual wears, undo, exclusions, restoring exclusions and pin/refresh/replace. Switch wardrobe, capsule, packed-only and availability while pins exist; only eligible scoped pieces may remain.
7. Drag/pinch and button-edit every category on small/large screens. Save/reopen, rotate/recreate the activity, restore a backup and change piece IDs via purchase/undo. Verify limits, selection, overlay order, background and draft retention. At 100%, 140% and 200% fonts in Ukrainian/English/Polish, inspect clipping, keyboard/focus, 48dp targets, TalkBack labels and reading order in both themes.

## Verification status

Executed: all 11 host scripts, production Java rules including 132 new experience assertions, actual SQL migration/default preservation, XML/translations/DAO and retained theme/config checks. Prepared but not executed: five ExperiencePersistenceTest Android scenarios, updated backup and migration tests, and the Kotlin/JUnit wrapper. Host checks do not compile Kotlin/Compose or execute repository, WorkManager, bitmap/URI or Android UI code. See VALIDATION.md and HOST-VALIDATION-0.9.0.json.

Android implementation references: [WorkManager requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [Compose multitouch](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch).
