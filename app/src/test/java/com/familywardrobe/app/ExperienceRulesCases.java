package com.familywardrobe.app;

import java.util.*;

/** User-visible invariants against the production Java implementations. */
public final class ExperienceRulesCases {
    private static int count;
    private static void ok(boolean value) { count++;if(!value) throw new AssertionError("Experience case "+count); }
    private static void refuses(Runnable action) { boolean refused=false;try { action.run(); } catch(IllegalArgumentException e) { refused=true; }ok(refused); }
    private static WardrobeRules.Piece piece(String id,String category,String season) {
        return new WardrobeRules.Piece(id,"home",category,"available",false,season,"casual","navy");
    }
    private static DailyRules.Item item(WardrobeRules.Piece p) { return new DailyRules.Item(p,0,false,""); }
    public static void main(String[] args) {
        ok(WardrobeRules.completeCategories(Arrays.asList("dress","shoes")));
        ok(WardrobeRules.completeCategories(Arrays.asList("jumpsuit","shoes","layer","layer","bag","jewelry","jewelry")));
        ok(!WardrobeRules.completeCategories(Arrays.asList("dress")));
        ok(!WardrobeRules.completeCategories(Arrays.asList("dress","top","shoes")));
        ok(!WardrobeRules.validCategories(Arrays.asList("dress","jumpsuit")));
        ok(!WardrobeRules.validCategories(Arrays.asList("top","top","bottom","shoes")));
        ok(!WardrobeRules.validCategories(Arrays.asList("layer","layer","layer")));
        ok(WardrobeRules.validCategories(Arrays.asList("accessory","accessory","accessory","accessory")));
        ok(!WardrobeRules.validCategories(Arrays.asList("accessory","accessory","accessory","accessory","accessory")));
        List<WardrobeRules.Piece> wardrobe=Arrays.asList(piece("dress","dress","summer"),piece("shoes","shoes","all"),
            piece("top","top","winter"),piece("bottom","bottom","summer"));
        ok(new HashSet<>(WardrobeRules.build(wardrobe.get(0),wardrobe)).equals(Set.of("dress","shoes")));
        ok(new HashSet<>(WardrobeRules.build(wardrobe.get(1),wardrobe)).equals(Set.of("dress","shoes")));
        ok(new HashSet<>(WardrobeRules.firstOutfit("home",wardrobe)).equals(Set.of("dress","shoes")));
        ok(WardrobeRules.startingCategories("home",wardrobe).equals(Arrays.asList("dress","shoes")));
        List<DailyRules.Item> all=Arrays.asList(item(piece("top","top","all")),item(piece("bottom","bottom","all")),
            item(piece("shoe","shoes","all")),item(piece("dress","dress","all")),item(piece("jump","jumpsuit","all")));
        List<List<String>> looks=DailyRules.generate(all,"home","everyday","warm","2026-10-02","seed",Set.of(),Map.of(),3);
        ok(looks.size()==3);ok(looks.stream().anyMatch(l->l.contains("dress")));ok(looks.stream().anyMatch(l->l.contains("jump")));
        List<List<String>> fixed=DailyRules.generate(all,"home","everyday","warm","2026-10-02","seed",Set.of(),Map.of("dress","dress"),3);
        ok(fixed.size()==1 && fixed.get(0).contains("dress") && !fixed.get(0).contains("top"));
        ok(DailyRules.generate(all,"home","everyday","warm","2026-10-02","seed",Set.of(),Map.of("top","top","dress","dress"),3).isEmpty());
        ok(DailyRules.generate(all,"other","everyday","warm","2026-10-02","seed",Set.of(),Map.of(),3).isEmpty());
        // 0.14.7: no coat in the wardrobe no longer empties cold-weather suggestions.
        ok(!DailyRules.generate(all,"home","everyday","cold","2026-10-02","seed",Set.of(),Map.of(),3).isEmpty());
        String rejected=DailyRules.signature(fixed.get(0));
        ok(DailyRules.generate(all,"home","everyday","warm","2026-10-02","seed",Set.of(rejected),Map.of("dress","dress"),3).isEmpty());
        List<LookSelection.Piece> selected=Arrays.asList(new LookSelection.Piece("d","dress","all"),new LookSelection.Piece("s","shoes","all"),
            new LookSelection.Piece("l1","layer","all"),new LookSelection.Piece("l2","layer","all"));
        ok(new HashSet<>(LookSelection.generate(selected,selected,Set.of("d"),Arrays.asList("top","bottom","shoes","dress","layer","layer"),false))
            .equals(Set.of("d","s","l1","l2")));
        ok(!RecommendationRules.allowed(true,"","2026-10-02",0));
        ok(RecommendationRules.allowed(false,"2026-10-02","2026-10-02",0));
        ok(!RecommendationRules.allowed(false,"2026-10-02","2026-10-02",7));
        ok(!RecommendationRules.allowed(false,"2026-09-26","2026-10-02",7));
        ok(RecommendationRules.allowed(false,"2026-09-25","2026-10-02",7));
        ok(RecommendationRules.allowed(false,"","2026-10-02",30));
        ok(RecommendationRules.allowed(false,"2024-02-28","2024-03-06",7));
        refuses(()->RecommendationRules.allowed(false,"","2026-10-02",31));
        refuses(()->RecommendationRules.allowed(false,"","2026-10-02",-1));
        int[] pixels=new int[10000];Arrays.fill(pixels,0xff123456);pixels[0]=0x00123456;
        PhotoMaskRules.Stroke erase=new PhotoMaskRules.Stroke(.5f,.5f,.5f,.5f,.08f,false);
        PhotoMaskRules.Stroke restore=new PhotoMaskRules.Stroke(.5f,.5f,.5f,.5f,.08f,true);
        int[] removed=PhotoMaskRules.paint(pixels,100,100,List.of(erase));
        ok(pixels[5050]==0xff123456);ok(removed[5050]==0x00123456);ok(removed[9999]==pixels[9999]);
        ok(Arrays.equals(PhotoMaskRules.paint(pixels,100,100,List.of(erase,restore)),pixels));
        ok(PhotoMaskRules.paint(pixels,100,100,List.of(erase))[0]==pixels[0]);
        refuses(()->new PhotoMaskRules.Stroke(Float.NaN,0,0,0,.02f,false));
        refuses(()->new PhotoMaskRules.Stroke(0,0,0,0,.51f,false));
        refuses(()->PhotoMaskRules.paint(pixels,99,100,List.of(erase)));
        for(String category:WardrobeRules.CATEGORIES) for(float scale:new float[]{.1f,.6f,1f,1.8f,100f}) {
            float[] p=CanvasRules.constrain(category,-3f,7f,scale);
            float w=CanvasRules.width(category,p[2]),h=CanvasRules.height(category,p[2]);
            ok(p[0]-w/2>=-.00001f && p[0]+w/2<=1.00001f && p[1]-h/2>=-.00001f && p[1]+h/2<=1.00001f);
        }
        for(int n:new int[]{1,2,5,6,12}) for(int index=0;index<n;index++) {
            float[] p=CanvasRules.initial("dress",index,n);
            ok(p[0]>=0 && p[0]<=1 && p[1]>=0 && p[1]<=1 && p[2]>=.6f && p[2]<=1.8f);
        }
        refuses(()->CanvasRules.constrain("dress",Float.NaN,0,1));
        refuses(()->CanvasRules.initial("dress",0,13));
        // 0.14.7: every curated inspiration key must be a valid canvas key (a move used to crash the collage).
        for(InspirationCatalog.Idea idea:InspirationCatalog.all()) ok(CanvasRules.validKey(idea.key));
        for(String category:WardrobeRules.CATEGORIES) ok(CanvasRules.validKey("idea:"+category));
        ok(CanvasRules.validKey("owned:0e6b1a2c-1111-4222-8333-944455556666"));
        ok(CanvasRules.validKey("wish:0e6b1a2c-1111-4222-8333-944455556666"));
        ok(!CanvasRules.validKey("idea:top_shirt_pink"));
        ok(!CanvasRules.validKey("idea:"));
        ok(!CanvasRules.validKey("photo:0e6b1a2c-1111-4222-8333-944455556666"));
        ok(!CanvasRules.validKey(null));
        System.out.println("PASS: "+count+" production flexible-outfit, recency, photo-mask and canvas assertions.");
    }
}
