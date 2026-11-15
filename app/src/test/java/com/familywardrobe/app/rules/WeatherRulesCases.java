package com.familywardrobe.app.rules;

import java.util.*;

/**
 * WTH-01 / GEN-04 (0.14.38): the forecast's temperature decides the coat, the second layer, tights and the seasons;
 * a weather chosen by hand works as before. Runs the production WeatherRules, DailyRules and StyleRules.
 */
public final class WeatherRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Weather check " + checks + ": " + what); }
    private static boolean refused(Runnable action) { try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; } }
    private static DailyRules.Item it(String id, String category, String season) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, "home", category, "available", false, season, "casual", "white"), 0, false, "");
    }
    private static List<List<String>> run(List<DailyRules.Item> items, String weather, Double temperature, Map<String, String> fixed, int limit) {
        return DailyRules.generate(items, "home", "everyday", weather, "2026-11-12", "seed", Set.of(), fixed, limit, Map.of(), null, Map.of(), temperature);
    }
    private static boolean any(List<List<String>> looks, String id) { for (List<String> look : looks) if (look.contains(id)) return true; return false; }
    private static boolean every(List<List<String>> looks, String id) { for (List<String> look : looks) if (!look.contains(id)) return false; return !looks.isEmpty(); }
    private static List<WardrobeRules.Piece> pieces(List<DailyRules.Item> items, String... ids) {
        List<WardrobeRules.Piece> result = new ArrayList<>();
        for (String id : ids) for (DailyRules.Item item : items) if (item.piece.id.equals(id)) result.add(item.piece);
        return result;
    }

    public static void main(String[] args) {
        // The temperature to dress for.
        check(WeatherRules.day(5, 20) == 15.0 && WeatherRules.day(10, 10) == 10.0, "the high counts twice");
        check(WeatherRules.today(5, 20, 7) == 11.0, "today: the day and how it feels now, equally");
        check(RoadmapRules.weatherMode(WeatherRules.today(5, 20, 7)).equals("cool") && RoadmapRules.weatherMode(7).equals("cold"),
            "a cold morning does not make a mild day a winter one");
        check(WeatherRules.shifted(20, "warmer") == 16 && WeatherRules.shifted(20, "lighter") == 24 && WeatherRules.shifted(20, "forecast") == 20,
            "«тепліше» dresses for 4° less, «легше» for 4° more");
        check(WeatherRules.shifted(-89, "warmer") == -90 && WeatherRules.shifted(64, "lighter") == 65, "stays within the forecast's range");
        check(refused(() -> WeatherRules.day(20, 5)) && refused(() -> WeatherRules.day(Double.NaN, 5)) && refused(() -> WeatherRules.today(5, 20, 99))
            && refused(() -> WeatherRules.shifted(Double.POSITIVE_INFINITY, "warmer")) && refused(() -> WeatherRules.outerwear("cool", 100.0)),
            "invalid temperatures are refused");

        // A weather chosen by hand has no temperature: exactly the earlier rules.
        for (String weather : List.of("any_weather", "warm", "cool", "cold", "rain")) {
            boolean chilly = weather.equals("cool") || weather.equals("cold") || weather.equals("rain");
            check(WeatherRules.outerwear(weather, null) == chilly, "by hand: a coat for cool, cold and rain — " + weather);
            check(WeatherRules.layer(weather, null) == weather.equals("cold") && WeatherRules.tights(weather, null) == weather.equals("cold"),
                "by hand: a second layer and tights only for cold — " + weather);
            for (String season : List.of("all", "summer", "midseason", "winter"))
                check(WeatherRules.inSeason(season, weather, null) == WardrobeRules.compatibleSeasons(season, DailyRules.season(weather)),
                    "by hand: the mode's season — " + weather + " / " + season);
        }
        // With a temperature: the limits.
        check(WeatherRules.outerwear("cool", 14.9) && !WeatherRules.outerwear("cool", 15.0) && !WeatherRules.outerwear("cool", 21.0), "a coat below +15, not at +21");
        check(WeatherRules.layer("cold", 7.9) && !WeatherRules.layer("cool", 8.0), "a second layer below +8");
        check(WeatherRules.tights("cool", 11.9) && !WeatherRules.tights("cool", 12.0), "tights below +12");
        check(!WeatherRules.inSeason("summer", "cool", 18.9) && WeatherRules.inSeason("summer", "cool", 19.0) && WeatherRules.inSeason("summer", "warm", 35.0), "summer items from +19");
        check(!WeatherRules.inSeason("midseason", "cold", 4.9) && WeatherRules.inSeason("midseason", "cold", 5.0)
            && WeatherRules.inSeason("midseason", "warm", 24.9) && !WeatherRules.inSeason("midseason", "warm", 25.0), "mid-season items from +5 up to +25");
        check(WeatherRules.inSeason("winter", "cool", 10.9) && !WeatherRules.inSeason("winter", "cool", 11.0), "winter items below +11");
        check(!WeatherRules.inSeason("spring", "cool", 12.0) && WeatherRules.inSeason("all", "cold", -30.0), "unknown seasons never, «усі сезони» always");
        // The temperature only ever widens what the mode alone allows, so an explanation of an empty day stays true.
        for (double t = -30; t <= 45; t += .5) {
            String mode = RoadmapRules.weatherMode(t);
            for (String season : List.of("all", "summer", "midseason", "winter"))
                check(!WeatherRules.inSeason(season, mode, null) || WeatherRules.inSeason(season, mode, t), "never narrower than the mode at " + t + " / " + season);
            check(!WeatherRules.outerwear(mode, t) || WeatherRules.outerwear(mode, null), "a coat only where the mode has one, at " + t);
            check(!WeatherRules.layer(mode, t) || WeatherRules.layer(mode, null), "a second layer only in the cold band, at " + t);
        }

        // The generator. A summer tee, a mid-season shirt, a winter jumper as a top; jeans, trainers, a coat, a cardigan.
        List<DailyRules.Item> items = List.of(it("tee", "top", "summer"), it("shirt", "top", "midseason"), it("knit", "top", "winter"),
            it("jeans", "bottom", "all"), it("trainers", "shoes", "all"), it("coat", "outerwear", "all"), it("cardigan", "layer", "all"),
            it("dress", "dress", "all"), it("tights", "tights", "all"));
        // +21: «прохолодно» by the band — but no coat, and the summer tee is allowed next to the shirt.
        List<List<String>> mild = run(items, "cool", 21.0, Map.of(), 20);
        check(!mild.isEmpty() && !any(mild, "coat") && !any(mild, "cardigan") && !any(mild, "tights"), "+21: no coat, no second layer, no tights");
        check(any(mild, "tee") && any(mild, "shirt") && !any(mild, "knit"), "+21: summer and mid-season tops, not the winter one");
        // The same band chosen by hand: the coat, and only mid-season tops (as before).
        List<List<String>> byHand = run(items, "cool", null, Map.of(), 20);
        check(every(byHand, "coat") && !any(byHand, "tee") && !any(byHand, "knit") && !any(byHand, "cardigan"), "by hand «прохолодно»: the coat, mid-season only");
        // +12: the coat, no second layer; +10 with a dress: tights too.
        List<List<String>> fresh = run(items, "cool", 12.0, Map.of(), 20);
        check(every(fresh, "coat") && !any(fresh, "cardigan") && !any(fresh, "tee"), "+12: the coat, no second layer, no summer tee");
        check(!any(fresh, "tights"), "+12: no tights yet");
        List<List<String>> chilly = run(items, "cool", 10.0, Map.of("dress", "dress"), 5);
        check(every(chilly, "tights") && every(chilly, "coat") && any(chilly, "dress"), "+10: tights and a coat with the dress");
        check(any(run(items, "cool", 10.0, Map.of(), 20), "knit"), "+10: the winter jumper is allowed next to mid-season");
        // +5: the second layer and the coat; tights only with the dress.
        List<List<String>> cold = run(items, "cold", 5.0, Map.of(), 20);
        check(every(cold, "cardigan") && every(cold, "coat"), "+5: a second layer and a coat");
        for (List<String> look : cold) check(look.contains("tights") == look.contains("dress"), "tights go with the dress only");
        check(any(cold, "shirt") && any(cold, "knit") && !any(cold, "tee"), "+5: winter and mid-season tops");
        // Without those pieces the outfit is still suggested.
        List<DailyRules.Item> bare = List.of(it("shirt", "top", "all"), it("jeans", "bottom", "all"), it("trainers", "shoes", "all"), it("dress", "dress", "all"));
        check(run(bare, "cold", 0.0, Map.of(), 20).size() == 2, "no coat, jumper or tights: still suggested");
        // A pinned coat stays even at +21; a pinned piece that is missing still means no outfit.
        check(every(run(items, "cool", 21.0, Map.of("outerwear", "coat"), 3), "coat"), "a pinned coat stays");
        check(run(bare, "cold", 0.0, Map.of("layer", "cardigan"), 3).isEmpty(), "a pinned piece stays mandatory");
        // Pieces of different seasons are still never mixed (saved outfits require it).
        for (List<String> look : run(items, "cool", 10.0, Map.of(), 20)) check(!(look.contains("knit") && look.contains("shirt")), "one top");
        List<DailyRules.Item> seasons = List.of(it("tee", "top", "summer"), it("cords", "bottom", "midseason"), it("trainers", "shoes", "all"));
        check(run(seasons, "cool", 20.0, Map.of(), 3).isEmpty(), "a summer top and mid-season trousers are not combined");

        // The rating agrees with the generator.
        check(StyleRules.rate(pieces(items, "tee", "jeans", "trainers"), "cool", "casual", 21.0).weatherCode.equals(StyleRules.WEATHER_OK), "+21 without a coat is fine");
        check(StyleRules.rate(pieces(items, "shirt", "jeans", "trainers"), "cool", "casual", 12.0).weatherCode.equals(StyleRules.NO_OUTERWEAR), "+12 without a coat is noted");
        check(StyleRules.rate(pieces(items, "tee", "jeans", "trainers", "coat"), "cool", "casual", 12.0).weatherCode.equals(StyleRules.OUT_OF_SEASON), "+12: a summer tee is out of season");
        check(StyleRules.rate(pieces(items, "shirt", "jeans", "trainers"), "cool", "casual").weatherCode.equals(StyleRules.NO_OUTERWEAR), "by hand «прохолодно» without a coat is noted, as before");
        check(StyleRules.rate(pieces(items, "tee", "jeans", "trainers"), "cool", "casual").weatherCode.equals(StyleRules.OUT_OF_SEASON), "by hand: the mode's season, as before");
        for (List<String> look : mild) {
            List<WardrobeRules.Piece> worn = pieces(items, look.toArray(new String[0]));
            check(StyleRules.rate(worn, "cool", "casual", 21.0).weatherCode.equals(StyleRules.WEATHER_OK), "what the generator suggests suits the weather");
        }
        // «Краще» only offers what suits the temperature.
        StyleRules.Swap swap = StyleRules.better(pieces(items, "knit", "jeans", "trainers"), pieces(items, "tee", "shirt", "knit", "jeans", "trainers"),
            "cool", "casual", Set.of(), Set.of(), 21.0);
        check(swap != null && swap.oldId.equals("knit") && !swap.newId.equals("knit"), "+21: the winter jumper is replaced");

        swap = StyleRules.better(pieces(items, "knit", "jeans", "trainers"), pieces(items, "tee", "knit", "jeans", "trainers"),
            "cool", "casual", Set.of(), Set.of(), 21.0);
        check(swap != null && swap.newId.equals("tee"), "+21: «Краще» may offer the summer tee");
        check(StyleRules.better(pieces(items, "knit", "jeans", "trainers"), pieces(items, "tee", "knit", "jeans", "trainers"),
            "cool", "casual", Set.of(), Set.of()) == null, "by hand «прохолодно»: it does not");
        // The order uses the same rating: at +21 a summer tee worn less often leads, it is not marked down as out of season.
        List<DailyRules.Item> order = List.of(it("tee", "top", "summer"),
            new DailyRules.Item(new WardrobeRules.Piece("shirt", "home", "top", "available", false, "midseason", "casual", "white"), 1, false, ""),
            it("jeans", "bottom", "all"), it("trainers", "shoes", "all"));
        check(run(order, "cool", 21.0, Map.of(), 1).get(0).contains("tee"), "+21: the order rates the tee as in season");

        // «Давно не вдягали» follows the same seasons.
        RotationRules.Item tee = new RotationRules.Item(items.get(0).piece, "", "2026-01-01", false, false);
        check(RotationRules.problem(tee, "cool", 21.0) == null && RotationRules.WEATHER.equals(RotationRules.problem(tee, "cool", 12.0))
            && RotationRules.WEATHER.equals(RotationRules.problem(tee, "cool")), "a summer tee suits +21, not +12, not «прохолодно» by hand");

        // «Тепліше» and «легше» move the temperature, so they move the coat too.
        check(!WeatherRules.outerwear("cool", WeatherRules.shifted(17, "forecast")) && WeatherRules.outerwear("cool", WeatherRules.shifted(17, "warmer")), "«тепліше» at +17 adds a coat");
        check(WeatherRules.outerwear("cool", WeatherRules.shifted(13, "forecast")) && !WeatherRules.outerwear("cool", WeatherRules.shifted(13, "lighter")), "«легше» at +13 leaves it out");
        // WTH-02: a mild day with a likely rain is «Дощ» by itself; warm and cold days keep their band.
        check(WeatherRules.mode(14, 60).equals("rain") && WeatherRules.mode(14, 59).equals("cool") && WeatherRules.mode(14, null).equals("cool"),
            "rain from 60%, not below, not without a chance");
        check(WeatherRules.mode(8, 90).equals("rain") && WeatherRules.mode(7.9, 90).equals("cold") && WeatherRules.mode(21.9, 90).equals("rain")
            && WeatherRules.mode(22, 90).equals("warm"), "only the mild band turns into rain");
        for (double t = -30; t <= 40; t += 0.5)
            check(WeatherRules.mode(t, null).equals(RoadmapRules.weatherMode(t)), "without rain the mode is the band of " + t);
        check(WeatherRules.mode(14, 100).equals("rain") && WeatherRules.mode(14, 101).equals("cool") && WeatherRules.mode(14, -1).equals("cool"),
            "an impossible chance is ignored");
        check(WeatherRules.outerwear("rain", 21.9) && !WeatherRules.outerwear("rain", 22.0) && !WeatherRules.outerwear("cool", 18.0)
            && WeatherRules.outerwear("rain", 18.0), "a rainy day gets a jacket up to +22; a dry one only below +15");
        check(!WeatherRules.layer("rain", 14.0) && !WeatherRules.tights("rain", 14.0)
            && WeatherRules.inSeason("midseason", "rain", 14.0) == WeatherRules.inSeason("midseason", "cool", 14.0)
            && WeatherRules.inSeason("summer", "rain", 20.0) == WeatherRules.inSeason("summer", "cool", 20.0),
            "rain changes only the jacket: layers, tights and seasons follow the temperature");
        List<DailyRules.Item> rainy = List.of(it("tee", "top", "all"), it("jeans", "bottom", "all"), it("trainers", "shoes", "all"),
            it("mac", "outerwear", "midseason"));
        check(every(run(rainy, "rain", 18.0, Map.of(), 3), "mac") && !any(run(rainy, "cool", 18.0, Map.of(), 3), "mac"),
            "+18: the raincoat comes with the rain, not on a dry day");
        check(!run(rainy.subList(0, 3), "rain", 18.0, Map.of(), 3).isEmpty(), "without a jacket a rainy day still gets an outfit");
        System.out.println("PASS: " + checks + " weather assertions (temperature, limits, by hand as before, layers, rating, rotation, rain).");
    }
}
