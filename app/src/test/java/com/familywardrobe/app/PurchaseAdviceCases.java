package com.familywardrobe.app;

import java.util.*;

/** Executed against production Java rules on both the host and Gradle/JUnit. */
public final class PurchaseAdviceCases {
    private static int checks;
    private static void check(boolean value,String reason) { checks++; if(!value)throw new AssertionError(reason); }
    private static PurchaseAdviceRules.Profile profile(String id,String category,String color,String subcategory,String season,String style,String link) {
        return new PurchaseAdviceRules.Profile(id,"home",category,color,subcategory,season,style,link);
    }
    private static PurchaseAdviceRules.Profile p(String category) { return profile("wish",category,"","","","",""); }
    private static PurchaseAdviceRules.Owned owned(String id,String wardrobe,String category,String color,String subcategory,String season,String style,
                                                  String availability,boolean archived,boolean eligible,String source) {
        WardrobeRules.Piece piece=new WardrobeRules.Piece(id,wardrobe,category,availability,archived,season,style,color);
        return new PurchaseAdviceRules.Owned(new DailyRules.Item(piece,0,false,""),"Item "+id,subcategory,eligible,Arrays.asList(source));
    }
    private static PurchaseAdviceRules.Owned o(String id,String category) { return owned(id,"home",category,"navy","","all","casual","available",false,true,""); }
    private static PurchaseAdviceRules.Analysis analyze(PurchaseAdviceRules.Profile p,List<PurchaseAdviceRules.Owned> items,String weather) {
        return PurchaseAdviceRules.analyze(p,items,Collections.emptySet(),"everyday",weather,"2026-10-08",Collections.emptySet());
    }
    private static boolean has(PurchaseAdviceRules.Analysis a,String id) { return a.similar.stream().anyMatch(m->m.owned.id().equals(id)); }
    public static void main(String[] args) { run(); System.out.println("PASS: "+checks+" purchase advice assertions against production rules"); }
    public static void run() {
        checks=0;
        PurchaseAdviceRules.Profile unknown=p("shoes");
        List<PurchaseAdviceRules.Owned> single=Arrays.asList(o("a","shoes"));
        List<PurchaseAdviceRules.Match> matches=PurchaseAdviceRules.similar(unknown,single);
        check(matches.size()==1,"unknown colour still shows category match");
        check(matches.get(0).reasons.equals(Arrays.asList("category")),"unknown traits are never invented");
        check(PurchaseAdviceRules.similar(profile("w","shoes","other","","","",""),Arrays.asList(owned("a","home","shoes","other","","all","casual","available",false,true,"")))
            .get(0).reasons.equals(Arrays.asList("category")),"other colour is not evidence of a match");
        PurchaseAdviceRules.Profile rich=profile("w","shoes","navy","  Running   shoes ","summer","sport","https://shop.example/item?size=M&utm_source=x");
        PurchaseAdviceRules.Owned exact=owned("exact","home","shoes","navy","running shoes","all","sport","laundry",false,true,"https://shop.example/item?size=M");
        matches=PurchaseAdviceRules.similar(rich,Arrays.asList(o("generic","shoes"),exact));
        check(matches.get(0).owned.id().equals("exact"),"documented traits outrank generic category, with availability separately shown");
        check(matches.get(0).reasons.containsAll(Arrays.asList("source","subcategory","color","season","style")),"reasons reflect each known trait");
        check(!matches.get(0).owned.ready(),"laundry is not available");
        check(!PurchaseAdviceRules.similar(profile("w","shoes","navy","","","","https://shop.example/item?size=L"),Arrays.asList(exact)).get(0).reasons.contains("source"),"different URL variant not merged");
        check(!PurchaseAdviceRules.similar(profile("w","shoes","red","boot","winter","formal",""),Arrays.asList(o("generic","shoes"))).get(0).reasons.contains("color"),"different colour never presented as matching");
        check(PurchaseAdviceRules.similar(unknown,Arrays.asList(o("top","top"))).isEmpty(),"different categories do not become similar");
        check(PurchaseAdviceRules.similar(unknown,Arrays.asList(owned("foreign","other","shoes","navy","","all","casual","available",false,true,""))).isEmpty(),"another wardrobe cannot enter matches");
        PurchaseAdviceRules.Owned archived=owned("archived","home","shoes","navy","","all","casual","available",true,true,"");
        check(PurchaseAdviceRules.similar(unknown,Arrays.asList(archived)).size()==1,"archived items remain discoverable");
        check(!archived.ready(),"archived availability cannot pretend ready");
        List<PurchaseAdviceRules.Owned> tied=Arrays.asList(o("z","shoes"),o("a","shoes"));
        check(PurchaseAdviceRules.similar(unknown,tied).get(0).owned.id().equals("a"),"stable tie ordering independent of insertion");
        check(PurchaseAdviceRules.similar(profile("a","shoes","","","","",""),tied).size()==1,"do not compare candidate with its own ID");

        List<PurchaseAdviceRules.Owned> wardrobe=new ArrayList<>(Arrays.asList(o("top","top"),o("bottom","bottom"),o("shoes","shoes")));
        PurchaseAdviceRules.Analysis report=analyze(unknown,wardrobe,"any_weather");
        check(report.previews.size()==1,"one complete anchored example");
        PurchaseAdviceRules.Preview preview=report.previews.get(0);
        check(preview.ids.contains("candidate:wish") && !preview.ids.contains("shoes"),"candidate is pinned, not substituted silently");
        check(report.partnerCount==2,"only actual own partners are counted");
        check(preview.hasReadyReplacement() && preview.replacements.size()==1,"owned alternative identified");
        check(report.withoutReplacement()==0,"new candidate ID alone adds no clothing capability");
        check(analyze(profile("different-id","shoes","","","","",""),wardrobe,"any_weather").withoutReplacement()==0,"novelty does not depend on wish ID");
        check(!preview.saved,"unsaved remains an independent fact");
        report=PurchaseAdviceRules.analyze(unknown,wardrobe,Collections.singleton(DailyRules.signature(Arrays.asList("top","bottom","wish:wish"))),
            "everyday","any_weather","2026-10-08",Collections.emptySet());
        check(report.previews.get(0).saved,"existing planned signature recognised");
        check(report.withoutReplacement()==0,"saved classification does not inflate gaps");
        report=analyze(unknown,Arrays.asList(o("top","top"),o("bottom","bottom")),"any_weather");
        check(report.withoutReplacement()==1,"missing owned category is a genuine bounded gap");
        report=analyze(unknown,Arrays.asList(o("top","top"),o("bottom","bottom"),archived),"any_weather");
        check(report.withoutReplacement()==0 && !report.previews.get(0).hasReadyReplacement(),"archive is not erased to manufacture a gap");
        check(report.unavailableCount==1,"unavailable count retains archive");
        PurchaseAdviceRules.Owned excluded=owned("excluded","home","shoes","navy","","all","casual","available",false,false,"");
        report=analyze(unknown,Arrays.asList(o("top","top"),o("bottom","bottom"),excluded),"any_weather");
        check(report.withoutReplacement()==0 && report.previews.get(0).hasReadyReplacement(),"excluded own item can still be selected manually");
        check(report.filteredCount==1 && !report.similar.get(0).owned.eligible,"automatic exclusion stays explicit");
        PurchaseAdviceRules.Owned excludedTop=owned("excluded-top","home","top","navy","","all","casual","available",false,false,"");
        report=analyze(unknown,Arrays.asList(excludedTop,o("bottom","bottom")),"any_weather");
        check(report.previews.isEmpty() && report.filteredCount==1,"automatic partners respect exclusions");
        check(report.missingLayouts.stream().anyMatch(m->m.contains("top")),"missing partner category explained");
        report=analyze(unknown,Arrays.asList(o("top","top"),o("bottom","bottom"),owned("foreign","other","shoes","navy","","all","casual","available",false,true,"")),"any_weather");
        check(report.withoutReplacement()==1 && !has(report,"foreign"),"foreign replacements and matches excluded");
        check(report.unavailableCount==0 && report.filteredCount==0,"other-wardrobe counts do not leak");
        report=analyze(unknown,Arrays.asList(o("top","top"),owned("wash","home","bottom","navy","","all","casual","laundry",false,true,"")),"any_weather");
        check(report.previews.isEmpty(),"laundry cannot become an outfit partner");
        check(report.unavailableCount==1,"laundry empty-state explanation");
        report=analyze(p("dress"),Collections.emptyList(),"any_weather");
        check(report.missingLayouts.equals(Arrays.asList(Arrays.asList("shoes"))),"one-piece garment needs shoes, not top and bottom");
        report=analyze(unknown,Collections.emptyList(),"any_weather");
        check(report.missingLayouts.size()==3 && report.missingLayouts.stream().noneMatch(m->m.contains("shoes")),"empty collection gives alternative layouts without missing the pinned category");
        report=analyze(profile("w","shoes","","","summer","",""),wardrobe,"cold");
        check(report.candidateSeasonConflict && report.previews.isEmpty(),"candidate season conflict is explicit");
        List<PurchaseAdviceRules.Owned> winter=Arrays.asList(
            owned("wt","home","top","navy","","winter","casual","available",false,true,""),
            owned("wb","home","bottom","navy","","winter","casual","available",false,true,""),
            owned("wo","home","outerwear","navy","","winter","casual","available",false,true,""),
            owned("ss","home","shoes","navy","","summer","casual","available",false,true,""));
        report=analyze(profile("w","shoes","","","winter","",""),winter,"cold");
        check(report.previews.size()==1 && report.withoutReplacement()==1,"season-incompatible replacement not offered");
        check(report.previews.get(0).ids.contains("wo"),"cold context needs own outerwear");
        report=analyze(unknown,Arrays.asList(
            owned("st","home","top","navy","","summer","casual","available",false,true,""),
            owned("wb","home","bottom","navy","","winter","casual","available",false,true,"")),"any_weather");
        check(report.previews.isEmpty() && report.missingLayouts.isEmpty(),"individual categories can exist without a mutually compatible set");
        report=analyze(p("bag"),wardrobe,"any_weather");
        check(report.previews.get(0).ids.size()==4 && report.previews.get(0).ids.contains("candidate:wish"),"non-core purchase remains pinned in complete outfit");
        report=PurchaseAdviceRules.analyze(unknown,wardrobe,Collections.emptySet(),"everyday","any_weather","2026-10-08",
            Collections.singleton(DailyRules.signature(Arrays.asList("top","bottom","candidate:wish"))));
        check(report.previews.isEmpty(),"rejected exact combination is respected");
        List<PurchaseAdviceRules.Owned> large=new ArrayList<>();
        for(int i=0;i<110;i++){large.add(o("t"+i,"top"));large.add(o("b"+i,"bottom"));}
        large.add(o("old-shoe","shoes"));
        report=analyze(unknown,large,"any_weather");
        check(report.previews.size()==20,"221-item collection is bounded to twenty examples");
        check(report.withoutReplacement()==0,"bounded sample is not mislabelled as all-new looks");
        Set<String> distinct=new HashSet<>();
        for(PurchaseAdviceRules.Preview row:report.previews) {
            check(row.ids.contains("candidate:wish") && new HashSet<>(row.ids).size()==row.ids.size(),"every large-collection example stays pinned without duplicate pieces");
            distinct.add(row.signature);
        }
        check(distinct.size()==20,"examples have unique real combinations");
        Collections.reverse(large);
        check(analyze(unknown,large,"any_weather").previews.get(0).signature.equals(report.previews.get(0).signature),"stable output despite collection ordering");
        check(large.size()==221,"analysis does not mutate source collection");

        check(PurchaseAdviceRules.budgetStatus(100L,"EUR",200L,"EUR").equals("within"),"same-currency comparison");
        check(PurchaseAdviceRules.budgetStatus(201L,"EUR",200L,"EUR").equals("above"),"over-budget amount");
        check(PurchaseAdviceRules.budgetStatus(200L,"EUR",200L,"EUR").equals("within"),"equal budget allowed");
        check(PurchaseAdviceRules.budgetStatus(200L,"EUR",200L,"PLN").equals("different_currency"),"no fabricated currency conversion");
        check(PurchaseAdviceRules.budgetStatus(null,"EUR",200L,"EUR").equals("unknown"),"missing price is not zero");
        check(PurchaseAdviceRules.budgetStatus(200L,"EUR",null,"EUR").equals("unknown"),"missing budget not treated as zero");
        check(PurchaseAdviceRules.budgetStatus(0L,"EUR",0L,"EUR").equals("within"),"explicit zero retained");
        check(PurchaseAdviceRules.budgetStatus(-1L,"EUR",200L,"EUR").equals("unknown"),"invalid amounts not compared");
        boolean invalid=false;try{analyze(profile("w","shoes","","","unknown-season","",""),wardrobe,"any_weather");}catch(IllegalArgumentException e){invalid=true;}
        check(invalid,"invalid profile rejected rather than silently changing season");
    }
}
