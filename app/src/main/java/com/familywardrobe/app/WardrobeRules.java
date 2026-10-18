package com.familywardrobe.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Domain rules shared by native browsing, onboarding, outfit saving and backup validation. */
public final class WardrobeRules {
    public static final List<String> CORE = Collections.unmodifiableList(Arrays.asList("top", "bottom", "shoes"));
    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList("top", "bottom", "dress", "jumpsuit", "shoes", "layer", "outerwear", "bag", "tights", "jewelry", "hat", "accessory", "other_item"));
    public static final int MAX_PARTS = 12;
    public static final Set<String> BASE_CATEGORIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("top","bottom","dress","jumpsuit")));
    public static int categoryLimit(String category) { return category.equals("layer") ? 2 : category.equals("accessory") || category.equals("jewelry") ? 4 : 1; }
    public static boolean validCategories(List<String> categories) {
        if (categories.isEmpty() || categories.size()>MAX_PARTS || !CATEGORIES.containsAll(categories)) return false;
        for(String c:new HashSet<>(categories)) if(Collections.frequency(categories,c)>categoryLimit(c)) return false;
        int onePiece=Collections.frequency(categories,"dress")+Collections.frequency(categories,"jumpsuit");
        return onePiece<=1 && (onePiece==0 || !categories.contains("top") && !categories.contains("bottom"));
    }
    public static List<List<String>> baseLayouts(Collection<String> fixed) {
        List<List<String>> result=new ArrayList<>();
        for(List<String> layout:Arrays.asList(CORE,Arrays.asList("dress","shoes"),Arrays.asList("jumpsuit","shoes"))) {
            boolean valid=true;
            for(String category:fixed) if(BASE_CATEGORIES.contains(category) && !layout.contains(category)) valid=false;
            if(valid) result.add(new ArrayList<>(layout));
        }
        return result;
    }
    public static List<String> startingCategories(String wardrobe,List<Piece> items) {
        for(String category:Arrays.asList("dress","jumpsuit")) for(Piece item:items)
            if(wardrobe.equals(item.wardrobe) && category.equals(item.category) && ready(item)) return Arrays.asList(category,"shoes");
        return CORE;
    }
    private static final Set<String> NEUTRALS = new HashSet<>(Arrays.asList("white", "black", "grey", "beige", "navy", "brown"));
    private WardrobeRules() {}

    public static final class Piece {
        public final String id, wardrobe, category, availability, season, style, color;
        public final boolean archived;
        public Piece(String id, String wardrobe, String category, String availability, boolean archived,
                     String season, String style, String color) {
            this.id = id; this.wardrobe = wardrobe; this.category = category;
            this.availability = availability; this.archived = archived;
            this.season = season; this.style = style; this.color = color;
        }
    }

    public static boolean ready(Piece item) {
        return !item.archived && "available".equals(item.availability) && CATEGORIES.contains(item.category);
    }

    public static boolean inCollection(String collection, Piece item) {
        if ("archive".equals(collection)) return item.archived;
        if ("laundry".equals(collection)) return !item.archived && "laundry".equals(item.availability);
        return "wardrobe".equals(collection) && !item.archived;
    }

    public static List<String> readyCoreCategories(String wardrobe, List<Piece> items) {
        List<String> result = new ArrayList<>();
        for (String category : CORE) {
            for (Piece item : items) {
                if (wardrobe.equals(item.wardrobe) && category.equals(item.category) && ready(item)) {
                    result.add(category); break;
                }
            }
        }
        return result;
    }

    public static boolean completeCategories(List<String> categories) {
        return validCategories(categories) && categories.contains("shoes") &&
            (categories.contains("dress") || categories.contains("jumpsuit") || categories.containsAll(CORE));
    }

    public static int wearDays(List<String> dates) {
        return new HashSet<>(dates).size();
    }

    public static boolean compatibleSeasons(String a, String b) {
        return "all".equals(a) || "all".equals(b) || a.equals(b);
    }

    public static int score(Piece a, Piece b) {
        int color;
        if ("other".equals(a.color) || "other".equals(b.color)) color = 0;
        else if (a.color.equals(b.color)) color = 2;
        else if (NEUTRALS.contains(a.color) || NEUTRALS.contains(b.color)) color = 3;
        else if (("olive".equals(a.color) && "blue".equals(b.color)) || ("blue".equals(a.color) && "olive".equals(b.color))) color = 1;
        else color = 0;
        return color + (a.style.equals(b.style) ? 4 : 0);
    }

    public static List<Piece> candidates(Piece base, List<Piece> items, String category) {
        List<Piece> result = new ArrayList<>();
        if (!ready(base)) return result;
        for (Piece item : items) {
            if (!base.id.equals(item.id) && base.wardrobe.equals(item.wardrobe) && category.equals(item.category)
                && ready(item) && compatibleSeasons(base.season, item.season)) result.add(item);
        }
        result.sort(Comparator.<Piece>comparingInt(item -> score(base, item)).reversed().thenComparing(item -> item.id));
        return result;
    }

    public static List<String> build(Piece base, List<Piece> items) {
        if (!ready(base)) return Collections.emptyList();
        List<Piece> best=Collections.emptyList(); int bestValue=Integer.MIN_VALUE;
        for(List<String> layout:baseLayouts(Collections.singleton(base.category))) {
            List<String> required=new ArrayList<>(layout); required.remove(base.category);
            Search search=new Search(base,items,required); search.visit(0,new ArrayList<>(Collections.singletonList(base)));
            List<String> categories=new ArrayList<>();for(Piece p:search.best) categories.add(p.category);
            int value=(completeCategories(categories)?100000:0)+search.best.size()*100+search.bestScore;
            if(value>bestValue) {best=search.best;bestValue=value;}
        }
        List<Piece> sorted=new ArrayList<>(best);sorted.sort(Comparator.comparingInt(item -> CATEGORIES.indexOf(item.category)));
        List<String> result = new ArrayList<>();for(Piece p:sorted) result.add(p.id);return result;
    }

    public static List<String> firstOutfit(String wardrobe, List<Piece> items) {
        List<String> best=Collections.emptyList();Set<String> tried=new HashSet<>();
        for(Piece anchor:items) if(wardrobe.equals(anchor.wardrobe) && BASE_CATEGORIES.contains(anchor.category) && ready(anchor) && tried.add(anchor.category+":"+anchor.season)) {
            List<String> candidate=build(anchor,items);List<String> categories=new ArrayList<>();
            for(String id:candidate) for(Piece item:items) if(item.id.equals(id)) {categories.add(item.category);break;}
            if(completeCategories(categories)) return candidate;
            if(candidate.size()>best.size()) best=candidate;
        }
        return best;
    }

    private static final class Search {
        final List<List<Piece>> choices = new ArrayList<>();
        List<Piece> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        Search(Piece base, List<Piece> items, List<String> categories) {
            for (String category : categories) {
                List<Piece> bounded=new ArrayList<>();java.util.Map<String,Integer> counts=new java.util.HashMap<>();
                for(Piece p:candidates(base,items,category)) if(counts.getOrDefault(p.season,0)<12) {
                    bounded.add(p);counts.put(p.season,counts.getOrDefault(p.season,0)+1);
                }
                choices.add(bounded);
            }
        }
        void visit(int index, List<Piece> picked) {
            if (picked.size() + choices.size() - index < best.size()) return;
            if (index == choices.size()) {
                int total = 0;
                for (int a = 0; a < picked.size(); a++) for (int b = a + 1; b < picked.size(); b++) total += score(picked.get(a), picked.get(b));
                if (picked.size() > best.size() || (picked.size() == best.size() && total > bestScore)) {
                    best = new ArrayList<>(picked); bestScore = total;
                }
                return;
            }
            for (Piece candidate : choices.get(index)) {
                boolean compatible = true;
                for (Piece other : picked) if (!compatibleSeasons(candidate.season, other.season)) { compatible = false; break; }
                if (compatible) {
                    picked.add(candidate); visit(index + 1, picked); picked.remove(picked.size() - 1);
                }
            }
            visit(index + 1, picked);
        }
    }
}
