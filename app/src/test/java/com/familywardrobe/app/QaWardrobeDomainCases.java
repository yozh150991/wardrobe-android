package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;
import static com.familywardrobe.app.QaCheck.expect;

import java.util.*;

/**
 * Wardrobe, outfit, canvas, calendar, recommendation, diagnostics and purchase-advice rules.
 * Techniques: EP, BVA, EG (regressions of fixed QA-05, QA-13).
 */
public final class QaWardrobeDomainCases {
    private QaWardrobeDomainCases() {}
    static final String W = "w";
    static final String DATE = "2026-10-22";

    public static void main(String[] args) { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaWardrobeDomainCases");
        categories(qa);
        collectionsAndMatching(qa);
        canvas(qa);
        roadmap(qa);
        recommendation(qa);
        diagnostics(qa);
        purchaseAdvice(qa);
        qa.done();
    }

    static WardrobeRules.Piece piece(String id, String category, String season, String availability, boolean archived) {
        return new WardrobeRules.Piece(id, W, category, availability, archived, season, "casual", "black");
    }
    static WardrobeRules.Piece piece(String id, String category) { return piece(id, category, "all", "available", false); }
    static DailyRules.Item item(String id, String category, String season, String availability, String lastWear) {
        return new DailyRules.Item(piece(id, category, season, availability, false), 0, false, lastWear);
    }
    static DailyRules.Item item(String id, String category) { return item(id, category, "all", "available", ""); }

    private static List<String> list(String... values) { return Arrays.asList(values); }
    private static List<String> repeat(String value, int times) { return Collections.nCopies(times, value); }

    private static void categories(QaCheck qa) {
        // EP: valid look shapes and the invalid classes.
        qa.isTrue(EP, "top+bottom+shoes", WardrobeRules.validCategories(list("top", "bottom", "shoes")));
        qa.isTrue(EP, "dress+shoes", WardrobeRules.validCategories(list("dress", "shoes")));
        qa.isTrue(EP, "jumpsuit+layer+bag", WardrobeRules.validCategories(list("jumpsuit", "layer", "bag")));
        qa.isFalse(EP, "empty", WardrobeRules.validCategories(list()));
        qa.isFalse(EP, "unknown category", WardrobeRules.validCategories(list("top", "socks")));
        qa.isFalse(EP, "dress with top", WardrobeRules.validCategories(list("dress", "top", "shoes")));
        qa.isFalse(EP, "jumpsuit with bottom", WardrobeRules.validCategories(list("jumpsuit", "bottom")));
        qa.isFalse(EP, "dress with jumpsuit", WardrobeRules.validCategories(list("dress", "jumpsuit")));
        // BVA: per-category limits (1 / 2 / 4) and the 12-piece maximum.
        for (String category : WardrobeRules.CATEGORIES) {
            int limit = WardrobeRules.categoryLimit(category);
            qa.isTrue(BVA, category + " at limit " + limit, WardrobeRules.validCategories(repeat(category, limit)));
            qa.isFalse(BVA, category + " over limit", WardrobeRules.validCategories(repeat(category, limit + 1)));
        }
        List<String> twelve = new ArrayList<>(list("top", "bottom", "shoes", "outerwear", "bag", "hat", "tights", "other_item"));
        twelve.addAll(repeat("accessory", 4));
        qa.equal(BVA, "fixture has 12", 12, twelve.size());
        qa.isTrue(BVA, "12 pieces", WardrobeRules.validCategories(twelve));
        List<String> thirteen = new ArrayList<>(twelve); thirteen.add("layer");
        qa.isFalse(BVA, "13 pieces", WardrobeRules.validCategories(thirteen));
        // EP: complete looks need shoes and a base.
        qa.isTrue(EP, "complete core", WardrobeRules.completeCategories(list("shoes", "bottom", "top")));
        qa.isTrue(EP, "complete dress", WardrobeRules.completeCategories(list("dress", "shoes", "outerwear")));
        qa.isFalse(EP, "no shoes", WardrobeRules.completeCategories(list("top", "bottom")));
        qa.isFalse(EP, "no bottom", WardrobeRules.completeCategories(list("top", "shoes")));
        qa.equal(EP, "base layouts for a fixed dress", List.of(list("dress", "shoes")), WardrobeRules.baseLayouts(Set.of("dress")));
        qa.equal(EP, "base layouts for a fixed bag", 3, WardrobeRules.baseLayouts(Set.of("bag")).size());
        qa.equal(EG, "fixed top and dress have no layout", 0, WardrobeRules.baseLayouts(Set.of("top", "dress")).size());
    }

    private static void collectionsAndMatching(QaCheck qa) {
        WardrobeRules.Piece clean = piece("a", "top"), laundry = piece("b", "top", "all", "laundry", false),
            archived = piece("c", "top", "all", "available", true), lent = piece("d", "top", "all", "lent", false);
        // EP: collections.
        qa.isTrue(EP, "wardrobe shows clean", WardrobeRules.inCollection("wardrobe", clean));
        qa.isTrue(EP, "wardrobe shows laundry", WardrobeRules.inCollection("wardrobe", laundry));
        qa.isFalse(EP, "wardrobe hides archive", WardrobeRules.inCollection("wardrobe", archived));
        qa.isTrue(EP, "laundry shows laundry", WardrobeRules.inCollection("laundry", laundry));
        qa.isFalse(EP, "laundry hides lent", WardrobeRules.inCollection("laundry", lent));
        qa.isTrue(EP, "archive shows archived", WardrobeRules.inCollection("archive", archived));
        qa.isFalse(EG, "unknown collection shows nothing", WardrobeRules.inCollection("trash", clean));
        // EP: readiness.
        qa.isTrue(EP, "available is ready", WardrobeRules.ready(clean));
        for (WardrobeRules.Piece p : list2(laundry, archived, lent, piece("e", "top", "all", "repair", false), piece("f", "socks")))
            qa.isFalse(EP, "not ready " + p.id, WardrobeRules.ready(p));
        // EP: season compatibility table.
        String[] seasons = {"all", "summer", "winter", "midseason"};
        for (String a : seasons) for (String b : seasons)
            qa.equal(EP, "season " + a + "/" + b, a.equals("all") || b.equals("all") || a.equals(b), WardrobeRules.compatibleSeasons(a, b));
        // EP: colour scores.
        qa.equal(EP, "neutral pair", 3 + 4, WardrobeRules.score(colour("black"), colour("white")));
        qa.equal(EP, "same colour", 2 + 4, WardrobeRules.score(colour("red"), colour("red")));
        qa.equal(EP, "olive/blue", 1 + 4, WardrobeRules.score(colour("olive"), colour("blue")));
        qa.equal(EP, "red/blue clash", 4, WardrobeRules.score(colour("red"), colour("blue")));
        qa.equal(EP, "other is neutral to nothing", 4, WardrobeRules.score(colour("other"), colour("black")));
        // EP: candidates exclude every invalid class.
        List<WardrobeRules.Piece> pool = list2(piece("b1", "bottom"), piece("b2", "bottom", "winter", "available", false),
            piece("b3", "bottom", "all", "laundry", false), piece("b4", "bottom", "all", "available", true),
            new WardrobeRules.Piece("b5", "other", "bottom", "available", false, "all", "casual", "black"), piece("s1", "shoes"));
        WardrobeRules.Piece summerTop = piece("t1", "top", "summer", "available", false);
        List<String> ids = new ArrayList<>();
        for (WardrobeRules.Piece p : WardrobeRules.candidates(summerTop, pool, "bottom")) ids.add(p.id);
        qa.equal(EP, "only ready, same-wardrobe, season-compatible bottoms", list("b1"), ids);
        qa.equal(EG, "unavailable base has no candidates", 0, WardrobeRules.candidates(piece("x", "top", "all", "laundry", false), pool, "bottom").size());
        // Build / first outfit.
        List<WardrobeRules.Piece> wardrobe = list2(piece("t", "top"), piece("b", "bottom"), piece("s", "shoes"), piece("d", "dress"));
        qa.equal(EP, "build from top", list("t", "b", "s"), WardrobeRules.build(piece("t", "top"), wardrobe));
        qa.equal(EP, "build from dress", list("d", "s"), WardrobeRules.build(piece("d", "dress"), wardrobe));
        qa.isTrue(EP, "first outfit complete", WardrobeRules.completeCategories(categoriesOf(WardrobeRules.firstOutfit(W, wardrobe), wardrobe)));
        qa.equal(EG, "first outfit of an empty wardrobe", list(), WardrobeRules.firstOutfit(W, list2()));
        qa.equal(EG, "first outfit of another wardrobe", list(), WardrobeRules.firstOutfit("other", wardrobe));
        qa.equal(EP, "starting categories prefer a ready dress", list("dress", "shoes"), WardrobeRules.startingCategories(W, wardrobe));
        qa.equal(BVA, "wear days count distinct dates", 2, WardrobeRules.wearDays(list(DATE, DATE, "2026-10-21")));
        qa.equal(BVA, "wear days of nothing", 0, WardrobeRules.wearDays(list()));
    }

    @SafeVarargs private static <T> List<T> list2(T... values) { return Arrays.asList(values); }
    private static int pieces;
    private static WardrobeRules.Piece colour(String colour) {
        return new WardrobeRules.Piece(colour + "-" + (++pieces), W, "top", "available", false, "all", "casual", colour);
    }
    private static List<String> categoriesOf(List<String> ids, List<WardrobeRules.Piece> pieces) {
        List<String> result = new ArrayList<>();
        for (String id : ids) for (WardrobeRules.Piece p : pieces) if (p.id.equals(id)) result.add(p.category);
        return result;
    }

    private static final String UUID = "0a1b2c3d-0000-4000-8000-00000000abcd";

    private static void canvas(QaCheck qa) {
        // EP: keys.
        qa.isTrue(EP, "owned key", CanvasRules.validKey("owned:" + UUID));
        qa.isTrue(EP, "wish key", CanvasRules.validKey("wish:" + UUID));
        qa.isTrue(EP, "generic idea", CanvasRules.validKey("idea:shoes"));
        qa.isTrue(EP, "catalog idea", CanvasRules.validKey("idea:top_shirt_white"));
        for (String bad : list("owned:" + UUID.toUpperCase(Locale.ROOT), "owned:" + UUID.replace("-", ""), "idea:top_shirt_pink",
                "idea:socks", "garment:" + UUID, "", "owned:../" + UUID))
            qa.isFalse(EP, "invalid key " + bad, CanvasRules.validKey(bad));
        qa.isFalse(EG, "null key", CanvasRules.validKey(null));
        qa.isFalse(BVA, "61 characters", CanvasRules.validKey("idea:" + "a".repeat(56)));
        // BVA: scale clamps to 0.6 and to the largest scale that still fits (1.8 or less).
        qa.equal(BVA, "scale below min", 0.6f, CanvasRules.constrain("bag", .5f, .5f, 0.1f)[2]);
        qa.equal(BVA, "scale at min", 0.6f, CanvasRules.constrain("bag", .5f, .5f, 0.6f)[2]);
        qa.equal(BVA, "scale at max", 1.8f, CanvasRules.constrain("bag", .5f, .5f, 1.8f)[2]);
        qa.equal(BVA, "scale above max", 1.8f, CanvasRules.constrain("bag", .5f, .5f, 9f)[2]);
        float dressMax = Math.min(.94f / .42f, .94f / .68f);
        qa.equal(BVA, "dress max scale fits the canvas", dressMax, CanvasRules.constrain("dress", .5f, .5f, 1.8f)[2]);
        for (String category : WardrobeRules.CATEGORIES) for (float x : new float[] {-5f, 0f, 1f, 5f}) {
            float[] p = CanvasRules.constrain(category, x, x, 1.8f);
            float halfW = CanvasRules.width(category, p[2]) / 2, halfH = CanvasRules.height(category, p[2]) / 2;
            qa.isTrue(BVA, category + " stays inside at " + x, p[0] - halfW >= -1e-6 && p[0] + halfW <= 1 + 1e-6
                && p[1] - halfH >= -1e-6 && p[1] + halfH <= 1 + 1e-6);
        }
        qa.raises(EG, "NaN position", IllegalArgumentException.class, () -> CanvasRules.constrain("top", Float.NaN, .5f, 1));
        qa.raises(EG, "infinite scale", IllegalArgumentException.class, () -> CanvasRules.constrain("top", .5f, .5f, Float.POSITIVE_INFINITY));
        qa.raises(EG, "unknown category", IllegalArgumentException.class, () -> CanvasRules.constrain("socks", .5f, .5f, 1));
        // BVA: initial placement for 1, 5 (last free-form size), 6 (first grid size) and 12 pieces; 0 and 13 rejected.
        qa.equal(BVA, "1 piece", 1, spots(list("top")));
        qa.equal(BVA, "5 pieces, valid look", 5, spots(list("top", "bottom", "shoes", "outerwear", "bag")));
        qa.equal(BVA, "6 pieces use the grid", 6, spots(list("top", "bottom", "shoes", "outerwear", "bag", "hat")));
        qa.equal(BVA, "12 pieces use the grid", 12, spots(list("top", "bottom", "shoes", "outerwear", "bag", "hat", "tights",
            "other_item", "accessory", "accessory", "accessory", "accessory")));
        // QA-13 (fixed in 0.14.14): a layer, a second layer and outerwear start on different spots.
        qa.equal(EG, "layer + outerwear", 5, spots(list("top", "bottom", "shoes", "layer", "outerwear")));
        qa.equal(EG, "two layers", 5, spots(list("top", "bottom", "shoes", "layer", "layer")));
        qa.equal(EG, "two layers + outerwear", 5, spots(list("dress", "shoes", "layer", "layer", "outerwear")));
        qa.raises(BVA, "13 pieces", IllegalArgumentException.class, () -> CanvasRules.initial("bag", 0, 13));
        qa.raises(BVA, "zero pieces", IllegalArgumentException.class, () -> CanvasRules.initial("bag", 0, 0));
        qa.raises(BVA, "index == total", IllegalArgumentException.class, () -> CanvasRules.initial("bag", 3, 3));
        qa.raises(BVA, "index -1", IllegalArgumentException.class, () -> CanvasRules.initial("bag", -1, 3));
    }

    /** Number of distinct initial centres for a look, after checking the look itself is valid. */
    private static int spots(List<String> categories) {
        expect(WardrobeRules.validCategories(categories), "fixture must be a valid look: " + categories);
        Set<String> spots = new HashSet<>();
        for (int i = 0; i < categories.size(); i++) {
            float[] p = CanvasRules.initial(categories.get(i), i, categories.size());
            spots.add(Math.round(p[0] * 100) + ":" + Math.round(p[1] * 100));
        }
        return spots.size();
    }

    private static void roadmap(QaCheck qa) {
        // BVA: supported years 1900..2200 and real calendar dates.
        qa.isTrue(BVA, "1900-01-01", RoadmapRules.validDate("1900-01-01"));
        qa.isFalse(BVA, "1899-12-31", RoadmapRules.validDate("1899-12-31"));
        qa.isTrue(BVA, "2200-12-31", RoadmapRules.validDate("2200-12-31"));
        qa.isFalse(BVA, "2201-01-01", RoadmapRules.validDate("2201-01-01"));
        qa.isTrue(BVA, "leap day 2028", RoadmapRules.validDate("2028-02-29"));
        qa.isFalse(BVA, "no leap day 2026", RoadmapRules.validDate("2026-02-29"));
        qa.isFalse(BVA, "month 13", RoadmapRules.validDate("2026-13-01"));
        qa.isFalse(BVA, "day 32", RoadmapRules.validDate("2026-01-32"));
        for (String bad : list("2026-1-01", "26-01-01", "2026/01/01", "2026-01-01T00:00", " 2026-01-01", "", null))
            qa.isFalse(EP, "date format " + bad, RoadmapRules.validDate(bad));
        // BVA: cost per wear.
        qa.equal(BVA, "no wears", null, RoadmapRules.costPerWear(1000L, 0));
        qa.equal(BVA, "one wear", 1000L, RoadmapRules.costPerWear(1000L, 1));
        qa.equal(BVA, "half rounds up", 3L, RoadmapRules.costPerWear(5L, 2));
        qa.equal(BVA, "below half rounds down", 3L, RoadmapRules.costPerWear(10L, 3));
        qa.equal(BVA, "no price", null, RoadmapRules.costPerWear(null, 3));
        qa.equal(BVA, "maximum price", WishRules.MAX_MINOR, RoadmapRules.costPerWear(WishRules.MAX_MINOR, 1));
        qa.raises(BVA, "negative wears", IllegalArgumentException.class, () -> RoadmapRules.costPerWear(1L, -1));
        qa.raises(BVA, "price over max", IllegalArgumentException.class, () -> RoadmapRules.costPerWear(WishRules.MAX_MINOR + 1, 1));
        // BVA: weather thresholds 8 and 22 °C (feels-like), and the -90..65 range.
        qa.equal(BVA, "7.99 is cold", "cold", RoadmapRules.weatherMode(7.99));
        qa.equal(BVA, "8 is cool", "cool", RoadmapRules.weatherMode(8));
        qa.equal(BVA, "21.99 is cool", "cool", RoadmapRules.weatherMode(21.99));
        qa.equal(BVA, "22 is warm", "warm", RoadmapRules.weatherMode(22));
        qa.equal(BVA, "-90 accepted", "cold", RoadmapRules.weatherMode(-90));
        qa.equal(BVA, "65 accepted", "warm", RoadmapRules.weatherMode(65));
        for (double bad : new double[] {-90.01, 65.01, Double.NaN, Double.POSITIVE_INFINITY})
            qa.raises(BVA, "weather " + bad, IllegalArgumentException.class, () -> RoadmapRules.weatherMode(bad));
        // EP: trip periods.
        qa.isTrue(EP, "no dates", RoadmapRules.period("", ""));
        qa.isTrue(BVA, "one-day trip", RoadmapRules.period(DATE, DATE));
        qa.isFalse(BVA, "end before start", RoadmapRules.period("2026-10-23", DATE));
        qa.isFalse(EP, "only start", RoadmapRules.period(DATE, ""));
        qa.isFalse(EP, "invalid end", RoadmapRules.period(DATE, "2026-02-30"));
        qa.equal(EP, "packing list is unique and sorted", list("a", "b", "c"),
            RoadmapRules.uniquePackingItems(list2(list("c", "a"), list("a")), list("b", "c")));
    }

    private static void recommendation(QaCheck qa) {
        qa.isTrue(EP, "never worn", RecommendationRules.allowed(false, "", DATE, 7));
        qa.isFalse(EP, "excluded", RecommendationRules.allowed(true, "", DATE, 0));
        qa.isTrue(EP, "rule off", RecommendationRules.allowed(false, DATE, DATE, 0));
        // BVA: elapsed days = N-1 / N / N+1.
        qa.isFalse(BVA, "2 of 3 days", RecommendationRules.allowed(false, "2026-10-20", DATE, 3));
        qa.isTrue(BVA, "3 of 3 days", RecommendationRules.allowed(false, "2026-10-19", DATE, 3));
        qa.isTrue(BVA, "4 of 3 days", RecommendationRules.allowed(false, "2026-10-18", DATE, 3));
        qa.isFalse(BVA, "worn today, 1-day rule", RecommendationRules.allowed(false, DATE, DATE, 1));
        qa.isFalse(BVA, "30-day rule, 29 days", RecommendationRules.allowed(false, "2026-09-23", DATE, 30));
        qa.isTrue(BVA, "30-day rule, 30 days", RecommendationRules.allowed(false, "2026-09-22", DATE, 30));
        qa.raises(BVA, "31-day rule", IllegalArgumentException.class, () -> RecommendationRules.allowed(false, "", DATE, 31));
        qa.raises(BVA, "negative rule", IllegalArgumentException.class, () -> RecommendationRules.allowed(false, "", DATE, -1));
        qa.isTrue(EG, "across a year boundary", RecommendationRules.allowed(false, "2025-12-29", "2026-01-05", 7));
    }

    private static DailyDiagnostics.Reason explain(List<DailyRules.Item> items, String weather, Set<String> scope, Set<String> full,
            boolean packed, Set<String> excluded, int days, Set<String> rejected, Map<String, String> fixed) {
        return DailyDiagnostics.explain(items, W, "everyday", weather, DATE, "seed", scope, full, packed, excluded, days, rejected, fixed);
    }

    private static void diagnostics(QaCheck qa) {
        Set<String> none = Collections.emptySet();
        Map<String, String> noPins = Collections.emptyMap();
        List<DailyRules.Item> core = list2(item("t", "top"), item("b", "bottom"), item("s", "shoes"));
        // EP: one representative per reason the daily screen can explain.
        qa.equal(EP, "NO_ITEMS", DailyDiagnostics.Reason.NO_ITEMS, explain(list2(), "any_weather", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "CAPSULE_EMPTY", DailyDiagnostics.Reason.CAPSULE_EMPTY, explain(core, "any_weather", none, none, true, none, 0, none, noPins));
        qa.equal(EP, "SHOES", DailyDiagnostics.Reason.SHOES, explain(list2(item("t", "top"), item("b", "bottom")), "any_weather", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "BASE", DailyDiagnostics.Reason.BASE, explain(list2(item("t", "top"), item("s", "shoes")), "any_weather", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "UNAVAILABLE", DailyDiagnostics.Reason.UNAVAILABLE, explain(list2(item("t", "top", "all", "laundry", ""), item("b", "bottom"), item("s", "shoes")),
            "any_weather", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "REPEATS", DailyDiagnostics.Reason.REPEATS, explain(list2(item("t", "top", "all", "available", "2026-10-21"), item("b", "bottom"), item("s", "shoes")),
            "any_weather", null, null, false, none, 3, none, noPins));
        qa.equal(EP, "EXCLUDED", DailyDiagnostics.Reason.EXCLUDED, explain(core, "any_weather", null, null, false, Set.of("t"), 0, none, noPins));
        qa.equal(EP, "FEEDBACK", DailyDiagnostics.Reason.FEEDBACK, explain(core, "any_weather", null, null, false, none, 0, Set.of("b|s|t"), noPins));
        qa.equal(EP, "PINS", DailyDiagnostics.Reason.PINS, explain(core, "any_weather", null, null, false, none, 0, none, Map.of("top", "missing")));
        qa.equal(EP, "WEATHER", DailyDiagnostics.Reason.WEATHER, explain(list2(item("t", "top", "winter", "available", ""),
            item("b", "bottom", "winter", "available", ""), item("s", "shoes", "winter", "available", "")), "warm", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "SEASONS", DailyDiagnostics.Reason.SEASONS, explain(list2(item("t", "top", "summer", "available", ""),
            item("b", "bottom", "winter", "available", ""), item("s", "shoes")), "any_weather", null, null, false, none, 0, none, noPins));
        qa.equal(EP, "PACKED", DailyDiagnostics.Reason.PACKED, explain(core, "any_weather", Set.of("s"), Set.of("t", "b", "s"), true, none, 0, none, noPins));
        // BVA: the repeat rule is explained only while it actually blocks (N-1 days blocks, N days does not).
        List<DailyRules.Item> wornTwoDaysAgo = list2(item("t", "top", "all", "available", "2026-10-20"), item("b", "bottom"), item("s", "shoes"));
        qa.equal(BVA, "3-day rule blocks", DailyDiagnostics.Reason.REPEATS, explain(wornTwoDaysAgo, "any_weather", null, null, false, none, 3, none, noPins));
        qa.isFalse(BVA, "2-day rule does not block", DailyRules.generate(filter(wornTwoDaysAgo, 2), W, "everyday", "any_weather", DATE, "s",
            none, noPins, 1).isEmpty());
        // EG: another wardrobe never explains this one.
        List<DailyRules.Item> foreign = list2(new DailyRules.Item(new WardrobeRules.Piece("x", "other", "top", "available", false, "all", "casual", "black"), 0, false, ""));
        qa.equal(EG, "foreign items are not ours", DailyDiagnostics.Reason.NO_ITEMS, explain(foreign, "any_weather", null, null, false, none, 0, none, noPins));
    }

    private static List<DailyRules.Item> filter(List<DailyRules.Item> items, int days) {
        List<DailyRules.Item> result = new ArrayList<>();
        for (DailyRules.Item i : items) if (RecommendationRules.allowed(false, i.lastWear, DATE, days)) result.add(i);
        return result;
    }

    static PurchaseAdviceRules.Owned owned(String id, String category, String season, String colour, String availability, boolean eligible, String... sources) {
        WardrobeRules.Piece p = new WardrobeRules.Piece(id, W, category, availability, false, season, "casual", colour);
        return new PurchaseAdviceRules.Owned(new DailyRules.Item(p, 0, false, ""), id, "", eligible, Arrays.asList(sources));
    }

    private static void purchaseAdvice(QaCheck qa) {
        // EP: profile validation.
        qa.raises(EP, "unknown category", IllegalArgumentException.class, () -> new PurchaseAdviceRules.Profile("c", W, "socks", "", "", "", "", ""));
        qa.raises(EP, "unknown season", IllegalArgumentException.class, () -> new PurchaseAdviceRules.Profile("c", W, "top", "", "", "spring", "", ""));
        qa.raises(EP, "unknown style", IllegalArgumentException.class, () -> new PurchaseAdviceRules.Profile("c", W, "top", "", "", "", "boho", ""));
        PurchaseAdviceRules.Profile top = new PurchaseAdviceRules.Profile("c", W, "top", "blue", "Polo", "summer", "casual", "https://shop.pl/p?utm_source=x");
        // EP: similar items are ranked by the strongest shared trait (source > subcategory > colour > style > season).
        List<PurchaseAdviceRules.Owned> owned = list2(
            owned("plain", "top", "all", "red", "available", true),
            owned("blue", "top", "all", "blue", "available", true),
            owned("same-link", "top", "all", "red", "available", true, "https://shop.pl/p"),
            owned("bottom", "bottom", "all", "blue", "available", true));
        List<String> order = new ArrayList<>();
        for (PurchaseAdviceRules.Match m : PurchaseAdviceRules.similar(top, owned)) order.add(m.owned.id());
        qa.equal(EP, "similar order", list("same-link", "blue", "plain"), order);
        // EP: budget classes (exhaustive combinations live in QaPairwiseCases).
        qa.equal(EP, "within", "within", PurchaseAdviceRules.budgetStatus(100L, "PLN", 200L, "PLN"));
        qa.equal(BVA, "equal is within", "within", PurchaseAdviceRules.budgetStatus(200L, "PLN", 200L, "PLN"));
        qa.equal(BVA, "one grosz above", "above", PurchaseAdviceRules.budgetStatus(201L, "PLN", 200L, "PLN"));
        qa.equal(EP, "currency mismatch", "different_currency", PurchaseAdviceRules.budgetStatus(1L, "EUR", 200L, "PLN"));
        qa.equal(EP, "no budget", "unknown", PurchaseAdviceRules.budgetStatus(1L, "PLN", null, "PLN"));
        // EP: analysis with a complete pinned look.
        PurchaseAdviceRules.Profile shirt = new PurchaseAdviceRules.Profile("c", W, "top", "blue", "", "", "", "");
        List<PurchaseAdviceRules.Owned> base = list2(owned("b", "bottom", "all", "black", "available", true), owned("s", "shoes", "all", "black", "available", true));
        PurchaseAdviceRules.Analysis ok = PurchaseAdviceRules.analyze(shirt, base, Collections.emptySet(), "everyday", "any_weather", DATE, Collections.emptySet());
        qa.equal(EP, "one preview", 1, ok.previews.size());
        qa.isTrue(EP, "candidate pinned", ok.previews.get(0).ids.contains(shirt.candidateId()));
        qa.equal(EP, "partners", 2, ok.partnerCount);
        qa.raises(EG, "unknown weather filter", IllegalArgumentException.class, () ->
            PurchaseAdviceRules.analyze(shirt, base, Collections.emptySet(), "everyday", "snow", DATE, Collections.emptySet()));
        qa.isTrue(EP, "summer candidate on a cold day conflicts", PurchaseAdviceRules.analyze(
            new PurchaseAdviceRules.Profile("c", W, "top", "", "", "summer", "", ""), base, Collections.emptySet(), "everyday", "cold", DATE,
            Collections.emptySet()).candidateSeasonConflict);
        qa.equal(EP, "missing shoes reported", List.of(list("shoes")), PurchaseAdviceRules.analyze(shirt,
            list2(owned("b", "bottom", "all", "black", "available", true)), Collections.emptySet(), "everyday", "any_weather", DATE,
            Collections.emptySet()).missingLayouts);
        // QA-05 (fixed in 0.14.14): weather outerwear is optional for the hint, as in the generator.
        String signature = DailyRules.signature(ok.previews.get(0).ids);
        for (String weather : list("cool", "cold", "rain")) {
            PurchaseAdviceRules.Analysis rejected = PurchaseAdviceRules.analyze(shirt, base, Collections.emptySet(), "everyday",
                weather, DATE, Collections.singleton(signature));
            qa.isTrue(EG, weather + ": the only look is rejected", rejected.previews.isEmpty());
            qa.equal(EG, weather + ": rejection is not a missing piece", List.of(), rejected.missingLayouts);
        }
        qa.equal(EG, "cold day without shoes asks only for shoes", List.of(list("shoes")), PurchaseAdviceRules.analyze(shirt,
            list2(owned("b", "bottom", "all", "black", "available", true)), Collections.emptySet(), "everyday", "cold", DATE,
            Collections.emptySet()).missingLayouts);
        qa.equal(EG, "outerwear purchase still needs a base", List.of(list("dress", "shoes"), list("jumpsuit", "shoes"), list("top", "bottom", "shoes")),
            PurchaseAdviceRules.analyze(new PurchaseAdviceRules.Profile("c", W, "outerwear", "", "", "", "", ""), list2(), Collections.emptySet(),
                "everyday", "rain", DATE, Collections.emptySet()).missingLayouts);
    }
}
