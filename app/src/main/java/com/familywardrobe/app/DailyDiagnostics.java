package com.familywardrobe.app;

import java.util.*;

/** Explains a strict empty result by checking the same generator with one explicit constraint removed. */
public final class DailyDiagnostics {
    public enum Reason { NO_ITEMS, CAPSULE_EMPTY, PACKED, UNAVAILABLE, SHOES, BASE, WEATHER, SEASONS, PINS, REPEATS, EXCLUDED, FEEDBACK, CONSTRAINTS }
    private DailyDiagnostics() {}
    public static Reason explain(List<DailyRules.Item> all, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> scope, Set<String> fullCapsule, boolean packedOnly,
            Set<String> excluded, int noRepeatDays, Set<String> rejected, Map<String,String> fixed) {
        List<DailyRules.Item> local = new ArrayList<>();
        for (DailyRules.Item i : all) if (wardrobe.equals(i.piece.wardrobe)) local.add(i);
        if (fullCapsule != null && fullCapsule.isEmpty()) return Reason.CAPSULE_EMPTY;
        if (local.isEmpty()) return Reason.NO_ITEMS;
        List<DailyRules.Item> scoped = filter(local, scope, excluded, noRepeatDays, date);
        if (packedOnly && !possible(scoped, wardrobe, occasion, weather, date, seed, rejected, fixed) &&
                possible(filter(local, fullCapsule, excluded, noRepeatDays, date), wardrobe, occasion, weather, date, seed, rejected, fixed)) return Reason.PACKED;
        List<DailyRules.Item> raw = filter(local, scope, Collections.emptySet(), 0, date);
        Set<String> categories = new HashSet<>();
        for (DailyRules.Item i : raw) if (WardrobeRules.ready(i.piece)) categories.add(i.piece.category);
        if (!base(categories)) {
            Set<String> owned = new HashSet<>(); for (DailyRules.Item i : raw) owned.add(i.piece.category);
            if (base(owned)) return Reason.UNAVAILABLE;
            if (!categories.contains("shoes")) return packedOnly ? Reason.PACKED : Reason.SHOES;
            return packedOnly ? Reason.PACKED : Reason.BASE;
        }
        if (!fixed.isEmpty() && possible(scoped, wardrobe, occasion, weather, date, seed, rejected, Collections.emptyMap())) return Reason.PINS;
        if (noRepeatDays > 0 && possible(filter(local, scope, excluded, 0, date), wardrobe, occasion, weather, date, seed, rejected, fixed)) return Reason.REPEATS;
        if (!excluded.isEmpty() && possible(filter(local, scope, Collections.emptySet(), noRepeatDays, date), wardrobe, occasion, weather, date, seed, rejected, fixed)) return Reason.EXCLUDED;
        if (!rejected.isEmpty() && possible(scoped, wardrobe, occasion, weather, date, seed, Collections.emptySet(), fixed)) return Reason.FEEDBACK;
        if (!weather.equals("any_weather") && possible(scoped, wardrobe, occasion, "any_weather", date, seed, rejected, fixed)) return Reason.WEATHER;
        if (!possible(raw, wardrobe, occasion, "any_weather", date, seed, Collections.emptySet(), Collections.emptyMap())) return Reason.SEASONS;
        return Reason.CONSTRAINTS;
    }
    private static boolean base(Set<String> categories) {
        return categories.contains("shoes") && (categories.contains("dress") || categories.contains("jumpsuit") || categories.containsAll(WardrobeRules.CORE));
    }
    private static List<DailyRules.Item> filter(List<DailyRules.Item> all, Set<String> scope, Set<String> excluded, int days, String date) {
        List<DailyRules.Item> result = new ArrayList<>();
        for (DailyRules.Item i : all) if ((scope == null || scope.contains(i.piece.id)) &&
                RecommendationRules.allowed(excluded.contains(i.piece.id), i.lastWear, date, days)) result.add(i);
        return result;
    }
    private static boolean possible(List<DailyRules.Item> items, String wardrobe, String occasion, String weather, String date,
            String seed, Set<String> rejected, Map<String,String> fixed) {
        return !DailyRules.generate(items, wardrobe, occasion, weather, date, seed, rejected, fixed, 1).isEmpty();
    }
}
