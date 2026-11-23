---
paths:
  - "app/src/main/java/com/familywardrobe/app/Data.kt"
  - "app/src/main/java/com/familywardrobe/app/data/**"
  - "app/src/main/java/com/familywardrobe/app/backup/**"
  - "app/src/main/java/com/familywardrobe/app/auth/**"
  - "app/schemas/**"
---

# Stored data

- A new persistent field needs: a Room migration (never destructive), the exported schema, backup encode/decode,
  the cloud copy, purchase history and outfit references. `tools/room_identity.py` recomputes Room's identity hash.
- Backup format is 10; 11 is written only when a capsule has trip details. Everything sent to a family server is
  encoded at `FamilyRules.CONTENT_VERSION` (10).
- Accounts are isolated, also in asynchronous work. A new per-account file or preference must be covered by account
  erasure: `tools/check_account_erasure.py` fails otherwise.
- Personal preferences (palette, rotation, weather city, feedback) live in account preferences and never go into
  backups or family copies.
- Validation that a form applies is applied again in the Repository (names, limits, uniqueness).
