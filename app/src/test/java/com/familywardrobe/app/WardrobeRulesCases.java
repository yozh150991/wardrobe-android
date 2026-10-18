package com.familywardrobe.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class WardrobeRulesCases {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static WardrobeRules.Piece piece(String id, String category, String season, String availability, boolean archived, String wardrobe) {
        return new WardrobeRules.Piece(id, wardrobe, category, availability, archived, season, "casual", "beige");
    }
    private static WardrobeRules.Piece piece(String id, String category) {
        return piece(id, category, "all", "available", false, "home");
    }
    private static boolean same(List<String> actual, String... expected) {
        return new HashSet<>(actual).equals(new HashSet<>(Arrays.asList(expected)));
    }
    public static int runAll() {
        checks = 0;
        WardrobeRules.Piece top = piece("top", "top"), bottom = piece("bottom", "bottom"), shoes = piece("shoes", "shoes");
        WardrobeRules.Piece laundry = piece("wash", "top", "all", "laundry", false, "home");
        WardrobeRules.Piece archived = piece("archived", "top", "all", "laundry", true, "home");
        WardrobeRules.Piece foreign = piece("foreign", "top", "all", "available", false, "another");
        check(WardrobeRules.CATEGORIES.containsAll(Arrays.asList("top", "bottom", "shoes", "outerwear", "other_item")), "Legacy category identifiers remain supported");
        check(WardrobeRules.inCollection("wardrobe", laundry), "Laundry remains visible in the active wardrobe");
        check(WardrobeRules.inCollection("laundry", laundry), "Laundry collection includes laundry");
        check(!WardrobeRules.inCollection("laundry", top), "Laundry excludes ready pieces");
        check(!WardrobeRules.inCollection("laundry", archived), "Laundry excludes archived pieces");
        check(WardrobeRules.inCollection("archive", archived), "Archive shows archived laundry");
        check(!WardrobeRules.inCollection("wardrobe", archived), "Active collection excludes archive");
        check(!WardrobeRules.inCollection("unknown", top), "Unknown collection fails closed");
        check(WardrobeRules.readyCoreCategories("home", Arrays.asList(laundry, archived, foreign, bottom, shoes, piece("jacket", "outerwear")))
            .equals(Arrays.asList("bottom", "shoes")), "Onboarding counts only ready core categories in this wardrobe");
        check(WardrobeRules.readyCoreCategories("home", Arrays.asList(top, piece("secondtop", "top"), bottom, shoes)).size() == 3, "Duplicate category does not inflate progress");
        check(WardrobeRules.readyCoreCategories("home", Arrays.asList(piece("repair", "top", "all", "repair", false, "home"))).isEmpty(), "Repair does not satisfy onboarding");
        check(WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "shoes")), "Legacy three-piece outfits remain valid");
        check(WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "shoes", "outerwear")), "Jacket can accompany a complete outfit");
        check(WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "shoes", "outerwear", "other_item")), "Legacy five-category outfits remain valid for backups");
        check(!WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "outerwear")), "Jacket cannot replace missing shoes");
        check(!WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "shoes", "top")), "Duplicate categories are rejected");
        check(!WardrobeRules.completeCategories(Arrays.asList("top", "bottom", "shoes", "unknown")), "Unknown category rejected");
        check(WardrobeRules.wearDays(Arrays.asList("2026-10-01", "2026-10-01", "2026-09-30")) == 2, "Independent item and outfit events count one wear per day");
        check(WardrobeRules.wearDays(new ArrayList<>()) == 0, "New pieces have zero wear");
        check(same(WardrobeRules.build(bottom, Arrays.asList(top, bottom, shoes, foreign, laundry, archived)), "top", "bottom", "shoes"), "Matching retains anchor and isolates wardrobe");
        check(same(WardrobeRules.build(bottom, Arrays.asList(bottom, shoes, laundry, archived)), "bottom", "shoes"), "Unavailable tops are excluded");
        check(WardrobeRules.build(bottom, Arrays.asList(bottom)).equals(Arrays.asList("bottom")), "Sparse wardrobes never invent garments");
        check(WardrobeRules.build(archived, Arrays.asList(top, bottom, shoes)).isEmpty(), "Archived anchor rejected");
        check(WardrobeRules.build(laundry, Arrays.asList(top, bottom, shoes)).isEmpty(), "Laundry anchor rejected");
        check(WardrobeRules.build(piece("bad", "unknown"), Arrays.asList(top, bottom, shoes)).isEmpty(), "Unknown anchor rejected");
        WardrobeRules.Piece winter = piece("winter", "top", "winter", "available", false, "home");
        WardrobeRules.Piece summerShoes = piece("summerShoes", "shoes", "summer", "available", false, "home");
        check(WardrobeRules.build(bottom, Arrays.asList(bottom, winter, summerShoes)).size() == 2, "All-season anchor cannot mix winter top with summer shoes");
        WardrobeRules.Piece summer = piece("summer", "top", "summer", "available", false, "home");
        check(same(WardrobeRules.build(bottom, Arrays.asList(bottom, winter, summer, summerShoes)), "bottom", "summer", "summerShoes"), "Complete compatible choice wins over incomplete alternatives");
        check(same(WardrobeRules.firstOutfit("home", Arrays.asList(winter, summer, bottom, summerShoes)), "summer", "bottom", "summerShoes"), "First-use action tries another available top when the latest top cannot form an outfit");
        check(WardrobeRules.firstOutfit("missing", Arrays.asList(top, bottom, shoes)).isEmpty(), "First outfit cannot use another wardrobe");
        WardrobeRules.Piece jacket = piece("jacket", "outerwear");
        check(same(WardrobeRules.build(jacket, Arrays.asList(jacket, top, bottom, shoes)), "jacket", "top", "bottom", "shoes"), "Jacket anchor keeps all three core categories");
        check(same(WardrobeRules.build(top, Arrays.asList(jacket, top, bottom, shoes)), "top", "bottom", "shoes"), "Adding jacket category does not force jackets into every outfit");
        WardrobeRules.Piece other = piece("other", "other_item");
        check(same(WardrobeRules.build(other, Arrays.asList(other, top, bottom, shoes)), "other", "top", "bottom", "shoes"), "Other-piece anchor is retained");
        check(same(WardrobeRules.build(jacket, Arrays.asList(jacket, top, bottom)), "jacket", "top", "bottom"), "Incomplete optional-anchor outfit stays incomplete");
        check(WardrobeRules.candidates(bottom, Arrays.asList(top, laundry, archived, foreign), "top").size() == 1, "Replacement candidates remain ready and local");

        Random random = new Random(5102026L);
        String[] seasons = {"all", "summer", "winter", "midseason"};
        for (int iteration = 0; iteration < 160; iteration++) {
            List<WardrobeRules.Piece> catalog = new ArrayList<>();
            WardrobeRules.Piece anchor = piece("anchor", WardrobeRules.CATEGORIES.get(iteration % 5));
            catalog.add(anchor);
            Map<String, WardrobeRules.Piece> byId = new HashMap<>();
            byId.put(anchor.id, anchor);
            for (int n = 0; n < 18; n++) {
                WardrobeRules.Piece item = piece("p" + n, WardrobeRules.CATEGORIES.get(random.nextInt(5)), seasons[random.nextInt(4)],
                    random.nextInt(5) == 0 ? "laundry" : "available", random.nextInt(6) == 0, random.nextInt(4) == 0 ? "foreign" : "home");
                catalog.add(item); byId.put(item.id, item);
            }
            List<String> result = WardrobeRules.build(anchor, catalog);
            check(result.contains("anchor"), "Generated outfit retains anchor");
            check(result.equals(WardrobeRules.build(anchor, catalog)), "Generation is deterministic");
            check(new HashSet<>(result).size() == result.size(), "No duplicate garment IDs");
            HashSet<String> categories = new HashSet<>();
            for (String id : result) {
                WardrobeRules.Piece item = byId.get(id);
                check(item != null && item.wardrobe.equals("home") && WardrobeRules.ready(item), "Every result is an available owned piece");
                check(categories.add(item.category), "One piece per category");
                for (String otherId : result) check(WardrobeRules.compatibleSeasons(item.season, byId.get(otherId).season), "Every pair has compatible seasons");
            }
        }
        return checks;
    }
    public static void main(String[] args) { System.out.println("PASS: " + runAll() + " production wardrobe assertions."); }
}
