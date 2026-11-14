# FAM-01/03 — isolated server-copy reader, 0.14.5

18 October 2026. Android `0.14.5-family-reader`, code 35; Room/backup 10.
FAM-01/03 remain **partial**; this read-only slice is **verify** pending Android
compilation and device acceptance. Based on DEV-03 / photo hotfix3; all earlier
photo, family-progress and GitHub changes are retained.

## User flow and scope

Settings → Family transfer → an existing transfer attempt → View server copy.
The server must already have a committed head; an incomplete attempt explains
that transfer must finish first. Downloaded items are searchable by name,
location and description; photos use the existing zoom viewer. Counts include
outfits/wishes but their detail/edit screens are not exposed in this reader.
Actual server role is displayed, with explicit read-only behavior for all roles.
Refresh closes the old view and downloads the latest server copy. Closing while
loading requests cancellation. UA/EN/PL/fallback and narrow-screen wrapping.

This is a reader for remote IDs already known from the local transfer journal.
It does not discover wardrobes on a second installation or grant membership.
The transport accepts server-authorized owner/viewer/editor roles, but invitation,
member discovery and member-facing navigation still belong to FAM-02. Continuous
sync, shared editing and owner conversion from a private wardrobe remain open.
No new backend deployment or Storage rule changes are part of this package.
The existing family_resume callable (backend 0.14.2+) and allowed snapshot get
must work; this release cannot repair separately modified production rules.

## Isolation and protocol

1. Authenticate the originating UID and require verified email; call family_resume.
2. Validate the requested remote identity, revision, archive path, size/hash and role.
3. Download the SDK-authenticated stream with an enforced byte bound, cancellation
   checkpoints and no public download URL. Stage under noBackupFilesDir with
   account key, runtime session ID and random generation names.
4. Verify SHA-256/size, ZIP central directory/CRC, unique flat names, UTF-8 manifest,
   entry/count/expanded limits. Decode the existing FamilyEnvelope and all scoped
   relationships; require the exact referenced photo set. Validate MIME/dimensions
   and sampled image decode; the existing trusted backend performs full decoding.
5. Re-read authenticated head/access after extraction. A changed identity/head is
   a conflict and never installs. An editor→viewer change uses the new role. Access,
   validation, network or cancellation errors discard the staged candidate.
6. Publish only the verified immutable Snapshot and its separate PhotoStore.
   No Room write, restore/remap, private cloud update or editor is called.

All 16 scoped collections stay in the decoded snapshot; the current UI renders
items/photos only. A separate mutable shared Room DB is intentionally not introduced
for this read-only slice. Any later shared editing model must keep this boundary
and implement server CAS; do not route a shared Snapshot into the private Repository.
Identical garment/photo IDs in personal and shared copies cannot overwrite bytes.
Private full/single/scheduled/cloud backups read only the existing personal DB and
PhotoStore, while the shared files are also excluded from Android automatic backup.

ZIP limits: 128 MiB compressed, 256 MiB expanded, 8 MiB manifest, 5000 photos,
12 MiB/photo, max side 8192 and 16 Mi pixels. Failure never silently trims content.
Only one current read view is retained, to bound disk and memory use.

## Session/offline/revocation

The 15-minute monotonic lease starts **before** the final verification request,
not at the end of a slow download. It exists only in memory in the current session.
Within that lease a closed reader can reopen its cached view without network.
Explicit Refresh drops the previous lease even if the new request fails.
Expiry hides the screen and zoom, clears the lease and deletes cached files; the
UI additionally checks authorization on lifecycle resume before rendering content.
Signing out invalidates view state immediately; AccountSession waits for cancelled
work before clearing the separate photo memory and cache files. Previous process
files cannot grant access and are pruned on first download or session closure.
Revocation discovered by refresh closes the view. Offline revocation is bounded by
the lease, not claimed to be instant; there is no background membership listener yet.

## Verification

See RELEASE-VALIDATION-0.14.5.json and the release-specific host report.
The host runner executes production Java ZIP/lease tests (49 assertions), including
hash/size/CRC, traversal, duplicate/missing/unexpected entries, UTF-8, per-file,
count/expanded limits, cancellation, lease boundary/session switch/clock rollback.

Prepared, **not executed** here:
- FamilyReadEngineTest: 12 JVM cases for authenticated readback, role downgrade,
  spoofed ownership, revocation, changed head/identity, missing head, account switch,
  verification timeout and failed recheck. Included in normal Gradle unit tests.
- FamilyReadCacheTest: 3 Android tests for same-ID photo isolation, private backup,
  malformed/extra photos, cleanup and process/session invalidation.
- Compose/Firebase adapter compilation, lint, APK, physical device, live Firebase.

Build command stopped at the build script's full-JDK check: javac executable is
absent. Java 17 runtime compiler modules support the host Java suites; that does
not establish Android compilation. Do not claim a new APK or CI success.

## Joint native acceptance

Build `testDebugUnitTest lintDebug assembleDebug`; then `connectedDebugAndroidTest`.
Open a committed server copy with photo IDs matching private data; confirm private
edits remain private and archive/personal backup bytes unchanged. Test empty head,
empty/search-no-result views, stale remote head, failed download, cancel and rapid
retry; rotate during download and zoom. Test offline reopen inside lease, background
for 15+ minutes, process kill, account A→B→A, expired access and revocation on refresh.
In an isolated emulator dataset, test owner/viewer/editor/unrelated UID and role
change during download. Check large photos, 200+ items, 200% font, UA/EN/PL, dark and
custom palettes, TalkBack and existing personal photo zoom regression.

Next: FAM-02 membership discovery/invite/accept/decline/role/revoke API + UI;
then shared editing/CAS conflicts and FAM-04 multi-account acceptance.
