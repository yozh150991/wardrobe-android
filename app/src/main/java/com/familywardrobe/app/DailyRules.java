package com.familywardrobe.app;

import java.util.*;

/** Bounded, deterministic outfit ranking; all candidates must be real, available owned items. */
public final class DailyRules {
    public static final class Item {
        public final WardrobeRules.Piece piece;
        public final int wears;
        public final boolean favorite;
        public final String lastWear;
        public Item(WardrobeRules.Piece piece, int wears, boolean favorite, String lastWear) {
            this.piece = piece; this.wears = wears; this.favorite = favorite; this.lastWear = lastWear;
        }
    }
    private static final class Candidate {
        final List<Item> items;
        final int score;
        Candidate(List<Item> items, int score) { this.items = items; this.score = score; }
    }
    public static String signature(Collection<String> ids) {
        List<String> sorted = new ArrayList<>(new HashSet<>(ids)); Collections.sort(sorted); return String.join("|", sorted);
    }
    public static List<List<String>> generate(List<Item> items, String wardrobe, String occasion, String weather,
            String date, String seed, Set<String> rejected, Map<String, String> fixed, int limit) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException();
        String season = weather.equals("warm") ? "summer" : weather.equals("cold") ? "winter" :
            weather.equals("cool") || weather.equals("rain") ? "midseason" : "all";
        String style = occasion.equals("work") || occasion.equals("occasion") ? "formal" :
            occasion.equals("active_day") ? "sport" : "casual";
        List<Candidate> all = new ArrayList<>();
        for (List<String> layout : WardrobeRules.baseLayouts(fixed.keySet())) {
        List<String> categories = new ArrayList<>(layout);
        if (weather.equals("cold") || weather.equals("cool") || weather.equals("rain")) categories.add("outerwear");
        if (!WardrobeRules.CATEGORIES.containsAll(fixed.keySet())) return Collections.emptyList();
        for (String category : WardrobeRules.CATEGORIES) if (fixed.containsKey(category) && !categories.contains(category)) categories.add(category);
        List<Candidate> beam = new ArrayList<>(); beam.add(new Candidate(Collections.emptyList(), 0));
        for (String category : categories) {
            List<Item> choices = new ArrayList<>();
            for (Item item : items) {
                WardrobeRules.Piece p = item.piece;
                if (wardrobe.equals(p.wardrobe) && category.equals(p.category) && WardrobeRules.ready(p) &&
                    WardrobeRules.compatibleSeasons(p.season, season) &&
                    (!fixed.containsKey(category) || fixed.get(category).equals(p.id))) choices.add(item);
            }
            if (choices.isEmpty()) { beam = Collections.emptyList(); break; }
            choices.sort(Comparator.<Item>comparingInt(i -> itemScore(i, style, date)).reversed()
                .thenComparingInt(i -> Objects.hash(seed, i.piece.id)).thenComparing(i -> i.piece.id));
            if (choices.size() > 24) choices = new ArrayList<>(choices.subList(0, 24));
            List<Candidate> expanded = new ArrayList<>();
            for (Candidate prior : beam) for (Item next : choices) {
                boolean compatible = true;
                int score = prior.score + itemScore(next, style, date);
                for (Item selected : prior.items) {
                    if (selected.piece.id.equals(next.piece.id) || !WardrobeRules.compatibleSeasons(selected.piece.season, next.piece.season)) { compatible = false; break; }
                    score += WardrobeRules.score(selected.piece, next.piece);
                }
                if (compatible) { List<Item> picked = new ArrayList<>(prior.items); picked.add(next); expanded.add(new Candidate(picked, score)); }
            }
            expanded.sort(Comparator.<Candidate>comparingInt(c -> c.score).reversed()
                .thenComparingInt(c -> Objects.hash(seed, key(c))).thenComparing(DailyRules::key));
            // Keep a broad beam so rejected top looks do not eliminate every alternative.
            beam = new ArrayList<>(expanded.subList(0, Math.min(256, expanded.size())));
            if (beam.isEmpty()) break;
        }
        all.addAll(beam);
        }
        all.sort(Comparator.<Candidate>comparingInt(c -> c.score * 1000 / Math.max(1, c.items.size())).reversed()
            .thenComparingInt(c -> Objects.hash(seed, key(c))).thenComparing(DailyRules::key));
        List<List<String>> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Candidate c : all) {
            List<String> ids = new ArrayList<>(); for (Item i : c.items) ids.add(i.piece.id);
            String sig = signature(ids);
            if (!rejected.contains(sig) && seen.add(sig)) { result.add(ids); if (result.size() == limit) break; }
        }
        return result;
    }
    public static List<String> replace(List<Item> items, String wardrobe, String occasion, String weather,
            String date, List<String> selected, String category, Set<String> rejected) {
        Map<String, String> fixed = new HashMap<>(); String old = "";
        for (String id : selected) {
            Item item = null; for (Item candidate : items) if (candidate.piece.id.equals(id)) { item = candidate; break; }
            if (item == null) return Collections.emptyList();
            if (category.equals(item.piece.category)) old = id; else fixed.put(item.piece.category, id);
        }
        final String excluded = old;
        List<Item> candidates = new ArrayList<>(); for (Item i : items) if (!i.piece.id.equals(excluded)) candidates.add(i);
        List<List<String>> result = generate(candidates, wardrobe, occasion, weather, date, "replace", rejected, fixed, 1);
        return result.isEmpty() ? Collections.emptyList() : result.get(0);
    }
    private static String key(Candidate c) { List<String> ids = new ArrayList<>(); for (Item i : c.items) ids.add(i.piece.id); return signature(ids); }
    private static int itemScore(Item item, String style, String date) {
        return (item.piece.style.equals(style) ? 12 : 0) + (item.favorite ? 4 : 0) - Math.min(12, Math.max(0, item.wears))
            - (date.equals(item.lastWear) ? 15 : 0);
    }
}
