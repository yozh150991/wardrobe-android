package com.familywardrobe.app;
import java.util.*;
public final class LookSelectionCases {
    private static int checks;
    private static LookSelection.Piece p(String id,String category,String season){return new LookSelection.Piece(id,category,season);}
    private static void equal(Object a,Object b){checks++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    public static void main(String[] args) {
        LookSelection.Piece top=p("top","top","all"), bottom=p("bottom","bottom","all"), shoes=p("shoes","shoes","all"), next=p("next","bottom","all");
        List<LookSelection.Piece> original=Arrays.asList(top,bottom,shoes),pool=Arrays.asList(top,bottom,shoes,next);
        equal(Arrays.asList("top","next","shoes"),LookSelection.generate(pool,original,new HashSet<>(Arrays.asList("top","shoes")),WardrobeRules.CORE,true));
        equal(Arrays.asList("top","bottom","shoes"),LookSelection.generate(pool,original,Collections.emptySet(),WardrobeRules.CORE,false));
        equal(Arrays.asList("top","bottom","shoes"),LookSelection.generate(pool,original,new HashSet<>(Arrays.asList("top","bottom","shoes")),WardrobeRules.CORE,true));
        equal(Arrays.asList("summer","bottom","summer-shoes"),LookSelection.generate(Arrays.asList(p("winter","top","winter"),p("summer","top","summer"),bottom,p("summer-shoes","shoes","summer")),Collections.emptyList(),Collections.emptySet(),WardrobeRules.CORE,false));
        equal(Collections.singletonList("top"),LookSelection.generate(Collections.emptyList(),original,Collections.singleton("top"),WardrobeRules.CORE,true));
        equal(Collections.emptyList(),LookSelection.generate(Collections.emptyList(),Collections.emptyList(),Collections.emptySet(),WardrobeRules.CORE,true));
        LookSelection.Piece wish=p("wish:1","shoes","all"),idea=p("idea:top","top","all");
        equal(Arrays.asList("idea:top","bottom","wish:1"),LookSelection.generate(Arrays.asList(idea,bottom,wish),Collections.emptyList(),Collections.emptySet(),WardrobeRules.CORE,false));
        for (int mask=0;mask<8;mask++) {
            Set<String> locks=new HashSet<>();for(int i=0;i<3;i++)if((mask&(1<<i))!=0)locks.add(original.get(i).key);
            List<String> result=LookSelection.generate(pool,original,locks,WardrobeRules.CORE,true);
            equal(true,result.containsAll(locks));equal(3,new HashSet<>(result).size());
        }
        System.out.println("PASS: "+checks+" production collage-selection assertions (locks, refresh, seasons, partial and labelled references).");
    }
}
