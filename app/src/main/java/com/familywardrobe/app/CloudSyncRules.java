package com.familywardrobe.app;

/** Revision decisions are shared by the worker and executable host tests. No last-write-wins. */
public final class CloudSyncRules {
    public enum Action { NONE, PUSH, PULL, CONFLICT }
    public static Action decide(long base,long remote,boolean dirty,boolean hasData) {
        if(base<0 || remote<0 || remote<base) throw new IllegalArgumentException("Invalid revision");
        if(remote==base)return dirty?Action.PUSH:Action.NONE;
        if(!dirty || (base==0 && !hasData))return Action.PULL;
        return Action.CONFLICT;
    }
    public static boolean acknowledged(String pendingPath,String remotePath,String pendingHash,String remoteHash) {
        return !pendingPath.isEmpty() && pendingPath.equals(remotePath) && pendingHash.equals(remoteHash);
    }
    public static boolean canClearDirty(long stagedRevision,long currentRevision) {return stagedRevision==currentRevision;}
    public static boolean validPath(String uid,String path) {
        return path.matches("users/"+java.util.regex.Pattern.quote(uid)+"/snapshots/[a-f0-9-]{36}\\.zip");
    }
    private CloudSyncRules(){}
}
