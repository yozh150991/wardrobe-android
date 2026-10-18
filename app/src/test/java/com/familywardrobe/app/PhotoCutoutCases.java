package com.familywardrobe.app;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Synthetic fixtures exercise production post-processing, not ML model accuracy. */
public final class PhotoCutoutCases {
    private static int assertions;
    private static final int W = 80, H = 80, RED = 0xffc92c45, WHITE = 0xfffafafa;
    private static void check(boolean pass, String message) { assertions++; if (!pass) throw new AssertionError(message); }
    private static final class Fixture {
        int[] rgb = new int[W * H];
        float[] mask = new float[W * H];
        Fixture() { Arrays.fill(rgb, WHITE); }
        void box(int l, int t, int r, int b, int colour, float c) {
            for (int y = t; y < b; y++) for (int x = l; x < r; x++) { rgb[y * W + x] = colour; mask[y * W + x] = c; }
        }
        int[] cut(boolean stronger) { return PhotoCutoutRules.refine(rgb, W, H, FloatBuffer.wrap(mask), stronger, () -> {}); }
    }
    private static Fixture garment() { Fixture f = new Fixture(); f.box(25, 15, 55, 66, RED, 1); return f; }
    private static int at(int[] p, int x, int y) { return p[y * W + x]; }

    public static void roundedConfidence() {
        Fixture f = garment();
        int[] expected = f.cut(false);
        int overshoots = 0;
        // Bilinear weights for a constant opaque region may sum just above 1 in float32.
        // This is a numerical fixture, not a captured ML Kit mask from the user's phone.
        for (int y = 15; y < 66; y++) for (int x = 25; x < 55; x++) {
            float fx = (x - 25) / 30f, fy = (y - 15) / 51f;
            float c = (1 - fx) * (1 - fy) + fx * (1 - fy) + (1 - fx) * fy + fx * fy;
            f.mask[y * W + x] = c;
            if (c > 1) overshoots++;
        }
        check(overshoots > 0, "Fixture must exercise real float32 rounding above one");
        check(Arrays.equals(expected, f.cut(false)), "Rounded opaque mask must not punch grid holes in fabric");
        check(Arrays.equals(expected, f.cut(true)), "Strong mode also preserves rounded opaque fabric");
        f.mask[30 * W + 30] = Math.nextUp(1f);
        f.rgb[30 * W + 30] = 0x78112233;
        check(at(f.cut(false), 30, 30) == 0x78112233, "Rounded mask preserves existing partial alpha and RGB");
        f.rgb[30 * W + 30] = 0x00112233;
        check(at(f.cut(false), 30, 30) == 0, "Rounded mask cannot restore a manually erased pixel");
        f.mask[0] = -0.000001f;
        check(at(f.cut(false), 0, 0) == 0, "Small negative rounding remains background");
    }

    public static void cleanup() {
        Fixture f = garment();
        // A connected band of overconfident pale background is excluded by border-guided refinement.
        f.box(20, 15, 25, 66, 0xfff2f3f4, .78f);
        f.box(7, 7, 9, 9, RED, 1); // detached dust, even if the model was certain
        int[] before = f.rgb.clone(), output = f.cut(true);
        check(at(output, 22, 30) == 0, "Remove connected background halo");
        check(at(output, 7, 7) == 0, "Remove tiny detached residue");
        check(at(output, 30, 30) == RED, "Preserve source fabric colour");
        check(Arrays.equals(before, f.rgb), "Do not mutate source");
        check(PhotoRules.usableCutout(output), "Usable cutout");
        check(Arrays.equals(output, f.cut(true)), "Deterministic result");
        check((at(f.cut(false), 22, 30) >>> 24) == 255, "Gentle mode does not colour-erase uncertain pale fabric");
        check("red".equals(PhotoRules.dominantColor(output)), "Backdrop does not influence colour detection");
        f = garment();
        f.box(20, 15, 25, 66, 0xff29344a, .48f);
        int[] normal = f.cut(false), stronger = f.cut(true);
        check((at(normal, 22, 30) >>> 24) >= 160, "Gentle mode preserves more of uncertain attached edges");
        check(at(stronger, 22, 30) == 0, "Strong mode suppresses uncertain edge");
        check(at(stronger, 30, 30) == RED, "Strong mode preserves confident core");
        // A uniform model mask is NOT a successful cutout.
        Arrays.fill(f.mask, 1);
        check(!PhotoRules.usableCutout(f.cut(false)), "Reject mask covering whole frame");
        Arrays.fill(f.mask, .5f);
        check(!PhotoRules.usableCutout(f.cut(false)), "Reject mask without a reliable subject");
    }

    public static void preserveDetails() {
        Fixture f = new Fixture();
        f.box(10, 25, 30, 55, RED, 1); f.box(48, 28, 70, 58, 0xff123458, 1);
        int[] p = f.cut(false);
        check(at(p, 18, 35) == RED && at(p, 55, 40) == 0xff123458, "Keep both shoes");
        f = garment();
        for (int k = 0; k < 14; k++) { int i = (15 - k) * W + 25 - k; f.rgb[i] = RED; f.mask[i] = 1; }
        f.box(35, 30, 43, 40, WHITE, .03f);
        p = f.cut(false);
        check(at(p, 13, 3) == RED, "Keep connected one-pixel diagonal strap");
        check(at(p, 38, 35) == 0, "Keep enclosed background hole open");
        f = new Fixture(); f.box(25, 15, 55, 66, WHITE, 1); f.box(23, 15, 25, 66, WHITE, .8f);
        p = f.cut(false);
        check(at(p, 30, 30) == WHITE, "White fabric is foreground");
        check((at(p, 24, 30) >>> 24) > 200, "No colour flood into white-on-white edges");
        f = garment();
        // Existing alpha edits are an upper bound, even if the model now says foreground.
        f.rgb[30 * W + 30] = 0x00112233; f.mask[30 * W + 30] = 1;
        f.rgb[31 * W + 30] = 0x78112233;
        p = f.cut(false);
        check(at(p, 30, 30) == 0, "Previously erased pixel stays erased");
        check((at(p, 30, 31) >>> 24) <= 120, "Previously partial alpha cannot increase");
        // Patterned backgrounds should not result in a guessed dominant-colour erase.
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (f.mask[y * W + x] == 0)
            f.rgb[y * W + x] = (x + y) % 2 == 0 ? 0xff101010 : WHITE;
        f.box(23, 15, 25, 66, WHITE, .8f);
        check((at(f.cut(false), 24, 30) >>> 24) > 200, "No colour flood from an inconsistent border");
        // Colour at the border must not override a high-confidence foreground touching that border.
        f = garment(); f.box(25, 0, 55, 15, RED, 1);
        check(at(f.cut(false), 30, 0) == RED, "Keep confident fabric touching frame");
    }

    public static void malformedAndCancellation() {
        Fixture f = garment();
        for (float bad : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1, 2, -.001f, 1.001f }) {
            for (boolean stronger : new boolean[] { false, true }) {
                f.mask[30 * W + 30] = bad;
                boolean rejected = false;
                try { f.cut(stronger); } catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "Reject malformed mask before accepting a damaged candidate");
            }
        }
        f.mask[30 * W + 30] = 1;
        int[] p = f.cut(false);
        boolean failed = false;
        try { PhotoCutoutRules.refine(f.rgb, W, H - 1, FloatBuffer.wrap(f.mask), false, () -> {}); }
        catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Reject mismatched dimensions");
        failed = false;
        try { PhotoCutoutRules.refine(f.rgb, W, H, FloatBuffer.wrap(new float[3]), false, () -> {}); }
        catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Reject truncated mask");
        float[] padded = new float[f.mask.length + 4]; System.arraycopy(f.mask, 0, padded, 4, f.mask.length);
        FloatBuffer buffer = FloatBuffer.wrap(padded); buffer.position(4);
        check(Arrays.equals(p, PhotoCutoutRules.refine(f.rgb, W, H, buffer, false, () -> {})), "Respect buffer position");
        check(buffer.position() == 4, "Do not consume SDK buffer");
        int[] checkpoints = {0}; failed = false;
        try { PhotoCutoutRules.refine(f.rgb, W, H, FloatBuffer.wrap(f.mask), false, () -> {
            if (++checkpoints[0] == 5) throw new CancellationException();
        }); } catch (CancellationException expected) { failed = true; }
        check(failed, "Propagate cancellation during work");
    }

    public static void gentleMode() {
        // The default must never remove more than the explicit stronger option, including
        // fine detail, already-transparent source pixels and a varied, nonuniform backdrop.
        Fixture f = garment();
        for (int y = 15; y < 66; y++) for (int x = 25; x < 55; x++) {
            int i = y * W + x;
            f.mask[i] = .16f + ((x + y) % 20) * .04f;
            if (x % 9 == 0) f.rgb[i] = WHITE;
        }
        int[] gentle = f.cut(false), strong = f.cut(true);
        boolean keepsAtLeastAsMuch = true, preservesColour = true;
        for (int i = 0; i < gentle.length; i++) {
            keepsAtLeastAsMuch &= (gentle[i] >>> 24) >= (strong[i] >>> 24);
            if ((gentle[i] >>> 24) > 0) preservesColour &= (gentle[i] & 0xffffff) == (f.rgb[i] & 0xffffff);
        }
        check(keepsAtLeastAsMuch, "Gentle alpha must dominate stronger cleanup");
        check(preservesColour, "Gentle cleanup changes alpha only, never invents fabric colour");
        check(at(gentle, 0, 0) == 0, "Confirmed background is still removed in gentle mode");
        check(PhotoRules.usableCutout(gentle), "Gentle garment remains a usable cutout");
    }

    public static void backdrops() {
        int[] p = new int[100]; Arrays.fill(p, RED);
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.WHITE, "Coloured fabric: white backdrop");
        Arrays.fill(p, WHITE);
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.CREAM, "White fabric: cream backdrop");
        Arrays.fill(p, 0x00ffffff); Arrays.fill(p, 20, 60, RED);
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.WHITE, "Transparent white does not count");
        Arrays.fill(p, 0xffaaaaaa);
        check(PhotoBackdropRules.background(p, "white") == PhotoBackdropRules.CREAM, "Manual white under shadow overrides detector");
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.WHITE, "Grey does not count as white");
        Arrays.fill(p, RED); Arrays.fill(p, 0, 10, WHITE);
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.WHITE, "Small white print does not dominate");
        Arrays.fill(p, 0xffefebe6);
        check(PhotoBackdropRules.background(p, null) == PhotoBackdropRules.CREAM, "Off-white fabric gets contrast");
        check(PhotoBackdropRules.background(new int[0], null) == PhotoBackdropRules.WHITE, "Empty fallback");
    }

    public static void largeFrame() {
        int w = 1600, h = 1200; int[] source = new int[w * h]; float[] mask = new float[source.length];
        Arrays.fill(source, WHITE);
        for (int y = 100; y < 1100; y++) for (int x = 500; x < 1100; x++) { source[y * w + x] = RED; mask[y * w + x] = 1; }
        int[] output = PhotoCutoutRules.refine(source, w, h, FloatBuffer.wrap(mask), false, () -> {});
        check(output[600 * w + 800] == RED && output[0] == 0, "Large camera-sized frame");
        check(PhotoRules.usableCutout(output), "Large frame result usable");
    }

    public static void main(String[] args) {
        roundedConfidence(); cleanup(); gentleMode(); preserveDetails(); malformedAndCancellation(); backdrops(); largeFrame();
        System.out.println("PASS: " + assertions + " production mask/backdrop assertions; synthetic fixtures, not ML accuracy.");
    }
}
