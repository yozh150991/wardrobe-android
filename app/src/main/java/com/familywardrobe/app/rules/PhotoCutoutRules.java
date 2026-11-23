package com.familywardrobe.app.rules;

import java.nio.FloatBuffer;
import java.util.Arrays;

/** Refines a model mask; never infers an object from its colour alone.
 * RGB comes from the source, so white fabric, prints and fine connected parts retain their colour.
 * All thresholds are heuristics awaiting device/photo-set acceptance, not accuracy guarantees.
 */
public final class PhotoCutoutRules {
    private PhotoCutoutRules() {}
    /** QA-61-30: how far outside 0..1 a confidence may be and still be saturated. On a Galaxy S23 every mask from
     * ML Kit's segmenter had 1–23 % of its values between about -0.17 and 1.21 (resampling overshoot at edges), and
     * the earlier float-rounding tolerance refused every photo. Values further out, NaN and infinities are still a
     * malformed mask.
     */
    private static final float CONFIDENCE_OVERSHOOT = .5f;

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
        clean(alpha, source, width, height, confidence, queue, visited, checkpoint);
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

    /** PHOTO-19: what the model itself gets wrong, measured on masks captured from a phone (Galaxy S23, six photos
     * on a patterned blanket): its mask is almost binary, so nothing above can tell that it is wrong.
     *
     * A. Backdrop at the edge. Colours that are common where the model is sure of the background and at least
     *    eight times rarer in the garment are removed where they reach the garment's outline (a patch of the
     *    blanket at a cuff, a thin halo). At most 3 % of the garment, or nothing: more means the colours are shared
     *    (the captures needed up to 1.3 %). Known limit: a trim under 3 % of the garment, at its edge, in one of the
     *    backdrop's main colours, is taken for the backdrop.
     * B. Something else in the frame joined by a narrow neck (a foot under a shirt). The garment's body is what
     *    survives an opening of radius 8.5 % of the square root of its area; a part outside it is removed only when
     *    it is small (0.2–6 % of the area), its colours are hardly the body's (overlap under 0.3) and are the
     *    background's (overlap of 0.3 or more, and twice the body's). A sleeve or a strap in the garment's colours,
     *    or in a colour the backdrop does not have, stays.
     * Both only remove; photos already cut out (no confident background left) are not touched.
     */
    private static void clean(byte[] alpha, int[] source, int width, int height, FloatBuffer confidence,
            int[] queue, byte[] visited, Runnable checkpoint) {
        final int n = alpha.length, offset = confidence.position();
        int[] fine = new int[512], fineBack = new int[512], coarseBack = new int[64];
        int solid = 0, back = 0;
        for (int i = 0; i < n; i++) {
            if ((i & 8191) == 0) checkpoint.run();
            if ((source[i] >>> 24) < 230) continue;
            if ((alpha[i] & 255) >= SOLID) { solid++; fine[bin(source[i])]++; }
            else if (alpha[i] == 0 && confidence.get(offset + i) < .1f) { back++; fineBack[bin(source[i])]++; coarseBack[coarse(source[i])]++; }
        }
        if (solid < 4096 || back < n * .02) return;
        // A. Backdrop colours reaching the outline.
        boolean[] backdrop = new boolean[512];
        boolean any = false;
        for (int b = 0; b < 512; b++) {
            backdrop[b] = fineBack[b] >= back * .003 && (long) fineBack[b] * solid >= 8L * fine[b] * back;
            any |= backdrop[b];
        }
        if (any) {
            int tail = 0;
            for (int i = 0; i < n; i++) {
                if ((i & 8191) == 0) checkpoint.run();
                if ((alpha[i] & 255) < SOLID || !backdrop[bin(source[i])]) continue;
                int x = i % width, y = i / width;
                boolean edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                    if ((alpha[i + dy * width + dx] & 255) < SOLID) { edge = true; break; }
                }
                if (edge) { visited[i] = 1; queue[tail++] = i; }
            }
            for (int head = 0; head < tail; head++) {
                if ((head & 8191) == 0) checkpoint.run();
                int i = queue[head], x = i % width, y = i / width;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                    int j = i + dy * width + dx;
                    if (visited[j] == 0 && (alpha[j] & 255) >= SOLID && backdrop[bin(source[j])]) { visited[j] = 1; queue[tail++] = j; }
                }
            }
            if (tail <= solid * .03) {
                for (int k = 0; k < tail; k++) alpha[queue[k]] = 0;
                // Crumbs and hairlines left beside what was just removed: a pixel that has lost five of its eight
                // neighbours goes too. Only next to the removed backdrop, so no other outline is thinned.
                int from = 0, end = tail;
                for (int pass = 0; pass < 4 && from < end; pass++) {
                    for (int k = from; k < end; k++) {
                        if ((k & 8191) == 0) checkpoint.run();
                        int i = queue[k], x = i % width, y = i / width;
                        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                            if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                            int j = i + dy * width + dx;
                            if (visited[j] != 0 || (alpha[j] & 255) < SOLID) continue;
                            int jx = j % width, jy = j / width, open = 0;
                            for (int ey = -1; ey <= 1; ey++) for (int ex = -1; ex <= 1; ex++) {
                                if (jx + ex < 0 || jx + ex >= width || jy + ey < 0 || jy + ey >= height) continue;
                                if ((alpha[j + ey * width + ex] & 255) < SOLID) open++;
                            }
                            if (open >= 5) { visited[j] = 1; queue[tail++] = j; }
                        }
                    }
                    for (int k = end; k < tail; k++) alpha[queue[k]] = 0;
                    from = end; end = tail;
                }
                solid -= tail;
                // The soft outline that was drawn around the removed backdrop, up to three pixels from it.
                int marked = tail;
                from = 0; end = tail;
                for (int pass = 0; pass < 3 && from < end; pass++) {
                    for (int k = from; k < end; k++) {
                        if ((k & 8191) == 0) checkpoint.run();
                        int i = queue[k], x = i % width, y = i / width;
                        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                            if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                            int j = i + dy * width + dx;
                            if (visited[j] == 0 && alpha[j] != 0 && (alpha[j] & 255) < SOLID) { visited[j] = 1; alpha[j] = 0; queue[marked++] = j; }
                        }
                    }
                    from = end; end = marked;
                }
                tail = marked;
            }
            for (int k = 0; k < tail; k++) visited[queue[k]] = 0;
        }
        // B. A foreign part joined by a narrow neck.
        final int radius = 3 * Math.max(3, (int) Math.round(Math.sqrt(solid) * .085));
        short[] depth = new short[n];
        for (int i = 0; i < n; i++) depth[i] = (alpha[i] & 255) >= SOLID ? Short.MAX_VALUE : 0;
        chamfer(depth, width, height, checkpoint);
        int best = -1, bestSize = 0;
        for (int start = 0; start < n; start++) {
            if ((start & 8191) == 0) checkpoint.run();
            if (visited[start] != 0 || depth[start] <= radius) continue;
            int size = flood(start, depth, radius, visited, (byte) 0, (byte) 1, queue, width, height, checkpoint);
            if (size > bestSize) { bestSize = size; best = start; }
        }
        if (best < 0) return;
        flood(best, depth, radius, visited, (byte) 1, (byte) 2, queue, width, height, checkpoint);
        for (int i = 0; i < n; i++) depth[i] = visited[i] == 2 ? 0 : Short.MAX_VALUE;
        chamfer(depth, width, height, checkpoint);
        final int reach = (int) (radius * 1.15f);
        int[] coarseBody = new int[64];
        int body = 0;
        for (int i = 0; i < n; i++) {
            boolean in = (alpha[i] & 255) >= SOLID && depth[i] <= reach;
            visited[i] = in ? (byte) 3 : 0;
            if (in) { body++; coarseBody[coarse(source[i])]++; }
        }
        int[] piece = new int[64];
        for (int start = 0; start < n; start++) {
            if ((start & 8191) == 0) checkpoint.run();
            if (visited[start] != 0 || (alpha[start] & 255) < SOLID) continue;
            Arrays.fill(piece, 0);
            int head = 0, tail = 1;
            boolean joined = false;
            visited[start] = 4; queue[0] = start;
            while (head < tail) {
                if ((head & 8191) == 0) checkpoint.run();
                int i = queue[head++], x = i % width, y = i / width;
                piece[coarse(source[i])]++;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
                    int j = i + dy * width + dx;
                    if (visited[j] == 3) joined = true;
                    else if (visited[j] == 0 && (alpha[j] & 255) >= SOLID) { visited[j] = 4; queue[tail++] = j; }
                }
            }
            if (!joined || tail < solid * .002 || tail > solid * .06) continue;
            double likeBody = 0, likeBack = 0;
            for (int b = 0; b < 64; b++) {
                double share = piece[b] / (double) tail;
                likeBody += Math.min(share, coarseBody[b] / (double) body);
                likeBack += Math.min(share, coarseBack[b] / (double) back);
            }
            if (likeBody < .3 && likeBack >= .3 && likeBack >= 2 * likeBody) for (int k = 0; k < tail; k++) alpha[queue[k]] = 0;
        }
    }

    private static int coarse(int p) { return ((p >> 18) & 48) | ((p >> 12) & 12) | ((p >> 6) & 3); }

    /** Marks the four-connected region of pixels deeper than `radius` that carry `from`; returns its size. */
    private static int flood(int start, short[] depth, int radius, byte[] marks, byte from, byte to, int[] queue,
            int width, int height, Runnable checkpoint) {
        int head = 0, tail = 1;
        marks[start] = to; queue[0] = start;
        while (head < tail) {
            if ((head & 8191) == 0) checkpoint.run();
            int i = queue[head++], x = i % width, y = i / width;
            if (x > 0 && marks[i - 1] == from && depth[i - 1] > radius) { marks[i - 1] = to; queue[tail++] = i - 1; }
            if (x < width - 1 && marks[i + 1] == from && depth[i + 1] > radius) { marks[i + 1] = to; queue[tail++] = i + 1; }
            if (y > 0 && marks[i - width] == from && depth[i - width] > radius) { marks[i - width] = to; queue[tail++] = i - width; }
            if (y < height - 1 && marks[i + width] == from && depth[i + width] > radius) { marks[i + width] = to; queue[tail++] = i + width; }
        }
        return tail;
    }

    /** 3-4 chamfer distance (three units a pixel) to the nearest zero; the frame is not a zero. */
    private static void chamfer(short[] d, int width, int height, Runnable checkpoint) {
        for (int y = 0, i = 0; y < height; y++) {
            checkpoint.run();
            for (int x = 0; x < width; x++, i++) {
                int v = d[i];
                if (v == 0) continue;
                if (x > 0) v = Math.min(v, d[i - 1] + 3);
                if (y > 0) {
                    v = Math.min(v, d[i - width] + 3);
                    if (x > 0) v = Math.min(v, d[i - width - 1] + 4);
                    if (x < width - 1) v = Math.min(v, d[i - width + 1] + 4);
                }
                d[i] = (short) Math.min(v, Short.MAX_VALUE);
            }
        }
        for (int y = height - 1, i = width * height - 1; y >= 0; y--) {
            checkpoint.run();
            for (int x = width - 1; x >= 0; x--, i--) {
                int v = d[i];
                if (v == 0) continue;
                if (x < width - 1) v = Math.min(v, d[i + 1] + 3);
                if (y < height - 1) {
                    v = Math.min(v, d[i + width] + 3);
                    if (x < width - 1) v = Math.min(v, d[i + width + 1] + 4);
                    if (x > 0) v = Math.min(v, d[i + width - 1] + 4);
                }
                d[i] = (short) Math.min(v, Short.MAX_VALUE);
            }
        }
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

    /** QA-61-30: the shape of a rejected mask for logcat (sizes and value range only, never pixels).
     * Reads with absolute indexes, so the SDK buffer position is left as it was.
     */
    public static String describe(FloatBuffer mask, int width, int height) {
        int start = mask.position(), end = mask.limit(), nonFinite = 0, below = 0, above = 0;
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            float value = mask.get(i);
            if (!Float.isFinite(value)) { nonFinite++; continue; }
            if (value < min) min = value;
            if (value > max) max = value;
            if (value < 0f) below++; else if (value > 1f) above++;
        }
        return "size=" + (end - start) + " expected=" + ((long) width * height) + " (" + width + "x" + height + ")" +
            " min=" + min + " max=" + max + " nonFinite=" + nonFinite + " below0=" + below + " above1=" + above;
    }

    /** Saturate overshoots instead of turning confident fabric into holes.
     * A genuinely malformed mask is rejected as a whole so the caller keeps the current
     * photo, rather than accepting a cutout with arbitrary transparent pixels.
     */
    private static float unitConfidence(float value) {
        if (!Float.isFinite(value) || value < -CONFIDENCE_OVERSHOOT || value > 1f + CONFIDENCE_OVERSHOOT)
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
