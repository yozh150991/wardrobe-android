package com.familywardrobe.app;
import java.util.*;

/** Linear seasonal search: do not let an early winter top hide a complete summer combination. */
public final class LookSelection {
    private LookSelection() {}
    public static final class Piece {
        public final String key, category, season;
        public Piece(String key, String category, String season) { this.key=key; this.category=category; this.season=season; }
    }
    public static List<String> generate(List<Piece> candidates, List<Piece> selected, Set<String> locked,
            List<String> categories, boolean refresh) {
        List<Piece> kept=new ArrayList<>();
        for (Piece p:selected) if (locked.contains(p.key)) kept.add(p);
        List<Piece> best=new ArrayList<>(kept); int bestScore=-1;
        List<String> fixedCategories=new ArrayList<>();for(Piece p:kept) fixedCategories.add(p.category);
        for(List<String> layout:WardrobeRules.baseLayouts(fixedCategories)) {
        List<String> requested=new ArrayList<>(layout);
        for(String c:categories) if(!WardrobeRules.BASE_CATEGORIES.contains(c) && !c.equals("shoes")) requested.add(c);
        for (String season:Arrays.asList("all","summer","winter","midseason")) {
            boolean compatible=true;
            for (Piece p:kept) if (!(p.season.equals("all") || p.season.equals(season))) compatible=false;
            if (!compatible) continue;
            List<Piece> result=new ArrayList<>(kept);
            for (String category:new LinkedHashSet<>(requested)) {
                List<Piece> oldCategory=new ArrayList<>();for(Piece p:selected) if(p.category.equals(category)) oldCategory.add(p);
                int target=Math.min(WardrobeRules.categoryLimit(category),Math.max(1,oldCategory.size()));
                for(int slot=0;slot<target;slot++) {
                    final int currentSlot=slot;
                    if(result.stream().filter(p->p.category.equals(category)).count()>currentSlot) continue;
                    Piece old=slot<oldCategory.size()?oldCategory.get(slot):null,first=null,alternative=null,retained=null;
                    for(Piece p:candidates) {
                        if(!p.category.equals(category) || !(p.season.equals("all") || p.season.equals(season)) || result.stream().anyMatch(q->q.key.equals(p.key))) continue;
                        if(first==null) first=p;
                        if(old!=null && old.key.equals(p.key)) retained=p;
                        else if(alternative==null) alternative=p;
                    }
                    Piece chosen=refresh?(alternative==null?first:alternative):(retained==null?first:retained);
                    if(chosen!=null) result.add(chosen);
                }
            }
            int core=0,change=0;
            for (Piece p:result) {
                if (WardrobeRules.CORE.contains(p.category)) core++;
                boolean same=false;
                for (Piece old:selected) if (old.key.equals(p.key)) same=true;
                if (refresh ? !same : same) change++;
            }
            List<String> resultCategories=new ArrayList<>();for(Piece p:result) resultCategories.add(p.category);
            if(!WardrobeRules.validCategories(resultCategories)) continue;
            int score=(WardrobeRules.completeCategories(resultCategories)?100000:0)+change*100+result.size();
            if (score>bestScore) { bestScore=score;best=result; }
        }
        }
        best.sort(Comparator.comparingInt(p->WardrobeRules.CATEGORIES.indexOf(p.category)));
        List<String> keys=new ArrayList<>(); for (Piece p:best) keys.add(p.key);return keys;
    }
}
