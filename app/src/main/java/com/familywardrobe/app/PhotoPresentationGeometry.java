package com.familywardrobe.app;

/** Trim transparent margins without guessing the garment's orientation or removing any subject. */
public final class PhotoPresentationGeometry {
    public static int[] bounds(int[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length) throw new IllegalArgumentException();
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if ((pixels[y * width + x] >>> 24) > 0) {
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        return right < left ? new int[]{0, 0, width, height} : new int[]{left, top, right + 1, bottom + 1};
    }
    public static float[] fit(int[] bounds, int canvas, float margin) {
        if (canvas <= 0 || bounds.length != 4 || margin < 0 || margin >= .5f ||
            bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) throw new IllegalArgumentException();
        float scale = canvas * (1 - 2 * margin) / Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]);
        float width = (bounds[2] - bounds[0]) * scale, height = (bounds[3] - bounds[1]) * scale;
        return new float[]{(canvas - width) / 2, (canvas - height) / 2, (canvas + width) / 2, (canvas + height) / 2};
    }
}
