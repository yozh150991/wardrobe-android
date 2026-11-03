package com.familywardrobe.app;

import java.util.List;

/** Base filters include their shades; a custom ID selects only that named shade. */
public final class ColorRules {
    private ColorRules() {}
    public static boolean matchesFilter(String filter, String base, String customId) {
        return filter == null || filter.isEmpty() || filter.equals(base) || filter.equals(customId);
    }

    /** COL-02: the ten base colours every app version and family server before 0.14.32 knows. */
    public static final List<String> LEGACY = List.of("white", "black", "grey", "beige", "navy", "brown", "olive", "blue", "red", "other");

    /**
     * COL-02: the nearest older base for a colour added in 0.14.32, used only when a family server has not been updated
     * (no "colors2" feature) — so a shared wardrobe stays readable for it. Older colours stay as they are.
     */
    public static String legacy(String colour) {
        switch (colour) {
            case "cream": return "white";
            case "camel": case "yellow": return "beige";
            case "khaki": case "green": return "olive";
            case "light_blue": return "blue";
            case "burgundy": case "pink": case "orange": return "red";
            case "purple": return "other";
            default: return colour;
        }
    }
}
