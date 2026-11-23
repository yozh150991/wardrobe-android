---
paths:
  - "cloud/**"
  - "app/src/main/java/com/familywardrobe/app/family/**"
  - "app/src/main/java/com/familywardrobe/app/cloud/**"
  - "app/src/main/java/com/familywardrobe/app/CloudSync.kt"
  - "app/src/main/java/com/familywardrobe/app/FamilySyncSchedule.kt"
---

# Cloud and family sharing

- The agent never deploys. A backend change is described for the owner with the exact scoped command
  (`firebase deploy --only functions:family --config firebase.family.example.json --project wardrobe-75fea`, or
  `--only firestore:rules`), never a plain `firebase deploy` and never `cloud/deploy-rules.ps1`.
- The client must work against the server that is deployed now: new behaviour is switched on by a feature flag the
  server reports (`change1`, `outfit1`, `colors2`), with the earlier path kept.
- Family access is per shared wardrobe through the backend; never open one account's archive to another UID.
- Changing `CloudEmulatorTest.kt` or `cloud/auth.test.mjs` needs a new hash in `tools/git_safety_allowlist.json` and
  `python3 tools/git_safety.py install`. When an allow-listed file changes in a stack of PRs, the owner merges the
  last PR, not one by one.
- Backend tests: `cloud/family-functions/tests`; emulator scenarios run in the CI job "Firebase emulators".
