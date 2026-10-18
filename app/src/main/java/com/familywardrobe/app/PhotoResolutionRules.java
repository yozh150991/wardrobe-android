package com.familywardrobe.app;

/** Bounded raster sizes. Larger canvases preserve available detail; they never invent pixels. */
public final class PhotoResolutionRules {
    private PhotoResolutionRules() {}
    public static int finalLimit(long heapBytes) {
        return heapBytes >= 256L * 1024 * 1024 ? 2048 : heapBytes >= 192L * 1024 * 1024 ? 1536 : 1024;
    }
    public static int sourceLimit(int finalLimit) { return finalLimit == 2048 ? 2560 : 1800; }
    public static long pixelLimit(int finalLimit) { return finalLimit == 2048 ? 4_194_304L : 3_240_000L; }
    public static int sample(int width, int height, int longest, long pixels) {
        if (width < 1 || height < 1 || longest < 1 || pixels < 1) throw new IllegalArgumentException();
        int sample = 1;
        while (ceil(width, sample) > longest || ceil(height, sample) > longest ||
                ceil(width, sample) * ceil(height, sample) > pixels) {
            if (sample >= (1 << 30)) throw new IllegalArgumentException();
            sample *= 2;
        }
        return sample;
    }
    private static long ceil(int value, int sample) { return ((long) value + sample - 1) / sample; }
    public static boolean matchesMask(int width, int height, int maskWidth, int maskHeight, int maximum) {
        int sample = sample(width, height, sourceLimit(maximum), pixelLimit(maximum));
        // Platform codecs may round a subsampled odd dimension up or down by one pixel.
        return maskWidth > 0 && maskHeight > 0 && maskWidth >= Math.max(1, width / sample) &&
                maskWidth <= ceil(width, sample) && maskHeight >= Math.max(1, height / sample) &&
                maskHeight <= ceil(height, sample);
    }
    public static int canvas(int[] bounds, int maximum, float margin) {
        if (bounds.length != 4 || maximum < 1 || !Float.isFinite(margin) || margin < 0 || margin >= .5f ||
                bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) throw new IllegalArgumentException();
        int longest = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]);
        return Math.max(1, Math.min(maximum, (int) Math.floor(longest / (1d - 2d * margin))));
    }
}
