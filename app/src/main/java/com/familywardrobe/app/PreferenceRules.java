package com.familywardrobe.app;

import java.util.*;

/**
 * PERS-01: what a reason for turning down a suggested outfit changes, and where. Transparent rules only.
 *
 * <p>The stored reason codes stay those of backup format 10 and the family server ("dislike", "hot", "occasion"), so
 * older phones, cloud copies and shared wardrobes accept every row. A single-item signature is an item-level row.</p>
 *
 * <ul>
 *   <li>«Не подобається поєднання» — dislike, whole set: this set is never suggested again, in any context.</li>
 *   <li>«Незручно» — dislike, one item: the item is suggested less often everywhere, never hidden.</li>
 *   <li>«Надто офіційно» — occasion, whole set: in this occasion the set is not suggested, its items less often.</li>
 *   <li>«Надто тепло» — hot, whole set: in this weather the set is not suggested, its items less often.</li>
 * </ul>
 *
 * <p>Penalties are capped so that one stray reason never hides an item: an item that is the only choice for its
 * category is still suggested, and only an explicit "do not suggest" on the item excludes it.</p>
 */
public final class PreferenceRules {
    public static final String DISLIKE = "dislike";
    public static final String HOT = "hot";
    public static final String OCCASION = "occasion";
    /** "Незручно" — one item, in every context. */
    public static final int UNCOMFORTABLE = 10;
    /** "Надто офіційно" / "Надто тепло" — each item of the set, in that occasion or weather. */
    public static final int CONTEXT = 6;
    /** At most this much per item, however many reasons point at it. A matching style is worth +12. */
    public static final int MAX = 12;

    public static final class Row {
        public final String signature, occasion, weather, reason;
        public Row(String signature, String occasion, String weather, String reason) {
            this.signature = signature; this.occasion = occasion; this.weather = weather; this.reason = reason;
        }
        List<String> ids() { return Arrays.asList(signature.split("\\|")); }
        boolean item() { return !signature.contains("|"); }
    }
    private PreferenceRules() {}

    /** Whole sets that are not suggested in this occasion and weather. */
    public static Set<String> rejected(List<Row> rows, String occasion, String weather) {
        Set<String> result = new HashSet<>();
        for (Row row : rows) if (!row.item() && applies(row, occasion, weather)) result.add(row.signature);
        return result;
    }

    /**
     * Score subtracted per item in this occasion and weather, capped at {@link #MAX}. An item marked uncomfortable
     * counts once, however many contexts it was marked in.
     */
    public static Map<String, Integer> penalties(List<Row> rows, String occasion, String weather) {
        Map<String, Integer> result = new HashMap<>();
        Set<String> uncomfortable = new HashSet<>();
        for (Row row : rows) {
            if (row.item()) { if (row.reason.equals(DISLIKE)) uncomfortable.add(row.signature); continue; }
            if (row.reason.equals(DISLIKE) || !applies(row, occasion, weather)) continue;
            for (String id : row.ids()) add(result, id, CONTEXT);
        }
        for (String id : uncomfortable) add(result, id, UNCOMFORTABLE);
        return result;
    }
    private static void add(Map<String, Integer> result, String id, int amount) {
        result.merge(id, amount, (a, b) -> Math.min(MAX, a + b));
    }

    static boolean applies(Row row, String occasion, String weather) {
        switch (row.reason) {
            case DISLIKE: return true;
            case OCCASION: return row.occasion.equals(occasion);
            case HOT: return row.weather.equals(weather);
            default: return false;
        }
    }

    /**
     * "Надто тепло" moves the conditions one step lighter: cold → cool → warm. Rain stays rain (it is about the
     * rain layer, not the temperature); the penalties then steer away from the warm pieces.
     */
    public static String lighter(String weather) {
        switch (weather) {
            case "cold": return "cool";
            case "cool": case "any_weather": return "warm";
            default: return weather;
        }
    }
}
