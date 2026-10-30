package com.familywardrobe.app;

import java.util.*;

/** PERS-01: scope and effect of each reason for turning down a suggestion, run on the production generator. */
public final class PreferenceRulesCases {
    // Same lists as LookRules.OCCASIONS / WEATHERS (Kotlin); tools/check_preferences.py keeps them equal.
    static final List<String> OCCASIONS = List.of("everyday", "work", "occasion", "active_day");
    static final List<String> WEATHERS = List.of("any_weather", "warm", "cool", "cold", "rain");
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Preference check " + checks + ": " + what); }
    private static DailyRules.Item item(String id, String category, String style) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, "home", category, "available", false, "all", style, "black"), 0, false, "");
    }
    private static PreferenceRules.Row row(List<String> ids, String occasion, String weather, String reason) {
        return new PreferenceRules.Row(DailyRules.signature(ids), occasion, weather, reason);
    }
    private static List<List<String>> generate(List<DailyRules.Item> items, List<PreferenceRules.Row> rows, String occasion, String weather) {
        return DailyRules.generate(items, "home", occasion, weather, "2026-10-30", "seed",
            PreferenceRules.rejected(rows, occasion, weather), Map.of(), 3, PreferenceRules.penalties(rows, occasion, weather));
    }

    public static void main(String[] args) {
        List<String> look = List.of("t1", "b1", "s1");
        String set = DailyRules.signature(look);

        // «Не подобається поєднання»: this set, in every context; its items stay as they were.
        List<PreferenceRules.Row> dislike = List.of(row(look, "everyday", "warm", PreferenceRules.DISLIKE));
        for (String occasion : OCCASIONS) for (String weather : WEATHERS)
            check(PreferenceRules.rejected(dislike, occasion, weather).equals(Set.of(set)), "dislike everywhere " + occasion + "/" + weather);
        check(PreferenceRules.penalties(dislike, "everyday", "warm").isEmpty(), "dislike does not touch items");

        // «Незручно»: one item, less often everywhere, never a rejected set.
        List<PreferenceRules.Row> uncomfortable = List.of(row(List.of("s1"), "work", "cold", PreferenceRules.DISLIKE));
        check(PreferenceRules.rejected(uncomfortable, "work", "cold").isEmpty(), "uncomfortable rejects nothing");
        check(PreferenceRules.penalties(uncomfortable, "everyday", "warm").equals(Map.of("s1", PreferenceRules.UNCOMFORTABLE)), "uncomfortable everywhere");
        List<PreferenceRules.Row> twice = List.of(uncomfortable.get(0), row(List.of("s1"), "everyday", "warm", PreferenceRules.DISLIKE));
        check(PreferenceRules.penalties(twice, "active_day", "rain").equals(Map.of("s1", PreferenceRules.UNCOMFORTABLE)), "uncomfortable counts once");

        // «Надто офіційно»: this occasion only, any weather.
        List<PreferenceRules.Row> formal = List.of(row(look, "everyday", "warm", PreferenceRules.OCCASION));
        check(PreferenceRules.rejected(formal, "everyday", "cold").equals(Set.of(set)), "formal in its occasion, other weather");
        check(PreferenceRules.rejected(formal, "work", "warm").isEmpty(), "formal not in another occasion");
        check(PreferenceRules.penalties(formal, "everyday", "rain").equals(Map.of("t1", 6, "b1", 6, "s1", 6)), "formal penalises its items in the occasion");
        check(PreferenceRules.penalties(formal, "work", "warm").isEmpty(), "formal penalises nothing elsewhere");

        // «Надто тепло»: this weather only, any occasion.
        List<PreferenceRules.Row> hot = List.of(row(look, "work", "cold", PreferenceRules.HOT));
        check(PreferenceRules.rejected(hot, "everyday", "cold").equals(Set.of(set)), "hot in its weather, other occasion");
        check(PreferenceRules.rejected(hot, "work", "cool").isEmpty(), "hot not in other weather");
        check(PreferenceRules.penalties(hot, "active_day", "cold").get("b1") == 6, "hot penalises in its weather");

        // Penalties add up but stop at the cap.
        List<PreferenceRules.Row> many = List.of(row(List.of("s1"), "everyday", "warm", PreferenceRules.DISLIKE),
            row(look, "everyday", "warm", PreferenceRules.OCCASION), row(look, "everyday", "warm", PreferenceRules.HOT));
        check(PreferenceRules.penalties(many, "everyday", "warm").get("s1") == PreferenceRules.MAX, "cap");
        check(PreferenceRules.penalties(many, "everyday", "warm").get("t1") == PreferenceRules.MAX, "two context reasons reach the cap");
        check(PreferenceRules.penalties(many, "work", "cool").get("s1") == PreferenceRules.UNCOMFORTABLE, "only the item reason elsewhere");

        // An unknown reason from a newer phone changes nothing rather than failing.
        check(PreferenceRules.rejected(List.of(row(look, "everyday", "warm", "later")), "everyday", "warm").isEmpty(), "unknown reason ignored");

        // «Надто тепло» moves the conditions one step lighter.
        check(PreferenceRules.lighter("cold").equals("cool") && PreferenceRules.lighter("cool").equals("warm"), "lighter steps");
        check(PreferenceRules.lighter("warm").equals("warm") && PreferenceRules.lighter("rain").equals("rain"), "lighter stops");
        check(PreferenceRules.lighter("any_weather").equals("warm"), "lighter from any weather");

        // On the generator: two equal tops and two shoes.
        List<DailyRules.Item> items = List.of(item("t1", "top", "casual"), item("t2", "top", "casual"), item("b1", "bottom", "casual"),
            item("s1", "shoes", "casual"), item("s2", "shoes", "casual"));
        List<List<String>> before = generate(items, List.of(), "everyday", "warm");
        String first = before.get(0).stream().filter(id -> id.startsWith("s")).findFirst().orElseThrow();
        List<List<String>> after = generate(items, List.of(row(List.of(first), "everyday", "warm", PreferenceRules.DISLIKE)), "everyday", "warm");
        check(!after.get(0).contains(first), "an uncomfortable item drops from the first suggestion");
        check(after.stream().anyMatch(ids -> ids.contains(first)), "but is still suggested");

        // The only shoes stay suggested however uncomfortable: a reason never hides the wardrobe.
        List<DailyRules.Item> oneShoe = List.of(item("t1", "top", "casual"), item("b1", "bottom", "casual"), item("s1", "shoes", "casual"));
        check(generate(oneShoe, List.of(row(List.of("s1"), "everyday", "warm", PreferenceRules.DISLIKE)), "everyday", "warm").size() == 1, "only item kept");

        // One disliked set leaves the others.
        List<List<String>> remaining = generate(items, List.of(row(before.get(0), "everyday", "warm", PreferenceRules.DISLIKE)), "work", "cold");
        check(!remaining.isEmpty() && remaining.stream().noneMatch(ids -> DailyRules.signature(ids).equals(DailyRules.signature(before.get(0)))), "others stay");

        // «Надто офіційно» in everyday steers away there but not at work.
        List<DailyRules.Item> styled = List.of(item("formal", "top", "formal"), item("casual", "top", "casual"), item("b1", "bottom", "casual"), item("s1", "shoes", "casual"));
        List<List<String>> work = generate(styled, List.of(), "work", "warm");
        check(work.get(0).contains("formal"), "formal top first at work");
        List<PreferenceRules.Row> tooFormal = List.of(row(work.get(0), "work", "warm", PreferenceRules.OCCASION));
        check(!generate(styled, tooFormal, "work", "warm").get(0).contains("formal"), "after «надто офіційно» the casual top leads at work");
        check(generate(styled, tooFormal, "work", "cold").stream().noneMatch(ids -> DailyRules.signature(ids).equals(DailyRules.signature(work.get(0)))), "the set stays out at work in any weather");

        // The old signature keeps working for callers without preferences.
        check(DailyRules.generate(items, "home", "everyday", "warm", "2026-10-30", "seed", Set.of(), Map.of(), 3).equals(before), "no penalties = old result");
        System.out.println("PASS: " + checks + " preference assertions (scope, effect, cap, generator).");
    }
}
