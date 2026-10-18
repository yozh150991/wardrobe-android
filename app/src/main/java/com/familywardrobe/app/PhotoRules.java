package com.familywardrobe.app;

import java.util.HashMap;
import java.util.Map;

/** Conservative interpretation of the documented ML Kit default label map.
 * Pure Java so the exact production rules can also be exercised without Android.
 */
public final class PhotoRules {
    private PhotoRules() {}

    public static String category(Map<Integer, Float> scores) {
        Map<String, Float> groups = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : scores.entrySet()) {
            float score = entry.getValue();
            if (!Float.isFinite(score) || score < 0.70f || score > 1f) continue;
            String group;
            switch (entry.getKey()) {
                case 165: case 197: case 230: case 255: group = "top"; break; // Jersey, Jacket, Outerwear, Blazer
                case 19: case 51: case 249: case 390: group = "bottom"; break; // Tights, Shorts, Leggings, Jeans
                case 293: case 343: group = "shoes"; break; // Shoe, Sneakers
                default: continue;
            }
            groups.put(group, Math.max(groups.getOrDefault(group, 0f), score));
        }
        String winner = null;
        float first = 0f, second = 0f;
        for (Map.Entry<String, Float> entry : groups.entrySet()) {
            if (entry.getValue() > first) {
                second = first; first = entry.getValue(); winner = entry.getKey();
            } else second = Math.max(second, entry.getValue());
        }
        if (winner == null || first - second < 0.12f) return null;
        // One-piece garments are not represented by the current top/bottom/shoes catalog.
        for (int label : new int[] {34, 58, 68, 137, 358}) {
            if (scores.getOrDefault(label, 0f) >= first - 0.10f) return null;
        }
        return winner;
    }

    public static boolean usableCutout(int[] pixels) {
        if (pixels.length == 0) return false;
        int opaque = 0, background = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha >= 230) opaque++;
            if (alpha <= 25) background++;
        }
        return opaque >= 64 && opaque >= pixels.length * 0.02 && background >= pixels.length * 0.02;
    }

    /** Null = insufficient foreground; other = mixed or outside our current palette. */
    public static String dominantColor(int[] pixels) {
        Map<String, Integer> counts = new HashMap<>();
        int considered = 0;
        int step = Math.max(1, pixels.length / 4096);
        for (int i = 0; i < pixels.length; i += step) {
            int p = pixels[i];
            if ((p >>> 24) < 230) continue;
            String code = color(p);
            counts.put(code, counts.getOrDefault(code, 0) + 1);
            considered++;
        }
        if (considered < 32) return null;
        String winner = null;
        int first = 0, second = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > first) {
                second = first; first = entry.getValue(); winner = entry.getKey();
            } else second = Math.max(second, entry.getValue());
        }
        if (first < considered * 0.50 || first - second < considered * 0.12) return "other";
        return winner;
    }

    private static String color(int argb) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float sat = max == 0f ? 0f : delta / max;
        if (max < 0.20f) return "black";
        if (sat < 0.12f && max > 0.86f) return "white";
        if (sat < 0.14f) return "grey";
        float hue;
        if (max == r) hue = 60f * (((g - b) / delta) % 6f);
        else if (max == g) hue = 60f * (((b - r) / delta) + 2f);
        else hue = 60f * (((r - g) / delta) + 4f);
        if (hue < 0f) hue += 360f;
        if (hue >= 200f && hue <= 260f && max < 0.45f) return "navy";
        if (hue >= 195f && hue <= 260f) return "blue";
        if (hue >= 25f && hue <= 60f && sat < 0.38f && max > 0.60f) return "beige";
        if (hue >= 15f && hue < 50f && max < 0.70f) return "brown";
        if (hue >= 45f && hue <= 85f && max < 0.65f) return "olive";
        if (hue < 15f || hue > 345f) return "red";
        return "other";
    }
}
