package com.familywardrobe.app;

import java.util.*;
import static com.familywardrobe.app.FamilyRules.Role.*;

/** Executes the production graph and access/revision contract, without Android stubs. */
public final class FamilyCases {
    private static int checks;
    private static final String A = id(1), B = id(2), P = id(3), Q = id(4), SHARED = id(5);
    private static String id(int value) { return String.format(Locale.ROOT, "00000000-0000-4000-8000-%012x", value); }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void deny(Runnable call, String message) {
        boolean rejected = false;
        try { call.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static WardrobeScope.Row row(String key, String parent, List<String> refs, String... photos) {
        return new WardrobeScope.Row(key, parent, refs, Arrays.asList(photos));
    }
    private static List<WardrobeScope.Row> wardrobe(String w, String photo) {
        String root = "wardrobe:" + w, g = "garment:" + w, o = "outfit:" + w, wish = "wish:" + w, c = "capsule:" + w;
        return List.of(row(root, null, List.of()),
            row(g, root, List.of("category:" + w, "color:" + w), photo),
            row(o, root, List.of()), row("part:" + w, o, List.of(g)),
            row("wear:" + w, g, List.of("plan:" + w)), row(wish, root, List.of(g), SHARED),
            row("idea:" + w, o, List.of(wish), SHARED), row("plan:" + w, root, List.of(o, g)),
            row("feedback:" + w, root, List.of(g)), row(c, root, List.of()),
            row("capsuleItem:" + w, c, List.of(g)), row("capsuleOutfit:" + w, c, List.of(o)),
            row("receipt:" + w, wish, List.of(g, "category:" + w, "color:" + w), photo),
            row("event:" + w, wish, List.of(), photo), row("category:" + w, root, List.of()),
            row("color:" + w, root, List.of()), row("unusedCategory:" + w, root, List.of()),
            row("unusedColor:" + w, root, List.of()));
    }
    private static List<WardrobeScope.Row> both() {
        List<WardrobeScope.Row> rows = new ArrayList<>(wardrobe(A, P)); rows.addAll(wardrobe(B, Q)); return rows;
    }
    private static List<WardrobeScope.Row> with(WardrobeScope.Row extra) {
        List<WardrobeScope.Row> rows = both(); rows.add(extra); return rows;
    }
    public static void main(String[] args) {
        checks = 0;
        List<WardrobeScope.Row> rows = both();
        WardrobeScope.Selection a = WardrobeScope.select(A, rows), b = WardrobeScope.select(B, rows);
        check(a.keys.size() == 18 && b.keys.size() == 18, "Every row kind, including unused definitions");
        check(Collections.disjoint(a.keys, b.keys), "No entity from another wardrobe");
        check(a.photos.equals(Set.of(P, SHARED)) && b.photos.equals(Set.of(Q, SHARED)), "Only referenced photos, shared ID retained for both");
        List<WardrobeScope.Row> reversed = new ArrayList<>(rows); Collections.reverse(reversed);
        check(a.keys.equals(WardrobeScope.select(A, reversed).keys), "Stable identity independent of input order");
        for (int i = 100; i < 600; i++) rows.add(row("garment:" + id(i), "wardrobe:" + (i % 2 == 0 ? A : B), List.of(), id(i + 1000)));
        check(WardrobeScope.select(A, rows).keys.size() == 268, "500 additional garments retain exact ownership");
        check(WardrobeScope.select(B, rows).photos.size() == 252, "Large wardrobe photo set is exact");
        String empty = id(6);
        rows.add(row("wardrobe:" + empty, null, List.of()));
        check(WardrobeScope.select(empty, rows).keys.equals(Set.of("wardrobe:" + empty)) && WardrobeScope.select(empty, rows).photos.isEmpty(), "Empty wardrobe works");
        deny(() -> WardrobeScope.select(id(99), both()), "Unknown wardrobe cannot fall back to whole account");
        deny(() -> WardrobeScope.select(A, with(row("wardrobe:" + A, null, List.of()))), "Duplicate row rejected");
        deny(() -> WardrobeScope.select(A, with(row("orphan", "missing", List.of()))), "Missing parent in unrelated data rejected");
        deny(() -> WardrobeScope.select(A, with(row("bad-root", null, List.of()))), "Only wardrobes may be roots");
        deny(() -> WardrobeScope.select(A, with(row("orphan", "wardrobe:" + A, List.of("missing")))), "Missing reference rejected");
        List<WardrobeScope.Row> cycle = both(); cycle.add(row("x", "y", List.of())); cycle.add(row("y", "x", List.of()));
        deny(() -> WardrobeScope.select(A, cycle), "Cyclic parent rejected");
        for (WardrobeScope.Row source : wardrobe(A, P)) {
            if (source.references.isEmpty()) continue;
            List<WardrobeScope.Row> cross = both(); cross.removeIf(r -> r.key.equals(source.key));
            List<String> refs = new ArrayList<>(source.references); refs.set(0, refs.get(0).replace(A, B));
            cross.add(row(source.key, source.parent, refs, source.photos.toArray(new String[0])));
            deny(() -> WardrobeScope.select(A, cross), "Cross-wardrobe " + source.key);
        }
        deny(() -> WardrobeScope.select(A, with(row("bad-photo", "wardrobe:" + B, List.of(), "../private"))), "Unsafe photo ID rejected even outside selection");
        List<WardrobeScope.Row> immutableSource = new ArrayList<>();
        List<String> mutablePhotos = new ArrayList<>(List.of(P));
        immutableSource.add(new WardrobeScope.Row("wardrobe:" + A, null, List.of(), mutablePhotos));
        mutablePhotos.add(Q);
        check(WardrobeScope.select(A, immutableSource).photos.equals(Set.of(P)), "Rows copy caller collections");
        boolean immutable = false;
        try { a.photos.add(Q); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Selection cannot be mutated");

        check(FamilyRules.role("owner", "owner", null, false) == OWNER, "Owner comes from authoritative root");
        check(FamilyRules.role("member", "owner", "viewer", true) == VIEWER, "Viewer role");
        check(FamilyRules.role("member", "owner", "editor", true) == EDITOR, "Editor role");
        check(FamilyRules.role("member", "owner", "owner", true) == null, "Membership cannot promote ownership");
        check(FamilyRules.role("member", "owner", "editor", false) == null, "Revoked member denied");
        check(FamilyRules.role("", "owner", "editor", true) == null, "Unauthenticated actor denied");
        check(FamilyRules.role("member", "owner", "unknown", true) == null, "Unknown role denied");
        for (FamilyRules.Role role : FamilyRules.Role.values()) {
            check(FamilyRules.mayRead(role), "Active role reads");
            check(FamilyRules.mayEdit(role) == (role != VIEWER), "Only owner/editor writes");
            check(FamilyRules.mayManage(role) == (role == OWNER), "Only owner manages members");
            check(FamilyRules.mayExport(role) == (role == OWNER), "Only owner exports shared wardrobe");
        }
        check(!FamilyRules.mayRead(null) && !FamilyRules.mayEdit(null) && !FamilyRules.mayManage(null) && !FamilyRules.mayExport(null), "No membership grants no operation");
        long ttl = FamilyRules.CACHE_LEASE_MS;
        check(FamilyRules.cachedRead(VIEWER, A, A, 10, 10 + ttl - 1), "Lease before expiry");
        check(!FamilyRules.cachedRead(VIEWER, A, A, 10, 10 + ttl), "Lease expires at boundary");
        check(!FamilyRules.cachedRead(VIEWER, A, B, 10, 20), "Other account or restarted session denied");
        check(!FamilyRules.cachedRead(VIEWER, A, A, 20, 10), "Monotonic clock rollback denied");
        check(!FamilyRules.cachedRead(null, A, A, 10, 20), "Revoked lease denied");
        check(!FamilyRules.cachedRead(EDITOR, "", "", 10, 20), "Missing session denied");
        check(!FamilyRules.cachedRead(EDITOR, A, A, -1, 20), "Missing verification time denied");
        check(FamilyRules.nextRevision(0, 0) == 1 && FamilyRules.nextRevision(12, 12) == 13, "CAS increments once");
        deny(() -> FamilyRules.nextRevision(11, 12), "Concurrent write conflict");
        deny(() -> FamilyRules.nextRevision(-1, -1), "Negative revision denied");
        deny(() -> FamilyRules.nextRevision(Long.MAX_VALUE, Long.MAX_VALUE), "Revision overflow denied");
        check(FamilyRules.archivePath(A, B).equals("familyWardrobes/" + A + "/snapshots/" + B + ".zip"), "Separate stable archive path");
        for (String invalid : new String[]{"", "../users/other", A + "/../" + B, A + ".zip", null}) {
            deny(() -> FamilyRules.archivePath(invalid, B), "Invalid remote ID");
            deny(() -> FamilyRules.archivePath(A, invalid), "Invalid archive ID");
        }
        check(FamilyRules.validHash("ab".repeat(32)) && !FamilyRules.validHash("G".repeat(64)) && !FamilyRules.validHash("ab"), "Exact SHA-256 format");
        check(!FamilyRules.validUid("x\n") && !FamilyRules.validUid("x".repeat(129)) && FamilyRules.validUid("test-user"), "Owner identity bounds");
        System.out.println("PASS: " + checks + " production family ownership, photo isolation, role, lease, revision and path assertions.");
    }
}
