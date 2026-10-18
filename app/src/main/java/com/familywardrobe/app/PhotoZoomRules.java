package com.familywardrobe.app;

/** Read-only viewer geometry; reuses the editor's fit and pan limits. */
public final class PhotoZoomRules {
    public static final float MIN_ZOOM = 1f;
    public static final float MAX_ZOOM = 5f;
    public static final float DOUBLE_TAP_ZOOM = 3f;
    private PhotoZoomRules() {}

    /** Returns zoom, panX, panY, keeping the touched image point under the gesture centroid. */
    public static float[] transform(int imageWidth, int imageHeight, float viewWidth, float viewHeight,
            float zoom, float panX, float panY, float requestedZoom,
            float anchorX, float anchorY, float dragX, float dragY) {
        if (!Float.isFinite(requestedZoom) || !Float.isFinite(anchorX) || !Float.isFinite(anchorY)
                || !Float.isFinite(dragX) || !Float.isFinite(dragY)) throw new IllegalArgumentException();
        float[] old = PhotoViewportRules.viewport(imageWidth, imageHeight, viewWidth, viewHeight, zoom, panX, panY);
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
        if (next == MIN_ZOOM) return new float[]{MIN_ZOOM, 0f, 0f};
        float ratio = next / zoom;
        float x = anchorX - viewWidth / 2f;
        float y = anchorY - viewHeight / 2f;
        float[] frame = PhotoViewportRules.viewport(imageWidth, imageHeight, viewWidth, viewHeight, next,
                x - (x - old[4]) * ratio + dragX, y - (y - old[5]) * ratio + dragY);
        return new float[]{next, frame[4], frame[5]};
    }
}
