package com.familywardrobe.app.rules;

import java.util.Locale;

/**
 * UX-11 (0.14.47): a garment's name is optional. Without one the item is called by what the person already chose:
 * the subcategory (or the category) and the colour — «Сорочка, білий», «Взуття». Adding many items no longer means
 * typing a name for each.
 */
public final class ItemNameRules {
    private ItemNameRules() {}
    public static final int MAX = 120;

    /** [category] and [colour] are the titles shown to the person; a blank colour («Інший») is left out. */
    public static String suggest(String subcategory, String category, String colour) {
        String base = clean(subcategory).isEmpty() ? clean(category) : clean(subcategory);
        if (base.isEmpty()) return "";
        String tone = clean(colour).toLowerCase(Locale.ROOT);
        String name = tone.isEmpty() ? base : base + ", " + tone;
        return name.length() <= MAX ? name : name.substring(0, MAX).trim();
    }
    /** The name to save: the person's own when it has visible text, otherwise the suggestion. */
    public static String effective(String typed, String suggestion) {
        return typed != null && TextLimits.hasVisibleText(typed) ? typed.trim() : suggestion == null ? "" : suggestion;
    }
    private static String clean(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
}
