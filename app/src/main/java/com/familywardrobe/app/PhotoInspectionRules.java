package com.familywardrobe.app;

/** Before/after divider travels across the visible photograph, including when zoomed/panned. */
public final class PhotoInspectionRules {
    private PhotoInspectionRules() {}
    public static float split(float imageLeft, float imageWidth, float canvasWidth, float fraction) {
        for (float v:new float[]{imageLeft,imageWidth,canvasWidth,fraction})
            if (!Float.isFinite(v)) throw new IllegalArgumentException();
        if (imageWidth<=0 || canvasWidth<=0 || fraction<0 || fraction>1) throw new IllegalArgumentException();
        float left=Math.max(0,Math.min(canvasWidth,imageLeft));
        float right=Math.max(left,Math.min(canvasWidth,imageLeft+imageWidth));
        return left+(right-left)*fraction;
    }
}
