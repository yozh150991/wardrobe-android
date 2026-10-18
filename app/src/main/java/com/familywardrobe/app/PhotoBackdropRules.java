package com.familywardrobe.app;

/** Display backdrop only: the editable PNG and collage transparency remain intact. */
public final class PhotoBackdropRules {
    public static final int WHITE = 0xffffffff;
    public static final int CREAM = 0xffeee5d5;
    private PhotoBackdropRules() {}

    public static int background(int[] samples, String garmentColour) {
        if ("white".equals(garmentColour)) return CREAM;
        int opaque = 0, white = 0;
        for (int p : samples) {
            if ((p >>> 24) < 230) continue;
            opaque++;
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max >= 218 && min >= 195 && max - min <= 30) white++;
        }
        return opaque >= 16 && white >= opaque * .35 ? CREAM : WHITE;
    }
}
