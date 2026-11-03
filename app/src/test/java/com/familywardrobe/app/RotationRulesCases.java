package com.familywardrobe.app;

import java.util.*;

/** ROT-01: when an item counts as not worn for a long time, which ones suit today and in what order. */
public final class RotationRulesCases {
    private static int checks;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Rotation check " + checks + ": " + what); }
    private static final String TODAY = "2026-11-03";
    private static WardrobeRules.Piece piece(String id, String category, String availability, boolean archived, String season, String style) {
        return new WardrobeRules.Piece(id, "home", category, availability, archived, season, style, "black");
    }
    private static RotationRules.Item item(String id, String category, String lastWear, String added) {
        return new RotationRules.Item(piece(id, category, "available", false, "all", "casual"), lastWear, added, false, false);
    }
    private static RotationRules.Item with(RotationRules.Item base, WardrobeRules.Piece piece, boolean excluded, boolean uncomfortable) {
        return new RotationRules.Item(piece, base.lastWear, base.added, excluded, uncomfortable);
    }

    public static void main(String[] args) {
        // 90 days from the last wear, both sides of the boundary; a never-worn item counts from the day it was added.
        check(RotationRules.FORGOTTEN_DAYS == 90, "the user's threshold");
        check(RotationRules.idleDays("2026-08-05", "2026-01-01", TODAY) == 90, "90 days since the last wear");
        check(RotationRules.forgotten("2026-08-05", "2026-01-01", TODAY), "day 90 counts");
        check(!RotationRules.forgotten("2026-08-06", "2026-01-01", TODAY), "day 89 does not");
        check(RotationRules.forgotten("", "2026-08-05", TODAY), "never worn: since it was added");
        check(!RotationRules.forgotten("", "2026-10-30", TODAY), "a new item is not forgotten");
        check(!RotationRules.forgotten("2026-01-02", "2025-01-01", "2026-01-02"), "worn today");
        check(RotationRules.idleDays("", "2026-12-01", TODAY) == 0, "a future date (clock change) is never negative");
        check(RotationRules.idleDays("2026-11-01", "2026-12-01", TODAY) == 2, "the wear decides, not the added date");

        // What stops an item, in the order the person can act on it.
        RotationRules.Item base = item("x", "top", "", "2026-01-01");
        check(RotationRules.problem(base, "warm") == null, "an all-season item suits any weather");
        check(RotationRules.UNAVAILABLE.equals(RotationRules.problem(with(base, piece("x", "top", "laundry", false, "all", "casual"), false, false), "warm")), "laundry");
        check(RotationRules.UNAVAILABLE.equals(RotationRules.problem(with(base, piece("x", "top", "repair", false, "all", "casual"), false, false), "warm")), "repair");
        check(RotationRules.UNAVAILABLE.equals(RotationRules.problem(with(base, piece("x", "top", "lent", false, "all", "casual"), false, false), "warm")), "lent");
        check(RotationRules.UNAVAILABLE.equals(RotationRules.problem(with(base, piece("x", "top", "available", true, "all", "casual"), true, true), "warm")), "archive first");
        check(RotationRules.UNAVAILABLE.equals(RotationRules.problem(with(base, piece("x", "scarf", "available", false, "all", "casual"), false, false), "warm")), "not a built-in category");
        check(RotationRules.EXCLUDED.equals(RotationRules.problem(with(base, base.piece, true, true), "warm")), "excluded before uncomfortable");
        check(RotationRules.UNCOMFORTABLE.equals(RotationRules.problem(with(base, base.piece, false, true), "warm")), "uncomfortable");
        RotationRules.Item winter = with(base, piece("x", "outerwear", "available", false, "winter", "casual"), false, false);
        check(RotationRules.WEATHER.equals(RotationRules.problem(winter, "warm")), "a winter coat on a warm day");
        check(RotationRules.WEATHER.equals(RotationRules.problem(winter, "rain")), "a winter coat in rain (midseason)");
        check(RotationRules.problem(winter, "cold") == null, "a winter coat on a cold day");
        check(RotationRules.problem(winter, "any_weather") == null, "any weather accepts every season");
        RotationRules.Item midseason = with(base, piece("x", "layer", "available", false, "midseason", "casual"), false, false);
        check(RotationRules.problem(midseason, "cool") == null && RotationRules.problem(midseason, "rain") == null, "midseason in cool and rain");
        check(DailyRules.season("warm").equals("summer") && DailyRules.season("cold").equals("winter") &&
            DailyRules.season("cool").equals("midseason") && DailyRules.season("any_weather").equals("all"), "weather to season");

        // Candidates: only this wardrobe's forgotten, ready, suitable items; the occasion's style first, then the longest wait.
        List<RotationRules.Item> items = new ArrayList<>(List.of(
            item("old-casual", "top", "2026-06-01", "2025-01-01"),
            item("older-casual", "bottom", "2026-03-01", "2025-01-01"),
            new RotationRules.Item(piece("formal", "top", "available", false, "all", "formal"), "2026-07-01", "2025-01-01", false, false),
            new RotationRules.Item(piece("very-old-formal", "shoes", "available", false, "all", "formal"), "", "2025-01-01", false, false),
            item("recent", "top", "2026-10-30", "2025-01-01"),
            item("new", "shoes", "", "2026-10-01"),
            with(item("laundry", "top", "", "2025-01-01"), piece("laundry", "top", "laundry", false, "all", "casual"), false, false),
            with(item("archived", "top", "", "2025-01-01"), piece("archived", "top", "available", true, "all", "casual"), false, false),
            with(item("excluded", "top", "", "2025-01-01"), piece("excluded", "top", "available", false, "all", "casual"), true, false),
            with(item("uncomfortable", "top", "", "2025-01-01"), piece("uncomfortable", "top", "available", false, "all", "casual"), false, true),
            with(item("summer", "top", "", "2025-01-01"), piece("summer", "top", "available", false, "summer", "casual"), false, false),
            new RotationRules.Item(new WardrobeRules.Piece("child", "child", "top", "available", false, "all", "casual", "black"), "", "2025-01-01", false, false)));
        List<String> casual = RotationRules.candidates(items, "home", "cold", "casual", TODAY);
        check(casual.equals(List.of("older-casual", "old-casual", "very-old-formal", "formal")), "casual day: " + casual);
        List<String> formal = RotationRules.candidates(items, "home", "cold", "formal", TODAY);
        check(formal.equals(List.of("very-old-formal", "formal", "older-casual", "old-casual")), "formal day: " + formal);
        check(RotationRules.candidates(items, "home", "warm", "casual", TODAY).contains("summer"), "a summer item on a warm day");
        check(RotationRules.candidates(items, "child", "cold", "casual", TODAY).equals(List.of("child")), "another wardrobe has its own");
        check(RotationRules.candidates(Collections.emptyList(), "home", "cold", "casual", TODAY).isEmpty(), "empty wardrobe");
        // Ties by ID: the order does not depend on the input order.
        List<RotationRules.Item> twins = List.of(item("b", "top", "2026-01-01", "2025-01-01"), item("a", "bottom", "2026-01-01", "2025-01-01"));
        List<RotationRules.Item> reversed = new ArrayList<>(twins); Collections.reverse(reversed);
        check(RotationRules.candidates(twins, "home", "cold", "casual", TODAY).equals(List.of("a", "b")) &&
            RotationRules.candidates(reversed, "home", "cold", "casual", TODAY).equals(List.of("a", "b")), "stable ties");

        // «Інша річ»: the next one, wrapping around; the current item comes last.
        List<String> four = List.of("a", "b", "c", "d");
        check(RotationRules.order(four, "").equals(four), "from the start");
        check(RotationRules.order(four, "b").equals(List.of("c", "d", "a", "b")), "after b");
        check(RotationRules.order(four, "d").equals(List.of("a", "b", "c", "d")), "wraps around");
        check(RotationRules.order(four, "gone").equals(four), "an item no longer a candidate restarts the list");
        check(RotationRules.order(List.of("a"), "a").equals(List.of("a")), "a single item stays");
        check(RotationRules.order(Collections.emptyList(), "a").isEmpty(), "nothing to cycle");

        // On the production generator: the forgotten item pinned in its category makes a whole outfit around it.
        List<DailyRules.Item> wardrobe = new ArrayList<>();
        for (String[] p : new String[][] {{"t-old", "top"}, {"t-new", "top"}, {"b1", "bottom"}, {"s1", "shoes"}})
            wardrobe.add(new DailyRules.Item(piece(p[0], p[1], "available", false, "all", "casual"), p[0].equals("t-new") ? 9 : 0, false, ""));
        List<List<String>> looks = DailyRules.generate(wardrobe, "home", "everyday", "warm", TODAY, "rotation:" + TODAY,
            Collections.emptySet(), Map.of("top", "t-old"), 3);
        check(!looks.isEmpty() && looks.stream().allMatch(look -> look.contains("t-old") && !look.contains("t-new")), "anchored look " + looks);
        check(looks.get(0).containsAll(List.of("b1", "s1")), "a whole outfit");

        // ROT-02 «Відкласти»: a week, a month or three months; the item is back on that day, an unreadable date never hides it.
        check(RotationRules.until(TODAY, RotationRules.SNOOZE_WEEK).equals("2026-11-10"), "a week");
        check(RotationRules.until(TODAY, RotationRules.SNOOZE_MONTH).equals("2026-12-03"), "a month");
        check(RotationRules.until(TODAY, RotationRules.SNOOZE_SEASON).equals("2027-02-01"), "three months");
        boolean refused = false;
        try { RotationRules.until(TODAY, 5); } catch (IllegalArgumentException e) { refused = true; }
        check(refused, "only the offered durations");
        check(RotationRules.snoozed("2026-11-10", "2026-11-09") && !RotationRules.snoozed("2026-11-10", "2026-11-10"), "until is exclusive");
        check(!RotationRules.snoozed("", TODAY) && !RotationRules.snoozed("garbage", TODAY), "no or broken date: not postponed");
        check(RotationRules.reasonDays(RotationRules.WAITING) == 90 && RotationRules.reasonDays(RotationRules.SIZE) == 90 &&
            RotationRules.reasonDays(RotationRules.NO_MATCH) == 30, "reasons postpone as agreed");
        refused = false;
        try { RotationRules.reasonDays(RotationRules.LATER); } catch (IllegalArgumentException e) { refused = true; }
        check(refused, "a plain postponement has its own duration");

        // Postponed items leave the candidates; an item the card showed on another day this week goes to the end.
        List<RotationRules.Item> week = new ArrayList<>();
        for (RotationRules.Item i : items) {
            String until = i.piece.id.equals("older-casual") ? "2026-11-10" : "";
            String seen = i.piece.id.equals("old-casual") ? "2026-11-01" : i.piece.id.equals("formal") ? TODAY : "";
            week.add(new RotationRules.Item(i.piece, i.lastWear, i.added, i.excluded, i.uncomfortable, until, seen));
        }
        List<String> fair = RotationRules.candidates(week, "home", "cold", "casual", TODAY);
        check(fair.equals(List.of("very-old-formal", "formal", "old-casual")), "postponed out, seen this week last, today's stays: " + fair);
        check(RotationRules.candidates(week, "home", "cold", "casual", "2026-11-10").equals(List.of("older-casual", "old-casual", "very-old-formal", "formal")),
            "back on its day; seen a week ago no longer last: " + RotationRules.candidates(week, "home", "cold", "casual", "2026-11-10"));
        check(!RotationRules.recentlySeen(TODAY, TODAY) && RotationRules.recentlySeen("2026-11-02", TODAY) &&
            RotationRules.recentlySeen("2026-10-28", TODAY) && !RotationRules.recentlySeen("2026-10-27", TODAY), "a week of history");
        check(!RotationRules.recentlySeen("", TODAY) && !RotationRules.recentlySeen("2026-11-05", TODAY) && !RotationRules.recentlySeen("x", TODAY), "no, future or broken history");

        // Frequency: daily by default, weekly from the last day shown, off; «Сховати на сьогодні» only for today.
        check(RotationRules.FREQUENCIES.equals(List.of("daily", "weekly", "off")), "the three choices");
        check(RotationRules.visible(RotationRules.DAILY, "2026-11-02", "", TODAY), "daily");
        check(RotationRules.visible("unknown", "", "", TODAY), "an unknown value is the default");
        check(!RotationRules.visible(RotationRules.OFF, "", "", TODAY), "off");
        check(!RotationRules.visible(RotationRules.DAILY, "", TODAY, TODAY) && RotationRules.visible(RotationRules.DAILY, "", "2026-11-02", TODAY), "hidden for today only");
        check(RotationRules.visible(RotationRules.WEEKLY, "", "", TODAY), "weekly: first time");
        check(RotationRules.visible(RotationRules.WEEKLY, TODAY, "", TODAY), "weekly: all day once shown");
        check(!RotationRules.visible(RotationRules.WEEKLY, "2026-10-28", "", TODAY), "weekly: six days later");
        check(RotationRules.visible(RotationRules.WEEKLY, "2026-10-27", "", TODAY), "weekly: a week later");
        check(RotationRules.visible(RotationRules.WEEKLY, "2026-11-20", "", TODAY), "weekly: a clock moved back never hides it");
        check(!RotationRules.visible(RotationRules.WEEKLY, "2026-10-27", TODAY, TODAY), "weekly: hidden today");

        System.out.println("PASS: " + checks + " rotation assertions (90-day rule, suitability, order, generator anchor, postponing, frequency).");
    }
}
