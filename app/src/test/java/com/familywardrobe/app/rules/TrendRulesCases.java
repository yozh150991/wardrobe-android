package com.familywardrobe.app.rules;

import java.util.*;

/**
 * TREND-01 (0.14.57) «Сезонні тренди»: the journal is well formed, a trend pair lifts a pair by one tier but never above
 * the top one, colours of the season get a small item bonus, and the generator follows both without hiding anything.
 * Runs the production TrendRules, DailyRules and StyleRules.
 */
public final class TrendRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Trend check " + checks + ": " + what); }
    private static WardrobeRules.Piece piece(String id, String category, String color) {
        return new WardrobeRules.Piece(id, "home", category, "available", false, "all", "casual", color);
    }
    private static DailyRules.Item it(String id, String category, String color) { return new DailyRules.Item(piece(id, category, color), 0, false, ""); }
    private static List<List<String>> run(List<DailyRules.Item> items, String seed, int limit, Map<String, Integer> bonus, Set<String> trends) {
        return DailyRules.generate(items, "home", "everyday", "warm", "2026-11-20", seed, Set.of(), Map.of(), limit, Map.of(), null, bonus, null, trends);
    }
    private static final List<String> BASE = List.of("white", "cream", "black", "grey", "beige", "camel", "khaki", "brown", "navy",
        "light_blue", "olive", "red", "orange", "yellow", "green", "blue", "purple", "pink", "burgundy");

    public static void main(String[] args) {
        // The journal: base colours only, two different colours per pair, each pair once in either order.
        check(TrendRules.PAIRS.size() == 16, "16 pairs from the posts of 20.11.2026");
        Set<String> keys = new HashSet<>();
        for (List<String> pair : TrendRules.PAIRS) {
            check(pair.size() == 2 && BASE.containsAll(pair), "a pair of two base colours: " + pair);
            check(!pair.get(0).equals(pair.get(1)), "two different colours: " + pair);
            check(keys.add(TrendRules.key(pair.get(0), pair.get(1))), "each pair once: " + pair);
            check(!StyleRules.pair(pair.get(0), pair.get(1)).equals(StyleRules.UNKNOWN), "the colour table knows " + pair);
            // Recorded in the journal: every pair from the posts already rates at least «good» (3) in the table.
            check(StyleRules.pairPoints(StyleRules.pair(pair.get(0), pair.get(1))) >= 3, "already ≥ 3 in the table: " + pair);
        }
        check(TrendRules.COLOURS.equals(List.of("burgundy", "brown")), "colours of the season: burgundy and brown");
        check(BASE.containsAll(TrendRules.COLOURS), "colours of the season are base colours");
        java.time.LocalDate.parse(TrendRules.UPDATED);
        check(TrendRules.pair("olive", "grey") && !TrendRules.pair("green", "grey"), "sage was recorded as olive");

        // Pairs are symmetric; a colour with itself, an unknown colour or null is never a trend pair.
        for (List<String> pair : TrendRules.PAIRS) check(TrendRules.pair(pair.get(1), pair.get(0)), "both orders: " + pair);
        check(TrendRules.key("navy", "camel").equals(TrendRules.key("camel", "navy")), "the key ignores the order");
        check(!TrendRules.pair("brown", "brown") && !TrendRules.pair("other", "brown") && !TrendRules.pair(null, "brown"), "no pair with itself or nothing");
        check(!TrendRules.pair("red", "green"), "a pair not in the journal");
        check(TrendRules.pairs(false).isEmpty() && TrendRules.pairs(true).size() == 16, "switched off = no pairs");

        // One tier up, never above the top one.
        check(TrendRules.pairBonus(2) == 1 && TrendRules.pairBonus(3) == 1 && TrendRules.pairBonus(4) == 0, "+1 up to 4");
        Set<String> on = TrendRules.pairs(true);
        check(DailyRules.trendScore(piece("a", "top", "burgundy"), piece("b", "bottom", "cream"), on) == 1, "burgundy + cream: 3 → 4");
        check(DailyRules.trendScore(piece("a", "top", "navy"), piece("b", "bottom", "beige"), on) == 0, "navy + beige is already 4");
        check(DailyRules.trendScore(piece("a", "top", "red"), piece("b", "bottom", "cream"), on) == 0, "red + cream is not a trend");
        check(DailyRules.trendScore(piece("a", "top", "burgundy"), piece("b", "bottom", "cream"), Set.of()) == 0, "nothing with trends off");
        check(DailyRules.pairScore(piece("a", "top", "burgundy"), piece("b", "bottom", "cream")) ==
            DailyRules.pairScore(piece("a", "top", "red"), piece("b", "bottom", "cream")), "the table itself is unchanged (manual building)");

        // Colours of the season: a small item bonus, below a liked colour, never for a colour worn less.
        List<WardrobeRules.Piece> pieces = List.of(piece("wine", "top", "burgundy"), piece("choc", "bottom", "brown"), piece("navy", "top", "navy"));
        check(TrendRules.bonus(pieces, Set.of()).equals(Map.of("wine", TrendRules.COLOUR, "choc", TrendRules.COLOUR)), "burgundy and brown items");
        check(TrendRules.bonus(pieces, Set.of("brown")).equals(Map.of("wine", TrendRules.COLOUR)), "a colour worn less gets nothing");
        check(TrendRules.COLOUR > 0 && TrendRules.COLOUR < PreferenceProfile.PALETTE_LIKED, "less than a liked colour");

        // The card lists the trend pairs among an outfit's colours, each once.
        check(TrendRules.found(List.of("burgundy", "cream", "burgundy", "brown")).equals(List.of(List.of("burgundy", "cream"), List.of("cream", "brown"))),
            "trend pairs among the colours, in order");
        check(TrendRules.found(List.of("red", "green")).isEmpty(), "no trend pair");

        // The generator: a burgundy and a red top rate the same on cream trousers with white shoes (every pair 3).
        // With trends the burgundy one leads whatever the seed; without them nothing changes for anyone.
        List<DailyRules.Item> tops = List.of(it("top-burgundy", "top", "burgundy"), it("top-red", "top", "red"),
            it("bottom", "bottom", "cream"), it("shoes", "shoes", "white"));
        for (String seed : List.of("a", "b", "c", "d", "e"))
            check(run(tops, seed, 1, Map.of(), on).get(0).contains("top-burgundy"), "a trend pair leads (seed " + seed + ")");
        check(run(tops, "a", 12, Map.of(), on).size() == run(tops, "a", 12, Map.of(), Set.of()).size(), "trends never hide a variant");
        check(run(tops, "a", 3, Map.of(), Set.of()).equals(DailyRules.generate(tops, "home", "everyday", "warm", "2026-11-20", "a", Set.of(), Map.of(), 3,
            Map.of(), null, Map.of(), null)), "trends off = the earlier generator");
        // A colour of the season through the bonus: a brown and a black bottom under a white top, white shoes.
        List<DailyRules.Item> bottoms = List.of(it("top", "top", "white"), it("bottom-brown", "bottom", "brown"),
            it("bottom-black", "bottom", "black"), it("shoes", "shoes", "white"));
        Map<String, Integer> season = TrendRules.bonus(List.of(bottoms.get(1).piece, bottoms.get(2).piece), Set.of());
        for (String seed : List.of("a", "b", "c", "d", "e"))
            check(run(bottoms, seed, 1, season, Set.of()).get(0).contains("bottom-brown"), "a colour of the season leads (seed " + seed + ")");
        // A trend never outweighs the occasion's style (+12) or a favourite (+4).
        List<DailyRules.Item> favourite = List.of(new DailyRules.Item(piece("top-red", "top", "red"), 0, true, ""), it("top-burgundy", "top", "burgundy"),
            it("bottom", "bottom", "cream"), it("shoes", "shoes", "white"));
        check(run(favourite, "a", 1, TrendRules.bonus(List.of(favourite.get(1).piece), Set.of()), on).get(0).contains("top-red"), "a favourite still leads");
        System.out.println("PASS: " + checks + " trend assertions (journal, one tier up, colours of the season, generator, nothing hidden).");
    }
}
