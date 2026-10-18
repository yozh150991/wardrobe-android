package com.familywardrobe.app;

import java.nio.FloatBuffer;
import java.util.Arrays;

/** Refines a model mask; never infers an object from its colour alone.
 * RGB comes from the source, so white fabric, prints and fine connected parts retain their colour.
 * All thresholds are heuristics awaiting device/photo-set acceptance, not accuracy guarantees.
 */
public final class PhotoCutoutRules {
    private PhotoCutoutRules() {}
    private static final float CONFIDENCE_ROUNDING_TOLERANCE = .00001f;

    public static int[] refine(int[] source, int width, int height, FloatBuffer confidence,
            boolean stronger, Runnable checkpoint) {
        if (width < 1 || height < 1 || (long) width * height > 16_000_000L ||
                (long) width * height != source.length || confidence.remaining() != source.length)
            throw new IllegalArgumentException("Invalid mask dimensions");
        checkpoint.run();
        final int n = source.length, offset = confidence.position();
        // Default to gentle cleanup: keep uncertain fabric and reserve aggressive trimming
        // for the user's explicit stronger action. These are photo-set tuning heuristics.
        final float low = stronger ? .52f : .15f, high = stronger ? .93f : .70f;
        byte[] alpha = new byte[n];
        for (int i = 0; i < n; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            float c = unitConfidence(confidence.get(offset + i));
            float t = Math.max(0, Math.min(1, (c - low) / (high - low)));
            int a = Math.round(255 * t * t * (3 - 2 * t));
            // Reprocessing a cutout must never fill already erased holes.
            alpha[i] = (byte) Math.min(source[i] >>> 24, a);
        }
        int[] queue = new int[n];
        byte[] visited = new byte[n];
        Integer background = stronger ? borderColour(source, width, height, confidence, checkpoint) : null;
        if (background != null) {
            // Do not colour-trim white-on-white (or any same-colour foreground/background).
            int foreground = 0, similar = 0;
            for (int i = 0; i < n; i++) {
                if ((i & 8191) == 0) checkpoint.run();
                if (confidence.get(offset + i) >= .90f && (source[i] >>> 24) >= 230) {
                    foreground++;
                    if (distance(source[i], background) <= 44) similar++;
                }
            }
            if (foreground >= 32 && similar < foreground * .08) {
                int head = 0, tail = 0;
                for (int i = 0; i < n; i++) {
                    if ((i & 8191) == 0) checkpoint.run();
                    // Seeds include enclosed background, e.g. between sleeves or handles.
                    if (confidence.get(offset + i) <= .15f || (source[i] >>> 24) == 0) {
                        visited[i] = 1; queue[tail++] = i;
                    }
                }
                final float protect = stronger ? .97f : .90f;
                final int tolerance = stronger ? 42 : 28;
                while (head < tail) {
                    if ((head & 8191) == 0) checkpoint.run();
                    int i = queue[head++], x = i % width;
                    alpha[i] = 0;
                    for (int d = 0; d < 4; d++) {
                        int j;
                        if (d == 0) { if (x == 0) continue; j = i - 1; }
                        else if (d == 1) { if (x + 1 == width) continue; j = i + 1; }
                        else if (d == 2) { if (i < width) continue; j = i - width; }
                        else { if (i + width >= n) continue; j = i + width; }
                        if (visited[j] == 0 && confidence.get(offset + j) < protect && distance(source[j], background) <= tolerance) {
                            visited[j] = 1; queue[tail++] = j;
                        }
                    }
                }
            }
        }
        Arrays.fill(visited, (byte) 0);
        // Retain ALL substantial components: a pair of shoes is not reduced to one shoe.
        // Eight-neighbour connectivity preserves diagonal straps and laces.
        int minimum = Math.max(8, (int) Math.ceil(n * .00006));
        int[] output = new int[n];
        for (int start = 0; start < n; start++) {
            if ((start & 8191) == 0) checkpoint.run();
            if (visited[start] != 0 || (alpha[start] & 255) == 0) continue;
            int head = 0, tail = 1, core = 0;
            visited[start] = 1; queue[0] = start;
            while (head < tail) {
                if ((head & 8191) == 0) checkpoint.run();
                int i = queue[head++], x = i % width, y = i / width;
                if ((alpha[i] & 255) >= 230) core++;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 || x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                    int j = i + dy * width + dx;
                    if (visited[j] == 0 && (alpha[j] & 255) != 0) { visited[j] = 1; queue[tail++] = j; }
                }
            }
            if (tail >= minimum && core >= 3) {
                for (int k = 0; k < tail; k++) {
                    if ((k & 8191) == 0) checkpoint.run();
                    int i = queue[k];
                    output[i] = ((alpha[i] & 255) << 24) | (source[i] & 0x00ffffff);
                }
            }
        }
        checkpoint.run();
        return output;
    }

    /** Saturate tiny float32 overshoots instead of turning confident fabric into holes.
     * A genuinely malformed mask is rejected as a whole so the caller keeps the current
     * photo, rather than accepting a cutout with arbitrary transparent pixels.
     */
    private static float unitConfidence(float value) {
        if (!Float.isFinite(value) || value < -CONFIDENCE_ROUNDING_TOLERANCE ||
                value > 1f + CONFIDENCE_ROUNDING_TOLERANCE)
            throw new IllegalArgumentException("Invalid foreground confidence mask");
        return Math.max(0f, Math.min(1f, value));
    }

    /** A dominant consistent border colour, only from model-confirmed background samples. */
    private static Integer borderColour(int[] source, int width, int height, FloatBuffer mask, Runnable checkpoint) {
        int[] samples = new int[2 * (width + height)];
        int count = 0, offset = mask.position();
        for (int y = 0; y < height; y++) {
            if ((y & 31) == 0) checkpoint.run();
            for (int x = 0; x < width; x++) {
                if (x != 0 && x != width - 1 && y != 0 && y != height - 1) continue;
                int i = y * width + x;
                float c = unitConfidence(mask.get(offset + i));
                if (Float.isFinite(c) && c >= 0 && c <= .15f && (source[i] >>> 24) >= 250) samples[count++] = source[i];
            }
        }
        if (count < 16) return null;
        int[] histogram = new int[512];
        int winner = 0;
        for (int i = 0; i < count; i++) {
            int p = samples[i], bin = ((p >> 15) & 448) | ((p >> 10) & 56) | ((p >> 5) & 7);
            if (++histogram[bin] > histogram[winner]) winner = bin;
        }
        int center = 0xff000000 | (((winner >> 6) * 32 + 16) << 16) | ((((winner >> 3) & 7) * 32 + 16) << 8) | ((winner & 7) * 32 + 16);
        long r = 0, g = 0, b = 0; int matches = 0;
        for (int i = 0; i < count; i++) if (distance(samples[i], center) <= 32) {
            int p = samples[i]; r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; matches++;
        }
        if (matches < count * .65) return null;
        return 0xff000000 | ((int) (r / matches) << 16) | ((int) (g / matches) << 8) | (int) (b / matches);
    }

    private static int distance(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)),
            Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)), Math.abs((a & 255) - (b & 255))));
    }
}
