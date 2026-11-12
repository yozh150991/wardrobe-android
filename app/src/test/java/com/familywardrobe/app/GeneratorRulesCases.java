package com.familywardrobe.app;

import java.util.*;

/**
 * GEN-01/02 (0.14.36): a dress competes fairly with a top and a bottom, and the variants shown together are different
 * outfits. Runs the production DailyRules.
 */
public final class GeneratorRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Generator check " + checks + ": " + what); }
    private static DailyRules.Item it(String id, String category, String color, int wears, boolean favorite) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, "home", category, "available", false, "all", "casual", color), wears, favorite, "");
    }
    private static List<List<String>> run(List<DailyRules.Item> items, String weather, Map<String, String> fixed, int limit) {
        return DailyRules.generate(items, "home", "everyday", weather, "2026-11-11", "seed", Set.of(), fixed, limit);
    }
    private static boolean has(List<String> look, String prefix) { for (String id : look) if (id.startsWith(prefix)) return true; return false; }
    private static Set<String> base(List<String> look) {
        Set<String> result = new TreeSet<>();
        for (String id : look) if (id.startsWith("top") || id.startsWith("bottom") || id.startsWith("dress") || id.startsWith("jumpsuit")) result.add(id);
        return result;
    }
    private static int differing(List<String> a, List<String> b) {
        Set<String> left = new HashSet<>(a); left.removeAll(b); Set<String> right = new HashSet<>(b); right.removeAll(a);
        return Math.max(left.size(), right.size());
    }

    public static void main(String[] args) {
        // GEN-01. The same colours and style: a white dress with white shoes against a white top, white trousers and
        // the same shoes. Every pair is «same neutral», so only the layout differs — and it must not decide.
        List<DailyRules.Item> equal = List.of(it("top", "top", "white", 0, false), it("bottom", "bottom", "white", 0, false),
            it("dress", "dress", "white", 0, false), it("shoes", "shoes", "white", 0, false));
        List<List<String>> both = run(equal, "warm", Map.of(), 3);
        check(both.size() == 2 && has(both.get(0), "dress") != has(both.get(1), "dress"), "both layouts are offered");
        // A favourite dress leads; a favourite top leads: the item decides, not the number of pieces.
        List<DailyRules.Item> favouriteDress = List.of(it("top", "top", "white", 0, false), it("bottom", "bottom", "white", 0, false),
            it("dress", "dress", "white", 0, true), it("shoes", "shoes", "white", 0, false));
        check(has(run(favouriteDress, "warm", Map.of(), 1).get(0), "dress"), "a favourite dress leads");
        List<DailyRules.Item> favouriteTop = List.of(it("top", "top", "white", 0, true), it("bottom", "bottom", "white", 0, false),
            it("dress", "dress", "white", 0, false), it("shoes", "shoes", "white", 0, false));
        check(has(run(favouriteTop, "warm", Map.of(), 1).get(0), "top"), "a favourite top leads");
        // The case from the review: 2 tops, 2 bottoms, 2 dresses (one a favourite), 2 pairs of shoes. Before, the
        // first dress was ninth of twelve; now a dress is among the three variants.
        List<DailyRules.Item> review = List.of(it("top-white", "top", "white", 0, false), it("bottom-navy", "bottom", "navy", 0, false),
            it("top-grey", "top", "grey", 0, false), it("bottom-beige", "bottom", "beige", 0, false),
            it("dress-navy", "dress", "navy", 0, false), it("dress-fav", "dress", "beige", 0, true),
            it("shoes-white", "shoes", "white", 0, false), it("shoes-brown", "shoes", "brown", 0, false));
        List<List<String>> three = run(review, "warm", Map.of(), 3);
        check(three.size() == 3 && (has(three.get(0), "dress") || has(three.get(1), "dress") || has(three.get(2), "dress")), "a dress is among three variants");
        check(run(review, "warm", Map.of(), 12).size() == 12, "every combination is still reachable");
        // With a coat (cool weather) the same holds: four pieces against three.
        List<DailyRules.Item> cool = new ArrayList<>(equal); cool.add(it("coat", "outerwear", "white", 0, false));
        List<List<String>> coats = run(cool, "cool", Map.of(), 3);
        check(coats.size() == 2 && has(coats.get(0), "coat") && has(coats.get(1), "coat"), "both layouts with the coat");
        // A dress worn a lot still gives way: wear and favourites keep working as before.
        List<DailyRules.Item> worn = List.of(it("top", "top", "white", 0, false), it("bottom", "bottom", "white", 0, false),
            it("dress", "dress", "white", 12, false), it("shoes", "shoes", "white", 0, false));
        check(has(run(worn, "warm", Map.of(), 1).get(0), "top"), "a dress worn a lot gives way");

        // GEN-02. Three tops, three bottoms, three pairs of shoes: the three variants differ by a base piece and by
        // two items or more, pair by pair.
        List<DailyRules.Item> nine = new ArrayList<>();
        String[] colours = {"white", "grey", "navy"};
        for (int i = 0; i < 3; i++) {
            nine.add(it("top" + i, "top", colours[i], i, false)); nine.add(it("bottom" + i, "bottom", colours[(i + 1) % 3], i, false));
            nine.add(it("shoes" + i, "shoes", colours[(i + 2) % 3], i, false));
        }
        List<List<String>> varied = run(nine, "warm", Map.of(), 3);
        check(varied.size() == 3, "three variants");
        for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
            check(differing(varied.get(a), varied.get(b)) >= DailyRules.DISTINCT_ITEMS, "variants " + a + " and " + b + " differ by two items or more");
            check(!base(varied.get(a)).equals(base(varied.get(b))), "variants " + a + " and " + b + " differ by a base piece");
        }
        // The best variant is always first, and the others keep the order of the full list.
        List<List<String>> full = run(nine, "warm", Map.of(), 20);
        check(varied.get(0).equals(run(nine, "warm", Map.of(), 1).get(0)) && varied.get(0).equals(full.get(0)), "the best variant stays first");
        check(full.indexOf(varied.get(1)) < full.indexOf(varied.get(2)) && full.indexOf(varied.get(1)) > 0, "the result keeps the order");
        check(new HashSet<>(full).size() == full.size() && full.size() == 20, "a long list has no repeats");
        check(varied.equals(run(nine, "warm", Map.of(), 3)), "deterministic");
        // A small wardrobe: one top, one bottom, three pairs of shoes — still three variants (only the shoes can differ).
        List<DailyRules.Item> small = List.of(it("top", "top", "white", 0, false), it("bottom", "bottom", "navy", 0, false),
            it("shoes0", "shoes", "white", 0, false), it("shoes1", "shoes", "grey", 0, false), it("shoes2", "shoes", "brown", 0, false));
        check(run(small, "warm", Map.of(), 3).size() == 3, "a small wardrobe keeps its variants");
        // Two tops, one bottom: the variants differ by the top first, then the shoes fill the list.
        List<DailyRules.Item> twoTops = List.of(it("top0", "top", "white", 0, false), it("top1", "top", "grey", 0, false),
            it("bottom", "bottom", "navy", 0, false), it("shoes0", "shoes", "white", 0, false), it("shoes1", "shoes", "brown", 0, false));
        List<List<String>> tops = run(twoTops, "warm", Map.of(), 3);
        check(tops.size() == 3 && has(tops.get(0), "top0") != has(tops.get(1), "top0"), "the second variant has the other top");
        Set<String> topsShown = new HashSet<>(); for (List<String> look : tops) topsShown.addAll(base(look));
        check(topsShown.containsAll(Set.of("top0", "top1")), "each top appears");
        // Other shoes and another coat are not a different outfit while another top is available: the second variant
        // changes a base piece, even though the top is worn more.
        List<DailyRules.Item> coatsAndShoes = List.of(it("top0", "top", "white", 0, false), it("top1", "top", "white", 6, false),
            it("bottom", "bottom", "white", 0, false), it("shoes0", "shoes", "white", 0, false), it("shoes1", "shoes", "white", 1, false),
            it("coat0", "outerwear", "white", 0, false), it("coat1", "outerwear", "white", 1, false));
        List<List<String>> layers = run(coatsAndShoes, "cool", Map.of(), 2);
        check(layers.size() == 2 && layers.get(0).contains("top0") && layers.get(1).contains("top1"), "the second variant changes the top, not only shoes and coat");
        check(differing(layers.get(0), layers.get(1)) >= DailyRules.DISTINCT_ITEMS, "and one more piece");
        // Three tops, two pairs of shoes: the second variant changes the top and the shoes; the third brings the
        // third top rather than the first top with other shoes.
        List<DailyRules.Item> threeTops = List.of(it("top0", "top", "white", 0, false), it("top1", "top", "white", 3, false),
            it("top2", "top", "white", 6, false), it("bottom", "bottom", "white", 0, false),
            it("shoes0", "shoes", "white", 0, false), it("shoes1", "shoes", "white", 1, false));
        List<List<String>> eachTop = run(threeTops, "warm", Map.of(), 3);
        check(eachTop.size() == 3 && eachTop.get(0).equals(List.of("top0", "bottom", "shoes0")), "the best first");
        check(eachTop.get(1).equals(List.of("top1", "bottom", "shoes1")), "the second differs by the top and the shoes");
        Set<String> shownTops = new TreeSet<>(); for (List<String> look : eachTop) shownTops.addAll(base(look));
        check(shownTops.containsAll(Set.of("top0", "top1", "top2")), "the third variant brings the third top");
        // A dress with the same shoes is a different outfit (it replaces two pieces), ahead of another top.
        List<DailyRules.Item> dressOrTop = List.of(it("top0", "top", "white", 0, false), it("top1", "top", "white", 2, false),
            it("bottom", "bottom", "white", 0, false), it("dress", "dress", "white", 3, false), it("shoes", "shoes", "white", 0, false));
        List<List<String>> dressSecond = run(dressOrTop, "warm", Map.of(), 2);
        check(dressSecond.size() == 2 && dressSecond.get(0).contains("top0") && has(dressSecond.get(1), "dress"), "a dress counts as two pieces changed");
        // Pinned pieces stay in every variant; the rest still varies.
        List<List<String>> pinned = run(nine, "warm", Map.of("top", "top1"), 3);
        check(pinned.size() == 3 && pinned.stream().allMatch(look -> look.contains("top1")), "the pinned top is in every variant");
        Set<String> bottoms = new HashSet<>(); for (List<String> look : pinned) for (String id : look) if (id.startsWith("bottom")) bottoms.add(id);
        check(bottoms.size() == 3, "with a pinned top the bottoms differ");
        // A hidden combination stays hidden and does not count as taken.
        Set<String> hidden = Set.of(DailyRules.signature(varied.get(0)));
        List<List<String>> without = DailyRules.generate(nine, "home", "everyday", "warm", "2026-11-11", "seed", hidden, Map.of(), 3);
        check(without.size() == 3 && !without.contains(varied.get(0)), "a hidden combination is not shown");
        // GEN-03: only the last 30 days' wear counts.
        check(DailyRules.RECENT_DAYS == 30, "30 days");
        check(DailyRules.recentWears(List.of("2026-11-11", "2026-10-13", "2026-10-12", "2026-11-12", "2026-11-11"), "2026-11-11") == 2,
            "today and the 29 days before it count, once per day; later days and older ones do not");
        check(DailyRules.recentWears(List.of(), "2026-11-11") == 0 && DailyRules.recentWears(List.of("2026-02-28", "2026-03-01"), "2026-03-29") == 2
            && DailyRules.recentWears(List.of("2026-02-28"), "2026-03-30") == 0, "across a month boundary");
        boolean refused = false;
        try { DailyRules.recentWears(List.of("2026-13-01"), "2026-11-11"); } catch (RuntimeException expected) { refused = true; }
        check(refused, "an invalid date is refused");
        System.out.println("PASS: " + checks + " generator assertions (dress and layout fairness, distinct variants, order, pins).");
    }
}
