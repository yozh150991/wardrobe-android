---
paths:
  - "app/src/main/java/com/familywardrobe/app/photo/**"
  - "app/src/main/java/com/familywardrobe/app/rules/Photo*.java"
  - "app/src/test/java/com/familywardrobe/app/rules/Photo*.java"
  - "tools/photo_stand/**"
---

# Photos and background removal

- The pipeline: PhotoAnalyzer (ML Kit labels, then subject segmentation) → PhotoCutoutRules.refine → PNG. Failures
  are logged under the logcat tag `WardrobePhoto` with their stage; never log pixels or file paths.
- The cutout must never damage the garment. Rules after the model may only remove what strong evidence says is not
  the garment; when in doubt, keep.
- The ML Kit mask on a real phone is almost binary and can overshoot 0..1 (seen −0.17…1.21): values within 0.5 of the
  range are saturated, anything else rejects the mask.
- Tune thresholds on real captures, not on guesses: a debug build writes source, mask and cutout to `photo-debug/`
  (docs/releases/MASK-CAPTURE-0.14.63.md); `tools/photo_stand/PhotoStand.java` runs the production rules on such a
  folder. The owner's photos never go into the repository — tests are synthetic scenes of the same shape.
- Every rule change keeps `PhotoCutoutCases` passing and is mutation-checked: remove the rule or its safeguard and
  see the matching assertion fail.
- Original photos are not kept permanently. Every FileProvider URI goes through `PhotoStore.sharedUri` with the
  application context; the provider exposes only `camera/` and `share/`.
