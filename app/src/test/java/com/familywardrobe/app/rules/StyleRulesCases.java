package com.familywardrobe.app.rules;

import java.util.*;

/** STYLE-01/02: the colour-pair table (rules v2, sourced in StyleRules), the 0–10 rating and «Добре → Краще». */
public final class StyleRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Style check " + checks + ": " + what); }
    // Same as Matching.colors (tools/check_style.py keeps them equal); ten were added in 0.14.32 (COL-02).
    static final List<String> COLOURS = List.of("white", "cream", "black", "grey", "beige", "camel", "khaki", "brown", "navy", "light_blue", "blue",
        "olive", "green", "red", "burgundy", "pink", "orange", "yellow", "purple", "other");
    private static WardrobeRules.Piece p(String id, String category, String colour, String style) {
        return new WardrobeRules.Piece(id, "home", category, "available", false, "all", style, colour);
    }
    private static WardrobeRules.Piece p(String id, String category, String colour, String style, String season) {
        return new WardrobeRules.Piece(id, "home", category, "available", false, season, style, colour);
    }

    public static void main(String[] args) {
        // The table is symmetric and covers every base colour.
        for (String a : COLOURS) for (String b : COLOURS) {
            String code = StyleRules.pair(a, b);
            check(code.equals(StyleRules.pair(b, a)), "symmetric " + a + "/" + b);
            int points = StyleRules.pairPoints(code);
            // No pairing is forbidden any more (S4–S6): the lowest tier is «with care».
            check(points >= 2 && points <= 4, "points in range " + a + "/" + b);
        }
        // The example from the user's screenshot: grey with navy is good, grey with brown is better.
        check(StyleRules.pair("grey", "navy").equals(StyleRules.CALM), "grey + navy");
        check(StyleRules.pair("grey", "brown").equals(StyleRules.WARM_COOL), "grey + brown");
        check(StyleRules.pairPoints(StyleRules.pair("grey", "brown")) > StyleRules.pairPoints(StyleRules.pair("grey", "navy")), "better than good");
        // Classic rules, each with its reason.
        check(StyleRules.pair("navy", "beige").equals(StyleRules.WARM_COOL) && StyleRules.pair("navy", "brown").equals(StyleRules.WARM_COOL), "navy with warm");
        check(StyleRules.pair("white", "navy").equals(StyleRules.CONTRAST) && StyleRules.pair("black", "white").equals(StyleRules.CONTRAST), "light with dark");
        check(StyleRules.pair("white", "red").equals(StyleRules.ACCENT) && StyleRules.pair("navy", "blue").equals(StyleRules.ACCENT), "one accent");
        // An accent on a neutral of the opposite temperature: blue + brown (S10), navy + burgundy, pink + grey (S8).
        check(StyleRules.pair("blue", "brown").equals(StyleRules.ACCENT_CONTRAST) && StyleRules.pair("red", "navy").equals(StyleRules.ACCENT_CONTRAST) &&
            StyleRules.pair("red", "grey").equals(StyleRules.ACCENT_CONTRAST) && StyleRules.pair("blue", "beige").equals(StyleRules.ACCENT_CONTRAST), "complementary accent");
        check(StyleRules.pair("red", "brown").equals(StyleRules.ACCENT) && StyleRules.pair("blue", "black").equals(StyleRules.ACCENT), "accent of the same or no temperature");
        // «Always work» lists (S7, S8) are never below 3.
        for (String[] combo : new String[][] {{"black", "white"}, {"navy", "beige"}, {"brown", "beige"}, {"olive", "black"}, {"olive", "white"},
            {"brown", "olive"}, {"grey", "grey"}, {"black", "beige"}, {"navy", "red"}, {"grey", "red"},
            // COL-02 colours in the same lists: navy + cream, black + camel, green + black/brown, navy + burgundy, pink + grey,
            // pink + light blue (pastel pink and blue, S7), sky blue + chocolate brown (S10).
            {"navy", "cream"}, {"black", "camel"}, {"green", "black"}, {"green", "brown"}, {"navy", "burgundy"}, {"pink", "grey"},
            {"pink", "light_blue"}, {"light_blue", "brown"}})
            check(StyleRules.pairPoints(StyleRules.pair(combo[0], combo[1])) >= 3, "always works: " + combo[0] + " + " + combo[1]);
        check(StyleRules.pair("grey", "grey").equals(StyleRules.TONAL), "tone on tone");
        // COL-02: accents by their place on the artist's wheel (S1): neighbours harmonise, opposites are a bold contrast.
        for (String[] combo : new String[][] {{"blue", "green"}, {"blue", "purple"}, {"red", "orange"}, {"red", "pink"}, {"pink", "purple"},
            {"orange", "yellow"}, {"yellow", "green"}, {"red", "burgundy"}, {"burgundy", "pink"}})
            check(StyleRules.pair(combo[0], combo[1]).equals(StyleRules.ANALOGOUS), "analogous " + combo[0] + " + " + combo[1]);
        for (String[] combo : new String[][] {{"blue", "orange"}, {"red", "green"}, {"yellow", "purple"}})
            check(StyleRules.pair(combo[0], combo[1]).equals(StyleRules.COMPLEMENTARY), "complementary " + combo[0] + " + " + combo[1]);
        check(StyleRules.pair("red", "blue").equals(StyleRules.TWO_ACCENTS) && StyleRules.pair("pink", "orange").equals(StyleRules.TWO_ACCENTS), "neither");
        check(StyleRules.pairPoints(StyleRules.ANALOGOUS) == 3 && StyleRules.pairPoints(StyleRules.COMPLEMENTARY) == 2, "wheel points");
        // New neutrals keep the earlier logic: cream and camel are warm, light blue is cool.
        check(StyleRules.pair("cream", "navy").equals(StyleRules.WARM_COOL) && StyleRules.pair("light_blue", "brown").equals(StyleRules.WARM_COOL) &&
            StyleRules.pair("light_blue", "navy").equals(StyleRules.CONTRAST) && StyleRules.pair("camel", "khaki").equals(StyleRules.CALM), "new neutrals");
        // Every base colour has attributes: none of the twenty falls back to «unknown» except "other".
        for (String colour : COLOURS) check(colour.equals("other") == StyleRules.pair(colour, "grey").equals(StyleRules.UNKNOWN), "attributes for " + colour);
        check(StyleRules.pair("red", "red").equals(StyleRules.SAME_ACCENT), "the same accent twice");
        check(StyleRules.pair("black", "brown").equals(StyleRules.BLACK_BROWN), "black and brown");
        check(StyleRules.pair("black", "navy").equals(StyleRules.DARK_DARK), "black and navy");
        check(StyleRules.pair("red", "blue").equals(StyleRules.TWO_ACCENTS), "two accents");
        check(StyleRules.pair("other", "grey").equals(StyleRules.UNKNOWN) && StyleRules.pair("turquoise", "grey").equals(StyleRules.UNKNOWN), "unknown colour");
        check(StyleRules.pair("white", "grey").equals(StyleRules.CALM), "calm neutrals");
        check(StyleRules.pairPoints(StyleRules.WARM_COOL) == 4 && StyleRules.pairPoints(StyleRules.ACCENT_CONTRAST) == 4 &&
            StyleRules.pairPoints(StyleRules.CONTRAST) == 3 && StyleRules.pairPoints(StyleRules.TONAL) == 3 &&
            StyleRules.pairPoints(StyleRules.BLACK_BROWN) == 2 && StyleRules.pairPoints(StyleRules.DARK_DARK) == 2 &&
            StyleRules.pairPoints(StyleRules.TWO_ACCENTS) == 2 && StyleRules.pairPoints(StyleRules.SAME_ACCENT) == 2, "points");
        boolean refused = false;
        try { StyleRules.pairPoints("nonsense"); } catch (IllegalArgumentException e) { refused = true; }
        check(refused, "unknown codes are refused");

        // Rating: colour (main pair + no clash) + style + weather = 0..10.
        List<WardrobeRules.Piece> good = List.of(p("t", "top", "grey", "casual"), p("b", "bottom", "navy", "casual"), p("s", "shoes", "white", "casual"));
        StyleRules.Rating r = StyleRules.rate(good, "any_weather", "casual");
        check(r.colour == 4 && r.style == 3 && r.weather == 2 && r.score == 9, "good outfit " + r.score);
        check(r.pair.equals(StyleRules.CALM) && r.pairA.equals("grey") && r.pairB.equals("navy") && r.clash.isEmpty(), "top and bottom make the main pair");
        check(r.label().equals(StyleRules.GREAT), "label 9");
        List<WardrobeRules.Piece> better = List.of(p("t", "top", "grey", "casual"), p("b2", "bottom", "brown", "casual"), p("s", "shoes", "white", "casual"));
        check(StyleRules.rate(better, "any_weather", "casual").score == 10, "grey + brown scores higher");
        // The order of pieces does not matter; small pieces neither decide the colours nor the style.
        List<WardrobeRules.Piece> shuffled = List.of(p("s", "shoes", "white", "casual"), p("j", "jewelry", "red", "formal"), p("b", "bottom", "navy", "casual"), p("t", "top", "grey", "casual"));
        StyleRules.Rating same = StyleRules.rate(shuffled, "any_weather", "casual");
        check(same.score == 9 && same.pairA.equals("grey") && same.counted == 3, "order and jewelry ignored");
        // A clash elsewhere costs the bonus point and is named.
        List<WardrobeRules.Piece> clash = List.of(p("t", "top", "grey", "casual"), p("b", "bottom", "black", "casual"), p("s", "shoes", "brown", "casual"));
        StyleRules.Rating c = StyleRules.rate(clash, "any_weather", "casual");
        check(c.colour == 3 && c.clash.equals(StyleRules.BLACK_BROWN) && c.clashA.equals("black") && c.clashB.equals("brown"), "black trousers, brown shoes");
        StyleRules.Rating accents = StyleRules.rate(List.of(p("t", "top", "red", "casual"), p("b", "bottom", "blue", "casual")), "any_weather", "casual");
        check(accents.colour == 2 && accents.clash.equals(StyleRules.TWO_ACCENTS), "two accents: with care, no harmony point");
        // The three-colour rule (S3): a fourth colour costs the harmony point and is named; unknown colours are not counted.
        List<WardrobeRules.Piece> four = new ArrayList<>(good); four.add(p("c", "outerwear", "brown", "casual"));
        StyleRules.Rating many = StyleRules.rate(four, "any_weather", "casual");
        check(many.colours == 4 && many.colour == 3 && many.clash.isEmpty(), "four colours: " + many.colours + "/" + many.colour);
        List<WardrobeRules.Piece> pattern = new ArrayList<>(good); pattern.add(p("c", "outerwear", "other", "casual"));
        StyleRules.Rating patterned = StyleRules.rate(pattern, "any_weather", "casual");
        check(patterned.colours == 3 && patterned.colour == 4, "a patterned coat is neither counted nor judged");
        // Style: one style throughout, mostly one, and whether it is the occasion's.
        check(StyleRules.rate(good, "any_weather", "formal").style == 2, "one style, not the occasion's");
        List<WardrobeRules.Piece> mixed = List.of(p("t", "top", "grey", "formal"), p("b", "bottom", "navy", "casual"), p("s", "shoes", "white", "casual"));
        StyleRules.Rating m = StyleRules.rate(mixed, "any_weather", "casual");
        check(m.style == 2 && m.dominant.equals("casual") && m.sharing == 2 && m.counted == 3, "mostly casual on a casual day");
        List<WardrobeRules.Piece> scattered = List.of(p("t", "top", "grey", "formal"), p("b", "bottom", "navy", "casual"), p("s", "shoes", "white", "sport"));
        check(StyleRules.rate(scattered, "any_weather", "casual").style == 1, "three styles: only the occasion's point");
        check(StyleRules.rate(scattered, "any_weather", "formal").dominant.equals("formal"), "a tie goes to the occasion's style");
        // Weather: out of season costs both points; no coat in the cold costs one.
        StyleRules.Rating cold = StyleRules.rate(good, "cold", "casual");
        check(cold.weather == 1 && cold.weatherCode.equals(StyleRules.NO_OUTERWEAR), "no coat in the cold");
        for (String chilly : List.of("cool", "rain")) check(StyleRules.rate(good, chilly, "casual").weatherCode.equals(StyleRules.NO_OUTERWEAR), "no coat: " + chilly);
        for (String mild : List.of("warm", "any_weather")) check(StyleRules.rate(good, mild, "casual").weatherCode.equals(StyleRules.WEATHER_OK), "no coat needed: " + mild);
        List<WardrobeRules.Piece> coat = new ArrayList<>(good); coat.add(p("c", "outerwear", "brown", "casual", "winter"));
        check(StyleRules.rate(coat, "cold", "casual").weatherCode.equals(StyleRules.WEATHER_OK), "a winter coat in the cold");
        StyleRules.Rating summer = StyleRules.rate(coat, "warm", "casual");
        check(summer.weather == 0 && summer.weatherCode.equals(StyleRules.OUT_OF_SEASON) && summer.seasonItem.equals("c"), "a winter coat on a warm day");
        check(StyleRules.rate(List.of(p("d", "dress", "red", "formal")), "any_weather", "formal").colour == 3, "one piece: neutral colour points");
        // Labels by score.
        check(StyleRules.rate(clash, "cold", "formal").label().equals(StyleRules.FAIR), "fair: " + StyleRules.rate(clash, "cold", "formal").score);

        // «Добре → Краще»: the one change that raises the score most, from the person's ready items only.
        List<WardrobeRules.Piece> wardrobe = List.of(p("b2", "bottom", "brown", "casual"), p("b3", "bottom", "black", "casual"),
            p("t2", "top", "red", "casual"), p("b4", "bottom", "brown", "formal"));
        StyleRules.Swap swap = StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of(), Set.of());
        check(swap != null && swap.oldId.equals("b") && swap.newId.equals("b2") && swap.before.score == 9 && swap.after.score == 10, "navy → brown trousers");
        // A red top would also reach 10 (red + navy, S8), but the neutral swap wins the tie.
        check(StyleRules.rate(List.of(p("t2", "top", "red", "casual"), p("b", "bottom", "navy", "casual"), p("s", "shoes", "white", "casual")), "any_weather", "casual").score == 10 &&
            swap.after.accents == 0, "tie goes to the neutral");
        StyleRules.Swap pinned = StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of("b"), Set.of());
        check(pinned != null && pinned.oldId.equals("t") && pinned.newId.equals("t2"), "a pinned piece stays; another one changes");
        check(StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of("b", "t"), Set.of()) == null, "nothing left to change");
        StyleRules.Swap notHidden = StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of(), Set.of(DailyRules.signature(List.of("t", "b2", "s"))));
        check(notHidden != null && notHidden.newId.equals("t2"), "a hidden combination stays hidden");
        List<WardrobeRules.Piece> unavailable = List.of(new WardrobeRules.Piece("b2", "home", "bottom", "laundry", false, "all", "casual", "brown"),
            new WardrobeRules.Piece("b5", "child", "bottom", "available", false, "all", "casual", "brown"), p("b6", "bottom", "brown", "casual", "winter"));
        check(StyleRules.better(good, unavailable, "warm", "casual", Set.of(), Set.of()) == null, "laundry, another wardrobe or the wrong season never");
        check(StyleRules.better(better, wardrobe, "any_weather", "casual", Set.of(), Set.of()) == null, "nothing better than 10");
        // A swap may fix the weather: a coat cannot be added, but an out-of-season top can be replaced.
        List<WardrobeRules.Piece> warm = List.of(p("t", "top", "grey", "casual", "winter"), p("b2", "bottom", "brown", "casual"), p("s", "shoes", "white", "casual"));
        StyleRules.Swap season = StyleRules.better(warm, List.of(p("t3", "top", "grey", "casual", "summer")), "warm", "casual", Set.of(), Set.of());
        check(season != null && season.newId.equals("t3") && season.after.weather == 2, "a summer top on a warm day");
        // Deterministic: the same inputs in another order give the same swap.
        List<WardrobeRules.Piece> reversed = new ArrayList<>(wardrobe); Collections.reverse(reversed);
        StyleRules.Swap again = StyleRules.better(good, reversed, "any_weather", "casual", Set.of(), Set.of());
        check(again != null && again.newId.equals(swap.newId), "stable");

        // 0.14.33: the daily generator scores colour pairs with this table and prefers the better-rated outfit.
        List<DailyRules.Item> daily = new ArrayList<>();
        for (WardrobeRules.Piece piece : List.of(p("t", "top", "grey", "casual"), p("navy", "bottom", "navy", "casual"),
            p("brown", "bottom", "brown", "casual"), p("s", "shoes", "white", "casual")))
            daily.add(new DailyRules.Item(piece, 0, false, ""));
        List<List<String>> picks = DailyRules.generate(daily, "home", "everyday", "warm", "2026-11-03", "seed", Set.of(), Map.of(), 2);
        check(picks.get(0).contains("brown") && picks.get(1).contains("navy"), "grey goes with brown first, navy second: " + picks);
        check(DailyRules.pairScore(p("a", "top", "grey", "casual"), p("b", "bottom", "brown", "casual")) == 8 &&
            DailyRules.pairScore(p("a", "top", "grey", "casual"), p("b", "bottom", "navy", "formal")) == 3, "pair score = table + style");
        // The best variant leaves nothing better to swap to.
        List<WardrobeRules.Piece> first = new ArrayList<>();
        for (String id : picks.get(0)) for (DailyRules.Item i : daily) if (i.piece.id.equals(id)) first.add(i.piece);
        List<WardrobeRules.Piece> all = new ArrayList<>(); for (DailyRules.Item i : daily) all.add(i.piece);
        check(StyleRules.better(first, all, "warm", "casual", Set.of(), Set.of()) == null, "no «Краще» under the first variant");
        // The person's own reasons still win: «Незручно» on the brown trousers (PERS-01) puts navy first.
        List<List<String>> uncomfortable = DailyRules.generate(daily, "home", "everyday", "warm", "2026-11-03", "seed", Set.of(), Map.of(), 2,
            Map.of("brown", PreferenceRules.UNCOMFORTABLE));
        check(uncomfortable.get(0).contains("navy"), "reasons beat colours: " + uncomfortable);
        // A fourth colour costs the rating; with otherwise equal scores the three-colour outfit comes first.
        check(DailyRules.RATING_WEIGHT > 0 && DailyRules.RATING_WEIGHT < 1000, "the rating nudges, the per-item score leads");

        System.out.println("PASS: " + checks + " style assertions (colour pairs, rating, labels, «Добре → Краще»).");
    }
}
