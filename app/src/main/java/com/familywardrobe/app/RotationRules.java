package com.familywardrobe.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ROT-01: which items have not been worn for a long time and which of them suit today. Pure rules, so the same
 * definition serves the statistics («Не носив 90+ днів») and the daily suggestion, and every case is tested on a host.
 * ROT-02: postponing an item, how often the card appears, and why an item is not worn — without nagging.
 */
public final class RotationRules {
    private RotationRules() {}
    /** The user's choice (3 November 2026): the same 90 days as the statistics. */
    public static final int FORGOTTEN_DAYS = 90;
    /** At most this many items are tried for one suggestion, so the daily screen stays fast. */
    public static final int ATTEMPTS = 8;

    public static final String UNAVAILABLE = "unavailable";
    public static final String EXCLUDED = "excluded";
    public static final String UNCOMFORTABLE = "uncomfortable";
    public static final String WEATHER = "weather";
    /** The wardrobe's «не повторювати N днів» rule still holds the item back (checked by the app, not here). */
    public static final String REPEAT = "repeat";

    /** ROT-02: how often the «Давно не вдягали» card appears (the user's choice: daily by default). */
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String OFF = "off";
    public static final List<String> FREQUENCIES = List.of(DAILY, WEEKLY, OFF);
    public static final int WEEK = 7;
    /** «Відкласти»: a week, a month or three months (the user's choice). */
    public static final int SNOOZE_WEEK = 7;
    public static final int SNOOZE_MONTH = 30;
    public static final int SNOOZE_SEASON = 90;
    public static final List<Integer> SNOOZES = List.of(SNOOZE_WEEK, SNOOZE_MONTH, SNOOZE_SEASON);
    /** Why an item is not worn. «Незручно» is a PERS-01 reason row instead; these are stored with a postponement. */
    public static final String LATER = "later";
    public static final String SIZE = "size";
    public static final String WAITING = "waiting";
    public static final String NO_MATCH = "no_match";
    public static final List<String> REASONS = List.of(LATER, SIZE, WAITING, NO_MATCH);

    public static final class Item {
        public final WardrobeRules.Piece piece;
        /** The last actual wear on or before today, or "" when never worn. */
        public final String lastWear;
        /** The day the item was added (local date). */
        public final String added;
        /** «Не пропонувати» in the daily rules. */
        public final boolean excluded;
        /** PERS-01 «Незручно» for this item. */
        public final boolean uncomfortable;
        /** ROT-02: postponed until this day (exclusive), or "". */
        public final String snoozedUntil;
        /** ROT-02: the last day the card showed this item, or "". */
        public final String seen;
        public Item(WardrobeRules.Piece piece, String lastWear, String added, boolean excluded, boolean uncomfortable) {
            this(piece, lastWear, added, excluded, uncomfortable, "", "");
        }
        public Item(WardrobeRules.Piece piece, String lastWear, String added, boolean excluded, boolean uncomfortable,
                    String snoozedUntil, String seen) {
            this.piece = piece; this.lastWear = lastWear; this.added = added; this.excluded = excluded; this.uncomfortable = uncomfortable;
            this.snoozedUntil = snoozedUntil; this.seen = seen;
        }
    }

    /** Days since the last wear, or since the item was added when it was never worn; never negative. */
    public static long idleDays(String lastWear, String added, String today) {
        LocalDate since = LocalDate.parse(lastWear.isEmpty() ? added : lastWear);
        return Math.max(0, ChronoUnit.DAYS.between(since, LocalDate.parse(today)));
    }

    public static boolean forgotten(String lastWear, String added, String today) {
        return idleDays(lastWear, added, today) >= FORGOTTEN_DAYS;
    }

    /**
     * Why [item] cannot be the centre of today's outfit, or null when it can (the outfit may still need other items).
     * Order matters: the first reason that applies is the one the person can act on.
     */
    public static String problem(Item item, String weather) { return problem(item, weather, null); }
    /** [temperature]: the forecast's temperature behind [weather], or null (WTH-01, see WeatherRules). */
    public static String problem(Item item, String weather, Double temperature) {
        if (!WardrobeRules.ready(item.piece)) return UNAVAILABLE;
        if (item.excluded) return EXCLUDED;
        if (item.uncomfortable) return UNCOMFORTABLE;
        if (!WeatherRules.inSeason(item.piece.season, weather, temperature)) return WEATHER;
        return null;
    }

    /**
     * Items of [wardrobe] not worn for [FORGOTTEN_DAYS] that suit today: ready (not archived, in the laundry, under
     * repair or lent), not excluded or uncomfortable, not postponed, and right for the weather. An item the card showed
     * on another day of the last week goes to the end (ROT-02: no nagging); then the occasion's style, then the longest
     * wait; ties by ID, so the order is stable.
     */
    public static List<String> candidates(List<Item> items, String wardrobe, String weather, String style, String today) {
        return candidates(items, wardrobe, weather, style, today, null);
    }
    public static List<String> candidates(List<Item> items, String wardrobe, String weather, String style, String today, Double temperature) {
        List<Item> picked = new ArrayList<>();
        for (Item item : items) {
            if (!wardrobe.equals(item.piece.wardrobe) || problem(item, weather, temperature) != null) continue;
            if (snoozed(item.snoozedUntil, today)) continue;
            if (forgotten(item.lastWear, item.added, today)) picked.add(item);
        }
        picked.sort(Comparator.<Item>comparingInt(i -> recentlySeen(i.seen, today) ? 1 : 0)
            .thenComparingInt(i -> i.piece.style.equals(style) ? 0 : 1)
            .thenComparing(Comparator.<Item>comparingLong(i -> idleDays(i.lastWear, i.added, today)).reversed())
            .thenComparing(i -> i.piece.id));
        List<String> ids = new ArrayList<>();
        for (Item item : picked) ids.add(item.piece.id);
        return ids;
    }

    /**
     * «Інша річ»: the candidates after [after], wrapping around; [after] itself comes last, so the card keeps the item
     * when no other one makes an outfit today.
     */
    public static List<String> order(List<String> candidates, String after) {
        int start = after.isEmpty() ? 0 : candidates.indexOf(after) + 1;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) result.add(candidates.get((start + i) % candidates.size()));
        return result;
    }

    /** ROT-02: the first day a postponed item may appear again. */
    public static String until(String today, int days) {
        if (!SNOOZES.contains(days)) throw new IllegalArgumentException("snooze " + days);
        return LocalDate.parse(today).plusDays(days).toString();
    }

    /** How long a reason postpones the item: «чекаю нагоди» and «не той розмір» (kept) 3 months, «немає з чим» a month. */
    public static int reasonDays(String reason) {
        switch (reason) {
            case SIZE: case WAITING: return SNOOZE_SEASON;
            case NO_MATCH: return SNOOZE_MONTH;
            default: throw new IllegalArgumentException("reason " + reason);
        }
    }

    /** Postponed until [until] (exclusive); an unreadable date never hides an item. */
    public static boolean snoozed(String until, String today) {
        if (until.isEmpty()) return false;
        try { return LocalDate.parse(today).isBefore(LocalDate.parse(until)); } catch (RuntimeException e) { return false; }
    }

    /** Shown by the card on another day of the last week; today's item stays first all day. */
    public static boolean recentlySeen(String seen, String today) {
        if (seen.isEmpty() || seen.equals(today)) return false;
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(seen), LocalDate.parse(today));
            return days > 0 && days < WEEK;
        } catch (RuntimeException e) { return false; }
    }

    /**
     * Whether the card appears today: never when off or hidden for today; daily — every day; weekly — on the day it
     * was last shown and again a week later. An unknown frequency counts as daily (the default).
     */
    public static boolean visible(String frequency, String lastShown, String hiddenOn, String today) {
        if (OFF.equals(frequency) || today.equals(hiddenOn)) return false;
        if (!WEEKLY.equals(frequency) || lastShown.isEmpty() || lastShown.equals(today)) return true;
        try {
            long days = ChronoUnit.DAYS.between(LocalDate.parse(lastShown), LocalDate.parse(today));
            return days < 0 || days >= WEEK; // a clock moved back never hides the card for longer
        } catch (RuntimeException e) { return true; }
    }
}
