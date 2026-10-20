package com.familywardrobe.app;
import java.util.*;
public final class LookNameRules {
    private LookNameRules() {}
    /** Cut on a grapheme boundary, then trim: no half flags and no trailing-space "duplicates" (BUG-07). */
    private static String bounded(String text, int maximum) {
        return TextLimits.takeTrimmed(text, maximum);
    }
    public static String unique(String proposed, Collection<String> existing) {
        String base = proposed.trim(); if (base.isEmpty()) throw new IllegalArgumentException();
        base = bounded(base,80); if (base.isEmpty()) throw new IllegalArgumentException();
        Set<String> names = new HashSet<>(); for (String name : existing) names.add(name.trim().toLowerCase(Locale.ROOT));
        if (!names.contains(base.toLowerCase(Locale.ROOT))) return base;
        for (int n=2; n<Integer.MAX_VALUE; n++) {
            String suffix = " ("+n+")"; String result = bounded(base,80-suffix.length())+suffix;
            if (!names.contains(result.toLowerCase(Locale.ROOT))) return result;
        }
        throw new IllegalStateException();
    }
}
