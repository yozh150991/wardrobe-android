package com.familywardrobe.app.rules;

import java.nio.FloatBuffer;
import java.util.Arrays;

/** Refines a model mask; never infers an object from its colour alone.
 * RGB comes from the source, so white fabric, prints and fine connected parts retain their colour.
 * All thresholds are heuristics awaiting device/photo-set acceptance, not accuracy guarantees.
 */
public final class PhotoCutoutRules {
    private PhotoCutoutRules() {}
    private static final float CONFIDENCE_ROUNDING_TOLERANCE = .00001f;

    /** Below this alpha a pixel counts as removed when the outside of the garment is traced. */
    private static final int SOLID = 128;
    /** Largest channel difference from the backdrop colour that still looks like the backdrop. */
    private static final int NEAR = 44;
    /** From this difference a pixel the model is unsure about is clearly not the backdrop. */
    private static final int FAR = 2 * NEAR;
    private static final byte OUTSIDE = 1, OPEN = 2, FILLED = 3, EDGE = 4;

    public static int[] refine(int[] source, int width, int height, FloatBuffer confidence,
            boolean stronger, Runnable checkpoint) {
        if (width < 1 || height < 1 || (long) width * height > 16_000_000L ||
                (long) width * height != source.length || confidence.remaining() != source.length)
            throw new IllegalArgumentException("Invalid mask dimensions");
        checkpoint.run();
        final int n = source.length, offset = confidence.position();
        int[] queue = new int[n];
        byte[] visited = new byte[n];
        Integer border = borderColour(source, width, height, confidence, checkpoint);
        // Colour is used only when the garment does not share it with the backdrop (white on white).
        boolean distinct = false;
        // The colours of the fabric the model is sure about: 512 bins, true where a bin holds 0.5% of it or more.
        boolean[] fabric = new boolean[512];
        if (border != null) {
            int foreground = 0, similar = 0;
            int[] bins = new int[512];
            for (int i = 0; i < n; i++) {
                if ((i & 8191) == 0) checkpoint.run();
                if (confidence.get(offset + i) >= .90f && (source[i] >>> 24) >= 230) {
                    foreground++; bins[bin(source[i])]++;
                    if (distance(source[i], border) <= NEAR) similar++;
                }
            }
            distinct = foreground >= 32 && similar < foreground * .08;
            for (int b = 0; b < 512; b++) fabric[b] = bins[b] > 0 && bins[b] >= foreground * .005;
        }
        int background = border == null ? 0 : border;
        byte[] alpha = mask(source, width, height, confidence, stronger, background, distinct, fabric, queue, visited, checkpoint);
        if (stronger) {
            // The explicit stronger action may only remove more than the default, never keep more.
            byte[] gentle = mask(source, width, height, confidence, false, background, distinct, fabric, queue, visited, checkpoint);
            for (int i = 0; i < n; i++) if ((gentle[i] & 255) < (alpha[i] & 255)) alpha[i] = gentle[i];
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

    /** The model's mask as alpha, with the garment's interior protected (see {@link #protect}). */
    private static byte[] mask(int[] source, int width, int height, FloatBuffer confidence, boolean stronger,
            int background, boolean distinct, boolean[] fabric, int[] queue, byte[] visited, Runnable checkpoint) {
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
        if (stronger && distinct) {
            Arrays.fill(visited, (byte) 0);
            int head = 0, tail = 0;
            for (int i = 0; i < n; i++) {
                if ((i & 8191) == 0) checkpoint.run();
                // Seeds include enclosed background, e.g. between sleeves or handles.
                if (confidence.get(offset + i) <= .15f || (source[i] >>> 24) == 0) {
                    visited[i] = 1; queue[tail++] = i;
                }
            }
            final float protect = .97f;
            final int tolerance = 42;
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
        protect(alpha, source, width, height, confidence, background, distinct, fabric, queue, visited, checkpoint);
        return alpha;
    }

    /**
     * Only the backdrop is removed; the garment itself is not touched (0.14.37). The model's confidence dips inside
     * garments — a print, a pocket, a fold, fabric close to the backdrop's colour — and the plain mask turned those
     * dips into holes and see-through patches. Here the outside is traced from the frame's edge instead:
     * <ol>
     *   <li><b>Outside</b> is what the model removed and that is connected to the frame's edge. With a known backdrop
     *   colour that the garment does not share, a pixel the model is merely unsure about (alpha above zero) does
     *   not count as outside when its colour is clearly not the backdrop's ({@link #FAR}) and is one of the colours
     *   of the fabric the model is sure about — so a shadow, a hanger or a hand is not kept this way.</li>
     *   <li><b>A removed area enclosed by the garment</b> stays open only when it really is backdrop seen through
     *   the garment (between a sleeve and the body, inside a handle): the model was sure, and it has the backdrop's
     *   colour — or, when colour cannot tell, it is substantial and the model was very sure. Otherwise it is fabric
     *   and is restored.</li>
     *   <li><b>Soft edges only at the edge.</b> Within a thin band along the outside (and along open areas) the
     *   model's soft alpha is kept; deeper inside every pixel is as opaque as the source photo.</li>
     * </ol>
     * Alpha never exceeds the source's, so what the person erased by hand stays erased. Nothing is ever removed here.
     */
    private static void protect(byte[] alpha, int[] source, int width, int height, FloatBuffer confidence,
            int background, boolean distinct, boolean[] fabric, int[] queue, byte[] state, Runnable checkpoint) {
        final int n = source.length, offset = confidence.position();
        Arrays.fill(state, (byte) 0);
        int head = 0, tail = 0;
        for (int y = 0; y < height; y++) {
            if ((y & 31) == 0) checkpoint.run();
            for (int x = 0; x < width; x += (y == 0 || y == height - 1 || width == 1) ? 1 : width - 1) {
                int i = y * width + x;
                if (state[i] == 0 && outside(alpha, source, i, background, distinct, fabric)) { state[i] = OUTSIDE; queue[tail++] = i; }
            }
        }
        while (head < tail) {
            if ((head & 8191) == 0) checkpoint.run();
            int i = queue[head++], x = i % width;
            for (int d = 0; d < 4; d++) {
                int j;
                if (d == 0) { if (x == 0) continue; j = i - 1; }
                else if (d == 1) { if (x + 1 == width) continue; j = i + 1; }
                else if (d == 2) { if (i < width) continue; j = i - width; }
                else { if (i + width >= n) continue; j = i + width; }
                if (state[j] == 0 && outside(alpha, source, j, background, distinct, fabric)) { state[j] = OUTSIDE; queue[tail++] = j; }
            }
        }
        // Removed areas the outside did not reach: backdrop seen through the garment, or fabric the model missed.
        final int substantial = Math.max(8, (int) Math.ceil(n * .002));
        for (int start = 0; start < n; start++) {
            if ((start & 8191) == 0) checkpoint.run();
            if (state[start] != 0 || !outside(alpha, source, start, background, distinct, fabric)) continue;
            head = 0; tail = 1; queue[0] = start; state[start] = FILLED;
            int matches = 0; double sure = 0;
            while (head < tail) {
                if ((head & 8191) == 0) checkpoint.run();
                int i = queue[head++], x = i % width;
                sure += confidence.get(offset + i);
                if (distinct && distance(source[i], background) <= NEAR) matches++;
                for (int d = 0; d < 4; d++) {
                    int j;
                    if (d == 0) { if (x == 0) continue; j = i - 1; }
                    else if (d == 1) { if (x + 1 == width) continue; j = i + 1; }
                    else if (d == 2) { if (i < width) continue; j = i - width; }
                    else { if (i + width >= n) continue; j = i + width; }
                    if (state[j] == 0 && outside(alpha, source, j, background, distinct, fabric)) { state[j] = FILLED; queue[tail++] = j; }
                }
            }
            double mean = sure / tail;
            boolean open = distinct ? mean <= .15 && matches >= tail * .60 : mean <= .05 && tail >= substantial;
            for (int k = 0; k < tail; k++) {
                if ((k & 8191) == 0) checkpoint.run();
                int i = queue[k];
                if (open) state[i] = OPEN; else alpha[i] = (byte) (source[i] >>> 24);
            }
        }
        // A thin band along the outside keeps the model's soft edge; everything deeper is as opaque as the source.
        final int band = Math.max(2, Math.round(Math.min(width, height) * .004f));
        head = 0; tail = 0;
        for (int i = 0; i < n; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            if (state[i] == OUTSIDE || state[i] == OPEN) queue[tail++] = i;
        }
        for (int depth = 1; depth <= band && head < tail; depth++) {
            int end = tail;
            while (head < end) {
                if ((head & 8191) == 0) checkpoint.run();
                int i = queue[head++], x = i % width, y = i / width;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 || x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                    int j = i + dy * width + dx;
                    if (state[j] != 0 && state[j] != FILLED) continue;
                    // Fabric the model was unsure about, kept by its colour: feather it in from the outside.
                    if (state[j] == 0 && (alpha[j] & 255) < SOLID)
                        alpha[j] = (byte) Math.min(source[j] >>> 24, Math.max(alpha[j] & 255, 255 * depth / (band + 1)));
                    state[j] = EDGE; queue[tail++] = j;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            if (state[i] == 0) alpha[i] = (byte) (source[i] >>> 24);
        }
    }
    /** Removed by the model and not clearly fabric by its colour. */
    private static boolean outside(byte[] alpha, int[] source, int i, int background, boolean distinct, boolean[] fabric) {
        int a = alpha[i] & 255;
        return a < SOLID && !(distinct && a > 0 && distance(source[i], background) > FAR && fabricColour(source[i], fabric));
    }
    private static int bin(int p) { return ((p >> 15) & 448) | ((p >> 10) & 56) | ((p >> 5) & 7); }
    /** A colour found in the fabric the model is sure about (its bin or one next to it): not a shadow, a hanger or a hand. */
    private static boolean fabricColour(int p, boolean[] fabric) {
        int r = (p >> 21) & 7, g = (p >> 13) & 7, b = (p >> 5) & 7;
        for (int dr = -1; dr <= 1; dr++) for (int dg = -1; dg <= 1; dg++) for (int db = -1; db <= 1; db++) {
            int rr = r + dr, gg = g + dg, bb = b + db;
            if (rr >= 0 && rr < 8 && gg >= 0 && gg < 8 && bb >= 0 && bb < 8 && fabric[(rr << 6) | (gg << 3) | bb]) return true;
        }
        return false;
    }

    /**
     * What the model should look at when the photo already has transparent pixels (a cutout cleaned again, or after
     * manual erasing). Transparent pixels carry no colour, so the model saw them as black and a dark garment merged
     * with them. They are put on a backdrop that contrasts with the garment instead: white under a dark garment,
     * dark grey under a light one. Returns null when the photo has no transparency — the photo itself is the input.
     */
    public static int[] modelInput(int[] source, Runnable checkpoint) {
        long light = 0; int opaque = 0; boolean transparent = false;
        for (int i = 0; i < source.length; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            int p = source[i], a = p >>> 24;
            if (a < 255) transparent = true;
            if (a >= 230) { light += (((p >> 16) & 255) * 299 + ((p >> 8) & 255) * 587 + (p & 255) * 114) / 1000; opaque++; }
        }
        if (!transparent) return null;
        int backdrop = opaque == 0 || light / opaque < 128 ? 255 : 32;
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            int p = source[i], a = p >>> 24;
            int r = (((p >> 16) & 255) * a + backdrop * (255 - a) + 127) / 255, g = (((p >> 8) & 255) * a + backdrop * (255 - a) + 127) / 255,
                b = ((p & 255) * a + backdrop * (255 - a) + 127) / 255;
            result[i] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        return result;
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
            int p = samples[i], bin = bin(p);
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
