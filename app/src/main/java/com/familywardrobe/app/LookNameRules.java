package com.familywardrobe.app;
import java.util.*;
public final class LookNameRules {
    private LookNameRules() {}
    private static String bounded(String text, int maximum) {
        int end = Math.min(text.length(), maximum);
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end-1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
    public static String unique(String proposed, Collection<String> existing) {
        String base = proposed.trim(); if (base.isEmpty()) throw new IllegalArgumentException();
        base = bounded(base,80);
        Set<String> names = new HashSet<>(); for (String name : existing) names.add(name.trim().toLowerCase(Locale.ROOT));
        if (!names.contains(base.toLowerCase(Locale.ROOT))) return base;
        for (int n=2; n<Integer.MAX_VALUE; n++) {
            String suffix = " ("+n+")"; String result = bounded(base,80-suffix.length())+suffix;
            if (!names.contains(result.toLowerCase(Locale.ROOT))) return result;
        }
        throw new IllegalStateException();
    }
}
