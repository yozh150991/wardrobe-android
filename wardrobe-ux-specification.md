# Wardrobe — personal wardrobe UX

## Current source: Android family transfer — 0.14.2, 16 October 2026

Specification 1.27. Android 0.14.2-release/code 32, Room/backup 10.
Settings exposes an explicit single-wardrobe family server copy: picker and
confirmation, prepare/upload/server-validation/readback progress, retained
attempts, Resume/check, Prepare again and email verification. Errors and
labels are localized UA/EN/PL. Local private content is retained. Invitations
and ongoing family sync are explicitly unavailable at this stage.

The durable account-scoped journal retains the remote identity across retries
and archive expiry. Server head identity/hash/path/size/revision and local
revision must match before success. Lost upload replies are recovered by a
commit probe, including without local ZIP bytes. Access failures, newer remote
heads and local changes never trigger silent overwrite. Session/UID guards
and expectedActorUid stop cross-account continuation. Shared caches are not
imported into the private DB. Updated backend 0.14.2 is required.

FAM-01/03 remain partial. Shared-cache isolation, invite/role/revoke and
continuous synchronization are next. Joint native acceptance is still open.
See FAMILY-ANDROID-0.14.2.md and RELEASE-VALIDATION-0.14.2.json.


## Previous source: family backend — 0.14.1, 14 October 2026

Specification 1.26. Android 0.14.1-release/code 31, Room/backup 10.
FAM-01/03 partial: trusted create/resume/begin-upload/commit backend,
strict scoped archive validation, role/expiry checks and revision CAS.
29 domain tests, 20 Firebase integration tests and 6 rules tests executed.
No new family UI is exposed: Android transport and shared-cache isolation
must precede invites and role controls. Existing private cloud and the
single-wardrobe portable backup UI retain their behavior.

Next client states: prepare/upload/validate/commit/readback, retained retry,
expired upload requiring a new archive ID, explicit revision conflict,
access revoked, account/session changed. Do not present committed replay as
the latest head: call resume. Never silently replace local data or include
shared caches in private/scheduled backups. Backend limits fail visibly
without trimming the wardrobe. Live rollout and native acceptance remain open.
See FAMILY-BACKEND-0.14.1.md and WARDROBE-ROADMAP.md for exact next steps.

## Previous source: family data foundation — 0.14.0, 13 October 2026

Specification 1.25. Android 0.14.0-release/code 30, Room/backup 10.
FAM-01 / partial. Settings adds Save one wardrobe: choose a wardrobe in a
searchable dialog, choose a ZIP destination, and receive confirmation only
after byte-for-byte readback verification. The copy includes its items, final
photos, looks, wishes, purchase history/receipts, wear, plans, feedback,
capsules and all custom categories/colours, including unused definitions.
Restore uses the existing additive flow with new IDs and consistent links.

The selected UUID stays attached to error/location retry; a deleted wardrobe
fails before writing. Stale callbacks across an account/session change cannot
export another scope. This copy does not count as a successful full-account
backup. Full-account and scheduled backups keep their existing behavior.
Five labels/hints are provided in UA/EN/PL/fallback using the existing themes,
searchable picker and accessible button sizes.

Internal foundation: strict ownership graph for all 16 portable collections,
family-wardrobe-scoped manifest v1 with content v10, stable entity IDs,
expected remote/wardrobe/owner/base-revision validation, owner/viewer/editor
policy helpers, owner-only shared export, CAS conflicts and a 15-minute
session-bound cache lease. An account-private AtomicFile checkpoint keeps a
stable remote/archive ID across retries and rejects corrupt or foreign journals.
It is excluded from backups and does not grant membership.

Family transport, server authorization, invitations, shared cache and role UI
are not connected yet. Existing private account sync remains private; no live
Firebase rules were changed. FAM-01 remains partial until infrastructure and
migration are completed; FAM-02–04 remain planned. Next source work is trusted
backend migration/membership/CAS, then invitation UI. Email delivery/provider
and pilot cost/quotas must be resolved before live deployment.

21 host suites passed, including 70 production Java assertions; 4264 source/SQL
checks, 11 XML files, 1011 localized strings and 72 DAO queries. Eight native
FamilyFoundationTest methods were prepared, not run. Kotlin/Compose/Room,
AtomicFile/ZIP/device UI, Firebase and Windows/PowerShell were not executed.
No new APK. Joint native acceptance remains deferred by the user. See
FAMILY-FOUNDATION-0.14.0.md and ADR-FAMILY-WARDROBES.md; roadmap revision 14,
all 60 task IDs retained. Previous categories, colours and photo work remains.

## Previous source: custom colours — 0.13.10, 12 October 2026

Specification 1.24. Android 0.13.10-release/code 29, Room/backup 10.
COL-01 / verify. Owned item editor and catalog colour filter offer Add custom
colour: a literal name (1–60 normalized characters) and nearest basic colour
for outfit matching. Searchable lists include standard and custom colours;
empty, invalid and duplicate names are rejected. Values belong to a wardrobe,
remain after restart even without items, and may repeat across wardrobes.

Catalog search includes localized basic and custom colour names with the existing
all-token partial matching, together with item/category/subcategory/description.
A custom filter selects its exact UUID; a basic filter includes its shades.
Details, durable item drafts and copy-previous preserve the custom colour.
Photo suggestions do not replace an explicitly selected custom value. Selecting
a standard colour clears its custom reference. UA/EN/PL/fallback, themes and
accessible labelled rows follow existing controls.

Room 9→10 adds custom_colors and nullable Garment.customColorId with FK/SQL
guards and unique wardrobe/nameKey. Canonical Garment.color remains the matching
code. Backup 10 remaps unused colours and item/wardrobe/purchase-receipt links;
legacy 1–9 backups and receipts default to no custom colour. Owned outfit parts
continue referencing garments. Wishlist/inspiration retain basic colour codes;
custom colours can be assigned when confirming a purchased owned item.
Cloud reads 8/9/10 and writes 10 with stable IDs and dirty/echo handling.
Bundled Firestore rules and both clients must be updated; downgrade is rejected.
Live rules were not deployed.

20 host suites passed, including 22 new production Java assertions and real
SQLite 9→10 data-preservation, isolation, uniqueness, FK, reopen, rollback and
sync checks. Six native tests and JVM wrapper are prepared; emulator tests were
updated, not run. Kotlin/Compose/Room build, Android UI and new APK remain pending
for joint acceptance. See COLORS-0.13.10.md; roadmap revision 13, 60 task IDs.

## Previous source: custom categories and subcategory filters — 0.13.9, 12 October 2026

Specification 1.23. Android 0.13.9-release/code 28, Room/backup 9. CAT-01 / verify.
Category selector in the owned catalog and item editor offers Add custom category,
name (1–60 characters) and a required built-in base type for outfit compatibility.
Categories belong to a wardrobe, survive restart even without items, and retain
literal user-provided names across languages. Normalized duplicate names are
rejected/reused within that wardrobe; names may repeat across wardrobes.

The subcategory field is directly visible under category. Enter a custom value or
choose one used by another item in the selected category. Saving an item persists
that value; no independent empty-subcategory dictionary is introduced. Catalog
has a visible searchable subcategory filter, also available in the filter dialog.
Changing category resets the dependent subcategory selection and retains unrelated
filters. Base-category filters include their custom categories. General text search
matches item name, localized base name, custom name, subcategory, description and
location, using partial all-token matching and Unicode whitespace/case/apostrophe
normalization. There is no automatic translation of user-entered values.

Custom category is shown on catalog cards/details and preserved by drafts/copy
previous. Photo suggestions cannot overwrite an explicitly chosen custom category.
Room migration 8→9 is additive, with nullable item reference, unique wardrobe/name
key and foreign-key/SQL guards. Backup 9 includes unused categories and remaps
category/item/wardrobe/purchase-receipt references on additive restore. Legacy
backups 1–8 default to no custom category. Cloud retains stable IDs, reads format 8
or 9 and writes 9. Updated Firestore rules (8/9, no downgrade) and both clients at
0.13.9 are required before live sync acceptance. Live rules were not deployed.

19 host suites passed, including 31 production Java search assertions and real
SQLite 8→9 migration, isolation, FK, rollback, reopen and dirty/echo guards.
Six Android tests and an added Firebase emulator test are prepared, not run.
No Kotlin/Compose build, device acceptance or new APK is claimed. Native acceptance
remains a joint phase requested by the user. See CATEGORIES-0.13.9.md and current
WARDROBE-ROADMAP.md (revision 12, 59 task IDs); prior features/backlog retained.

## Previous source: precision tools and durable photo editing — 0.13.8, 11 October 2026

Specification 1.22. Android 0.13.8-release/code 27, Room/backup 8. PHOTO-12–14.
Edges and lasso adds alpha contraction/expansion (−8…+8 image pixels), feathering
(0…6), before-step preview and explicit confirmation. Expansion never invents
colour or alpha absent from the session source. Thin detail must be inspected.
Polygonal lasso uses 3–128 taps or accessible X/Y coordinate controls. Remove the
last point, clear or confirm erase/restore/protect/unprotect. Cyan marks protected
pixels; brush, connected region, lasso and edges honor protection. Crop/rotate
transform that mask; source reset clears it. Every confirmed action is one step
in the common Undo/Redo history. Overlay colour is never exported.

Confirmed commands, the redo branch and selected tool live in the existing
account-private item draft. Form autosave preserves this recipe only for the
same item and photo. Back/keep draft permits resuming after closure or restart;
Discard explicitly removes photo commands. Apply writes one PNG and atomically
switches the draft photo while clearing the recipe. The item form offers Resume
and requires applying/discarding pending edits before saving; replacement and
automatic reprocessing wait. Owner/source checks prevent late writes. Reset and
redo sources remain protected; allocation and cleanup references are durable.
Temporary source data is released through existing cleanup after completion,
without a permanent original or new database/backup format. Corrupt commands
are retained with an error until explicitly discarded. Unfinished gestures,
unconfirmed previews and brush/zoom settings are not durable command steps.

UA/EN/PL and fallback: 987 strings, 29 new. Low height or large fonts allow
the whole dialog to scroll as well as the bounded tool pane. 18 host suites and
588864 new production Java assertions passed; synthetic 2048-square edge
processing ran under a 256 MiB host heap. Eight Android recovery/PNG/Room tests
and one JUnit wrapper are prepared, NOT RUN. No Kotlin/Compose/Gradle/APK result
is claimed. User deferred combined native acceptance; PHOTO-12–14 are verify.
PHOTO-15 (light/white balance), PHOTO-16 (alignment/composition), PHOTO-17 (file
export) are recorded as deferred. PHOTO-11 remains separate ML research.
See PHOTO-PRECISION-0.13.8.md and canonical roadmap revision 11 (58 stable IDs).

## Previous source: photo detail and selected reprocessing — 0.13.7, 11 October 2026

Specification 1.21. Android 0.13.7-release/code 26, Room/backup 8. PHOTO-09–10.
Cutouts use up to 2048 square pixels, constrained by available subject detail and
heap tiers (1024/1536/2048). Eight percent padding, no AI upscaling or permanent
original. Import/ML decode and card previews are bounded; zoom reads the saved
raster. Compressed size depends on content; native memory/time/storage/backup
profiling is still pending.

Catalog selection → bulk actions → Reprocess photos starts one account-local
queue of up to 500 items in one wardrobe. An existing queue opens for continuation.
Each result requires review and explicit Save, with before/after, zoom, dimensions
and file size. Save changes only the current photo and prepares the next item;
Skip keeps the old photo. Balanced/strong retry starts from the saved source.
Cancel remaining removes unapproved results and preserves approved replacements.
Saved-photo processing cannot recover previously removed content.

The queue survives restart and resumes ML only on explicit request. Atomic
allocation/recovery and retained cleanup IDs protect interrupted candidates;
the old photo remains until the database commit succeeds. Source/wardrobe/item
guards reject stale or deleted items; entry/token guards prevent a repeated click
or retry from approving the next item. Names, category, colour, fingerprint and
other metadata are preserved. Shared photo references and editor drafts are
protected. Account switch cancels private work; cloud snapshot apply waits for
local queue completion. Portable backup/cloud retain final photos, not this queue.

UA/EN/PL plus fallback. 18 host suites passed, including 108013 production Java
resolution assertions and 14 actual SQLite replacement checks. Twelve new Android
tests are prepared, not executed. Kotlin/Compose, APK, device/ML/memory and
accessibility acceptance are deferred to the combined stage requested by the user.
PHOTO-09–10 are verify. PHOTO-11 remains deferred research; PHOTO-01–08/R1/R2/DEV-01
and family backlog remain. See PHOTO-QUALITY-0.13.7.md and canonical roadmap rev 10.

## Previous source: connected cleanup and inspection — 0.13.6, 10 October 2026

Specification 1.20. Android 0.13.6-release/code 25, Room/backup 8. PHOTO-06–08.
Tap an area selects only four-connected visible pixels within a fixed-seed colour
tolerance (0–100%, default 10%). Transparent pixels block selection. A pink preview
must be reviewed and explicitly erased; tolerance/seed changes and Clear preserve
the photo and redo. Erasing adds one command to the unified session history.
All-visible-pixel selection cannot be confirmed. Similar garment/background
colours can still join; the UI explains tolerance adjustment or brush correction.

The preview is bound to the current frame, seed and tolerance; stale/cancelled
work cannot be confirmed. Apply waits for the pending selection decision. Tool
navigation, history/source changes and recreation drop unconfirmed selection.
Tap cancellation, drag and a second finger do not commit a new selection.
Region commands retain the restoration source and use the same replay for PNG.

Inspection offers auto, white, cream, dark, purple and grid backgrounds; these
change only the editor display. A before/after slider clips the transformed source
before manual alpha edits on the left and the result on the right. Crop, rotation,
zoom and pan match; the divider spans the visible photo. Painting is disabled
while comparing. This does not recover a deleted original or compare against an
unavailable pre-ML photo. No display background or highlight is baked into PNG.

17 host suites pass: 3925 source/SQL checks, 935 strings, 52357 new production
Java assertions plus the previous 22649 session and 1774 editor assertions.
Four JUnit methods and four Android methods are prepared, not executed.
At the user's explicit request, Android build/device acceptance is deferred to
one combined testing stage. No new APK; no new Gradle bootstrap attempt is
claimed. See PHOTO-INSPECTION-0.13.6.md and RELEASE-VALIDATION-0.13.6.json.

Roadmap revision 9 preserves 52 IDs and DEV-01. PHOTO-06–08 are verify;
PHOTO-09–11 remain deferred. No permanent originals, new model/API, Room/backup
change or automatic rewrite of existing photos. Earlier sections are historical.

## Previous source: loupe, unified history and soft brushes — 0.13.5, 10 October 2026

Specification 1.19. Android 0.13.5-release/code 24, Room/backup 8. PHOTO-05.
A 3× loupe follows the painted region above the finger, relocating at canvas
edges; it can be disabled. Existing 1 px–large diameter, zoom and pan remain.
Brush softness is 0–100%, strength 1–100%; default hard edge/full strength.
Erase/restore changes alpha and preserves RGB. Maximum pixel coverage within
one gesture makes its strength independent of pointer-event frequency; separate
gestures can build up the effect.

One undo/redo history spans mask, crop, rotation and source reset. Tool switching
preserves it and creates no intermediate file. Completed crop-handle/slider
gestures commit one operation and fit the cropped frame. Undo restores discarded
geometry. A completed new action after Undo replaces the redo branch; cancelled
gestures, settings and navigation preserve it. Source/reset actions are undoable.
Brush settings survive tool switches. Before view shows the currently transformed
source before manual alpha strokes; this is not a new before/after slider.

Preview and Apply share replay logic, including transformed restoration pixels.
Incremental preview requires the same PhotoStore and source. The before-view
cache is bound to the current frame so an old image cannot flash on source/account
changes. Apply writes one PNG
candidate; empty results are rejected and failed/cancelled candidates removed.
An open editor saves command/cursor state, not bitmaps; closing ends its history.
Limits remain 128 commands/160000 characters and 2048 applied segments; no silent
history eviction. Permanent originals, Room/backup and permissions are unchanged.

17 host suites passed: 3824 source/SQL checks, 911 strings, 22649 new production
Java assertions plus the existing 1774 editor assertions. Four JUnit methods and
five Android tests are prepared, not executed. Gradle bootstrap failed before
compilation with UnknownHostException: services.gradle.org. No new APK. Native
gestures, Bitmap/PNG equality, restoration, performance, 200%/narrow layouts and
TalkBack need PHOTO-TOOLS-0.13.5.md acceptance. PHOTO-05 remains verify.

Previous PHOTO-01–04/R1/R2 and concurrent DEV-01 Git Safety are preserved.
Roadmap revision 8 holds all 52 IDs; PHOTO-06–11 retain deferred ideas: connected
area erase, edge inspection, comparison slider, higher final resolution, selected
photo reprocessing and interactive subject selection. These are not implemented
in this release. Historical 0.13.4 stage boundaries below are superseded by the
unified history described here.

## Previous source: undo/redo and precise brushes — 0.13.4, 9 October 2026

Specification 1.18. Android 0.13.4-release/code 23, Room/backup 8. PHOTO-04.
Fixed Undo/Redo controls undo whole taps/strokes and restore only committed undone
steps. A new completed action discards the redo branch; changing brush settings,
zooming or cancelling a gesture preserves it. Crop-handle/slider gestures, rotation
and reset also use command history. The available source-with-background reset is
undoable in the mask editor.

Brush diameter is in image pixels, from 1 px to roughly 16% of the shorter side
(164 px for 1024×1024), retaining the old broad range. Default 16 px; logarithmic
slider, ±1 px buttons, 1/4/16/64/max presets when applicable. Taps paint a dot;
preview/save use identical resolution and preserve RGB. Separate pan/zoom mode,
cursor, white/cream backdrop and optional transparency grid remain available.

History is scoped to the current editing stage: switching between mask and changed
crop/rotation applies edits to the next tool and starts a new history for the new
source. It does not undo across already-applied stages or after closing the editor.
Completed commands and redo cursor use rememberSaveable; interrupted gestures are
discarded. Commands are bounded to 128 steps/160000 characters and mask histories
to 2048 applied segments; reaching the limit asks to apply/reopen, without silently
dropping earlier steps. No permanent originals or new data schema.

17 host suites and 1774 production Java assertions passed; 3782 source/SQL checks,
902 localized strings. JUnit wrapper and five Android JSON/history tests prepared,
not executed. Gradle bootstrap stopped with UnknownHostException: services.gradle.org
before Kotlin/Compose compilation. No new APK. Native gesture handling, saved-state
restoration, narrow/200% layouts and TalkBack require PHOTO-EDITOR-0.13.4.md acceptance.
PHOTO-04 remains verify; previous PHOTO-01–03/R1/R2 are retained cumulatively.

## Previous source: cleaner cutouts and neutral backdrops — 0.13.3, 9 October 2026

Specification 1.17. Android 0.13.3-release/code 22, Room/backup 8. PHOTO-03.
Confidence-mask refinement adds soft edge thresholds, removal of tiny disconnected
residue and guarded cleanup matching a reliable uniform background. White-on-white
fabric bypasses colour trimming; substantial separate components remain together.
The editor offers standard and stronger cleanup with cancellation and existing
manual correction. Retries use the temporary pre-cutout source while available.

Cards, fullscreen viewer and editors show white (#FFFFFF), or cream (#EEE5D5)
for white/light-neutral foreground pixels. Explicit item colour “white” overrides
the detector on the main form/cards/details. The brush includes a transparency-grid
toggle. Display backgrounds do not flatten the stored PNG; editable alpha and
collage backgrounds remain available. Opaque source photos are not presented as
successfully cleaned when segmentation fails. No permanent original, paid API,
new model, Room/backup field or automatic batch rewrite is introduced.

16 host suites and 40 new production Java mask/backdrop assertions passed on
synthetic fixtures; these do not measure ML quality on real photographs. Android
compilation was blocked before compilation by UnknownHostException for
services.gradle.org. No new APK; Kotlin/Compose, Gradle JUnit/lint, device ML quality,
UI/TalkBack and Windows/PowerShell execution remain unverified. PHOTO-03 stays
verify; run PHOTO-CLEANUP-0.13.3.md after building, then previous PHOTO-01–02/R1/R2.




## Previous source: saved-photo zoom — 0.13.2, 8 October 2026

Specification 1.16. Android 0.13.2-release/code 21, Room/backup 8.
Tap a photo with a magnifier on a garment/wish card or its detail screen to open
the saved image fullscreen. Pinch 1–5×, drag, double tap 3×/fit, zoom buttons,
fit, Close and system Back are available. Catalogue selection mode keeps its
selection tap; the text section of a card still opens details.

Zoom is anchored at the gesture centroid, pan is bounded by the fitted image,
and a resized viewport or reopened viewer starts at fit. A neutral grey canvas
helps show transparent cutouts. Loading/missing/retry, localized labels and
TalkBack actions are included. Viewing performs no file or repository writes.
The saved bitmap is used at its available resolution; no original is retained.

16 host suites passed, including 225 assertions against the production zoom
geometry. The JUnit wrapper is prepared but not run. Kotlin/Compose build,
Android gesture handling, accessibility/device acceptance and APK remain
unverified because Gradle bootstrap is network-blocked. PHOTO-02 stays verify;
see PHOTO-ZOOM-0.13.2.md. R1/R2/PHOTO-01 changes remain in the cumulative package.

## Previous source: photo recovery — 0.13.1, 8 October 2026

Specification 1.15. Android 0.13.1-release/code 20, Room/backup 8.
The queue retains an account-private processing report per unfinished import.
Review separates background removal from category recognition and offers manual
completion. A failed row can be opened and completed with a readable replacement.
The save blocker stays above the main button; Save brings the missing field into
view. Secondary review actions scroll with the form, freeing space for fields.

Missing model, Play services, failed segmentation, timeout, inaccessible/missing/
unreadable/oversized files, read/write failure, confirmed storage exhaustion and
unknown errors have distinct explanations. No claim about darkness, blur or
network state is inferred from an unexplained failure. Old rows without a report
say that the details are unavailable. Background removal is optional for Save;
photo, name and category remain required. Current photo/fields are retained.

Reports contain no photos, URIs, exception messages or cache paths. Batch reports
survive restart and are removed at Save/Skip; old reports are not attributed to
replacement photos. Manual editor reanalysis reports are session state.

15 host suites passed. Five new Android recovery tests are prepared, not run.
Gradle bootstrap is blocked by network; Kotlin/Compose and native UI/device
acceptance are pending. See PHOTO-RECOVERY-0.13.1.md and PHOTO-01 in the roadmap.
The installed app version and exact cause of the user's screenshot are unknown.

## Previous source: R2 — 0.13.0, 8 October 2026

Specification 1.14. Android 0.13.0-release/code 19, Room/backup 8.
The purchase assistant now explains trait-based similarity, includes archived
and temporarily unavailable matches, previews owned replacements and can save
an all-owned outfit without buying the product. Price and budget are compared
only within the same currency. Deferring leaves the product in the existing list.

Unsaved is no longer labelled as a new wardrobe capability. Up to 20 pinned
examples are assessed for owned replacements; the bounded sample is explicit.
Unknown traits are not inferred from names or photos. Optional comparison
traits apply to this review and may be carried into the existing purchase editor.
They do not add permanent fields to Wish. This is not visual product identity.

15 host suites passed, including 76 purchase-advice assertions and the 34
import assertions retained from R1. Seven new Gradle/JUnit test methods and one
Android flow test are prepared but not executed. Gradle bootstrap remains
blocked by network access; Kotlin/Compose, lint, APK and native acceptance have
not been completed. No new APK is included in wardrobe-release-update-0.13.0.zip.

[WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md), revision 3, remains the authoritative
backlog with all 40 IDs and agent handoff. R1 and R2 are verify, not done. Next:
REL-01/QA-01–02 and the R1/R2 native matrices; then complete FAM-01 infrastructure
and cost decisions before family roles/invitations. Older sections below are
historical and do not supersede this status.

## Prior source: R1 — 0.12.0, 7 October 2026

Version 1.13 of this specification. Android 0.12.0-release/code 18; Room/backup 8.
Compact catalogue header and add-method sheet; durable, account-bound Android
Share intake for text/URLs; bounded JSON-LD/Open Graph product import with
manual fallback; review in existing item/wish editors, need conversion and URL
duplicate hints. Owned imports retain source/price/photo in existing purchase
history in the same transaction as the item. No new APK has been compiled.

All 14 host suites passed, including 34 production Java import assertions.
Kotlin/Compose, Gradle JUnit/lint, Android/Firebase tests and native UI acceptance
remain unexecuted because Gradle bootstrap was unavailable. Eight new Android
regression tests are prepared, not claimed as executed. Historical results below
apply only to their named releases.

The authoritative backlog remains [WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md),
revision 2, bundled with AGENTS.md in wardrobe-release-update-0.12.0.zip.
Next: REL-01/QA-01 and R1 acceptance, then SHOP-01–03. Direct image Share,
network fixtures and full UI/process-death acceptance remain recorded there.
Family wardrobe architecture is documented in ADR-FAMILY-WARDROBES.md; roles,
invitations, rules and infrastructure are not implemented by R1.

## Prior roadmap baseline — 0.11.2 documentation revision

The current source package is `wardrobe-release-update-0.11.2.zip` (Android
`0.11.2-release`, version code 17). The category row wraps and the full chooser
is labelled “Категорії”. The 0.11.2 build and native layout acceptance remain
unverified; successful 0.11.1 tests below are historical evidence.

[WARDROBE-ROADMAP.md](WARDROBE-ROADMAP.md) is the authoritative backlog for
future priorities, status, dependencies, acceptance criteria and agent handoff.
The package includes it plus `wardrobe-android/AGENTS.md`, installed by the
updater. The roadmap records the approved sequence: compact catalogue and
Android Share import; enhanced purchase assistance; separate family accounts
and wardrobe roles; feedback-based preferences; daily widget and forgotten-item
prompts; smarter travel capsules. Deferred ideas and outstanding release checks
are retained there. Earlier “next release” sections below describe their own
historical baseline and must not be used as the current implementation plan.

The basic purchase compatibility screen already exists; it must be extended.
Same-account cloud snapshots are not family sharing: invitations and roles are
still to be implemented. This documentation revision does not add these features
or produce a new APK.

**Version 1.12 · 3 October 2026 · Android release 0.11.1-release**

## Prior Android release — 0.11.1-release

All seven agreed blocks are implemented in Android source. This current section supersedes prior source-only build and unexecuted-test statements below. Debug and Release APKs have now been compiled from a clean build (137 executed Gradle tasks); 43 JVM tests, 71 Android instrumented tests, 6 Firebase Auth/Firestore/Storage emulator tests, 13 host suites passed without failures or skips. Release UI smoke acceptance could not finish: repeated Android System UI ANRs/UIAutomator timeouts in the software emulator; none of the five planned navigation/large-text checks is claimed as passed. Lint reports zero errors; warning totals and actual JUnit/SDK evidence and failed UI diagnostics are recorded in docs/history/RELEASE-VALIDATION-0.11.1.json and release-evidence. This is evidence for those executed checks, not completion of every human acceptance step.

Authentication, account binding, photo work with durable drafts and retry, need/product/purchase history and safe restart-persistent undo, owned/mixed/inspiration collage editing, daily status/undo/unavailable explanations, persistent offline cloud revisions/conflict choices, and calendar/wear/cost-per-wear/travel packing retain the implementation described in prior 0.11.0. Final fixes correct the DAO call, explicitly deny Storage archive overwrites with resource == null, use memory-cache Firestore alongside Room's durable queue, and bundle the genuine Gradle 8.11.1 wrapper with both checksums. Only debug-local emulator hosts permit HTTP. Explicit Android system-backup/transfer exclusions preserve per-account isolation while the app's deliberate ZIP and Firebase features remain available.

Five real Android cloud SDK tests use named demo Firebase apps, two independent clients, isolated Room/photo stores and injected transport failures. They prove reopen/retry, lost commit acknowledgement, later edits retained during upload, local/cloud/both conflict choices with rescue/remapping, and compare-and-set concurrency with idempotent retry. The Node Auth tests execute registration/duplicate/wrong-password/client isolation and one-use password reset links. Production Firebase was not enabled or deployed: no owner CLI login is available. The existing google-services.json remains byte-identical; it is client configuration, not administrative access.

Version code 16, Room 8 and backup format 8. The full source and safe updater support baselines 0.8/0.9/0.10/0.11, with actual PowerShell 7.6.2 Linux checks recorded in package-validation.json, including dry run, partial state, BOM/CRLF, configuration preservation, conflicts and exact-byte rollback on a real write failure. Windows filesystem/PowerShell 5.1 still require local acceptance. A separate ready unsigned Release APK kit reuses the user's existing signing key locally; no private key is distributed or replaced. Its helper's signature/payload/alignment/tamper guards were tested using the existing session-local debug key, not the user's unavailable Windows key.

Remaining acceptance/owner actions: activate or verify Auth provider, Firestore/Storage and rules in the existing project, complete any owner billing setup for Storage, check real delivered email links and two physical phones, then accept full wardrobe UI flows, camera/provider grants, real ML, TalkBack, revoked sessions and OEM background execution. The code and 52 original scenario criteria are supplied; every native/manual criterion is not claimed as already accepted. Legacy purchases without an original exact receipt retain history but cannot acquire safe persistent undo retroactively.

## Prior Android scenario coverage — 0.11.0-scenarios

All seven agreed blocks have implementation and acceptance coverage in Android source. This section supersedes the older roadmap/prototype notes below. The release is a source package for building and acceptance: no compiled APK, native UI verification or live Firebase synchronization is claimed.

1. Authentication retains email/password login, registration, reset by email link, checked remembered sessions and explicit non-destructive binding of the original local wardrobe. First use and explicit sign-out open Login. Existing account IDs, photos and authentication/signing configuration are preserved.
2. Add-item input remains available during photo processing; account-private editor drafts survive reopening. Retry/current-background recovery and Save-and-add-next remain, with stale photo result ownership checks and post-commit advancement.
3. Needs and products support optional reference photos and links. Need-to-product conversion retains the original name, category, requirements, budget and reference photo/link/inspiration. Candidate reversal restores that original need and records the previous product in durable history. New purchases record a durable undo receipt; undo remains available after restart and refuses to delete later edits, wear or capsule references. Purchase/cancellation events and their final photos survive backup and sync. Legacy purchases retain their history but lack the original exact receipt needed for safe persistent undo.
4. The canvas, multiple locks, selective replacement and manual owned/mixed/inspiration modes remain. General inspiration now uses 69 curated stable examples with seasonal/occasion preferences. Saved legacy idea keys are supported; examples never become owned garments implicitly.
5. First-outfit guidance and reversible laundry/archive actions remain. Saved looks now explain each unavailable piece and offer replacement or return to available. Deleted wishlist references preserve their saved snapshot and explain the missing entry.
6. Firebase Firestore/Storage snapshot sync is implemented with Room transaction-owned dirty revisions, a restart-safe offline queue, WorkManager retries and UID-scoped paths. Server revision comparison uses a transaction; lost upload/commit acknowledgement can be recovered, and edits made during upload stay queued. Conflicts offer local, cloud or both versions; cloud replacement first makes a dated local backup, and both versions import cloud wardrobes separately with remapped internal references. Stale choices and unfinished local drafts/imports block replacement. Stable IDs are retained during normal cloud application. Multi-instance Room invalidation refreshes the visible collection/status after worker writes. Activation of services, rules and Storage billing in the existing Firebase project and two-device validation are still required.
7. Calendar plans, actual wear, unique-day statistics, cost per wear and travel packing remain implemented and are included in cloud snapshots and backup 8. Planning alone does not add wear; currencies remain separate.

Version code 15. Room schema 8 adds wish reference-history fields, transition events, purchase undo and cloud metadata through migration 7→8; the full migration chain is registered. Backup format 8 accepts formats 1–7 additively and includes history/undo references. Cloud replacement uses a separate stable-ID apply transaction. Drafts, pending photo imports, local appearance/language, URI/folder grants, city settings and transient pins are device-local.

Executed: all 13 host check scripts, matching Ukrainian/English/Polish resources, production Java rules and actual SQLite schema 2→8 preservation. New scenario checks execute 294 Java assertions and 42 persistent SQLite queue triggers with restart, rollback and edit-during-upload cases. JS test syntax was checked. Prepared, not executed: ScenarioPersistenceTest, expanded editor draft tests, existing Android/JUnit coverage and Firebase emulator security/concurrency tests. The Android build stopped at the missing full-JDK check; Kotlin/Compose/KSP/Gradle/APK, native behavior, Firebase and human acceptance remain pending. The coverage matrix and exact commands are in docs/history/SCENARIO-COVERAGE-0.11.0.md and CLOUD-SETUP-UK.md.

## Prior Android UX refinement — 0.10.0-ux


All 14 findings from the 0.9.0 UX audit are implemented in Android source. This current section takes precedence over earlier release/prototype descriptions. No new APK or native visual verification is claimed.

| ID | Implemented behavior | Main source |
| --- | --- | --- |
| UX-01 | Actual return stack, wardrobe-scoped route entries and saved screen state; details return to their originating catalog, daily result, calendar or capsule. Missing records have a recovery action. | Ui.kt, DailyScreen.kt |
| UX-02 | Contextual file, folder, space, photo, save and stale-selection errors with an appropriate recovery action. Form input remains intact after failure. | OperationState.kt, WardrobeViewModel.kt |
| UX-03 | Operation labels, honest indeterminate progress and cancellation for interruptible photo/import/export/verification work. Read-only navigation remains available during foreground backup/verification and background scheduled copies. | WardrobeViewModel.kt, PhotoStore.kt, Backup.kt |
| UX-04 | Universal Add item, neutral first-item copy, and the existing flexible dress/jumpsuit + shoes readiness checklist. | CatalogScreen.kt, strings.xml |
| UX-05 | Daily results follow a compact conditions summary; Configure opens a sheet. Item details, replacement, pinning and exclusion are contextual actions, with visible pin badges. | DailyScreen.kt, OutfitComposition.kt |
| UX-06 | Specific empty-result reasons for scope, packing, unavailable/missing pieces, weather/seasons, pins, repeats, exclusions and feedback; explicit recovery actions preserve strict rules. | DailyDiagnostics.java, DailyScreen.kt |
| UX-07 | Separate Content and Composition modes, one-step placement/background undo, reset confirmation, button alternatives and a persistent Save area above the keyboard. Duplicate composition has an explicit open/update choice. | OutfitEditor.kt, OutfitComposition.kt, CanvasControls.kt |
| UX-08 | Ordered review session with progress and next thumbnail; Save and next re-reads the ready queue after commit. Defer keeps the photo in the queue; explicit field selection controls copying. Camera series offers an inline next/review action. | Ui.kt, ImportTools.kt, ImportReviewRules.java |
| UX-09 | One photo-tool dialog, direct crop handles with slider alternatives, explicit pan/zoom mask mode, brush preview, inverse image coordinates and cancellation checkpoints. Preview results are tied to the current source. | PhotoEditor.kt, PhotoMaskEditor.kt, PhotoViewportRules.java |
| UX-10 | Searchable thumbnail selectors for calendar/capsule/editor items, contextual labels and editable unique daily-look names. | SelectionControls.kt, CalendarScreen.kt, CapsuleScreen.kt, LookNameRules.java |
| UX-11 | Compact last-verified status, details and real WorkManager state. Restore first verifies and shows counts, explains additive import, and checks the same ZIP fingerprint before durable writes. | BackupHealthPanel.kt, BackupHealth.kt, Backup.kt, VerifiedArchiveInput.java |
| UX-12 | Compact text cards for Needs, product photos only when present, All/Needs/Products filters, search and Found product action. Existing ownership/history/undo contracts remain. | WishlistScreen.kt |
| UX-13 | Short relevant category rows plus searchable full category access. Labelled full-row selection has one action owner and exposes its state. | CatalogScreen.kt, OutfitEditor.kt, SelectionControls.kt, CapsuleScreen.kt |
| UX-14 | Calendar uses saved layout/background when actual piece keys match. Different actual-wear sets show their own pieces with a clear caption. Travel, wear and backup dates use the current app locale. | CalendarScreen.kt, CapsuleScreen.kt, StatisticsScreen.kt, DateLabels.kt |

The main flow remains owned wardrobe → daily/saved looks, with separate wanted products and explicit ownership confirmation. Screen return frames retain catalog query/filters/scroll, calendar date, capsule/packing and generated daily result IDs and conditions. Root destination, active wardrobe and account changes clear incompatible return entries. Nested Save returns to its originating detail; creation retains its completion flow. Dirty forms still require an explicit discard action.

Contextual errors identify the failed operation and offer a meaningful retry, alternative file/folder, refresh or close action. Import advancement uses fresh post-commit ready rows. Deferral is view-session state and does not delete the queue photo. Selected previous fields alone are copied. Daily empty-state diagnostics check the same strict generator; combined causes have a general constraints explanation rather than a falsely specific reason. Rules change only through explicit controls.

Saved-photo ownership remains unchanged: remove raw imports and unused candidates after a successful commit if no unfinished queue or actual shared reference needs them; keep only the final photo on a saved garment. Preserve recoverable unsaved drafts and failed/processing imports. Alpha-only mask operations preserve RGB and source files. Crop/mask previews are tagged with the current photo source to reject stale display/application.

Foreground operation labels and cancellation distinguish interruptible work from atomic Save/Restore. Read-only routes remain available for export/verification and scheduled copies; writes remain serialized. WorkManager state comes from the actual unique work records. Restore preview shows verified wardrobe/item/outfit counts and additive behavior; confirmation checks the exact ZIP fingerprint before DB/final-photo writes. Scheduling enabled is not represented as copying in progress.

Version code 14. Room 7, backup format 7, migrations, dependencies, account boundaries and supplied Firebase/signing settings are retained. Dates are localized for presentation while ISO storage stays intact. UI return frames, current pins/results, review deferrals, drafts, source URIs, folder grants/scheduling preferences and city settings are not portable.

Executed: all 12 host source/Java/SQLite scripts, 3000 source/SQL checks, 718 matched localized keys, 6817 new production-rule assertions plus retained rules and real migration 2→7 preservation. Prepared, not executed: eight ExperiencePersistenceTest Android scenarios, RefinementRulesTest and existing native/JUnit coverage. The actual Android build stopped at the missing full-JDK check. Kotlin/Compose/Gradle/KSP, APK, native provider/worker/photo/UI, gestures, Firebase/weather/ML and TalkBack remain pending. The source package includes 70 host-model updater scenarios across 0.9.0/0.8.0/0.7.0/0.6.1/0.6.0/0.5.0/0.4.1; PowerShell remains unexecuted. Details and native acceptance are in docs/history/UX-REFINEMENT-0.10.0.md and VALIDATION.md.

## Prior Android experience — 0.9.0

This section records 0.9.0. The current 0.10.0-ux section above takes precedence.

This section takes precedence over earlier versions and prototype notes below. The six additions approved after competitor-review research are implemented in source, in the agreed order. No new APK or native visual verification is claimed.

1. **Backup reliability.** Settings shows the last verified export, missing/7-day-old reminder and failure status. Verification reads a portable ZIP without importing. The user may grant a document-tree folder and enable approximately daily WorkManager backups with an initial copy. Each attempt creates a new document, stages the account snapshot under the shared account write lock, rereads the written bytes and checks SHA-256 before success. Failed partial scheduled documents are removed and old complete copies remain. Account/folder/revision guards prevent stale or signed-out work. Android scheduling and provider behavior require device checks; no exact-time alarm, cloud sync or retention deletion is promised.
2. **Faster capture and photo correction.** Camera series, selected-row category/season/style templates and copying the previous item's descriptive fields supplement the persistent reviewed import queue. Completed camera photos are copied to private storage before enqueueing; explicit category choices survive recognition. Exact imported-photo SHA-256 duplicates trigger a warning and allow explicit separate addition; this does not detect the same garment in different photos or all legacy items. Alpha-only erase/restore, brush size, per-drag undo and before/after are added. Restore affects manual erasures from the current session; correcting an automatic cutout starts from its available background original. Applying creates a candidate; Save changes the stored reference and cleans unused originals as before.
3. **Flexible outfit structure.** Categories include dress, jumpsuit, layer, bag, tights, jewelry, hat and accessory alongside legacy categories. A complete outfit is top+bottom+shoes, dress+shoes or jumpsuit+shoes. One-piece garments cannot mix with top/bottom. Up to 12 parts, two layers and four jewelry/accessory items are supported; other categories allow one each. Optional free-text subcategories are saved and searchable. Matching, onboarding, manual locks, purchase conversion/undo, daily generation and backup validation use the same structures. The existing classifier is retained; new categories can be selected manually.
4. **Scoped capsule generation.** A capsule/trip can generate looks only from its shared owned-item union; trips offer packed-only mode. Recommendation rules and readiness/season checks still apply. Saving rechecks current capsule membership/packing and links the look in the same transaction. Garments are referenced, not cloned. Scope/availability changes clear ineligible daily pins.
5. **Recommendation controls.** Per-wardrobe no-repeat periods 0/1/3/7/14/30 days use actual recorded wear, never plans. Seven days excludes today and the preceding six days; exactly seven days since wear is eligible. Future wear entries are ignored. Item exclusions affect automatic recommendations and are reversible; explicit manual composition remains available. Eligible daily pins survive refresh and disable replacement until unpinned. Pins are view-session state, not portable or durable rules. Existing manual-editor locks remain. Empty results explain how to adjust rules/scope.
6. **Saved visual canvas.** The editor offers drag/pinch resizing, five backgrounds, front order, reset and labelled movement/size buttons. Normalized category-aware bounds retain the complete image inside the board. Saved layout/background are stored in outfit rows and drafts, displayed in saved details and included in portable backups. Restore and purchase/undo remap piece keys; removed pieces prune stale placements. Large text uses the labelled reading-order alternative while adjustment buttons remain. Native rendering, performance and accessibility are pending.

Ukrainian, English and Polish plus Ukrainian fallback cover 617 strings. Four bottom destinations and account-private storage remain. WorkManager 2.10.1 and DocumentFile 1.0.1 support scheduled document-tree writes; other supplied configuration/signing settings are retained.

Room schema 7 adds seven fields through explicit 6→7 ALTER statements, preserving existing data and registering the full migration chain. Backup format 7 preserves repeat settings, subcategories, exclusions, fingerprints, layout/background and earlier plans/wear/capsules. Formats 1–7 restore additively into new wardrobes with ID remapping. Unfinished import queues, source URIs, folder grants, backup scheduling preferences, transient pins, city settings and drafts are not portable. The original exported schema 2 remains; KSP must generate and validate the genuine schema 7.

Executed: all 11 host Java/SQLite/source check scripts, including 132 new production experience assertions and actual migration 2→7 preservation. Prepared, not executed: five ExperiencePersistenceTest Android scenarios, the Java-rule JUnit wrapper and updated backup/migration tests. The actual native build stopped at the missing full-JDK check. Kotlin/Compose compilation, KSP/Room validation, Gradle, APK, native bitmap/provider/worker/UI, ML/weather/Firebase, TalkBack and PowerShell remain pending. The package includes a source updater for 0.8.0/0.7.0/0.6.1/0.6.0/0.5.0/0.4.1, host-model package checks and native acceptance instructions in EXPERIENCE-UPDATE-0.9.0.md. Source checks do not establish Kotlin compilation or usability.

## Prior Android roadmap — 0.8.0

This prior section records 0.8.0. The current 0.9.0 section above takes precedence. All six roadmap steps were implemented in source, in the agreed order. This was not a compiled APK or a visually verified native build.

1. **Batch capture and mass actions.** The catalog has an up-to-20-photo picker and an account-local Room queue, processed serially. Paused and interrupted work resumes; failed images can be retried or skipped. Each item requires review before ownership is saved, with original-background and editing options. Catalog selection supports laundry, available, archive, restore and season changes with guarded undo. Selection applies only to shown items.
2. **Daily owned looks.** Up to three complete, distinct looks use the active wardrobe's available owned items, compatible seasons, occasion style preference, color matching, favorites and recorded wear rotation. Cool/cold/rain contexts require outerwear. Replacement freezes the other pieces. Explicit feedback hides exact sets for that context; too warm switches to manual warm weather, and feedback can be reset. These are deterministic rules, not a trained personal AI model.
3. **Outfit calendar.** Week/day navigation, arbitrary dates, multiple plans per day, move and remove for unworn entries. A plan never increments wear. Separate confirmation records actual wear on today or a past date, rejects missing/unpurchased/unavailable items, and captures actual item IDs. Calendar undo removes only its own wear source. Independent item/outfit marks survive. Item-day undo reconciles calendar history.
4. **Useful statistics.** Unique recorded item wear days, most used, never worn, not worn for 90+ days, optional archive inclusion, and cost per wear. Optional integer-minor-unit prices in PLN/EUR/USD/UAH/GBP can be edited. No price and no wear have distinct empty states; currencies are not combined. A ready forgotten item can anchor a new look.
5. **Capsules and travel packing.** Named capsules/trips with optional ordered dates, saved owned looks, a deduplicated owned-item union, manual extras and packing checks. Outfit edits reconcile the union transactionally while retaining other checks and manual items. Unavailable items cannot be newly packed. Mixed outfits warn about omitted unpurchased pieces. Delete/reset actions preserve wardrobe records; stale purchase undo refuses newly referenced capsule items.
6. **Purchase compatibility.** A product is fixed in up to 20 generated combinations with available owned items. Context controls, new/already-saved counts, same-category-and-color comparisons and planned mixed-outfit saving precede explicit buying. The preview assumes all seasons for products and states this; material, fit and image similarity are not inferred. The existing Need → Product → Owned flow, retained history and guarded undo remain.

Optional city weather uses explicit geocoding selection without device location permission. Open-Meteo/GeoNames are attributed; the account-local cache shows its update timestamp, and manual weather remains available offline. Automatic use stops after 24 hours of cache age; current apparent temperature is used up to 6 hours. Rain protection remains a user check because item metadata does not describe waterproofness. Live Android weather requests are not verified here.

Navigation retains four bottom destinations. Daily looks and statistics are reachable from the catalog; calendar and capsules are tools under outfits; compatibility is on a product page. New screens use existing theme and owned/planned labels. Ukrainian, English and Polish translations plus Ukrainian fallback cover 532 keys.

Room schema 6 has explicit 4→5→6 migrations, preserving existing garments, wear, looks and historical purchase prices. Backup format 6 includes saved plans, their wear ledger and actual item IDs, feedback, capsules, packing and optional prices. Import validates references before database writes and remaps all IDs, including plan wear sources. Formats 1–5 remain accepted and restore as new wardrobes. Unfinished queues, URIs, city settings and drafts are not portable. The earlier exported schema 2 is retained; KSP must generate and validate schema 6 during the Android build.

Validation includes executed production Java rules and actual SQLite migrations. Android repository/backup tests are included, not executed. Kotlin/Compose compilation, KSP/Room on Android, live ML/weather/Firebase, device layout/accessibility and PowerShell execution remain pending. The source package includes detailed results, a safe updater for 0.7.0/0.6.1/0.6.0/0.5.0/0.4.1 and Android acceptance steps in ROADMAP-UPDATE-0.8.0.md. No new APK was built because the full JDK/Gradle/Android SDK toolchain is absent.

## Prior Android UX — 0.7.0

This prior section documents 0.7.0. The current 0.8.0 section above takes precedence. This is an implemented source release, not a compiled APK or a visually verified Android build.

- Add-item forms show photo, name, category and colour first. Size, storage location, season, style, availability and notes are expandable. Save stays visible above the keyboard; extra fields retain draft state while hidden. Product ideas avoid reserving a large empty image area, and optional details are expandable.
- Photo analysis runs separately from database writes. The user can type while analysis is running or keep the current photo. Successful background removal is applied automatically with transparent margins trimmed and the whole subject centred on a transparent 1024px square. EXIF orientation is retained; actual garment direction remains manually editable. Recognition suggestions require an explicit Apply action. Generation and editor checks reject late results after another image, manual editing, save, cancellation or sign-out. Raw imports and unused variants are removed after commit. A previously saved product reference can remain as a final image in purchase history.
- The wardrobe uses a compact search/filter row, category chips, a collection menu and one grid toggle. The first-outfit checklist is collapsible after the first item. Availability badges remain; redundant owned badges are removed in the owned-only catalog.
- Outfit creation and saved outfit details share a tap-based clothing composition. Enlarged text switches to a labelled reading-order list. Lock, replace and details remain contextual. Source mode and weather rules remain explicit.
- Product detail → I bought it → review owned-item details/photo → explicit ownership confirmation → atomic move. The original Wish record stores purchase history and an owned-item reference; it disappears from active wanted lists. Related planned outfits switch to the owned item without losing their IDs. Repeated confirmation returns the existing item. Short-lived Undo returns the item and associated outfits to planned state; changed items, recorded wear and category conflicts reject stale Undo without deleting data. Initial requirements, budget, price, currencies and link remain accessible from owned-item details.
- Network loss shows a non-blocking banner inside a remembered account. Changes remain local; no cloud sync is promised. On a new launch, an existing SDK identity is checked before the account opens. Token invalidation, sign-out, cancelled attempts and account ownership checks continue to protect private data. Focused editors and detail screens hide bottom tab navigation and retain Back/unsaved-change protection.
- Room schema 4 adds only the empty-default ownedId field to Wish; migrations 1→2→3→4 are registered. ZIP backup format 5 includes ownership references; formats 1–4 remain readable. Restore remaps both wish and garment IDs and validates account/wardrobe ownership.

Executed: source/XML/localization and actual DAO SQL checks; production Java wardrobe, authentication, money/URL, outfit-selection and photo rules; real migration SQL; photo-result ownership and centring geometry. Kotlin/Compose/Room compilation, APK, Firebase, native ML, device layout, font scaling, TalkBack and human usability are still unverified. Six purchase-flow instrumentation tests and updated photo/backup/account tests are included for execution on Android. Details and update instructions are in wardrobe-ux-update-0.7.0.zip.

## Agreed direction

Wardrobe helps the user manage clothes they own, save things they want to buy, and build outfits. This revision supersedes the store interpretation in version 1.0.

- Remove the store, shopping bag, payment, checkout, ordering, delivery, returns and refund experiences.
- Remove one-time login codes and code-entry screens.
- First use and explicit sign-out open Login. An existing Firebase SDK session is checked before reopening the same account. A rejected or missing session requires login; there is no guest bypass.
- Use email and password as the proposed replacement authentication method. Password recovery uses an email reset link, not a typed code.
- Separate owned items from things the user wants to buy. Moving an idea into the wardrobe requires explicit confirmation that the user owns it.

The interactive prototype is a design concept: its authentication, image processing, sync and wardrobe persistence are simulated. Separate native Android implementation packages and their validation limits are recorded below. Prototype presentation preferences use local storage with a session-only fallback in restricted previews.

## Visual direction

The accepted direction uses a compact, clothing-first interface with warm neutral surfaces and charcoal text. Olive and Terracotta is the native default for devices without a saved choice. Six full colour combinations and five classic accents are available in Android 0.6.1 and later; existing choices are retained. The classic accent token tables below also document the older prototype. The large editorial heading is reserved for login; working screens use compact sans-serif headings. Neutral photographic backgrounds keep the interface accent separate from garment colours.

| Token | Light appearance | Dark appearance |
| --- | --- | --- |
| Canvas | #FAF8F5 | #222120 |
| Surface | #FFFFFF | #2B2927 |
| Main text | #292B2D | #F3F0EB |
| Secondary text | #686862 | #B6B2AC |
| Classic blue primary action fill | #38596C | #C1D9E5 |
| Classic blue primary action text | #FFFFFF | #22343D |
| Subtle surface | #EEEAE4 | #3B3732 |
| Error text | #9E302C | #FFB7AE |
| Error surface | #FFF0ED | #422D2A |
| Photo background, every palette | #EEEFED | #353634 |

### Appearance settings

**Happy path:** Login → Profile → Appearance → choose a colour scheme and appearance mode → see the change immediately → return to the wardrobe. The Ukrainian settings entry is **Профіль → Оформлення**; the screen heading is **Кольори й тема**.

| Palette / Ukrainian name | Primary fill, light / dark | Text on primary, light / dark | Selected surface, light / dark |
| --- | --- | --- | --- |
| Blue / Синя — default | #38596C / #C1D9E5 | #FFFFFF / #22343D | #E7EEF2 / #34434B |
| Sage / Шавлієва | #42634D / #BFD3BC | #FFFFFF / #223529 | #E9EEE6 / #344236 |
| Terracotta / Теракотова | #914C36 / #EFB49C | #FFFFFF / #45291E | #F6EAE4 / #49372E |
| Plum / Сливова | #71536F / #D8BFD9 | #FFFFFF / #372738 | #EFE8EF / #423545 |
| Graphite / Графітова | #484B4F / #D1D4D8 | #FFFFFF / #282B2F | #EBEDEF / #393C40 |

The palette is independent of **Як у системі / Світлий / Темний**. The native default is Olive and Terracotta + System; the older prototype uses Blue + System. Each palette row has a name, a short description, two swatches and a native radio control; its entire labelled row is a touch target. Selected state uses the checked control and a border as well as colour. Swatches adapt to the active light/dark appearance. Keyboard arrow keys change the radio selection while retaining focus. At large text sizes, swatches move below the label rather than compressing its text.

Apply changes immediately, without loading or a separate Save step. Back keeps the chosen setting; choosing the previous palette reverses it. A View wardrobe button makes it easy to inspect the result using existing clothes. The Profile row shows the current palette and mode. Theme changes retain garment records, their colours, drafts, outfits, search context and ownership states. Informational panels use neutral surfaces; the accent is concentrated on primary actions, selection, focus and active navigation. Error red retains its meaning in every palette, and messages continue to use text rather than colour alone.

Store only the palette identifier and appearance mode as a device-level presentation preference. Apply it before the first Login render and retain it on sign-out; it does not remember authentication or expose an account. System mode follows the platform appearance, including changes while the app is open. Explicit Light/Dark mode takes priority. Unknown values recover to defaults; malformed or unavailable storage must not block opening or changing appearance. A failed write keeps the selection for the current session and explains its temporary scope.

The web concept uses the versioned key `wardrobe.appearance.v1` when browser storage is available. Its sandboxed conversation preview blocks that storage, so the preference lasts for the current preview session; the external prototype footer states this. Android preference persistence and startup application are now implemented in the 0.3.0 source package described below, but have not been compiled or run on Android in this environment. No account-level theme synchronization is implied.

Use at least 48 × 48 dp touch targets in the Android implementation (48 CSS px in this web prototype), restrained rounded surfaces, labelled navigation and visible focus. Owned, wanted and need states have text labels. Switch the wardrobe between two and three columns without changing the filters. A uniform product-image area keeps the full garment visible. The current prototype uses garment symbols as temporary image placeholders; production uses the user’s actual final edited photos. Photo processing is simulated in the concept. At enlarged text sizes, the grid, collage and forms reflow to one column; navigation reflows so all labels remain available.

## Navigation and opening behaviour

### Android implementation status — 0.3.0

The package `wardrobe-design-update-0.3.0.zip` ports the visual system to the existing local Kotlin/Compose app, reconstructed from source 0.1.0 and the management 0.1.1, photo 0.2.0 and editor 0.2.1 updates. It includes the complete source and a guarded contextual update for existing 0.2.1 projects.

Implemented in source: all five palettes; independent System/Light/Dark mode; a dedicated **Налаштування → Оформлення** screen; device-level Preferences DataStore; initial preference loading before the main app content; queued writes and a retryable write-failure state; neutral photo surfaces and full-garment image fitting; two/three-column catalog layout; saved catalog view state during navigation; larger-font grid/navigation reflow; updated typography, shapes, system bars and all three language resources. The local shopping tab is renamed **Хочу купити**.

This native branch still has no account authentication and opens the local wardrobe. Login-first remains the approved requirement for the account-enabled version; no simulated login has been introduced into the real app. The prototype's wider flow refinements are not all ported by this design update. Room schema, application ID, photo processing, matching and backup format remain unchanged; the new theme preference is not included in the wardrobe ZIP backup.

Completed checks: 814 source/SQL checks, management SQL checks, 19 existing production Java photo/geometry scenarios and 120 production colour pairings (minimum checked text contrast 4.66:1). The update data passed clean-baseline, repeat, CRLF, unrelated-edit and conflict checks. Four new JVM theme tests and two Android persistence tests are included but unexecuted. PowerShell execution, Kotlin/Compose compilation, Gradle/JUnit, Android UI, TalkBack and APK generation remain unverified because the required build environment is unavailable. Native source changes must not be presented as a tested APK or the web preview as an Android screenshot.

### Android authentication implementation status — 0.4.0

`wardrobe-auth-update-0.4.0.zip` is an isolated source update based on 0.3.0, leaving that UI-testing package unchanged. It includes complete native source, a guarded contextual updater and Firebase setup instructions. Source changes add Firebase Email/Password sign-in, registration, hosted password-reset links, login-first opening, loading, validation, network/rate-limit errors, cancellation, same-account reauthentication and confirmed sign-out. The provider is not configured in this environment: `app/google-services.json` and server-side Email/Password enablement are still required. Missing configuration displays a blocked Login screen, with no guest bypass or simulated credentials.

Account-local storage uses a stable hash of project ID and Firebase UID. Each account has separate Room, photos, cache and preferences. Existing 0.3.0 data requires explicit ownership confirmation and remains in its original files; a durable registry prevents assignment to another account and fails closed if malformed. Starting empty retains the unassigned legacy data. Device-level theme/language are shared, and backups contain only the opened account's data. Cloud synchronization is not included.

SDK requests are serialized. Cancellation invalidates UI acceptance and signs out a late result; another request waits until the non-cancellable SDK task settles. Cancellation cannot undo an account already created or an email already sent. Passwords are never saved in the app's preferences, saved-state bundle or logs. Native autofill semantics and first-invalid-field focus are included. Foreground token validation hides the private UI after a detected invalid session; an authenticated local session remains usable during a network outage. Reauthentication of the same UID retains the current task; switching accounts discards its unsaved UI state. Temporary per-account cleanup is serialized before reopening that account.

Executed checks: 38 assertions against production Java validation/identity/request-gate code, structural/resource/SQL checks (240 localized keys), management SQL, 19 existing production photo/geometry scenarios and 120 production colour pairs. Contextual-package checks cover clean 0.3.0, repeated application, CRLF, unrelated edits, preserved client config and conflict rejection. One JUnit wrapper and six Android account-storage tests were added but not run through Gradle/Android. Kotlin/Compose compilation, PowerShell execution, device UI/TalkBack, APK and live Firebase/email flows remain unverified. This is an integration source package, not a tested production build.

### Target product navigation

| Screen / tab | Purpose | Primary action |
| --- | --- | --- |
| Login | First use, explicit sign-out or rejected session | Log in |
| Wardrobe | Browse and manage clothes already owned | Add a piece |
| Want to buy | Save future purchases and ideas | Add something I want |
| Outfits | Create combinations using clear ownership rules | Create my outfit |
| Profile | Account, appearance and privacy controls | Manage settings / sign out |
| Appearance, within Profile | Five palettes and independent System / Light / Dark mode | Choose a scheme; preview in the wardrobe |

The bottom navigation is hidden throughout login, registration and password recovery. Successful login opens Wardrobe. A new account opens an empty wardrobe. Signing out returns to Login and clears navigation history; Back must not reopen private screens. Resuming an already open app preserves the current task; a new app opening checks a remembered SDK session and reopens its account; otherwise it starts at Login. Version 1.7 adopts remembered SDK sessions as the product decision; invalid sessions and explicit sign-out still require login.

## 1. Login, registration and recovery

**Happy path:** Open app → Login → email + password → signing in → Wardrobe.

| State | User experience | Recovery |
| --- | --- | --- |
| Login | Email and password; show/hide password; Log in, Create account, Forgot password | No code delivery or entry step |
| Empty / malformed fields | Field-specific messages after submission | Keep email; focus the first invalid field |
| Incorrect credentials | “Check your email and password, then try again.” | Avoid identifying whether the account exists |
| Signing in | Announce progress, prevent duplicate submissions | Cancel returns to Login; ignore late responses from cancelled attempts |
| Network error | Explain that sign-in could not complete | Retry without clearing email |
| Rate limit | Explain temporary retry restriction using server timing | Do not invent a fixed timeout |
| Successful login | Open wardrobe or restore a protected task after reauthentication | Clear credentials from UI memory |
| Create account | Email and new password with visible requirements | Reject missing/weak fields and handle existing-account responses safely |
| Registration success | Empty wardrobe with “Add my first piece” | No unsolicited extra onboarding pages |
| Forgot password | Request an email reset link | Generic response whether or not an account exists |
| Reset link | Open a new-password screen | Expired/used link offers a fresh request |
| Reset complete | Return to Login with confirmation | Use the new password; revoke old reset links |
| Session expired | Preserve draft and explain the need to log in again | Restore the interrupted task after successful authentication |
| Sign out | Resolve unsynced work first; clear tokens and private cache | Return to Login |

Proposed new-password rule in this concept: at least 12 characters, allowing spaces and password managers. Do not trim passwords. Permit paste and autofill. Backend authentication and account-recovery safeguards must be implemented independently of this visual prototype.

## 2. Owned wardrobe

**Happy path:** Wardrobe → Add a piece → Camera/gallery → Crop/rotate → Optional image processing while entering details → Save → Wardrobe with a short success message and Add next / Open actions.

For a new wardrobe, show three useful starting categories: top, bottoms and shoes. A compact checklist derives progress from actual ready-to-wear owned items. The next action requests the first missing category and preselects it in the item form. After all three categories are available, offer Create first outfit; after saving that complete outfit, dismiss the checklist. Laundry and archived items do not satisfy readiness. This gets the user to a first simple outfit without completing a full inventory or a style questionnaire.

Successful saves return to the relevant collection. Keep the confirmation inline, with Add next and Open actions and an explicit dismiss control; do not require another success-screen acknowledgement. A second form action, Save and add next, starts a fresh item after saving and announces what was saved. Cancelling the new draft does not undo the previous save. Preserve active filters and let Open reach a newly saved item even if filters hide it.

The working wardrobe screen has visible search, category chips, expandable colour/season/sort filters and a two-/three-column switch. Search and filters combine. All-season pieces stay eligible for warm/cold season filtering. Show a clear no-results state with a reset action. Keep the selected layout during subsequent browsing.

| State | Interface and recovery |
| --- | --- |
| Empty wardrobe | “Start with one favourite.” and Add my first piece |
| Camera permission | Request only when needed; denial offers gallery |
| Gallery | Use the platform picker without requiring full-library access |
| Unsupported/unreadable photo | Explain format/size issue; select another without losing draft details |
| Editor | Crop, rotate 90°, reset; preserve source temporarily while editing |
| Processing consent | Explain upload purpose and temporary image handling; allow manual entry |
| Processing | Keep the details form editable; announce progress without a fabricated percentage. Save stays disabled until a usable photo is ready or the user explicitly chooses the current background |
| Background removal failure | Keep edited photo; retry or explicitly save with background |
| Recognition uncertain/failed | Editable suggested category; manual selection always available |
| Missing category | Inline error; Other is an acceptable option |
| Save failure | Preserve final photo and metadata as a local draft; retry |
| Leave dirty editor | Keep editing / Save draft / Discard changes |
| Success | Return to collection with inline confirmation and Add next / Open; no mandatory acknowledgement page |
| Remove clothing | Explain impact on outfits; provide undo |

Owned-item data: stable ID, final image, category, availability, optional editable name, colour, brand, size, season and notes. Search and filter by the user's own collection. Do not require a retailer link.

### Concurrent photo processing and correction

The user can type while photo processing runs. Completion must retain entered text, manually selected category, keyboard focus and caret position. Apply recognition only to empty fields and show the suggestion separately. A later result from a cancelled or replaced photo job must not change the current draft.

Photo review offers Before / After and Correct crop. Returning to the editor preserves all metadata; the user can rotate, crop and process again. A saved unfinished draft pauses an active job and offers Resume processing / Keep current background when reopened. Background-removal failure blocks ambiguous saving until the user retries or explicitly chooses the existing background; recognition failure alone allows manual category selection.

### Availability and archive

| State | Collection behaviour | Outfit behaviour |
| --- | --- | --- |
| Ready to wear | In active wardrobe | Eligible for new suggestions, replacement and manual selection |
| In laundry | Visible in active wardrobe and the In laundry view with a text label | Excluded from new outfits; existing outfits show its unavailable state |
| Archived | Visible only in Archive | Excluded from new outfits; saved outfit history retains the reference |

Item details provide Mark in laundry / Return after washing, Archive and Restore actions. Archiving explains its impact before confirmation. Every availability change offers Undo. Restoration preserves the ID, wear count, image, notes and outfit links. An unavailable item cannot be a new outfit anchor. If a previously locked piece becomes unavailable, regeneration explains its replacement while preserving other eligible locks.

**Photo lifecycle:** temporary original → editing draft → final image. On successful save or discard, delete the original and obsolete intermediate files. An unfinished local draft may temporarily retain the source to support further cropping; this is not permanent retention. Original photos must not enter backups or exports.

## 3. Want to buy

**Happy path:** Want to buy → choose Product or Need → name + category → optional details → Save → preview with owned clothes.

**Product** is a specific thing the user has found: optional reference photo, product URL, estimated price and notes. **Need** is a description of what to look for: optional budget and requirements; no photo or retailer URL is required. Both records live in the same Want-to-buy list with distinct labels. Needs are conceptual, not actual owned garments. When used as an outfit anchor, show them explicitly as a requirement/placeholder.

| Field | Requirement |
| --- | --- |
| Item name | Required, nonblank |
| Category | Required; Other is available |
| Photo / screenshot | Optional; can be added or replaced later |
| Product link | Optional; valid http/https URL if supplied |
| Estimated price and currency | Optional; nonnegative numeric amount; no live pricing implication |
| Why I want it | Optional notes, such as a missing layer or replacement |
| Size / colour / priority | Optional future refinements |

A user can save an idea from a description alone. Link retrieval must never block manual saving. No automatic retailer catalogue or purchasing integration is implied.

### “Found a candidate” transition

Need detail → Found a candidate → optional photo / URL, product name and estimated price → Save → same wishlist record becomes a Product.

Preserve the stable record ID. Store the original need name, requirements and budget separately and show them in the product form and detail. A product price must not overwrite the original budget. Cancelling leaves the original Need unchanged; saving must not create a second record. Existing outfit references use the same ID and update their label. A later I bought it action keeps this history when moving the record to owned. The current flow associates one selected candidate with the need; comparison of multiple candidates is outside this revision.

| State | Behaviour |
| --- | --- |
| Empty list | Explain that a photo, link or simple description can start an idea |
| Validation error | Mark the relevant field; retain all correct values and photo |
| Photo unavailable | Save the idea with a placeholder; allow later replacement |
| Link unavailable | Preserve the typed URL and manual metadata |
| Save in progress | Disable duplicate save; avoid duplicate records |
| Save failed/offline | Retain a draft locally and show sync status accurately |
| Save success | Return to Want to buy with inline confirmation and Open / Add more actions |
| Edit/cancel | Keep editing or discard the edit; original saved idea remains intact |
| Duplicate idea | Warn with matching item and let the user choose |
| Remove | Remove from the list; offer undo |

### “I bought it” transition

Wish-list detail → I bought it → confirm that item is now owned → review/complete wardrobe details → move to Wardrobe → success with Undo.

This is a record-status change only. It does not initiate or track a purchase. Keep existing photo, name, category, source link and notes. Allow the user to replace a reference image with a photo of the actual item. If an owned-item photo is still missing, prompt for one before finalising or mark the record explicitly as needing a photo. Perform the move atomically so retry cannot create duplicates. Undo returns the item to Want to buy and reverses the ownership change. Related outfit labels update with ownership.

## 4. Outfits

**Happy path:** Choose occasion and weather → choose source → generate → review → save.

| Mode | Rules |
| --- | --- |
| Only my wardrobe | Use only available owned clothes |
| My wardrobe + Want to buy | Mix owned pieces and wish-list ideas; label each ownership state |
| General inspiration | Label examples as ideas; never imply those items are already owned |

Use manual weather selection without requiring location permission. Explain why combinations work. When a result contains a wanted item, offer viewing its saved idea. Do not introduce a purchase button.

**Anchor and selective replacement:** The item-detail action “Create an outfit with this piece” opens the builder with that specific item selected and locked. Users can lock/unlock any category. “Replace” changes only the selected unlocked category. “Refresh unlocked items” must preserve all locked IDs. The result labels each item as Owned, Want to buy or Need. The user can switch to a manual builder and save partial outfits. If an owned-only mode is selected after a wanted anchor, remove the ineligible anchor explicitly and explain why.

**Interactive collage:** Show the outfit together in a compact composition. Every piece is a labelled button. Selecting one reveals contextual Lock / Unlock, Replace and Details controls beneath the composition. Display a lock icon plus an accessible state; never depend on the icon or colour alone. Disable Replace while the piece is locked or no alternative is available and explain why. No drag gesture is required. At large text sizes, stack pieces in reading order. Distinguish Ready to wear, Planned, Incomplete and Needs replacement states. Wear-the-outfit logging is available only when every included piece is owned and ready.

**Daily wear log:** “Wore today” adds one wear event per item and local calendar day. Repeating the same action on an outfit must not double-count a piece already logged for that date. A single-item action lets the user undo today’s mark. Actual garment wear counts are shown in item details; most-/least-worn sorting uses this history. Full analytics, cost-per-wear dashboards and calendar planning are deferred. In this prototype, a Set represents today within one session; production requires date-keyed records using the user’s timezone.

Loading preserves settings and allows cancellation. Not enough clothing offers adding pieces or general inspiration. Service failures offer retry and a manual outfit builder. Manual outfits must include at least one item. Deleted or unavailable pieces are identified in saved outfits and can be replaced. A saved outfit using wanted items remains clearly a planned outfit until all items are owned.

## 5. Cross-cutting requirements

- Offline: previously authenticated sessions can use cached collection data and edit local drafts; a fresh login still needs authentication. Explain the distinction on the login screen when offline.
- Sync: queue edits, surface pending/failed sync, and resolve two-device conflicts explicitly.
- Back/cancel: preserve entered information; protect unsaved edits and prevent navigation back into a signed-out account.
- App restart: protect local drafts until reauthentication and restore them afterward.
- Accessibility: labelled controls, visible focus, keyboard operation, screen-reader status announcements, enlarged text and reduced motion. Minimum 48 × 48 dp targets on Android; support 320 CSS px width and 200% text in the prototype without horizontal overflow. Associate errors with inputs and focus the first invalid field. Restore keyboard focus after collage selection and replacement; background photo completion must not move focus away from the edited field.
- Validation: associate inline messages with fields, focus the first error and preserve valid values. Never rely on colour alone.
- Privacy: optional cloud processing, final-photo-only retention, and transparent draft handling.
- Account deletion: explain scope, reauthenticate using password, confirm deletion; no typed email/SMS code. Distinguish requested deletion from completed deletion.
- Localization: English, Ukrainian and Polish are intended production languages. The current concept is in Ukrainian; price notes use PLN as an example.

## Prototype coverage and limits

The Ukrainian interactive prototype opens on Login. A control outside the phone fills demo credentials, after which the normal Login button proceeds through a loading screen. Eighteen external scenarios include appearance settings, first-use progress, concurrent photo processing, the collage, conversion from Need to Product, laundry, archive, authentication recovery and error states. A separate preview-only text-size control shows 100%, 150% and 200%. These review controls are not part of the app; selecting a prepared first-use scenario replaces demo data to make that state reviewable. The Appearance review scenario gives a direct shortcut to the new settings; normal product access is through Profile after login.

Within the session, the prototype maintains item records, a resumable draft and saved outfits. Search, combined filters, layout changes, validation, edit-discard, ownership moves, undo, collage selection, locks, manual outfits and daily-wear simulation respond to interaction. This revision adds the adaptive first-outfit checklist, concurrent photo-processing simulation, photo-review correction loop, quick consecutive saves, Need-to-Product conversion without duplicate IDs and reversible laundry/archive states. Duplicate saved combinations and repeated wear logging are handled.

Loading, authentication, password-reset email, camera capture, image processing and sync are simulated. Login and outfit loading retain Continue preview controls. Photo processing automatically completes after a short demonstration delay, and saves finish automatically. Outfit combinations use local sample data and demonstrate control behaviour; they are not real style/weather recommendations. Item images remain temporary symbols; the collage demonstrates interaction and placement, not final photographic styling. No photos, credentials or wardrobe data are sent to a service. Wardrobe records do not persist durably. Only presentation preferences use storage when available, with the session-only preview limitation described above.

Production integration must add server authentication and authorization, task restoration, durable drafts, date-keyed wear logs, actual image processing, idempotent saves/moves, conflict handling, mobile lifecycle behaviour and accessibility verification. Marketing copy, product prices and visual placeholders in the concept are sample content.

## Acceptance checks

1. First use and explicit sign-out render Login. A remembered SDK identity is checked before private screens open, and no guest entry exists.
2. Login uses email/password; no code-request or code-entry screen remains.
3. Payment, orders, shipping and checkout are absent from product navigation and flows.
4. Successful login opens Wardrobe; logout cannot be reversed with Back.
5. Wanted items remain separate from owned items until explicit confirmation.
6. A name and category are enough to save a wish-list idea; optional fields validate only when populated.
7. “I bought it” updates ownership exactly once; Undo restores the wish-list item.
8. Owned-only outfits never include wanted or generic example items.
9. Recognition failure and denied camera permission still allow manual progress.
10. Saved clothing retains only the final photo; errors and cancellations preserve or discard drafts according to the selected action.
11. Loading, cancellation, errors and success have distinct, truthful messages.
12. Final UI passes mobile layout, keyboard, screen-reader and contrast checks.
13. Search, category, colour and season filtering combine correctly; a reset recovers from no results.
14. Two-/three-column switching keeps the same filtered records.
15. An anchor remains unchanged during reshuffle; replacing one category leaves the others intact.
16. Product and Need entries preserve their distinct fields and labels through editing and cancellation.
17. Repeated wear logging for the same day does not inflate counts.
18. Saved outfit ownership labels update after a wish-list item moves to owned or the move is undone.
19. Photo completion preserves user input, a manual category and focus. Cancelled jobs cannot modify another draft.
20. Re-cropping retains metadata. A paused draft offers explicit processing recovery; a failed background removal offers retry or the current background.
21. Creation Save completes to the collection; editing Save returns to the originating detail. Save and add next starts a fresh draft only after successful save. Repeated completion cannot duplicate a record.
22. Need-to-Product conversion preserves the record ID, original requirements and original budget. Cancel leaves the Need intact; a later ownership move retains its history.
23. Laundry and archived items are absent from new outfit candidates and manual selection. Existing outfit references remain visible with availability labels.
24. Archive / restore and laundry / return support Undo without changing wear counts or IDs.
25. The first-outfit checklist reacts to actual available categories and requests the missing piece. It completes after saving the first complete owned outfit.
26. All collage actions work with taps and keyboard. Layout and labels remain usable at 200% text; visible interactive targets meet the prototype's 48 × 48 CSS px minimum.
27. Five named palettes are available from Profile → Appearance. Selection updates all product screens immediately without resetting wardrobe data, navigation or focus.
28. Palette and System / Light / Dark mode are independent; explicit mode overrides the system, and System follows later platform changes.
29. A stored preference applies on Login and survives sign-out without retaining authentication. Invalid or unavailable storage recovers safely; a failed write leaves the current selection usable.
30. Theme radio rows are labelled, keyboard operable and large enough to tap. All palette text/action pairings pass their contrast thresholds in both appearances. Garment colours and error semantics stay independent of the chosen accent.

## Implementation order

**MVP**

1. Login-first navigation and email/password access, with reset links and meaningful loading/error states.
2. Owned wardrobe: add/edit/delete, crop/rotate, reliable background removal, editable recognition, final-image-only lifecycle and manual fallback.
3. Compact wardrobe browsing: search, category/colour/season filters, two-/three-column grid and recoverable drafts.
4. Want-to-buy: Product and Need records, optional photo/link/budget, edit/discard, move-to-owned and undo.
5. Controlled outfit building: anchor, lock/unlock, replace one category, refresh unlocked items, owned/mixed/inspiration modes and saved outfits.
6. Basic “Wore today” recording. Sync, accessibility and localization are quality requirements across the core flows.
7. The approved daily-use refinements in this revision: collage, concurrent photo form, quick repeated saves, Need-to-Product conversion, adaptive first-outfit checklist and reversible laundry/archive states.
8. Appearance settings: five curated palettes, independent system/light/dark mode and local preference persistence before the first Login render.

**Next release:** calendar, meaningful usage analytics, cost per wear after sufficient data, and travel capsules/packing lists. A later import enhancement may add batch photos and operating-system Share-to-Wardrobe; neither should delay a reliable single-item flow.

**Deferred:** social feed, resale, virtual try-on and stylist chat. Commerce remains outside scope.

## Moderated usability test protocol

Prepared for the next test with people using the app for the first time. This protocol has not yet been run with human participants. Browser automation and accessibility audits below are separate engineering checks.

Use the prototype on a phone, with a fresh wardrobe for Task 1 and reset demo data for later tasks. Ask the participant to think aloud. Give the task goal without naming the exact button. Record the first attempt before helping; use the same help rule for each participant. Allow about 20 minutes per session.

| Task to read to participant | Observe | Completion criterion |
| --- | --- | --- |
| “You have a favourite top, trousers and shoes. Add them and save an outfit you could wear.” | Whether the starting checklist is understood; repeated-entry friction; discovery of the first-outfit action | Three records and a saved outfit, with no unintended duplicates |
| “While this photo is being prepared, name the item and correct its category. Check the result and change the crop.” | Whether the form is usable during processing; trust in recognition; confidence about the saved photo | The intended name/category survive processing and re-editing |
| “Keep these shoes in the outfit, then find a different top.” | Whether selecting a piece, locking it and replacing another piece are understood | Shoes unchanged; only the requested top changes |
| “You were looking for a rain jacket with a budget of 350 zł. You found one for 299 zł. Save it, then mark it as bought.” | Need-versus-Product understanding; discovery of conversion; whether budget and price are confused | One record moves through the stages, preserving the original need and budget |
| “This shirt is in the wash. Build another outfit. Later, archive the shirt and bring it back.” | Availability labels, exclusion from suggestions, archive versus deletion, restoration | The shirt is excluded while unavailable and restored with its history |
| “Photo processing failed. Finish adding the item without starting over.” | Whether retry and keeping the background are understandable; whether data loss is feared | A deliberate recovery choice and one saved item with retained details |

For each task record independent completion / completion with help / incomplete, elapsed time, mis-taps or wrong turns, abandoned attempts and the participant's explanation of the result. Ask one neutral follow-up: “What, if anything, was unclear?” Collect a 1–5 confidence rating. Do not fabricate target completion times before observing a baseline.

Stop and revise any issue causing data loss, a misleading ownership state, an unexpected unavailable item in an outfit or an inaccessible essential action. Use recurring hesitation to prioritise the next wording or navigation revision. Separately run TalkBack, system font scaling, real camera/gallery permission and app-restart checks on the Android build.

## Revision validation

Validated on 24 September 2026:

- All 17 prepared scenarios render, including first-use progress, concurrent processing, the collage, need conversion, laundry, archive and the existing recovery states.
- Twelve interaction groups passed: login/validation; search context/grid; concurrent photo input, crop and quick save; cancellation of late photo results; paused draft recovery; processing retry and add-next; recognition/save recovery; need conversion and ownership undo; collage locks/replacement and duplicate protection; laundry/archive/restoration; first-outfit completion; reauthentication and sign-out.
- Browser interaction checks confirmed that automatic photo completion retains edited text, a manually chosen category and input focus. Quick save, first-outfit completion and need-to-product-to-owned flows passed with real DOM interaction.
- All 48 layout/target checks passed: 8 representative screens × 3 widths (320, 390 and 736 CSS px) × 2 text scales (100% and 200%). Checked fields, controls and headings had no horizontal overflow; visible interactive controls met the 48 × 48 CSS px target. No JavaScript errors were recorded.
- Axe automated checks reported zero violations for WCAG 2 A/AA and 2.1 AA rule tags on login, collage, need conversion, photo failure and wardrobe screens. This is a limited automated audit, not certification of full accessibility.
- Collage, first-use checklist and need-conversion screenshots were visually inspected. Light/dark outfit previews were generated; the mobile collage and contextual controls preserve readable labels and explicit selection.

These checks validate the local UX prototype. Real authentication, email delivery, camera permissions, image processing, synchronization, persistent data and TalkBack behaviour still require implementation and validation on the Android build. The human usability-test protocol above is prepared; no human-participant results are claimed.

### Theme revision validation — 29 September 2026

- Browser interaction checks passed for login-first behaviour, Profile → Appearance navigation, immediate palette/mode switching, arrow-key selection with retained focus, system appearance changes and an explicit override. Palette changes retained the visible wardrobe records and garment-placeholder colours; the chosen palette also remained active on Login.
- All 140 computed contrast pairings passed across five palettes × two modes: main/secondary text on four surfaces, primary button text, selected icon, navigation, link and error pairings. The lowest checked body-text contrast was 4.66:1, above the 4.5:1 threshold. Primary button text ranged from 6.38:1 to 9.56:1. The selected-icon threshold was 3:1. This token check is not a claim that every possible production photograph or overlay has been audited.
- The settings page passed six layout/touch-target combinations: 320, 390 and 736 CSS px × 100% and 200% text. No tested element overflowed; labelled radio rows and other interactive controls met the 48 × 48 CSS px effective-target requirement.
- Axe reported zero violations for WCAG 2 A/AA and 2.1 AA tags on all ten palette/mode settings combinations, plus representative Login, Outfit and photo-failure screens. No JavaScript page errors were recorded. These are automated prototype checks, not full accessibility certification.
- Preference-storage tests passed for storing only the two presentation choices, restoring them while still opening on Login, unknown identifiers, malformed JSON, blocked storage and a failed write that retains the session selection. Durable storage was tested with an isolated storage adapter; the actual inline preview correctly uses the blocked-storage fallback.
- Settings screenshots in light and dark appearance and a Terracotta wardrobe screenshot were visually inspected. Native Android persistence, real system appearance changes, TalkBack and preference research with users remain untested.
