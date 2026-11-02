package com.familywardrobe.app;

import java.util.*;

/** PERS-02: generalised preferences — sources, thresholds, the person's choice first, and no rule ever empties the day. */
public final class PreferenceProfileCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Profile check " + checks + ": " + what); }
    private static PreferenceRules.Row row(String signature, String occasion, String weather, String reason) {
        return new PreferenceRules.Row(signature, occasion, weather, reason);
    }
    private static DailyRules.Item item(String id, String category, String style) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, "home", category, "available", false, "all", style, "black"), 0, false, "");
    }
    private static List<WardrobeRules.Piece> pieces(List<DailyRules.Item> items) {
        List<WardrobeRules.Piece> result = new ArrayList<>(); for (DailyRules.Item i : items) result.add(i.piece); return result;
    }

    public static void main(String[] args) {
        // No history: the occasion's usual style, the forecast as it is.
        PreferenceProfile.Style none = PreferenceProfile.style(List.of(), "work", null);
        check(none.style.equals("formal") && none.source == PreferenceProfile.Source.DEFAULT && !none.avoidFormal, "default work style");
        check(PreferenceProfile.style(List.of(), "active_day", PreferenceProfile.AUTO).style.equals("sport"), "auto with no history = default");
        PreferenceProfile.Warmth calm = PreferenceProfile.warmth(List.of(), null);
        check(calm.shift.equals("forecast") && calm.source == PreferenceProfile.Source.DEFAULT, "default warmth");

        // One stray reason does not generalise; two different sets do.
        List<PreferenceRules.Row> one = List.of(row("a|b", "work", "warm", PreferenceRules.OCCASION));
        check(PreferenceProfile.style(one, "work", null).source == PreferenceProfile.Source.DEFAULT, "one reason stays specific");
        List<PreferenceRules.Row> sameTwice = List.of(row("a|b", "work", "warm", PreferenceRules.OCCASION), row("a|b", "work", "cold", PreferenceRules.OCCASION));
        check(PreferenceProfile.style(sameTwice, "work", null).evidence == 1, "the same set counts once");
        List<PreferenceRules.Row> two = List.of(row("a|b", "work", "warm", PreferenceRules.OCCASION), row("c|d", "work", "cold", PreferenceRules.OCCASION));
        PreferenceProfile.Style auto = PreferenceProfile.style(two, "work", null);
        check(auto.style.equals("casual") && auto.source == PreferenceProfile.Source.AUTO && auto.evidence == 2 && auto.avoidFormal, "two reasons → casual at work");
        check(PreferenceProfile.style(two, "occasion", null).style.equals("formal"), "another occasion unaffected");
        check(PreferenceProfile.style(List.of(row("a", "work", "warm", PreferenceRules.OCCASION), row("b", "work", "warm", PreferenceRules.OCCASION)), "work", null)
            .source == PreferenceProfile.Source.DEFAULT, "item rows are not style evidence");

        // The person's choice wins; choosing formal also stops "formal less often".
        PreferenceProfile.Style chosen = PreferenceProfile.style(two, "work", "formal");
        check(chosen.style.equals("formal") && chosen.source == PreferenceProfile.Source.CHOICE && !chosen.avoidFormal, "explicit formal");
        check(PreferenceProfile.style(two, "work", "sport").avoidFormal, "explicit non-formal keeps the evidence");
        check(PreferenceProfile.style(List.of(), "work", "nonsense").source == PreferenceProfile.Source.DEFAULT, "unknown choice ignored");

        // Warmth from «Надто тепло», counted per set and weather.
        List<PreferenceRules.Row> hot = List.of(row("a|b", "everyday", "cold", PreferenceRules.HOT), row("a|b", "everyday", "cool", PreferenceRules.HOT));
        PreferenceProfile.Warmth warm = PreferenceProfile.warmth(hot, null);
        check(warm.shift.equals("lighter") && warm.source == PreferenceProfile.Source.AUTO && warm.evidence == 2, "auto lighter");
        check(PreferenceProfile.warmth(hot, "forecast").shift.equals("forecast"), "choice overrides auto");
        check(PreferenceProfile.warmth(hot.subList(0, 1), null).source == PreferenceProfile.Source.DEFAULT, "one hot reason stays specific");
        check(PreferenceProfile.apply("cold", "lighter").equals("cool") && PreferenceProfile.apply("cool", "warmer").equals("cold"), "shift steps");
        check(PreferenceProfile.apply("warm", "warmer").equals("cool") && PreferenceProfile.apply("rain", "warmer").equals("rain"), "warmer bounds");
        check(PreferenceProfile.apply("cold", "warmer").equals("cold") && PreferenceProfile.apply("warm", "forecast").equals("warm"), "no further than cold");

        // On the generator: the target style picks the top; avoidFormal adds to the cap, never past it.
        List<DailyRules.Item> items = List.of(item("formal", "top", "formal"), item("casual", "top", "casual"),
            item("b1", "bottom", "casual"), item("s1", "shoes", "casual"));
        Map<String, Integer> plain = PreferenceProfile.penalties(List.of(), none, "work", "warm", pieces(items));
        check(plain.isEmpty(), "no penalties without reasons");
        List<String> atWork = DailyRules.generate(items, "home", "work", "warm", "2026-10-30", "s", Set.of(), Map.of(), 3, plain, none.style).get(0);
        check(atWork.contains("formal"), "formal top first at work by default");
        check(DailyRules.generate(items, "home", "work", "warm", "2026-10-30", "s", Set.of(), Map.of(), 3, Map.of(), "casual").get(0).contains("casual"),
            "a casual target alone picks the casual top at work");
        check(DailyRules.generate(items, "home", "everyday", "warm", "2026-10-30", "s", Set.of(), Map.of(), 3, Map.of(), "formal").get(0).contains("formal"),
            "a formal target alone picks the formal top for everyday");
        Map<String, Integer> steered = PreferenceProfile.penalties(two, auto, "work", "warm", pieces(items));
        check(steered.get("formal") == PreferenceRules.CONTEXT && !steered.containsKey("casual"), "formal items less often");
        List<String> after = DailyRules.generate(items, "home", "work", "warm", "2026-10-30", "s", Set.of(), Map.of(), 3, steered, auto.style).get(0);
        check(after.contains("casual"), "casual top first after the preference forms");
        List<PreferenceRules.Row> heavy = new ArrayList<>(two); heavy.add(row("formal", "work", "warm", PreferenceRules.DISLIKE));
        check(PreferenceProfile.penalties(heavy, auto, "work", "warm", pieces(items)).get("formal") == PreferenceRules.MAX, "still capped");

        // Contradicting preferences only change scores: with every combination of choices the day is never empty.
        List<DailyRules.Item> onlyFormal = List.of(item("formal", "top", "formal"), item("b1", "bottom", "formal"), item("s1", "shoes", "formal"));
        for (String occasion : List.of("everyday", "work", "occasion", "active_day")) for (String choice : List.of("auto", "casual", "formal", "sport")) {
            PreferenceProfile.Style style = PreferenceProfile.style(two, occasion, choice);
            Map<String, Integer> penalty = PreferenceProfile.penalties(heavy, style, occasion, "warm", pieces(onlyFormal));
            check(!DailyRules.generate(onlyFormal, "home", occasion, "warm", "2026-10-30", "s", Set.of(), Map.of(), 3, penalty, style.style).isEmpty(),
                "never empty " + occasion + "/" + choice);
        }
        System.out.println("PASS: " + checks + " profile assertions (sources, thresholds, choices, never empty).");
    }
}
