package com.familywardrobe.app;
import java.util.*;
public final class RoadmapRulesCases {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Roadmap check " + checks); }
    private static DailyRules.Item item(String id, String wardrobe, String category, String season, String availability, boolean archived) {
        return new DailyRules.Item(new WardrobeRules.Piece(id, wardrobe, category, availability, archived, season, "casual", "black"), 0, false, "");
    }
    public static void main(String[] args) {
        List<DailyRules.Item> items = new ArrayList<>(List.of(item("t1","home","top","all","available",false),item("t2","home","top","all","available",false),
            item("b1","home","bottom","all","available",false),item("s1","home","shoes","all","available",false),item("o1","home","outerwear","all","available",false),
            item("dirty","home","top","all","laundry",false),item("archived","home","bottom","all","available",true),item("foreign","other","shoes","all","available",false),
            item("winter","home","top","winter","available",false)));
        var warm = DailyRules.generate(items,"home","everyday","warm","2026-10-02","seed",Set.of(),Map.of(),3);
        check(warm.size()==2); for (var ids:warm) { check(ids.size()==3);check(!ids.contains("dirty")&&!ids.contains("archived")&&!ids.contains("foreign")&&!ids.contains("winter")); }
        check(warm.equals(DailyRules.generate(items,"home","everyday","warm","2026-10-02","seed",Set.of(),Map.of(),3)));
        var selected = warm.get(0); var changed = DailyRules.replace(items,"home","everyday","warm","2026-10-02",selected,"top",Set.of());
        check(!changed.isEmpty()&&!DailyRules.signature(changed).equals(DailyRules.signature(selected)));check(changed.contains("b1")&&changed.contains("s1"));
        var rejected = Set.of(DailyRules.signature(warm.get(0)),DailyRules.signature(warm.get(1)));
        check(DailyRules.generate(items,"home","everyday","warm","2026-10-02","seed",rejected,Map.of(),3).isEmpty());
        check(DailyRules.generate(items,"home","work","cold","2026-10-02","seed",Set.of(),Map.of(),3).stream().allMatch(x->x.contains("o1")));
        var noCoat = DailyRules.generate(items.stream().filter(x->!x.piece.category.equals("outerwear")).toList(),"home","work","cold","2026-10-02","seed",Set.of(),Map.of(),3);
        check(!noCoat.isEmpty() && noCoat.stream().noneMatch(x->x.contains("o1")));
        List<DailyRules.Item> preview = new ArrayList<>(items); preview.add(item("candidate", "home", "other_item", "all", "available", false));
        var purchase = DailyRules.generate(preview,"home","everyday","warm","2026-10-02","preview",Set.of(),Map.of("other_item","candidate"),20);
        check(purchase.size()==2);check(purchase.stream().allMatch(x->x.size()==4&&x.contains("candidate")));
        check(DailyRules.generate(items,"home","everyday","warm","2026-10-02","preview",Set.of(),Map.of("top","foreign"),20).isEmpty());
        preview.add(item("summer", "home", "outerwear", "summer", "available", false));
        check(DailyRules.generate(preview,"home","everyday","cold","2026-10-02","preview",Set.of(),Map.of("outerwear","summer"),20).isEmpty());
        for (int count = 1; count <= 12; count++) {
            Long cost = RoadmapRules.costPerWear(10000L,count); check(cost!=null && cost>=0 && cost<=10000);
        }
        try { RoadmapRules.costPerWear(-1L,2); throw new AssertionError("Negative price accepted"); } catch (IllegalArgumentException expected) { check(true); }
        try { RoadmapRules.weatherMode(Double.NaN); throw new AssertionError("NaN temperature accepted"); } catch (IllegalArgumentException expected) { check(true); }
        check(RoadmapRules.validDate("2024-02-29"));check(!RoadmapRules.validDate("2025-02-29"));check(!RoadmapRules.validDate("2026-2-1"));check(!RoadmapRules.validDate("2201-01-01"));
        check(RoadmapRules.period("", ""));check(RoadmapRules.period("2026-10-02","2026-10-08"));check(!RoadmapRules.period("2026-10-08","2026-10-02"));check(!RoadmapRules.period("2026-10-02",""));
        check(RoadmapRules.costPerWear(null,10)==null);check(RoadmapRules.costPerWear(1000L,0)==null);check(RoadmapRules.costPerWear(1000L,3)==333L);check(RoadmapRules.costPerWear(1L,2)==1L);check(RoadmapRules.costPerWear(0L,2)==0L);
        check(RoadmapRules.uniquePackingItems(List.of(List.of("a","b"),List.of("b","c")),List.of("a","d")).equals(List.of("a","b","c","d")));
        check(RoadmapRules.weatherMode(22).equals("warm"));check(RoadmapRules.weatherMode(8).equals("cool"));check(RoadmapRules.weatherMode(7.9).equals("cold"));
        System.out.println("PASS: "+checks+" production daily outfit, date, packing and cost-per-wear assertions.");
    }
}
