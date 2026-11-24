---
name: texts
description: Add or change user-visible strings in Ukrainian, English and Polish. Use for any edit of strings.xml, a new label, error, hint or dialog text, or when a text is reported as wrong, unclear or inconsistent.
---

# Texts in three languages

The four `strings.xml` files are the source of truth and change together; `values/` is a byte copy of `values-uk/`.

1. **Find what exists.** Search `app/src/main/res/values-uk/strings.xml` for the wording and for the concept: reuse
   a string that already says it, and keep one name per thing.
2. **Write a script, not hand edits.** Copy the shape of `tools/qa_fixes_strings.py` to
   `tools/<release>_strings.py`: `UPDATED = {key: (uk, en, pl)}` (use `None` to leave a language as it is) and
   `ADDED = {key: (uk, en, pl)}`, each with a comment naming the finding. The script must be idempotent: run it twice
   and the second run changes nothing.
3. **Wording.**
   - Ukrainian: «ти», no gendered verbs for the user, the noun before a number («Речей: 2»).
   - English: British spelling, ’ apostrophes, “ ” quotes.
   - Polish: „ ” quotes; natural Polish, not a word-for-word copy of the Ukrainian.
   - Quote button and tab names exactly as their labels read. Say what happens, not how the app is built
     (no «чернетка», «запис», «синхронізація» where a plain word works).
   - Required fields end with « *»; optional ones say so («Назва — необов’язково»).
4. **Use the string** in code through `R.string.<key>`; remove strings that are no longer referenced only if no
   guard names them.
5. **Check.** `python3 tools/check_consistency.py`. If it flags a text that is right, add the key to the guard's
   exception set with a comment explaining why — never loosen the rule itself.
6. **Look at it.** A changed text on a main screen is visible in the screenshot tour in uk, uk-large, en and pl:
   after CI, use the `ci-review` skill and look for clipped or wrapped labels.
