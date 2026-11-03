package com.familywardrobe.app;

import java.util.*;

/** STYLE-01/02: the colour-pair table, the 0–10 rating and «Добре → Краще» on the production rules. */
public final class StyleRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Style check " + checks + ": " + what); }
    static final List<String> COLOURS = List.of("white", "black", "grey", "beige", "navy", "brown", "olive", "blue", "red", "other");
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
            check(points >= 0 && points <= 4, "points in range " + a + "/" + b);
        }
        // The example from the user's screenshot: grey with navy is good, grey with brown is better.
        check(StyleRules.pair("grey", "navy").equals(StyleRules.CALM), "grey + navy");
        check(StyleRules.pair("grey", "brown").equals(StyleRules.WARM_COOL), "grey + brown");
        check(StyleRules.pairPoints(StyleRules.pair("grey", "brown")) > StyleRules.pairPoints(StyleRules.pair("grey", "navy")), "better than good");
        // Classic rules, each with its reason.
        check(StyleRules.pair("navy", "beige").equals(StyleRules.WARM_COOL) && StyleRules.pair("navy", "brown").equals(StyleRules.WARM_COOL), "navy with warm");
        check(StyleRules.pair("white", "navy").equals(StyleRules.CONTRAST) && StyleRules.pair("black", "white").equals(StyleRules.CONTRAST), "light with dark");
        check(StyleRules.pair("white", "red").equals(StyleRules.ACCENT) && StyleRules.pair("navy", "blue").equals(StyleRules.ACCENT), "one accent");
        check(StyleRules.pair("grey", "grey").equals(StyleRules.TONAL), "tone on tone");
        check(StyleRules.pair("red", "red").equals(StyleRules.SAME_ACCENT), "the same accent twice");
        check(StyleRules.pair("black", "brown").equals(StyleRules.BLACK_BROWN), "black and brown");
        check(StyleRules.pair("black", "navy").equals(StyleRules.DARK_DARK), "black and navy");
        check(StyleRules.pair("red", "blue").equals(StyleRules.TWO_ACCENTS), "two accents");
        check(StyleRules.pair("other", "grey").equals(StyleRules.UNKNOWN) && StyleRules.pair("pink", "grey").equals(StyleRules.UNKNOWN), "unknown colour");
        check(StyleRules.pair("white", "grey").equals(StyleRules.CALM), "calm neutrals");
        check(StyleRules.pairPoints(StyleRules.WARM_COOL) == 4 && StyleRules.pairPoints(StyleRules.CONTRAST) == 3 && StyleRules.pairPoints(StyleRules.TONAL) == 2 &&
            StyleRules.pairPoints(StyleRules.BLACK_BROWN) == 1 && StyleRules.pairPoints(StyleRules.TWO_ACCENTS) == 0, "points");
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
        check(StyleRules.rate(List.of(p("t", "top", "red", "casual"), p("b", "bottom", "blue", "casual")), "any_weather", "casual").colour == 0, "two accents main pair");
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
        check(StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of("b"), Set.of()) == null, "a pinned piece stays");
        check(StyleRules.better(good, wardrobe, "any_weather", "casual", Set.of(), Set.of(DailyRules.signature(List.of("t", "b2", "s")))) == null, "a hidden combination stays hidden");
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

        System.out.println("PASS: " + checks + " style assertions (colour pairs, rating, labels, «Добре → Краще»).");
    }
}
