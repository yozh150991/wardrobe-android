package com.familywardrobe.app;

import java.util.Arrays;

/** Deterministic alpha-only refinement and even-odd polygon selection in image coordinates. */
public final class PhotoPrecisionRules {
    private PhotoPrecisionRules() {}
    public static final int MAX_POINTS = 128;
    public static void validatePolygon(float[] points) {
        if (points == null || points.length < 6 || points.length > MAX_POINTS * 2 || points.length % 2 != 0)
            throw new IllegalArgumentException("Invalid polygon");
        for (float value : points) if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Invalid coordinate");
    }
    public static void validateEdges(int offset, int feather) {
        if (offset < -8 || offset > 8 || feather < 0 || feather > 6) throw new IllegalArgumentException("Invalid edge radius");
    }
    private static void dimensions(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > 16_000_000) throw new IllegalArgumentException();
    }
    /** Pixel-centre coverage; self intersections use the even-odd fill rule. */
    public static byte[] polygon(int width, int height, float[] points, Runnable checkpoint) {
        dimensions(width, height); validatePolygon(points);
        byte[] mask = new byte[width * height];
        double[] intersections = new double[points.length / 2];
        for (int y = 0; y < height; y++) {
            checkpoint.run(); double scan = (y + .5) / height; int count = 0;
            for (int i = 0, j = points.length - 2; i < points.length; j = i, i += 2) {
                double yi = points[i + 1], yj = points[j + 1];
                if ((yi > scan) != (yj > scan))
                    intersections[count++] = (points[i] + (scan - yi) * (points[j] - points[i]) / (yj - yi)) * width;
            }
            Arrays.sort(intersections, 0, count);
            for (int i = 0; i + 1 < count; i += 2) {
                int left = Math.max(0, (int) Math.ceil(intersections[i] - .5));
                int right = Math.min(width, (int) Math.ceil(intersections[i + 1] - .5));
                if (right > left) Arrays.fill(mask, y * width + left, y * width + right, (byte) 255);
            }
        }
        return mask;
    }
    /** Expanding can restore only alpha that exists in this session's source. Never invent RGB. */
    public static int[] edges(int[] pixels, int[] original, byte[] protection, int width, int height,
                              int offset, int feather, Runnable checkpoint) {
        dimensions(width, height); validateEdges(offset, feather);
        int n = width * height;
        if (pixels == null || original == null || protection == null || pixels.length != n || original.length != n || protection.length != n)
            throw new IllegalArgumentException();
        byte[] alpha = new byte[n];
        for (int i = 0; i < n; i++) { if ((i & 4095) == 0) checkpoint.run(); alpha[i] = (byte) (pixels[i] >>> 24); }
        if (offset != 0) {
            alpha = extremum(alpha, width, height, Math.abs(offset), offset > 0, true, checkpoint);
            alpha = extremum(alpha, width, height, Math.abs(offset), offset > 0, false, checkpoint);
        }
        if (feather != 0) {
            alpha = blur(alpha, width, height, feather, true, checkpoint);
            alpha = blur(alpha, width, height, feather, false, checkpoint);
        }
        int[] result = pixels.clone();
        for (int i = 0; i < n; i++) {
            if ((i & 4095) == 0) checkpoint.run();
            if (protection[i] != 0) continue;
            int a = Math.min(alpha[i] & 255, original[i] >>> 24);
            result[i] = (a << 24) | (pixels[i] & 0xffffff);
        }
        return result;
    }
    private static byte[] extremum(byte[] input, int width, int height, int radius, boolean max, boolean horizontal, Runnable checkpoint) {
        byte[] out = new byte[input.length];
        int length = horizontal ? width : height, lines = horizontal ? height : width, step = horizontal ? 1 : width;
        int[] queue = new int[length + radius * 2];
        for (int line = 0; line < lines; line++) {
            checkpoint.run(); int base = horizontal ? line * width : line, head = 0, tail = 0;
            for (int i = -radius; i < length + radius; i++) {
                while (head < tail && queue[head] < i - 2 * radius) head++;
                int value = sample(input, base, step, length, i);
                while (head < tail && (max ? sample(input, base, step, length, queue[tail - 1]) <= value
                                          : sample(input, base, step, length, queue[tail - 1]) >= value)) tail--;
                queue[tail++] = i;
                if (i >= radius) out[base + (i - radius) * step] = (byte) sample(input, base, step, length, queue[head]);
            }
        }
        return out;
    }
    private static byte[] blur(byte[] input, int width, int height, int radius, boolean horizontal, Runnable checkpoint) {
        byte[] out = new byte[input.length];
        int length = horizontal ? width : height, lines = horizontal ? height : width, step = horizontal ? 1 : width, size = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            checkpoint.run(); int base = horizontal ? line * width : line, sum = 0;
            for (int i = -radius; i <= radius; i++) sum += sample(input, base, step, length, i);
            for (int i = 0; i < length; i++) {
                out[base + i * step] = (byte) ((sum + size / 2) / size);
                sum += sample(input, base, step, length, i + radius + 1) - sample(input, base, step, length, i - radius);
            }
        }
        return out;
    }
    private static int sample(byte[] input, int base, int step, int length, int index) {
        return index < 0 || index >= length ? 0 : input[base + index * step] & 255;
    }
}
