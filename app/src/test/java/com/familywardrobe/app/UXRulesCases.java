package com.familywardrobe.app;

import java.util.Arrays;

public final class UXRulesCases {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("UX case " + checks); }
    public static void main(String[] args) {
        PhotoWorkGate gate = new PhotoWorkGate();
        gate.activate("account-a:editor-1");
        long first = gate.begin("account-a:editor-1", "photo-1");
        check(gate.accepts(first, "account-a:editor-1", "photo-1"));
        check(!gate.accepts(first, "account-a:editor-1", "photo-2"));
        check(!gate.accepts(first, "account-b:editor-1", "photo-1"));
        gate.activate("account-a:editor-1"); // Activity recreation of the same editor.
        check(gate.accepts(first, "account-a:editor-1", "photo-1"));
        long second = gate.begin("account-a:editor-1", "photo-2");
        check(!gate.accepts(first, "account-a:editor-1", "photo-1"));
        check(gate.accepts(second, "account-a:editor-1", "photo-2"));
        gate.cancel(); // Manual crop, save, keep current image or sign-out.
        check(!gate.accepts(second, "account-a:editor-1", "photo-2"));
        long third = gate.begin("account-a:editor-1", "photo-3");
        gate.activate("account-a:editor-2");
        check(!gate.accepts(third, "account-a:editor-1", "photo-3"));
        int[] pixels = new int[7 * 9];
        pixels[2 * 7 + 1] = 0x01000000; // Preserve soft edges, not only opaque pixels.
        pixels[7 * 7 + 5] = 0xff123456;
        check(Arrays.equals(new int[]{1, 2, 6, 8}, PhotoPresentationGeometry.bounds(pixels, 7, 9)));
        check(Arrays.equals(new int[]{0, 0, 7, 9}, PhotoPresentationGeometry.bounds(new int[63], 7, 9)));
        for (int width : new int[]{1, 3, 400, 1800}) for (int height : new int[]{1, 5, 900, 1800}) {
            float[] rect = PhotoPresentationGeometry.fit(new int[]{0, 0, width, height}, 1024, .08f);
            check(Math.abs(rect[0] + rect[2] - 1024) < .001f);
            check(Math.abs(rect[1] + rect[3] - 1024) < .001f);
            check(rect[0] >= 81.9f && rect[1] >= 81.9f && rect[2] <= 942.1f && rect[3] <= 942.1f);
            check(Math.abs((rect[2] - rect[0]) / (rect[3] - rect[1]) - (float) width / height) < .15f);
        }
        try { PhotoPresentationGeometry.bounds(new int[3], 2, 2); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        try { PhotoPresentationGeometry.fit(new int[]{0,0,0,1}, 1024, .08f); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS: " + checks + " production photo-result ownership and image-presentation checks.");
    }
}
