package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;
import static com.familywardrobe.app.QaCheck.expect;

import java.util.*;

/**
 * Pairwise (all-pairs) combinations for the rules with many interacting inputs. Each row is checked
 * against an independent oracle, not against recorded output:
 *  - daily outfit generator: weather × occasion × seasons/availability of every piece × extra pieces × feedback;
 *  - look shape rules: the count of each of the 13 categories;
 *  - shop JSON-LD offers: price format × currency × offer type × offer shape × image shape × name;
 *  - purchase budget: every price/budget/currency class (small enough to run exhaustively).
 */
public final class QaPairwiseCases {
    private QaPairwiseCases() {}
    private static final String W = "w", DATE = "2026-10-22";
    private static final String[] WEATHERS = {"any_weather", "warm", "cool", "cold", "rain"};
    private static final String[] OCCASIONS = {"everyday", "work", "occasion", "active_day"};
    private static final String[] SEASONS = {"all", "summer", "winter", "midseason"};
    private static final String[] AVAILABILITY = {"available", "laundry", "repair", "lent"};

    public static void main(String[] args) { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaPairwiseCases");
        qa.isTrue(PW, "generator covers all pairs", QaCheck.allPairs(3, 3, 3, 3).size() >= 9);
        dailyGenerator(qa);
        lookShapes(qa);
        shopOffers(qa);
        budgets(qa);
        qa.done();
    }

    // ---------- Daily outfit generator ----------

    private static DailyRules.Item item(String id, String wardrobe, String category, String season, String availability) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, wardrobe, category, availability, false, season, "casual", "black"), 0, false, "");
    }

    private static String weatherSeason(String weather) {
        switch (weather) { case "warm": return "summer"; case "cold": return "winter"; case "cool": case "rain": return "midseason"; default: return "all"; }
    }

    /** Independent oracle: every complete look the rules allow, as sorted signatures. */
    private static Set<String> expectedLooks(List<DailyRules.Item> items, String weather) {
        String season = weatherSeason(weather);
        boolean wantsOuterwear = !weather.equals("any_weather") && !weather.equals("warm");
        Set<String> looks = new TreeSet<>();
        for (List<String> layout : Arrays.asList(Arrays.asList("top", "bottom", "shoes"), Arrays.asList("dress", "shoes"), Arrays.asList("jumpsuit", "shoes"))) {
            List<List<DailyRules.Item>> choices = new ArrayList<>();
            List<String> categories = new ArrayList<>(layout);
            if (wantsOuterwear) categories.add("outerwear");
            boolean impossible = false;
            for (String category : categories) {
                List<DailyRules.Item> options = new ArrayList<>();
                for (DailyRules.Item i : items) {
                    WardrobeRules.Piece p = i.piece;
                    boolean seasonOk = p.season.equals("all") || season.equals("all") || p.season.equals(season);
                    if (p.wardrobe.equals(W) && p.category.equals(category) && !p.archived && p.availability.equals("available") && seasonOk) options.add(i);
                }
                if (options.isEmpty()) {
                    if (category.equals("outerwear")) continue;  // weather layer is optional
                    impossible = true; break;
                }
                choices.add(options);
            }
            if (impossible) continue;
            combine(choices, 0, new ArrayList<>(), looks);
        }
        return looks;
    }

    private static void combine(List<List<DailyRules.Item>> choices, int index, List<DailyRules.Item> picked, Set<String> out) {
        if (index == choices.size()) {
            List<String> ids = new ArrayList<>();
            for (DailyRules.Item i : picked) ids.add(i.piece.id);
            out.add(DailyRules.signature(ids));
            return;
        }
        for (DailyRules.Item next : choices.get(index)) {
            boolean ok = true;
            for (DailyRules.Item p : picked) if (!WardrobeRules.compatibleSeasons(p.piece.season, next.piece.season)) ok = false;
            if (!ok) continue;
            picked.add(next); combine(choices, index + 1, picked, out); picked.remove(picked.size() - 1);
        }
    }

    private static void dailyGenerator(QaCheck qa) {
        // Parameters: weather, occasion, top season, top availability, bottom season, bottom availability,
        // shoes season, shoes availability, dress, outerwear, second top, foreign wardrobe shoes, reject first look.
        int[] sizes = {5, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 2, 2};
        List<int[]> rows = QaCheck.allPairs(sizes);
        long exhaustive = 1; for (int s : sizes) exhaustive *= s;
        qa.isTrue(PW, "pairwise rows " + rows.size() + " instead of " + exhaustive, rows.size() < 60 && rows.size() < exhaustive);
        int withLooks = 0, withoutLooks = 0;
        for (int[] r : rows) {
            String weather = WEATHERS[r[0]], occasion = OCCASIONS[r[1]];
            List<DailyRules.Item> items = new ArrayList<>(Arrays.asList(
                item("t1", W, "top", SEASONS[r[2]], AVAILABILITY[r[3]]),
                item("b1", W, "bottom", SEASONS[r[4]], AVAILABILITY[r[5]]),
                item("s1", W, "shoes", SEASONS[r[6]], AVAILABILITY[r[7]])));
            if (r[8] == 1) items.add(item("d1", W, "dress", "summer", "available"));
            if (r[8] == 2) items.add(item("d1", W, "dress", "all", "laundry"));
            if (r[9] == 1) items.add(item("o1", W, "outerwear", "midseason", "available"));
            if (r[9] == 2) items.add(item("o1", W, "outerwear", "winter", "available"));
            if (r[10] == 1) items.add(item("t2", W, "top", "all", "available"));
            if (r[10] == 2) items.add(item("t2", W, "top", "winter", "available"));
            if (r[11] == 1) items.add(item("x1", "other", "shoes", "all", "available"));
            String label = "row " + Arrays.toString(r);
            Set<String> expected = expectedLooks(items, weather);
            Set<String> rejected = new HashSet<>();
            if (r[12] == 1 && !expected.isEmpty()) {
                List<List<String>> first = DailyRules.generate(items, W, occasion, weather, DATE, "seed", Collections.emptySet(), Collections.emptyMap(), 1);
                expect(!first.isEmpty(), label + ": oracle found a look, generator none");
                String sig = DailyRules.signature(first.get(0));
                rejected.add(sig); expected.remove(sig);
            }
            List<List<String>> looks = DailyRules.generate(items, W, occasion, weather, DATE, "seed", rejected, Collections.emptyMap(), 20);
            Set<String> actual = new TreeSet<>();
            for (List<String> look : looks) actual.add(DailyRules.signature(look));
            qa.equal(PW, label + " looks match the oracle", expected, actual);
            qa.equal(PW, label + " no duplicate looks", looks.size(), actual.size());
            qa.equal(PW, label + " deterministic", looks, DailyRules.generate(items, W, occasion, weather, DATE, "seed", rejected, Collections.emptyMap(), 20));
            for (List<String> look : looks) {
                List<String> categories = new ArrayList<>();
                for (String id : look) for (DailyRules.Item i : items) if (i.piece.id.equals(id)) categories.add(i.piece.category);
                qa.isTrue(PW, label + " complete " + look, WardrobeRules.completeCategories(categories));
                qa.isFalse(PW, label + " foreign piece " + look, look.contains("x1"));
            }
            if (actual.isEmpty()) withoutLooks++; else withLooks++;
        }
        qa.isTrue(PW, "rows exercise both outcomes", withLooks > 0 && withoutLooks > 0);
    }

    // ---------- Look shapes ----------

    private static void lookShapes(QaCheck qa) {
        // Two designs: a binary one (each category absent/present) that yields many valid looks, and one with
        // boundary counts 0/1/2/4/5 around every per-category limit.
        int valid = 0, invalid = 0;
        for (int[] counts : new int[][] {{0, 1}, {0, 1, 2, 4, 5}}) {
            List<String> categories = WardrobeRules.CATEGORIES;
            int[] sizes = new int[categories.size()];
            Arrays.fill(sizes, counts.length);
            for (int[] r : QaCheck.allPairs(sizes)) {
                List<String> look = new ArrayList<>();
                Map<String, Integer> byCategory = new LinkedHashMap<>();
                for (int c = 0; c < categories.size(); c++) {
                    byCategory.put(categories.get(c), counts[r[c]]);
                    for (int n = 0; n < counts[r[c]]; n++) look.add(categories.get(c));
                }
                boolean expected = specValid(byCategory, look.size());
                if (expected) valid++; else invalid++;
                qa.equal(PW, "shape " + byCategory, expected, WardrobeRules.validCategories(look));
                boolean complete = expected && byCategory.get("shoes") > 0 && (byCategory.get("dress") > 0 || byCategory.get("jumpsuit") > 0
                    || byCategory.get("top") > 0 && byCategory.get("bottom") > 0);
                qa.equal(PW, "complete " + byCategory, complete, WardrobeRules.completeCategories(look));
                // The same look in another order gives the same verdict.
                List<String> reversed = new ArrayList<>(look); Collections.reverse(reversed);
                qa.equal(PW, "order independent " + byCategory, expected, WardrobeRules.validCategories(reversed));
            }
        }
        qa.isTrue(PW, "designs exercise both verdicts (" + valid + " valid, " + invalid + " invalid)", valid > 0 && invalid > 0);
    }

    /** Written from the product rules, not from the implementation. */
    private static boolean specValid(Map<String, Integer> counts, int total) {
        if (total < 1 || total > 12) return false;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int limit = e.getKey().equals("layer") ? 2 : e.getKey().equals("accessory") || e.getKey().equals("jewelry") ? 4 : 1;
            if (e.getValue() > limit) return false;
        }
        int onePiece = counts.get("dress") + counts.get("jumpsuit");
        if (onePiece > 1) return false;
        return onePiece == 0 || counts.get("top") == 0 && counts.get("bottom") == 0;
    }

    // ---------- Shop offers ----------

    private static final String[] PRICE = {"\"229.90\"", "229.9", "\"229,90\"", "\"abc\""};
    private static final String[] CURRENCY = {"\"PLN\"", "\"pln\"", "\"CHF\"", null};
    private static final String[] OFFER_TYPE = {"\"Offer\"", "\"AggregateOffer\"", null};
    private static final String[] SHAPE = {"map", "list1", "list2same", "list2diff", "none"};
    private static final String[] IMAGE = {"\"/a.jpg\"", "[\"/a.jpg\",\"/b.jpg\"]", "{\"@type\":\"ImageObject\",\"contentUrl\":\"/c.jpg\"}", null};
    private static final String[] NAME = {"Coat", "Coat &amp; hat", "x"};

    private static String offer(int price, int currency, int type, boolean plusOne) {
        StringBuilder o = new StringBuilder("{");
        if (OFFER_TYPE[type] != null) o.append("\"@type\":").append(OFFER_TYPE[type]).append(',');
        String p = PRICE[price];
        if (plusOne) p = p.replace("229", "230");
        o.append("\"price\":").append(p);
        if (CURRENCY[currency] != null) o.append(",\"priceCurrency\":").append(CURRENCY[currency]);
        return o.append('}').toString();
    }

    private static void shopOffers(QaCheck qa) {
        List<int[]> rows = QaCheck.allPairs(PRICE.length, CURRENCY.length, OFFER_TYPE.length, SHAPE.length, IMAGE.length, NAME.length);
        for (int[] r : rows) {
            String name = NAME[r[5]].equals("x") ? "x".repeat(121) : NAME[r[5]];
            String offers;
            switch (SHAPE[r[3]]) {
                case "map": offers = offer(r[0], r[1], r[2], false); break;
                case "list1": offers = "[" + offer(r[0], r[1], r[2], false) + "]"; break;
                case "list2same": offers = "[" + offer(r[0], r[1], r[2], false) + "," + offer(r[0], r[1], r[2], false) + "]"; break;
                case "list2diff": offers = "[" + offer(r[0], r[1], r[2], false) + "," + offer(r[0], r[1], r[2], true) + "]"; break;
                default: offers = null;
            }
            String json = "{\"@type\":\"Product\",\"name\":\"" + name + "\"" + (IMAGE[r[4]] == null ? "" : ",\"image\":" + IMAGE[r[4]])
                + (offers == null ? "" : ",\"offers\":" + offers) + "}";
            List<ShopMetadata.Product> products = ShopMetadata.parse("<script type=\"application/ld+json\">" + json + "</script>", "https://shop.pl/p");
            String label = "offer row " + Arrays.toString(r);
            qa.equal(PW, label + " one product", 1, products.size());
            ShopMetadata.Product p = products.get(0);
            // Oracle for price certainty.
            boolean priceOk = r[0] != 3, currencyOk = r[1] == 0 || r[1] == 1, aggregate = r[2] == 1;
            int offerCount = SHAPE[r[3]].equals("none") ? 0 : SHAPE[r[3]].startsWith("list2") ? 2 : 1;
            boolean distinctPrices = SHAPE[r[3]].equals("list2diff");
            boolean uncertain = offerCount > 0 && (aggregate || !priceOk || !currencyOk || distinctPrices);
            qa.equal(PW, label + " uncertain", uncertain, p.uncertainPrice);
            qa.equal(PW, label + " price", offerCount > 0 && !uncertain ? "229.90" : "", p.price);
            qa.equal(PW, label + " currency", offerCount > 0 && !uncertain ? "PLN" : "", p.currency);
            int images = IMAGE[r[4]] == null ? 0 : r[4] == 1 ? 2 : 1;
            qa.equal(PW, label + " images", images, p.images.size());
            String expectedName = r[5] == 1 ? "Coat & hat" : r[5] == 2 ? "x".repeat(120) : "Coat";
            qa.equal(PW, label + " name", expectedName, p.name);
        }
    }

    // ---------- Budget ----------

    private static void budgets(QaCheck qa) {
        Long[] amounts = {null, 0L, 100L, 101L, WishRules.MAX_MINOR, WishRules.MAX_MINOR + 1, -1L};
        String[] currencies = {"PLN", "EUR", "UAH", "CHF", ""};
        for (Long price : amounts) for (Long budget : amounts) for (String currency : currencies) for (String budgetCurrency : currencies) {
            String expected;
            boolean pricesKnown = price != null && budget != null && price >= 0 && budget >= 0
                && price <= WishRules.MAX_MINOR && budget <= WishRules.MAX_MINOR;
            if (!pricesKnown) expected = "unknown";
            else if (!WishRules.CURRENCIES.contains(currency) || !currency.equals(budgetCurrency)) expected = "different_currency";
            else expected = price > budget ? "above" : "within";
            qa.equal(PW, "budget " + price + currency + "/" + budget + budgetCurrency, expected,
                PurchaseAdviceRules.budgetStatus(price, currency, budget, budgetCurrency));
        }
    }
}
