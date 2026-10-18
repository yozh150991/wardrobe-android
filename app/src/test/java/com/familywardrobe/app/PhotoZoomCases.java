package com.familywardrobe.app;

/** Executed against the same geometry used by the Compose viewer. */
public final class PhotoZoomCases {
    private static int assertions;
    private static void close(float expected, float actual) {
        assertions++;
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .002f)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError(); }
    private static float[] move(int w, int h, float vw, float vh, float z, float x, float y,
            float next, float ax, float ay, float dx, float dy) {
        return PhotoZoomRules.transform(w, h, vw, vh, z, x, y, next, ax, ay, dx, dy);
    }
    public static void main(String[] args) {
        assertions = 0;
        // Fitted photo stays centered despite a one-finger drag.
        float[] fit = move(1000, 1000, 300, 500, 1, 0, 0, 1, 100, 200, 80, -90);
        close(1, fit[0]); close(0, fit[1]); close(0, fit[2]);
        // Pinch preserves the touched image point, then applies the finger translation.
        float[] before = PhotoViewportRules.viewport(1000, 1000, 300, 500, 1, 0, 0);
        float[] touched = PhotoViewportRules.point(75, 200, before, false);
        float[] pinched = move(1000, 1000, 300, 500, 1, 0, 0, 2, 75, 200, 0, 0);
        close(2, pinched[0]); close(75, pinched[1]); close(50, pinched[2]);
        float[] after = PhotoViewportRules.viewport(1000, 1000, 300, 500, pinched[0], pinched[1], pinched[2]);
        float[] anchored = PhotoViewportRules.point(75, 200, after, false);
        close(touched[0], anchored[0]); close(touched[1], anchored[1]);
        float[] dragged = move(1000, 1000, 300, 500, 2, 75, 50, 2, 75, 200, -25, -20);
        close(50, dragged[1]); close(30, dragged[2]);
        float[] translated = PhotoViewportRules.viewport(1000, 1000, 300, 500, dragged[0], dragged[1], dragged[2]);
        float[] samePoint = PhotoViewportRules.point(50, 180, translated, false);
        close(touched[0], samePoint[0]); close(touched[1], samePoint[1]);
        // Zoom buttons center on the visible viewport and preserve pan proportionally.
        float[] centered = move(900, 900, 300, 300, 2, 20, -30, 3, 150, 150, 0, 0);
        close(3, centered[0]); close(30, centered[1]); close(-45, centered[2]);
        float[] back = move(900, 900, 300, 300, 3, 30, -45, 2, 150, 150, 0, 0);
        close(2, back[0]); close(20, back[1]); close(-30, back[2]);
        // Min/max are hard limits, including rapid repeated button taps.
        float[] max = move(900, 900, 300, 300, 1, 0, 0, 999, 150, 150, 0, 0);
        close(PhotoZoomRules.MAX_ZOOM, max[0]);
        float[] reset = move(900, 900, 300, 300, 5, 600, -600, .1f, 0, 0, 90, 90);
        close(1, reset[0]); close(0, reset[1]); close(0, reset[2]);
        close(3, PhotoZoomRules.DOUBLE_TAP_ZOOM);
        // Portrait, landscape, square and thin images never drag fully off screen.
        int[][] shapes = {{1000,1000}, {1800,600}, {600,1800}, {1,1800}, {1800,1}};
        for (int[] image : shapes) for (int[] view : new int[][]{{320,640}, {640,320}}) {
            for (float z : new float[]{1, 1.5f, 3, 5}) {
                float[] state = move(image[0], image[1], view[0], view[1], 1, 0, 0, z,
                        view[0] / 2f, view[1] / 2f, 100000, -100000);
                float[] frame = PhotoViewportRules.viewport(image[0], image[1], view[0], view[1], state[0], state[1], state[2]);
                if (frame[2] <= view[0]) close(0, state[1]);
                else { check(frame[0] <= .002f); check(frame[0] + frame[2] >= view[0] - .002f); }
                if (frame[3] <= view[1]) close(0, state[2]);
                else { check(frame[1] <= .002f); check(frame[1] + frame[3] >= view[1] - .002f); }
                float[] home = move(image[0], image[1], view[0], view[1], state[0], state[1], state[2], 1, 0, 0, 0, 0);
                close(0, home[1]); close(0, home[2]);
            }
        }
        // Invalid geometry is rejected instead of producing NaN drawing coordinates.
        for (float invalid : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            try { move(10,10,100,100,1,0,0,invalid,50,50,0,0); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        try { move(10,10,0,100,1,0,0,2,50,50,0,0); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("PASS: " + assertions + " photo zoom assertions against production geometry");
    }
}
