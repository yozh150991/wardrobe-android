# FAM-01/03 — Android initial family transfer, 0.14.2

16 October 2026. Code 32; Room and backup schema 10. Implementation is ready
for automated verification and the agreed joint native acceptance. This is
an initial server copy; invitations and continuous shared editing remain open.

## User flow

Settings → Family transfer → choose one wardrobe → explicit confirmation.
The archive includes its 16 scoped collections, final photos, purchase history,
custom categories and colors. Other wardrobes, account settings, migration
journal and temporary originals are excluded. Local content is never deleted
or rewritten. The existing private cloud continues to work independently.

The panel reports access checking, archive preparation, byte upload progress,
server validation and final verification. It retains attempts across restart,
provides Resume/check and explicit Prepare again. Account email verification
can be requested by a button. UA/EN/PL resources, themed controls, wrapped text,
48dp primary targets and polite progress/error accessibility announcements.

## Protocol and recovery

- `FamilyBackend`: Firebase Functions in `europe-west1`, Storage `putFile`,
  SDK authentication and `expectedActorUid` on every callable. The backend
  rejects an account switch even if the SDK retrieves another account token
  after the client started the call. No family Firestore writes from Android.
- `FamilyTransferEngine`: create → begin → commit probe → upload if missing →
  commit → resume. A probe recovers an immutable staged object after a lost
  upload reply without trying to overwrite it, including when the local ZIP
  has disappeared. Commit replay data is never treated as the current head.
- Durable `FamilyMigrationStore` and `FamilyArchive`: account-scoped final ZIP
  and journal, SHA-256/size/revision, fd sync and rename before STAGED. Existing
  v1 journals remain readable; size-zero staged foundation records need an
  explicit rebuild. Verified checkpoints cannot be reset into a new upload.
- Archive expiry is signaled with structured `UPLOAD_EXPIRED` details; a single
  automatic renewal rotates archive ID but keeps the family root and snapshot.
  `ARCHIVE_MISSING` alone permits upload. Configuration/access/validation
  failures are never interpreted as a missing object or expiry.
- Before accepting success: compare remote/wardrobe/owner, role, archive ID,
  path, hash, size, base=0 and revision=1, then check the local revision under
  the same write lock. New local edits remain on the device and show a warning.
  The revision is account-wide: unrelated wardrobe edits can conservatively
  trigger the warning too. No silent conflict resolution or automatic merge.
- Explicit Prepare again requires an empty server head and keeps the same
  family root. A competing commit still wins through server CAS; Android
  reports a conflict rather than overwriting that head.
- Sign-out cancels the ViewModel job and upload task. Guards check both the
  session lifetime and account before/after asynchronous calls. The DB is kept
  open until that job completes by the existing AccountSession close protocol.

Limits match the server: ZIP 128 MiB, expanded 256 MiB, manifest 8 MiB,
5000 photos, 12 MiB/photo, 8192px side and 16 Mi pixels. Server performs full
schema/reference/CRC/hash/photo decoding validation; client failures do not
trim content. Missing/damaged photos require user correction/rebuild.

## Deployment and remaining work

The updated callable backend is required alongside this Android client.
The old 0.14.1 backend rejects the added actor binding and does not expose
structured recovery reasons. Use the separate family config and follow
FAMILY-BACKEND-0.14.1.md. No live deployment, billing change or credentials
are included in this package. Preserve the existing client config/signing key.

FAM-01/03 stay partial. Next: distinct shared cache/DB and photo ownership,
exclusion from private/portable/scheduled backups, invite/accept/revoke/role
API and UI, shared synchronization with conflict resolution and lease/revoke
handling. The initial local wardrobe is still private content; it has not been
converted into a shared local editing model. No member data is imported here.

## Native acceptance (deferred together by user)

Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
`connectedDebugAndroidTest`, including `FamilyTransferStorageTest` and
`FamilyFoundationTest`. Test two wardrobes with disjoint photos/history, ZIP
limits and damaged photos, airplane mode, kill before/after each checkpoint,
lost Storage/callable reply, expired ticket, revoked/disabled auth, account
A→B→A during upload, local edit/private-cloud pull during commit, new server
head, repeated transfer, cancel/reopen and Prepare again. Verify no duplicate
root or false success, unchanged local content and backup isolation. Check
UA/EN/PL, 200% font and narrow screens. Live GCS preconditions and production
App Check/GC/cost gates from 0.14.1 remain outstanding.

Executed evidence is recorded in RELEASE-VALIDATION-0.14.2.json and
HOST-VALIDATION-0.14.2.json. Prepared native tests alone are not a pass.

## Reported 0.14.1 build fix

PhotoEditor captures BoxWithConstraints.maxHeight as availableHeight before
entering ColumnScope, fixing the Kotlin implicit-receiver error reported on
Windows. photo_brush_soft_hint is marked formatted=false in all four resource
sets because its 0%/100% values are literal text, not format substitutions.
The SDK XML warning is unrelated to that compiler failure.
