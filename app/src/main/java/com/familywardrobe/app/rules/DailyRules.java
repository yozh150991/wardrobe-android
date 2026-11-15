package com.familywardrobe.app.rules;

import java.util.*;

/** Bounded, deterministic outfit ranking; all candidates must be real, available owned items. */
public final class DailyRules {
    public static final class Item {
        public final WardrobeRules.Piece piece;
        public final int wears;
        public final boolean favorite;
        public final String lastWear;
        public Item(WardrobeRules.Piece piece, int wears, boolean favorite, String lastWear) {
            this.piece = piece; this.wears = wears; this.favorite = favorite; this.lastWear = lastWear;
        }
    }
    private static final class Candidate {
        final List<Item> items;
        /** Beam order inside one layout: the items' own scores plus every pair's score. */
        final int score;
        /** The pairs' part of [score], kept apart for the final order (see {@link #rank}). */
        final int pairs;
        /** STYLE: the whole outfit's 0–10 rating, worked out once for the final order. */
        private int rating = -1;
        // Sorting compares tied scores by signature; building it on every comparison dominated the run time.
        private String key;
        private int tie;
        private boolean tied;
        Candidate(List<Item> items, int score, int pairs) { this.items = items; this.score = score; this.pairs = pairs; }
        String key() {
            if (key == null) { List<String> ids = new ArrayList<>(); for (Item i : items) ids.add(i.piece.id); key = signature(ids); }
            return key;
        }
        int tie(String seed) { if (!tied) { tie = Objects.hash(seed, key()); tied = true; } return tie; }
        int rating(String weather, String style, Double temperature) {
            if (rating < 0) { List<WardrobeRules.Piece> pieces = new ArrayList<>(); for (Item i : items) pieces.add(i.piece); rating = StyleRules.rate(pieces, weather, style, temperature).score; }
            return rating;
        }
        /**
         * The final order across layouts: the average item score, the average pair score and [RATING_WEIGHT] per
         * rating point. GEN-01 (0.14.36): pairs are averaged over the pairs, not the items. Before, a dress with
         * shoes (one pair for two items) counted half a pair per item while a top, a bottom and shoes (three pairs)
         * counted a whole one, so a dress almost never reached the three variants; an outfit with a coat (six pairs
         * for four items) counted one and a half. For three items the value is the same as before.
         */
        int rank(String weather, String style, Double temperature) {
            int size = Math.max(1, items.size()), pairCount = Math.max(1, items.size() * (items.size() - 1) / 2);
            return (score - pairs) * 1000 / size + pairs * 1000 / pairCount + RATING_WEIGHT * rating(weather, style, temperature);
        }
    }
    /**
     * 0.14.33: how much one point of the outfit rating counts in the final order — half a point of the per-item
     * average, so the rating decides between close variants (three colours at most, no «with care» pair) without
     * overriding the person's own reasons, wears and favourites.
     */
    static final int RATING_WEIGHT = 500;

    /**
     * 0.14.33: two pieces together — the colour pair from the sourced table ([StyleRules], 2–4 points) and 4 when the
     * styles match. Before, the colours used WardrobeRules.score (neutral 3, same 2, olive + blue 1, otherwise 0).
     */
    public static int pairScore(WardrobeRules.Piece a, WardrobeRules.Piece b) {
        return StyleRules.pairPoints(StyleRules.pair(a.color, b.color)) + (a.style.equals(b.style) ? 4 : 0);
    }
    /** The item season that suits [weather]; "all" fits every season. */
    public static String season(String weather) {
        return weather.equals("warm") ? "summer" : weather.equals("cold") ? "winter" :
            weather.equals("cool") || weather.equals("rain") ? "midseason" : "all";
    }
    public static String signature(Collection<String> ids) {
        List<String> sorted = new ArrayList<>(new HashSet<>(ids)); Collections.sort(sorted); return String.join("|", sorted);
    }
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit) {
        return generate(items, wardrobe, occasion, weather, date, seed, rejected, fixed, limit, Collections.emptyMap());
    }
    /** [penalty]: score subtracted per item ID (PERS-01 reasons, see PreferenceRules); never excludes an item. */
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit, Map<String, Integer> penalty) {
        return generate(items, wardrobe, occasion, weather, date, seed, rejected, fixed, limit, penalty, null);
    }
    /** [targetStyle]: the style favoured for this occasion (PERS-02, see PreferenceProfile); null = the occasion's usual one. */
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit, Map<String, Integer> penalty,
            String targetStyle) {
        return generate(items, wardrobe, occasion, weather, date, seed, rejected, fixed, limit, penalty, targetStyle, Collections.emptyMap());
    }
    /**
     * [bonus]: score added per item ID (PERS-03 liked colours, see PreferenceProfile), at most
     * {@link PreferenceProfile#PALETTE_LIKED}; like a penalty it never decides on its own whether an item can be used.
     */
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit, Map<String, Integer> penalty,
            String targetStyle, Map<String, Integer> bonus) {
        return generate(items, wardrobe, occasion, weather, date, seed, rejected, fixed, limit, penalty, targetStyle, bonus, null);
    }
    /**
     * [temperature]: the forecast's temperature behind [weather] (WTH-01, see WeatherRules), or null when the weather
     * was chosen by hand. It decides the coat, the second layer, tights and which seasons' items suit.
     * GEN-04: the pieces weather adds are optional — without a ready one the outfit is still suggested.
     */
    /**
     * GEN-03 (0.14.39): wear counts against an item only within this many days. Before, every wear day of the item's
     * whole history counted (up to −12), so after a year most items sat at the cap and a well-loved favourite lost
     * to a new item for good.
     */
    public static final int RECENT_DAYS = 30;
    /** Distinct wear days in the {@link #RECENT_DAYS} days up to and including [today]; later days do not count. */
    public static int recentWears(Collection<String> dates, String today) {
        java.time.LocalDate end = java.time.LocalDate.parse(today), start = end.minusDays(RECENT_DAYS - 1);
        Set<String> days = new HashSet<>();
        for (String date : dates) {
            java.time.LocalDate day = java.time.LocalDate.parse(date);
            if (!day.isBefore(start) && !day.isAfter(end)) days.add(date);
        }
        return days.size();
    }
    static final List<String> WEATHER_PIECES = Collections.unmodifiableList(Arrays.asList("outerwear", "layer", "tights"));
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit, Map<String, Integer> penalty,
            String targetStyle, Map<String, Integer> bonus, Double temperature) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException();
        String style = targetStyle != null ? targetStyle : PreferenceProfile.defaultStyle(occasion);
        List<Candidate> all = new ArrayList<>();
        for (List<String> layout : WardrobeRules.baseLayouts(fixed.keySet())) {
        List<String> categories = new ArrayList<>(layout);
        if (WeatherRules.layer(weather, temperature)) categories.add("layer");
        if (layout.contains("dress") && WeatherRules.tights(weather, temperature)) categories.add("tights");
        if (WeatherRules.outerwear(weather, temperature)) categories.add("outerwear");
        if (!WardrobeRules.CATEGORIES.containsAll(fixed.keySet())) return Collections.emptyList();
        for (String category : WardrobeRules.CATEGORIES) if (fixed.containsKey(category) && !categories.contains(category)) categories.add(category);
        List<Candidate> beam = new ArrayList<>(); beam.add(new Candidate(Collections.emptyList(), 0, 0));
        for (String category : categories) {
            List<Item> choices = new ArrayList<>();
            for (Item item : items) {
                WardrobeRules.Piece p = item.piece;
                if (wardrobe.equals(p.wardrobe) && category.equals(p.category) && WardrobeRules.ready(p) &&
                    WeatherRules.inSeason(p.season, weather, temperature) &&
                    (!fixed.containsKey(category) || fixed.get(category).equals(p.id))) choices.add(item);
            }
            if (choices.isEmpty()) {
                // What the weather adds is preferred, not a base piece: without a ready, season-compatible coat,
                // jumper or tights the look is still suggested. A pinned piece stays mandatory.
                if (WEATHER_PIECES.contains(category) && !fixed.containsKey(category)) continue;
                beam = Collections.emptyList(); break;
            }
            choices.sort(Comparator.<Item>comparingInt(i -> itemScore(i, style, date, penalty, bonus)).reversed()
                .thenComparingInt(i -> Objects.hash(seed, i.piece.id)).thenComparing(i -> i.piece.id));
            if (choices.size() > 24) choices = new ArrayList<>(choices.subList(0, 24));
            List<Candidate> expanded = new ArrayList<>();
            for (Candidate prior : beam) for (Item next : choices) {
                boolean compatible = true;
                int pairs = prior.pairs;
                for (Item selected : prior.items) {
                    if (selected.piece.id.equals(next.piece.id) || !WardrobeRules.compatibleSeasons(selected.piece.season, next.piece.season)) { compatible = false; break; }
                    pairs += pairScore(selected.piece, next.piece);
                }
                int score = prior.score - prior.pairs + itemScore(next, style, date, penalty, bonus) + pairs;
                if (compatible) { List<Item> picked = new ArrayList<>(prior.items); picked.add(next); expanded.add(new Candidate(picked, score, pairs)); }
            }
            expanded.sort(Comparator.<Candidate>comparingInt(c -> c.score).reversed()
                .thenComparingInt(c -> c.tie(seed)).thenComparing(DailyRules::key));
            // Keep a broad beam so rejected top looks do not eliminate every alternative.
            beam = new ArrayList<>(expanded.subList(0, Math.min(256, expanded.size())));
            if (beam.isEmpty()) break;
        }
        all.addAll(beam);
        }
        all.sort(Comparator.<Candidate>comparingInt(c -> c.rank(weather, style, temperature)).reversed()
            .thenComparingInt(c -> c.tie(seed)).thenComparing(DailyRules::key));
        List<Candidate> shown = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Candidate c : all) if (!rejected.contains(c.key()) && seen.add(c.key())) shown.add(c);
        List<List<String>> result = new ArrayList<>();
        for (Candidate c : distinct(shown, limit)) {
            List<String> ids = new ArrayList<>(); for (Item i : c.items) ids.add(i.piece.id);
            result.add(ids);
        }
        return result;
    }
    /**
     * GEN-02 (0.14.36): the variants shown together should be different outfits, not one outfit with other shoes.
     * From [ordered] (best first) take up to [limit], in three passes, each keeping the order:
     * <ol>
     *   <li>variants that differ from every one already taken by {@link #DISTINCT_ITEMS} items or more, at least one
     *   of them a base piece (top, bottom, dress or jumpsuit);</li>
     *   <li>then ones that differ by a base piece;</li>
     *   <li>then whatever is left, so a small wardrobe (or pinned pieces) still gets its variants.</li>
     * </ol>
     * The best variant is always first, and the result stays in the original order.
     */
    static final int DISTINCT_ITEMS = 2;
    private static List<Candidate> distinct(List<Candidate> ordered, int limit) {
        if (ordered.size() <= limit || limit == 1) return ordered.subList(0, Math.min(limit, ordered.size()));
        List<Integer> taken = new ArrayList<>();
        for (int pass = 0; pass < 3 && taken.size() < limit; pass++) {
            for (int index = 0; index < ordered.size() && taken.size() < limit; index++) {
                if (taken.contains(index)) continue;
                boolean fits = true;
                for (int other : taken) {
                    int[] d = difference(ordered.get(index), ordered.get(other));
                    if (pass == 0 && (d[0] < DISTINCT_ITEMS || d[1] == 0) || pass == 1 && d[1] == 0) { fits = false; break; }
                }
                if (fits) taken.add(index);
            }
        }
        Collections.sort(taken);
        List<Candidate> result = new ArrayList<>(); for (int index : taken) result.add(ordered.get(index));
        return result;
    }
    /** [0]: how many items one outfit has that the other lacks (the larger side); [1]: how many of those are base pieces. */
    private static int[] difference(Candidate a, Candidate b) {
        int[] left = missing(a, b), right = missing(b, a);
        return new int[]{Math.max(left[0], right[0]), Math.max(left[1], right[1])};
    }
    private static int[] missing(Candidate from, Candidate in) {
        int items = 0, base = 0;
        for (Item i : from.items) {
            boolean found = false;
            for (Item j : in.items) if (i.piece.id.equals(j.piece.id)) { found = true; break; }
            if (!found) { items++; if (WardrobeRules.BASE_CATEGORIES.contains(i.piece.category)) base++; }
        }
        return new int[]{items, base};
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather,
            String date, List<String> selected, String category, Set<String> rejected) {
        return replace(items, wardrobe, occasion, weather, date, selected, category, rejected, Collections.emptyMap());
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather,
            String date, List<String> selected, String category, Set<String> rejected, Map<String, Integer> penalty) {
        return replace(items, wardrobe, occasion, weather, date, selected, category, rejected, penalty, null);
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather,
            String date, List<String> selected, String category, Set<String> rejected, Map<String, Integer> penalty, String targetStyle) {
        return replace(items, wardrobe, occasion, weather, date, selected, category, rejected, penalty, targetStyle, Collections.emptyMap());
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather, String date,
            List<String> selected, String category, Set<String> rejected, Map<String, Integer> penalty, String targetStyle,
            Map<String, Integer> bonus) {
        return replace(items, wardrobe, occasion, weather, date, selected, category, rejected, penalty, targetStyle, bonus, null);
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather, String date,
            List<String> selected, String category, Set<String> rejected, Map<String, Integer> penalty, String targetStyle,
            Map<String, Integer> bonus, Double temperature) {
        Map<String, String> fixed = new HashMap<>(); String old = "";
        for (String id : selected) {
            Item item = null; for (Item candidate : items) if (candidate.piece.id.equals(id)) { item = candidate; break; }
            if (item == null) return Collections.emptyList();
            if (category.equals(item.piece.category)) old = id; else fixed.put(item.piece.category, id);
        }
        final String excluded = old;
        List<Item> candidates = new ArrayList<>(); for (Item i : items) if (!i.piece.id.equals(excluded)) candidates.add(i);
        List<List<String>> result = generate(candidates, wardrobe, occasion, weather, date, "replace", rejected, fixed, 1, penalty, targetStyle, bonus, temperature);
        return result.isEmpty() ? Collections.emptyList() : result.get(0);
    }
    private static String key(Candidate c) { return c.key(); }
    private static int itemScore(Item item, String style, String date, Map<String, Integer> penalty, Map<String, Integer> bonus) {
        return (item.piece.style.equals(style) ? 12 : 0) + (item.favorite ? 4 : 0) - Math.min(12, Math.max(0, item.wears))
            - (date.equals(item.lastWear) ? 15 : 0) - Math.min(PreferenceRules.MAX, Math.max(0, penalty.getOrDefault(item.piece.id, 0)))
            + Math.min(PreferenceProfile.PALETTE_LIKED, Math.max(0, bonus.getOrDefault(item.piece.id, 0)));
    }
}
