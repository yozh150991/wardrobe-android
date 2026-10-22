package com.familywardrobe.app;
import java.util.*;
public final class LookNameRules {
    private LookNameRules() {}
    /** Cut on a grapheme boundary, then trim: no half flags and no trailing-space "duplicates" (BUG-07). */
    private static String bounded(String text, int maximum) {
        return TextLimits.takeTrimmed(text, maximum);
    }
    /** Composed (NFC) form: "Café" typed as e + U+0301 and as é is one name (QA-10). */
    private static String key(String name) {
        return java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    public static String unique(String proposed, Collection<String> existing) {
        String base = java.text.Normalizer.normalize(proposed.trim(), java.text.Normalizer.Form.NFC); if (base.isEmpty()) throw new IllegalArgumentException();
        base = bounded(base,80); if (base.isEmpty()) throw new IllegalArgumentException();
        Set<String> names = new HashSet<>(); for (String name : existing) names.add(key(name));
        if (!names.contains(key(base))) return base;
        for (int n=2; n<Integer.MAX_VALUE; n++) {
            String suffix = " ("+n+")"; String result = bounded(base,80-suffix.length())+suffix;
            if (!names.contains(key(result))) return result;
        }
        throw new IllegalStateException();
    }
}
