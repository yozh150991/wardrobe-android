package com.familywardrobe.app;
import java.util.*;

public final class ScenarioRulesCases {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        usingCloudRevisions(); usingInspiration();
        System.out.println("PASS: "+checks+" production Java assertions for sync decisions, lost acknowledgements, account paths, varied inspiration and lock/replace behavior.");
    }
    private static void usingCloudRevisions() {
        check(CloudSyncRules.decide(0,0,false,false)==CloudSyncRules.Action.NONE,"new empty account");
        check(CloudSyncRules.decide(0,0,true,true)==CloudSyncRules.Action.PUSH,"first upload");
        check(CloudSyncRules.decide(0,8,true,false)==CloudSyncRules.Action.PULL,"new device seed");
        check(CloudSyncRules.decide(0,8,true,true)==CloudSyncRules.Action.CONFLICT,"existing legacy data retained");
        check(CloudSyncRules.decide(8,8,true,true)==CloudSyncRules.Action.PUSH,"durable offline change");
        check(CloudSyncRules.decide(8,9,false,true)==CloudSyncRules.Action.PULL,"clean device downloads");
        check(CloudSyncRules.decide(8,9,true,true)==CloudSyncRules.Action.CONFLICT,"concurrent edits");
        check(CloudSyncRules.decide(8,8,false,true)==CloudSyncRules.Action.NONE,"idempotent repeated sync");
        try {CloudSyncRules.decide(9,8,true,true);throw new AssertionError("server rollback accepted");}catch(IllegalArgumentException e){checks++;}
        check(CloudSyncRules.acknowledged("p","p","h","h"),"lost acknowledgement recognized");
        check(!CloudSyncRules.acknowledged("","","h","h"),"empty path is not acknowledgement");
        check(!CloudSyncRules.acknowledged("p","p","h","different"),"archive bytes differ");
        check(CloudSyncRules.canClearDirty(11,11),"staged changes acknowledged");
        check(!CloudSyncRules.canClearDirty(11,12),"edit during upload remains queued");
        String path="users/alice/snapshots/"+UUID.randomUUID()+".zip";
        check(CloudSyncRules.validPath("alice",path),"owner path");
        check(!CloudSyncRules.validPath("bob",path),"another account");
        check(!CloudSyncRules.validPath("alice",path+"/../head"),"path traversal");
        check(!CloudSyncRules.validPath("a.*",path),"UID escaped as literal");
    }
    private static void usingInspiration() {
        Set<String> keys=new HashSet<>();Map<String,Integer> counts=new HashMap<>();
        for(InspirationCatalog.Idea idea:InspirationCatalog.all()) {
            check(keys.add(idea.key),"unique stable idea");
            check(InspirationCatalog.validKey(idea.key,idea.category),"saved key accepted");
            check(!InspirationCatalog.validKey(idea.key,"other_item"),"category substitution rejected");
            counts.merge(idea.category,1,Integer::sum);
        }
        check(keys.size()==69,"69 distinct examples");
        for(String category:Arrays.asList("top","bottom","shoes","outerwear","dress","layer","bag"))check(counts.get(category)>=3,"multiple alternatives in "+category);
        check(InspirationCatalog.validKey("idea:top","top"),"legacy backup idea supported");
        check(!InspirationCatalog.validKey("idea:top_invented_blue","top"),"unknown catalog key rejected");
        check(InspirationCatalog.forContext("work","warm").get(0).style.equals("formal"),"occasion prioritizes formal ideas");
        for(InspirationCatalog.Idea i:InspirationCatalog.forContext("everyday","cold"))check(WardrobeRules.compatibleSeasons(i.season,"winter"),"winter filters summer examples");
        List<LookSelection.Piece> pool=new ArrayList<>();
        for(InspirationCatalog.Idea i:InspirationCatalog.forContext("everyday","any_weather"))pool.add(new LookSelection.Piece(i.key,i.category,i.season));
        List<String> core=Arrays.asList("top","bottom","shoes");
        List<String> first=LookSelection.generate(pool,Collections.emptyList(),Collections.emptySet(),core,false);
        check(first.size()==3,"inspiration generates complete base outfit");
        List<LookSelection.Piece> selected=new ArrayList<>();for(String key:first)for(LookSelection.Piece p:pool)if(p.key.equals(key))selected.add(p);
        Set<String> locks=new HashSet<>(first.subList(0,2));
        List<String> next=LookSelection.generate(pool,selected,locks,core,true);
        check(next.containsAll(locks),"two locks survive refresh");
        check(!next.get(2).equals(first.get(2)),"unlocked shoes change");
        check(next.size()==3,"refresh retains complete categories");
    }
}
