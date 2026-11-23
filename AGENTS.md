# Wardrobe — working rules for agents

Android app for a family wardrobe: items with photos, outfits, a daily suggestion, a wish list, shared family
wardrobes. Kotlin + Compose, Room, Firebase (auth, Firestore, Storage, Functions). Package `com.familywardrobe.app`.
The owner (Ihor) writes in Ukrainian; answer him in Ukrainian. Code, comments, commits and these files are English;
release documents, CHANGELOG and the roadmap are Ukrainian. The user's direct instructions outrank this file.

## Current state

0.14.64-cutout-cleanup / code 94, Room 11, backup format 10 (11 only when a capsule has trip details). Bug-fix phase:
fix what the owner finds on the phone, one small PR each. Open: «Очистити фон сильніше» re-analyses the already cut
photo and changes nothing (next after the harness); low findings QA-61-12–14, -16, -20, -22–24, -26.
Replace this section at every release; the previous text goes to the top of docs/history/HANDOFFS.md.

## Where things are

| Path | What |
| --- | --- |
| `app/src/main/java/com/familywardrobe/app/` | `rules` (pure Java rules), `ui`, `photo`, `data`, `outfits`, `family`, `auth`, `backup`, `cloud`, `importing` |
| root of that package | MainActivity.kt, TodayWidget.kt, Data.kt, BackupHealth.kt, CloudSync.kt, FamilySyncSchedule.kt — known by class name outside the code, never move or rename |
| `app/src/test/java/.../rules/*Cases.java` | Java cases run both by JUnit and by the host checks |
| `app/src/androidTest/` | instrumentation tests and the screenshot tour |
| `cloud/` | Firestore/Storage rules, `family-functions` (Python), emulator tests |
| `tools/` | host checks (`check_*.py`), string tools, CI helpers, `photo_stand/` |
| `docs/releases/` | one document per release (`NAME-version.md`) — write every new one here |
| `docs/guides/`, `docs/history/` | guides; old notes and docs/history/HANDOFFS.md |
| `WARDROBE-ROADMAP.md`, `CHANGELOG.md` | the backlog with statuses; the user-facing change list |

## Commands

- Host checks (no Android SDK needed): `python3 tools/run_host_checks.py --allow-missing-client-config` — every
  suite must pass (51 now); the auth-config suite is skipped without the owner's Firebase file. The run writes
  `release-evidence/host-runs/<version>-<time>.json`, which is committed: delete the earlier file of the same version
  (and `git rm --cached` it) before a new run.
- One suite: `python3 tools/check_<name>.py`.
- Android build, unit tests and lint run in CI only (no SDK in cloud sessions). Locally the owner runs
  `.\build-apk.ps1` on Windows.
- Push: `git -c push.negotiate=true push`. Open a PR with
  `gh api repos/yozh150991/wardrobe-android/pulls -X POST -f title=… -f head=… -f base=main -F body=@file`.
- CI status: `gh api repos/yozh150991/wardrobe-android/commits/<sha>/check-runs`.

## Never

- Deploy anything live (Firebase rules, functions) or tell the owner to run `cloud/deploy-rules.ps1`: with `-Deploy`
  it also deploys storage.rules, which lacks the family rules. Deploys are the owner's, with scoped commands.
- Bypass Git Safety (`--no-verify`, rewriting history, untracking a flagged file). Never commit keys,
  `google-services.json`, Firebase config or user data. Read docs/guides/GIT-SAFETY.md before touching the guard.
- Rename `versionName` while keeping `versionCode`, or change the code alone, once a branch is pushed: CI compares
  with the branch's previous push. Choose the final name before the first push, or bump both.
- Give the owner the CI preview APK for device checks: it is a separate `.ci` package without Firebase config, so
  sign-in does not work. He builds the branch locally.
- Use a destructive Room migration, or change a stored format without the migration, backup, cloud and family paths.
- Merge PRs. The owner merges; for a stack he merges the latest PR, which contains the earlier ones.
- Say "done" for something only checked by source guards. Say what ran: host checks, CI build, CI instrumentation,
  the phone — and what did not.

## How a change goes

1. Branch from main (or from the latest unmerged PR of a stack). One topic per PR.
2. A code change gets a new version: bump `versionCode` and give `versionName` a new suffix. Docs-only and
   harness-only changes keep the version.
3. Change the code; add or update a Java case or a guard in `tools/check_*.py` for every fix.
4. Run the host checks. Then write docs/releases/<NAME>-<version>.md, a CHANGELOG entry, the roadmap status and the
   "Current state" above; copy the release document to the claude.ai Project (`claude/<NAME>-<version>.md`).
5. Commit, push, open the PR, wait for CI. A failed "Android debug preview" that says the KSP plugin was not found is
   a network failure: rerun the job. For UI changes review the screenshot tour from the instrumentation job's log
   (docs/releases/SCREENSHOTS-0.14.59.md says how).
6. Report in Ukrainian: what changed, what was verified and how, what is still open.

Area rules load on their own when you open matching files: see `.claude/rules/` (strings, ui, photo, data, cloud).
