package com.familywardrobe.app;

/** Runtime-only authorization. Never serialize or reconstruct a lease from cached files. */
public final class FamilyReadLease {
    private String session;
    private FamilyRules.Role role;
    private long verifiedAt = -1;

    public synchronized void grant(String currentSession, FamilyRules.Role verifiedRole, long requestStarted, long now) {
        clear();
        if (!FamilyRules.cachedRead(verifiedRole, currentSession, currentSession, requestStarted, now))
            throw new IllegalArgumentException("Expired family verification");
        session = currentSession;
        role = verifiedRole;
        verifiedAt = requestStarted;
    }
    public synchronized boolean permits(String currentSession, long now) {
        return FamilyRules.cachedRead(role, session, currentSession, verifiedAt, now);
    }
    public synchronized void clear() { session = null; role = null; verifiedAt = -1; }
}
