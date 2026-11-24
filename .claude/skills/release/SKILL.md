---
name: release
description: Finish a code change in this repository as a release - version bump, guards, host checks, release document, CHANGELOG, roadmap, AGENTS state, commit, PR. Use when a fix or feature is implemented and has to be handed to the owner, or when he says "оформи", "зроби PR", "реліз".
argument-hint: "[short-name]"
---

# Release a change

A release is one topic in one PR. `$ARGUMENTS` is the short name (for example `cutout-cleanup`); choose one if absent.
Docs-only and harness-only changes skip steps 1 and 5–6 and keep the version.

1. **Version, before the first push.** In `app/build.gradle.kts` add 1 to `versionCode` and set
   `versionName = "<x.y.z>-<short-name>"` (patch number +1). Never change them again on a pushed branch.
2. **Proof for every fix.** A Java case in `app/src/test/java/com/familywardrobe/app/rules/*Cases.java` for rule
   changes, or a check in `tools/check_*.py` for UI and wiring. Prove a new check can fail: break the code once and
   see it fail. A new suite is added to the list in `tools/run_host_checks.py`.
3. **Texts.** If any string changed, follow the `texts` skill.
4. **Host checks.** Delete `release-evidence/host-runs/<version>-*.json` (and `git rm --cached` it if tracked), then
   `python3 tools/run_host_checks.py --allow-missing-client-config`. Every suite passes; only the auth-config suite
   may be skipped. Fix failures by fixing the code, or the guard together with a stated reason.
5. **Release document** `docs/releases/<NAME>-<version>.md`, in Ukrainian: the header line (date · Claude ·
   version / code · Room / backup format), the base (which PR it is on), what changed for the user, a table of
   changes with files, what is not done, and "Перевірки" stating exactly what ran. Model: the newest file there.
6. **Other documents.** A CHANGELOG entry at the top (Ukrainian, user-facing); the roadmap's «Актуальна контрольна
   точка» line and the task's row; "Current state" in AGENTS.md, moving the earlier text to the top of
   `docs/history/HANDOFFS.md`. Run `python3 tools/check_harness.py`.
7. **Commit and PR.** Commit as the session's attribution rules say; `git -c push.negotiate=true push -u origin
   <branch>`; open the PR with `gh api repos/yozh150991/wardrobe-android/pulls -X POST …`. The PR body says what
   changed, what was verified, and for a stack which PRs it contains.
8. **Project copy.** Write the release document to the claude.ai Project as `claude/<NAME>-<version>.md`.
9. **CI.** Follow the `ci-review` skill. Add the CI results to the release document's "Перевірки" (a second commit).
10. **Report** to the owner in Ukrainian: the result first, then what was verified and how (host checks, CI build,
    instrumentation, phone) and what was not, then what he has to do. If the phone must confirm it, give the local
    build commands from the `device-check` skill — never the CI APK.
