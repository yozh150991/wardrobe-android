package com.familywardrobe.app.rules;

import java.util.*;

/**
 * TREND-01 (0.14.57) «Сезонні тренди»: colour pairs and colours of the season the person picked from posts they liked
 * (the YouTube channel @theglowup_effectglowup), read from screenshots and reduced to the base colours. The journal
 * below is updated by hand, a few entries at a time, and has no end date: old entries are removed when the person
 * says so. The same journal, with the post and the label each colour had there, is in docs/releases/TRENDS-0.14.57.md.
 *
 * <p>Like «Мої кольори» (PreferenceProfile) trends only move variants up or down; they never hide an item and never
 * change the 0–10 rating, which stays the colour theory's (StyleRules, sources [S1–S11]):
 * <ul>
 *   <li>a trend pair raises the pair's colour points by {@link #PAIR} — one tier of the table — but not above the top
 *   tier (4), so a trend never beats a pair the sources already rate best;</li>
 *   <li>an item of a colour of the season gets {@link #COLOUR} — less than a liked colour (+3), within the same per-item
 *   cap, and never for a colour the person wears less.</li>
 * </ul>
 */
public final class TrendRules {
    private TrendRules() {}

    public static final int PAIR = 1;
    public static final int COLOUR = 2;
    /** The top tier of the colour table (StyleRules.pairPoints); a trend pair never goes above it. */
    static final int TOP = 4;

    /** When the journal was last changed. */
    public static final String UPDATED = "2026-11-20";

    /** Colours of the season: seen most often in the posts (4 of 16 pairs each, 20.11.2026). */
    public static final List<String> COLOURS = Collections.unmodifiableList(Arrays.asList("burgundy", "brown"));

    /** Trend pairs, each once; the order inside a pair does not matter. */
    public static final List<List<String>> PAIRS;
    static {
        String[][] journal = {
            // 20.11.2026, post 1 (10 slides)
            {"burgundy", "black"},      // Deep Maroon + Black
            {"light_blue", "navy"},     // Blue Denim + Navy Blue
            {"white", "khaki"},         // White + Khaki
            {"grey", "black"},          // photos: grey wide trousers, black top
            {"black", "light_blue"},    // photos: black top, light blue jeans
            // 20.11.2026, post 2 (9 slides)
            {"navy", "camel"},          // Blue Stripe + Camel
            {"black", "olive"},         // Black + Olive
            {"navy", "beige"},          // Navy + Stone Beige
            {"olive", "grey"},          // Sage + Charcoal (sage = olive, the person's choice)
            {"pink", "brown"},          // Baby Pink + Chocolate
            {"burgundy", "cream"},      // Burgundy + Ivory
            {"beige", "brown"},         // Soft Taupe + Dark Brown
            // 20.11.2026, post 3 (6 slides)
            {"cream", "brown"},         // Cream + Brown
            {"black", "beige"},         // Black + Sand
            {"light_blue", "grey"},     // Blue + Charcoal
            {"burgundy", "beige"},      // Burgundy + Beige
        };
        List<List<String>> pairs = new ArrayList<>();
        for (String[] pair : journal) pairs.add(Collections.unmodifiableList(Arrays.asList(pair)));
        PAIRS = Collections.unmodifiableList(pairs);
    }
    private static final Set<String> KEYS = new HashSet<>();
    static { for (List<String> pair : PAIRS) KEYS.add(key(pair.get(0), pair.get(1))); }

    /** The same key for both orders of two colours. */
    public static String key(String a, String b) { return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a; }

    /** Two different colours that make a trend pair. */
    public static boolean pair(String a, String b) { return a != null && b != null && !a.equals(b) && KEYS.contains(key(a, b)); }

    public static boolean colour(String colour) { return COLOURS.contains(colour); }

    /** Every trend pair's key, for the generator ({@link DailyRules}); empty when the person switched trends off. */
    public static Set<String> pairs(boolean on) { return on ? Collections.unmodifiableSet(KEYS) : Collections.emptySet(); }

    /** Points a trend pair adds to two colours worth [points] in the colour table: {@link #PAIR}, up to {@link #TOP}. */
    public static int pairBonus(int points) { return Math.max(0, Math.min(PAIR, TOP - points)); }

    /** Points added per item of a colour of the season, except colours the person wears less ([avoided]). */
    public static Map<String, Integer> bonus(Collection<WardrobeRules.Piece> pieces, Set<String> avoided) {
        Map<String, Integer> result = new HashMap<>();
        for (WardrobeRules.Piece piece : pieces)
            if (colour(piece.color) && !avoided.contains(piece.color)) result.put(piece.id, COLOUR);
        return result;
    }

    /** The trend pairs among [colours] (each colour once), in the order they are first met; for the card. */
    public static List<List<String>> found(List<String> colours) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(colours));
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i++) for (int j = i + 1; j < distinct.size(); j++)
            if (pair(distinct.get(i), distinct.get(j))) result.add(Arrays.asList(distinct.get(i), distinct.get(j)));
        return result;
    }
}
