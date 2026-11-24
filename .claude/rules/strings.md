---
paths:
  - "app/src/main/res/values*/strings.xml"
  - "tools/*strings*.py"
  - "tools/check_consistency.py"
---

# Texts (UA / EN / PL)

- Four files change together: `values-uk`, `values-en`, `values-pl`, and `values/strings.xml`, which is a byte copy
  of `values-uk`. The strings are the source of truth: never regenerate them with `tools/make_strings.py`.
- Preferred way: add entries to a small idempotent script like `tools/qa_fixes_strings.py` (UPDATED / ADDED dicts)
  and run it; it rewrites all four files.
- Ukrainian addresses the user as «ти», with no gendered verb forms (not «зупинився», «вийшов»).
- One name per thing: образ / outfit / zestaw; the tabs «Сьогодні», «Гардероб», «Хочу купити», «Образи», «Профіль».
- A number follows its noun («Речей: 2»), except after «із» / of / z («2 із 3 речей»).
- Button names in running text are quoted («» / “” / „”) and must equal the real label.
- British English, typographic apostrophes (’).
- `python3 tools/check_consistency.py` guards all of this; run it after any text change.
