package com.familywardrobe.app;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Host/JUnit cases execute the same history, brush sizing and alpha painter as the editor. */
public final class PhotoEditorCases {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Photo editor check " + checks); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
    public static void history() {
        PhotoEditHistory empty = new PhotoEditHistory();
        check(!empty.canUndo() && !empty.canRedo() && empty.getCurrent() == null);
        check(empty.undo() == empty && empty.redo() == empty);
        PhotoEditHistory edited = empty.append("whole stroke A").append("whole stroke B");
        PhotoEditHistory undone = edited.undo();
        check(undone.getCursor() == 1 && undone.canUndo() && undone.canRedo());
        check(Arrays.equals(undone.getAppliedSteps(), new String[]{"whole stroke A"}));
        check(undone.redo().getCurrent().equals("whole stroke B"));
        check(undone.undo().getCurrent() == null && undone.undo().canRedo());
        // A pending/cancelled gesture and brush-size changes do not append a command.
        check(undone.canAppend("pending") && undone.canRedo());
        PhotoEditHistory branch = undone.append("replacement stroke");
        check(!branch.canRedo() && branch.getSteps().length == 2);
        check(branch.getCurrent().equals("replacement stroke"));
        check(edited.getCurrent().equals("whole stroke B") && edited.getCursor() == 2);
        String[] exported = undone.getSteps(); exported[0] = "external mutation";
        check(undone.getCurrent().equals("whole stroke A"));
        PhotoEditHistory restored = new PhotoEditHistory(undone.getSteps(), undone.getCursor());
        check(restored.canRedo() && restored.redo().getCurrent().equals("whole stroke B"));
        String[] incoming = {"a", "b"}; PhotoEditHistory copy = new PhotoEditHistory(incoming, 1); incoming[0] = "c";
        check(copy.getCurrent().equals("a"));
        PhotoEditHistory full = empty;
        for (int i = 0; i < PhotoEditHistory.MAX_STEPS; i++) full = full.append("stroke " + i);
        check(!full.canAppend("overflow") && full.getCursor() == PhotoEditHistory.MAX_STEPS);
        check(full.undo().append("replacement").getSteps().length == PhotoEditHistory.MAX_STEPS);
        PhotoEditHistory limit = full;
        rejects(() -> limit.append("overflow"));
        PhotoEditHistory textLimit = empty.append("x".repeat(PhotoEditHistory.MAX_CHARACTERS));
        check(!textLimit.canAppend("x") && textLimit.canUndo());
        check(textLimit.undo().append("small replacement").getSteps().length == 1);
        rejects(() -> new PhotoEditHistory(new String[]{"x".repeat(PhotoEditHistory.MAX_CHARACTERS + 1)}, 0));
        rejects(() -> new PhotoEditHistory(new String[]{"a"}, 2));
        rejects(() -> new PhotoEditHistory(new String[]{null}, 0));
        rejects(() -> empty.append(""));
    }
    public static void brush() {
        check(PhotoBrushRules.maximumDiameter(1024, 2048) == 164);
        check(PhotoBrushRules.maximumDiameter(2048, 1024) == 164);
        check(PhotoBrushRules.maximumDiameter(1, 1) == 1);
        for (int maximum : new int[]{1, 2, 4, 16, 64, 164, 640}) {
            check(PhotoBrushRules.diameter(0, maximum) == 1);
            check(PhotoBrushRules.diameter(1, maximum) == maximum);
            for (int size = 1; size <= maximum; size++)
                check(PhotoBrushRules.diameter(PhotoBrushRules.position(size, maximum), maximum) == size);
            int previous = 1;
            for (int p = 0; p <= 100; p++) {
                int size = PhotoBrushRules.diameter(p / 100f, maximum);
                check(size >= previous && size >= 1 && size <= maximum); previous = size;
            }
        }
        check(PhotoBrushRules.radius(1, 1024, 2048) == 1 / 2048f);
        rejects(() -> PhotoBrushRules.radius(0, 1024, 2048));
        rejects(() -> PhotoBrushRules.radius(165, 1024, 2048));
        rejects(() -> PhotoBrushRules.maximumDiameter(0, 1));
        rejects(() -> PhotoBrushRules.diameter(Float.NaN, 100));
        rejects(() -> PhotoBrushRules.position(5, 4));
    }
    private static int erased(int[] pixels) { int count = 0; for (int pixel : pixels) if ((pixel >>> 24) == 0) count++; return count; }
    public static void pixels() {
        for (int side : new int[]{1, 2, 3, 10, 41, 1024}) {
            int[] source = new int[side * side]; Arrays.fill(source, 0xff6b8194);
            for (float point : new float[]{0f, .25f, .5f, .75f, 1f}) {
                PhotoMaskRules.Stroke dot = new PhotoMaskRules.Stroke(point, point, point, point, PhotoBrushRules.radius(1, side, side), false);
                int[] result = PhotoMaskRules.paint(source, side, side, List.of(dot));
                int pixel = Math.round(point * (side - 1));
                check(erased(result) == 1);
                check(result[pixel * side + pixel] == 0x006b8194);
                check(source[pixel * side + pixel] == 0xff6b8194);
                PhotoMaskRules.Stroke restore = new PhotoMaskRules.Stroke(point, point, point, point, dot.radius, true);
                check(Arrays.equals(source, PhotoMaskRules.paint(source, side, side, List.of(dot, restore))));
            }
        }
        int side = 101;
        int[] source = new int[side * side]; Arrays.fill(source, 0xb46b8194);
        PhotoMaskRules.Stroke line = new PhotoMaskRules.Stroke(0f, .5f, 1f, .5f, PhotoBrushRules.radius(1, side, side), false);
        int[] result = PhotoMaskRules.paint(source, side, side, List.of(line));
        check(erased(result) == side);
        check(result[50 * side] == 0x006b8194 && result[49 * side] == source[49 * side]);
        float broad = PhotoBrushRules.radius(PhotoBrushRules.maximumDiameter(side, side), side, side);
        PhotoMaskRules.Stroke large = new PhotoMaskRules.Stroke(.5f, .5f, .5f, .5f, broad, false);
        PhotoMaskRules.Stroke tinyRestore = new PhotoMaskRules.Stroke(.5f, .5f, .5f, .5f, line.radius, true);
        int[] erasedLarge = PhotoMaskRules.paint(source, side, side, List.of(large));
        check(erased(erasedLarge) > 150);
        int[] restoredDot = PhotoMaskRules.paint(source, side, side, List.of(large, tinyRestore));
        check(erased(restoredDot) == erased(erasedLarge) - 1 && restoredDot[50 * side + 50] == source[50 * side + 50]);
        for (int i = 0; i < restoredDot.length; i++) if ((restoredDot[i] & 0xffffff) != (source[i] & 0xffffff)) throw new AssertionError("RGB changed");
        checks++;
        check(Arrays.equals(source, PhotoMaskRules.paint(source, side, side, List.of())));
        rejects(() -> new PhotoMaskRules.Stroke(0, 0, 0, 0, 0, false));
        rejects(() -> new PhotoMaskRules.Stroke(0, 0, 0, 0, Float.NaN, false));
        rejects(() -> new PhotoMaskRules.Stroke(0, 0, 0, 0, .51f, false));
        rejects(() -> PhotoMaskRules.paint(source, side, side, Collections.nCopies(2049, line)));
        try {
            PhotoMaskRules.paint(source, side, side, List.of(large), () -> { throw new CancellationException(); });
            throw new AssertionError("Cancellation swallowed");
        } catch (CancellationException expected) { checks++; }
    }
    public static void main(String[] args) {
        history(); brush(); pixels();
        System.out.println("PASS: " + checks + " photo editor assertions (history, redo branches, pixel brushes, alpha and cancellation)");
    }
}
