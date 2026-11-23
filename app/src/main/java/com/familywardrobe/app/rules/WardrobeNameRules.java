package com.familywardrobe.app.rules;

import java.util.Collection;
import java.util.Locale;

/** QA-61-06: two wardrobes never share a name, or the switcher shows two identical rows.
 * Names are compared as people read them: ignoring case and runs of spaces.
 */
public final class WardrobeNameRules {
    private WardrobeNameRules() {}

    public static String key(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** True when name is already used by one of the other wardrobes (the wardrobe being renamed is not among them). */
    public static boolean taken(Collection<String> others, String name) {
        String wanted = key(name);
        if (wanted.isEmpty()) return false;
        for (String other : others) if (key(other).equals(wanted)) return true;
        return false;
    }
}
