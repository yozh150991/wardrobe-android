package com.familywardrobe.app;

/** Base filters include their shades; a custom ID selects only that named shade. */
public final class ColorRules {
    private ColorRules() {}
    public static boolean matchesFilter(String filter, String base, String customId) {
        return filter == null || filter.isEmpty() || filter.equals(base) || filter.equals(customId);
    }
}
