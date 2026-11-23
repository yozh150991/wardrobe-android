---
paths:
  - "app/src/main/java/com/familywardrobe/app/ui/**"
  - "app/src/androidTest/**/ScreenshotTourTest.kt"
---

# Compose screens

- The app is edge-to-edge: anything drawn at the top of a screen must clear `WindowInsets.statusBars`.
- Choice chips are `PillChip` / `ChipChoice`, search fields are `SearchBox` (ui/FormControls.kt); one of several
  answers is a `RadioRow`; full-screen dialogs use `Surface(color = background)`.
- Nothing inside a sideways-scrolling row may use `Modifier.weight`. Tab labels stay on one line (shared `navScale`),
  never `maxLines = 2`.
- Touch targets are at least 48 dp. Check the large font (1.3) and all three languages: no clipped or
  horizontally cut text.
- Forms keep a draft (`vm.keepDraft` / `vm.draftFor`). State that must survive the process being stopped behind
  another app (the camera) goes into the draft, not only into `rememberSaveable`.
- Every earlier action of a screen must survive a redesign; the design guards (`tools/check_*_design.py`,
  `check_item_page.py`, `check_look.py`, `check_qa_fixes.py`) list them. Update a guard only together with the reason.
- A new screen or a changed one gets a step in ScreenshotTourTest (tags `nav-<screen>`; optional steps use
  `extra()`), and `tools/check_screenshots.py` lists it. Review the screenshots after CI.
