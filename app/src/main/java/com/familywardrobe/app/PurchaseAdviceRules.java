package com.familywardrobe.app;

import java.text.Normalizer;
import java.util.*;

/** Explainable local comparisons. A ranking is not a probability or a recommendation to buy. */
public final class PurchaseAdviceRules {
    private PurchaseAdviceRules() {}
    public static final class Profile {
        public final String id, wardrobe, category, color, subcategory, season, style, source;
        public Profile(String id, String wardrobe, String category, String color, String subcategory,
                       String season, String style, String source) {
            if (!WardrobeRules.CATEGORIES.contains(category)) throw new IllegalArgumentException("Unknown category");
            if (!Arrays.asList("", "all", "summer", "winter", "midseason").contains(season) ||
                !Arrays.asList("", "casual", "formal", "sport").contains(style)) throw new IllegalArgumentException("Unknown context");
            this.id=id; this.wardrobe=wardrobe; this.category=category; this.color=color;
            this.subcategory=normalized(subcategory); this.season=season; this.style=style;
            this.source=ShopImportRules.linkKey(source);
        }
        public String candidateId() { return "candidate:" + id; }
    }
    public static final class Owned {
        public final DailyRules.Item item;
        public final String name, subcategory;
        public final boolean eligible;
        public final Set<String> sources;
        public Owned(DailyRules.Item item, String name, String subcategory, boolean eligible, List<String> sources) {
            this.item=item; this.name=name; this.subcategory=normalized(subcategory); this.eligible=eligible;
            Set<String> keys=new HashSet<>();
            for (String source:sources) { String key=ShopImportRules.linkKey(source); if (!key.isEmpty()) keys.add(key); }
            this.sources=Collections.unmodifiableSet(keys);
        }
        public String id() { return item.piece.id; }
        public boolean ready() { return WardrobeRules.ready(item.piece); }
    }
    public static final class Match {
        public final Owned owned;
        public final List<String> reasons;
        private final int rank;
        private Match(Owned owned, List<String> reasons, int rank) {
            this.owned=owned; this.reasons=Collections.unmodifiableList(reasons); this.rank=rank;
        }
    }
    public static List<Match> similar(Profile profile, List<Owned> items) {
        List<Match> matches=new ArrayList<>();
        for (Owned owned:items) {
            WardrobeRules.Piece p=owned.item.piece;
            if (!profile.wardrobe.equals(p.wardrobe) || !profile.category.equals(p.category) || profile.id.equals(p.id)) continue;
            List<String> reasons=new ArrayList<>(); reasons.add("category"); int rank=1;
            if (!profile.source.isEmpty() && owned.sources.contains(profile.source)) { reasons.add("source"); rank+=32; }
            if (!profile.subcategory.isEmpty() && profile.subcategory.equals(owned.subcategory)) { reasons.add("subcategory"); rank+=16; }
            if (!profile.color.isEmpty() && !profile.color.equals("other") && profile.color.equals(p.color)) { reasons.add("color"); rank+=8; }
            if (!profile.season.isEmpty() && WardrobeRules.compatibleSeasons(profile.season,p.season)) { reasons.add("season"); rank+=2; }
            if (!profile.style.isEmpty() && profile.style.equals(p.style)) { reasons.add("style"); rank+=4; }
            matches.add(new Match(owned,reasons,rank));
        }
        matches.sort(Comparator.<Match>comparingInt(m -> m.rank).reversed()
            .thenComparingInt(m -> m.owned.ready()?0:1)
            .thenComparing(m -> normalized(m.owned.name)).thenComparing(m -> m.owned.id()));
        return matches;
    }
    public static final class Preview {
        public final List<String> ids;
        public final String signature;
        public final boolean saved;
        public final List<Owned> replacements;
        private Preview(List<String> ids, String signature, boolean saved, List<Owned> replacements) {
            this.ids=Collections.unmodifiableList(new ArrayList<>(ids)); this.signature=signature; this.saved=saved;
            this.replacements=Collections.unmodifiableList(replacements);
        }
        public boolean hasReadyReplacement() { for (Owned o:replacements) if(o.ready()) return true; return false; }
    }
    public static final class Analysis {
        public final List<Preview> previews;
        public final List<Match> similar;
        public final List<List<String>> missingLayouts;
        public final int partnerCount, unavailableCount, filteredCount;
        public final boolean candidateSeasonConflict;
        private Analysis(List<Preview> previews, List<Match> similar, List<List<String>> missingLayouts,
                         int partners, int unavailable, int filtered, boolean conflict) {
            this.previews=Collections.unmodifiableList(previews); this.similar=Collections.unmodifiableList(similar);
            this.missingLayouts=Collections.unmodifiableList(missingLayouts); partnerCount=partners;
            unavailableCount=unavailable; filteredCount=filtered; candidateSeasonConflict=conflict;
        }
        public int withoutReplacement() { int n=0; for(Preview p:previews) if(p.replacements.isEmpty()) n++; return n; }
    }
    public static Analysis analyze(Profile profile, List<Owned> items, Set<String> savedSignatures,
                                   String occasion, String weather, String date, Set<String> rejected) {
        if (!Arrays.asList("everyday","work","occasion","active_day").contains(occasion) ||
            !Arrays.asList("any_weather","warm","cool","cold","rain").contains(weather)) throw new IllegalArgumentException("Unknown filters");
        List<Owned> local=new ArrayList<>(); List<DailyRules.Item> eligible=new ArrayList<>();
        Map<String,Owned> byId=new HashMap<>(); int unavailable=0,filtered=0;
        for(Owned owned:items) if(profile.wardrobe.equals(owned.item.piece.wardrobe) && !profile.id.equals(owned.id())) {
            local.add(owned); byId.put(owned.id(),owned);
            if(!owned.ready()) unavailable++;
            else if(!owned.eligible) filtered++;
            if(owned.eligible) eligible.add(owned.item);
        }
        String candidateSeason=profile.season.isEmpty()?"all":profile.season;
        WardrobeRules.Piece candidate=new WardrobeRules.Piece(profile.candidateId(),profile.wardrobe,profile.category,
            "available",false,candidateSeason,profile.style,profile.color.isEmpty()?"other":profile.color);
        eligible.add(new DailyRules.Item(candidate,0,false,""));
        List<List<String>> choices=DailyRules.generate(eligible,profile.wardrobe,occasion,weather,date,profile.id,
            rejected,Collections.singletonMap(profile.category,profile.candidateId()),20);
        List<Preview> previews=new ArrayList<>(); Set<String> partners=new HashSet<>();
        for(List<String> ids:choices) {
            if(!ids.contains(profile.candidateId())) throw new IllegalStateException("Candidate was not pinned");
            List<String> canonical=new ArrayList<>();
            for(String id:ids) { canonical.add(id.equals(profile.candidateId())?"wish:"+profile.id:id); if(!id.equals(profile.candidateId()))partners.add(id); }
            String signature=DailyRules.signature(canonical);
            List<Owned> replacements=new ArrayList<>();
            for(Owned o:local) if(replaces(o,candidate,ids,byId,weather)) replacements.add(o);
            // Availability is the primary order; matching traits and a stable tie-breaker follow.
            replacements.sort(Comparator.<Owned>comparingInt(o -> o.ready()?0:1)
                .thenComparing(Comparator.<Owned>comparingInt(o -> replacementRank(profile,o)).reversed())
                .thenComparing(o -> normalized(o.name)).thenComparing(Owned::id));
            previews.add(new Preview(ids,signature,savedSignatures.contains(signature),replacements));
        }
        previews.sort(Comparator.comparing((Preview p)->p.saved));
        boolean conflict=!WardrobeRules.compatibleSeasons(candidateSeason,weatherSeason(weather));
        List<List<String>> missing=previews.isEmpty()&&!conflict?missing(profile,eligible,weather):Collections.emptyList();
        return new Analysis(previews,similar(profile,local),missing,partners.size(),unavailable,filtered,conflict);
    }
    private static int replacementRank(Profile p,Owned o) {
        int rank=0;
        if(!p.color.isEmpty()&&!p.color.equals("other")&&p.color.equals(o.item.piece.color))rank+=8;
        if(!p.subcategory.isEmpty()&&p.subcategory.equals(o.subcategory))rank+=16;
        if(!p.source.isEmpty()&&o.sources.contains(p.source))rank+=32;
        if(!p.style.isEmpty()&&p.style.equals(o.item.piece.style))rank+=4;
        return rank;
    }
    private static boolean replaces(Owned replacement,WardrobeRules.Piece candidate,List<String> ids,Map<String,Owned> byId,String weather) {
        WardrobeRules.Piece p=replacement.item.piece;
        if(!candidate.category.equals(p.category) || ids.contains(p.id) ||
            !WardrobeRules.compatibleSeasons(p.season,weatherSeason(weather)))return false;
        List<String> categories=new ArrayList<>();categories.add(p.category);
        for(String id:ids) if(!id.equals(candidate.id)) {
            Owned partner=byId.get(id); if(partner==null || !partner.ready())return false;
            if(!WardrobeRules.compatibleSeasons(p.season,partner.item.piece.season))return false;
            categories.add(partner.item.piece.category);
        }
        return WardrobeRules.completeCategories(categories);
    }
    private static List<List<String>> missing(Profile profile,List<DailyRules.Item> eligible,String weather) {
        List<List<String>> result=new ArrayList<>();
        for(List<String> base:WardrobeRules.baseLayouts(Collections.singleton(profile.category))) {
            // Weather outerwear is optional in DailyRules, so it is never reported as the missing piece (QA-05).
            List<String> required=new ArrayList<>(base);
            if(!required.contains(profile.category))required.add(profile.category);
            List<String> absent=new ArrayList<>();
            for(String category:required) {
                boolean found=false;
                for(DailyRules.Item item:eligible) if(category.equals(item.piece.category) && WardrobeRules.ready(item.piece) &&
                    WardrobeRules.compatibleSeasons(item.piece.season,weatherSeason(weather)) &&
                    WardrobeRules.compatibleSeasons(item.piece.season,profile.season.isEmpty()?"all":profile.season)) {found=true;break;}
                if(!found)absent.add(category);
            }
            // If one layout has all categories, the problem is compatibility/rejection, not missing clothing.
            if(absent.isEmpty())return Collections.emptyList();
            if(!result.contains(absent))result.add(Collections.unmodifiableList(absent));
        }
        result.sort(Comparator.comparingInt(List::size));return result;
    }
    private static String weatherSeason(String weather) {
        return weather.equals("warm")?"summer":weather.equals("cold")?"winter":
            weather.equals("cool")||weather.equals("rain")?"midseason":"all";
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    public static String budgetStatus(Long price,String currency,Long budget,String budgetCurrency) {
        if(price==null || budget==null || !WishRules.validMinor(price) || !WishRules.validMinor(budget))return "unknown";
        if(!WishRules.CURRENCIES.contains(currency)||!WishRules.CURRENCIES.contains(budgetCurrency)||!currency.equals(budgetCurrency))return "different_currency";
        return price>budget?"above":"within";
    }
}
