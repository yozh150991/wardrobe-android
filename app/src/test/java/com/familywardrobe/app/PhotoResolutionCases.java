package com.familywardrobe.app;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;

public final class PhotoResolutionCases {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static void rejects(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected);
    }
    public static void main(String[] args) {
        check(PhotoResolutionRules.finalLimit(128L << 20) == 1024);
        check(PhotoResolutionRules.finalLimit((192L << 20) - 1) == 1024);
        check(PhotoResolutionRules.finalLimit(192L << 20) == 1536);
        check(PhotoResolutionRules.finalLimit((256L << 20) - 1) == 1536);
        check(PhotoResolutionRules.finalLimit(256L << 20) == 2048);
        check(PhotoResolutionRules.sample(4000, 3000, 2560, 4_194_304) == 2);
        check(PhotoResolutionRules.sample(2049, 2048, 2560, 4_194_304) == 2);
        check(PhotoResolutionRules.sample(2048, 2048, 2560, 4_194_304) == 1);
        check(PhotoResolutionRules.sample(Integer.MAX_VALUE, Integer.MAX_VALUE, 2560, 4_194_304) == 1 << 20);
        check(PhotoResolutionRules.canvas(new int[]{0, 0, 30, 10}, 2048, .08f) == 35);
        check(PhotoResolutionRules.canvas(new int[]{0, 0, 2000, 1400}, 2048, .08f) == 2048);
        rejects(() -> PhotoResolutionRules.sample(0, 100, 2560, 4_194_304));
        rejects(() -> PhotoResolutionRules.sample(100, 100, 0, 4_194_304));
        rejects(() -> PhotoResolutionRules.canvas(new int[]{1, 0, 1, 100}, 2048, .08f));
        rejects(() -> PhotoResolutionRules.canvas(new int[]{0, 0, 100, 100}, 2048, Float.NaN));
        Random random = new Random(137);
        for (int i = 0; i < 12_000; i++) {
            int width = 1 + random.nextInt(100_000), height = 1 + random.nextInt(100_000);
            int limit = new int[]{1024, 1536, 2048}[i % 3];
            int longest = PhotoResolutionRules.sourceLimit(limit);
            long budget = PhotoResolutionRules.pixelLimit(limit);
            int sample = PhotoResolutionRules.sample(width, height, longest, budget);
            long w = ((long) width + sample - 1) / sample, h = ((long) height + sample - 1) / sample;
            check((sample & (sample - 1)) == 0);
            check(w <= longest && h <= longest && w * h <= budget);
            if (sample > 1) {
                long pw = ((long) width + sample / 2 - 1) / (sample / 2), ph = ((long) height + sample / 2 - 1) / (sample / 2);
                check(pw > longest || ph > longest || pw * ph > budget);
            }
            check(PhotoResolutionRules.matchesMask(width, height, (int) w, (int) h, limit));
            check(!PhotoResolutionRules.matchesMask(width, height, (int) w + 1, (int) h, limit));
            check(!PhotoResolutionRules.matchesMask(width, height, (int) w, (int) h + 1, limit));
            int[] bounds = {5, 7, 5 + (int) w, 7 + (int) h};
            int canvas = PhotoResolutionRules.canvas(bounds, limit, .08f);
            float[] fitted = PhotoPresentationGeometry.fit(bounds, canvas, .08f);
            check(canvas <= limit && canvas > 0);
            check(fitted[2] - fitted[0] <= w + .001f && fitted[3] - fitted[1] <= h + .001f);
            check(fitted[0] >= 0 && fitted[1] >= 0 && fitted[2] <= canvas && fitted[3] <= canvas);
        }
        // Real production refinement at the new maximum. Synthetic data, not ML quality evidence.
        int side = 2048, n = side * side;
        int[] source = new int[n]; Arrays.fill(source, 0xff326591);
        float[] mask = new float[n]; Arrays.fill(mask, 1f);
        long start = System.nanoTime();
        int[] output = PhotoCutoutRules.refine(source, side, side, FloatBuffer.wrap(mask), false, () -> {});
        check(Arrays.equals(source, output));
        System.out.println("PASS: " + checks + " photo resolution assertions; 2048x2048 production refinement " +
                ((System.nanoTime() - start) / 1_000_000) + " ms on this host, -Xmx256m. Not Android/ML profiling.");
    }
}
