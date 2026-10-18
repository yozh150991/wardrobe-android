package com.familywardrobe.app;

import java.util.regex.Pattern;

/** Client contract only. Roles must come from the authenticated server, never from an archive. */
public final class FamilyRules {
    private FamilyRules() {}
    private static final Pattern ID = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    public static final long CACHE_LEASE_MS = 15L * 60 * 1000;
    public static final int WIRE_VERSION = 1;
    public static final int CONTENT_VERSION = 10;
    public enum Role { OWNER, VIEWER, EDITOR }

    public static boolean validId(String value) { return value != null && ID.matcher(value).matches(); }
    public static boolean validHash(String value) { return value != null && HASH.matcher(value).matches(); }
    public static boolean validUid(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }
    public static Role role(String actorUid, String ownerUid, String membershipRole, boolean active) {
        if (!validUid(actorUid) || !validUid(ownerUid)) return null;
        if (actorUid.equals(ownerUid)) return Role.OWNER;
        if (!active) return null;
        if ("viewer".equals(membershipRole)) return Role.VIEWER;
        if ("editor".equals(membershipRole)) return Role.EDITOR;
        return null; // A membership can never assign ownership.
    }
    public static boolean mayRead(Role role) { return role != null; }
    public static boolean mayEdit(Role role) { return role == Role.OWNER || role == Role.EDITOR; }
    public static boolean mayManage(Role role) { return role == Role.OWNER; }
    public static boolean mayExport(Role role) { return role == Role.OWNER; }
    public static boolean cachedRead(Role role, String verifiedSession, String currentSession, long verifiedElapsed, long nowElapsed) {
        return mayRead(role) && verifiedSession != null && !verifiedSession.isEmpty() && verifiedSession.equals(currentSession)
            && verifiedElapsed >= 0 && nowElapsed >= verifiedElapsed && nowElapsed - verifiedElapsed < CACHE_LEASE_MS;
    }
    public static long nextRevision(long base, long current) {
        if (base < 0 || current < 0 || base != current || current == Long.MAX_VALUE)
            throw new IllegalArgumentException("Family revision conflict");
        return current + 1;
    }
    public static String archivePath(String remoteId, String archiveId) {
        if (!validId(remoteId) || !validId(archiveId)) throw new IllegalArgumentException("Invalid family path");
        return "familyWardrobes/" + remoteId + "/snapshots/" + archiveId + ".zip";
    }
}
