package com.familywardrobe.app;

/** Image-pixel diameter, with a logarithmic slider giving fine sizes enough travel. */
public final class PhotoBrushRules {
    private PhotoBrushRules() {}
    public static int maximumDiameter(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException();
        return Math.max(1, Math.round(Math.min(width, height) * .16f));
    }
    public static float radius(int diameter, int width, int height) {
        int maximum = maximumDiameter(width, height);
        if (diameter < 1 || diameter > maximum) throw new IllegalArgumentException();
        return diameter / (2f * Math.min(width, height));
    }
    public static int diameter(float position, int maximum) {
        if (!Float.isFinite(position) || position < 0 || position > 1 || maximum < 1) throw new IllegalArgumentException();
        return Math.max(1, Math.min(maximum, Math.round((float) Math.exp(position * Math.log(maximum)))));
    }
    public static float position(int diameter, int maximum) {
        if (maximum < 1 || diameter < 1 || diameter > maximum) throw new IllegalArgumentException();
        return maximum == 1 ? 0 : (float) (Math.log(diameter) / Math.log(maximum));
    }
}
