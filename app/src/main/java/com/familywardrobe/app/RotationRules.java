package com.familywardrobe.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ROT-01: which items have not been worn for a long time and which of them suit today. Pure rules, so the same
 * definition serves the statistics («Не носив 90+ днів») and the daily suggestion, and every case is tested on a host.
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
        public Item(WardrobeRules.Piece piece, String lastWear, String added, boolean excluded, boolean uncomfortable) {
            this.piece = piece; this.lastWear = lastWear; this.added = added; this.excluded = excluded; this.uncomfortable = uncomfortable;
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
    public static String problem(Item item, String weather) {
        if (!WardrobeRules.ready(item.piece)) return UNAVAILABLE;
        if (item.excluded) return EXCLUDED;
        if (item.uncomfortable) return UNCOMFORTABLE;
        if (!WardrobeRules.compatibleSeasons(item.piece.season, DailyRules.season(weather))) return WEATHER;
        return null;
    }

    /**
     * Items of [wardrobe] not worn for [FORGOTTEN_DAYS] that suit today: ready (not archived, in the laundry, under
     * repair or lent), not excluded or uncomfortable, and right for the weather. The occasion's style comes first,
     * then the longest wait; ties by ID, so the order is stable.
     */
    public static List<String> candidates(List<Item> items, String wardrobe, String weather, String style, String today) {
        List<Item> picked = new ArrayList<>();
        for (Item item : items) {
            if (!wardrobe.equals(item.piece.wardrobe) || problem(item, weather) != null) continue;
            if (forgotten(item.lastWear, item.added, today)) picked.add(item);
        }
        picked.sort(Comparator.<Item>comparingInt(i -> i.piece.style.equals(style) ? 0 : 1)
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
}
