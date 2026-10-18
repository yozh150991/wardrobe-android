package com.familywardrobe.app;

/** Pixel bounds shared by the on-screen selection and file output. */
public final class PhotoGeometry {
    private PhotoGeometry() {}
    public static int turns(int quarterTurns) { return Math.floorMod(quarterTurns, 4); }
    public static int[] crop(int width, int height, float left, float top, float right, float bottom) {
        if (width <= 0 || height <= 0 || !Float.isFinite(left) || !Float.isFinite(top) ||
            !Float.isFinite(right) || !Float.isFinite(bottom) || left < 0 || top < 0 ||
            right > 1 || bottom > 1 || left >= right || top >= bottom) {
            throw new IllegalArgumentException("Invalid crop bounds");
        }
        int x = Math.min(width - 1, Math.max(0, Math.round(width * left)));
        int y = Math.min(height - 1, Math.max(0, Math.round(height * top)));
        int endX = Math.min(width, Math.max(x + 1, Math.round(width * right)));
        int endY = Math.min(height, Math.max(y + 1, Math.round(height * bottom)));
        return new int[] {x, y, endX - x, endY - y};
    }
}
