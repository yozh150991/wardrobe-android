package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State machines of the app: sign-in attempt gate, family read lease, private cloud sync decisions,
 * family revisions/roles, photo edit history, late photo results and the import review queue.
 * Each machine is checked transition by transition (valid and invalid events per state). Technique: ST (+ BVA, EG).
 */
public final class QaStateTransitionCases {
    private QaStateTransitionCases() {}
    private static final long MINUTE = 60_000L;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaStateTransitionCases");
        authGate(qa);
        readLease(qa);
        cloudSync(qa);
        familyRevisions(qa);
        photoHistory(qa);
        photoWorkGate(qa);
        importReview(qa);
        qa.done();
    }

    /** IDLE --begin--> BUSY --cancel--> CANCELLING --finish--> IDLE (result rejected); BUSY --finish--> IDLE (accepted). */
    private static void authGate(QaCheck qa) {
        AuthAttemptGate gate = new AuthAttemptGate();
        // IDLE
        qa.isFalse(ST, "idle is not busy", gate.isBusy());
        qa.isFalse(ST, "idle finish ignored", gate.finish(1));
        gate.cancel();
        qa.isFalse(ST, "idle cancel has no effect", gate.isCancelling());
        // IDLE -> BUSY
        long first = gate.begin();
        qa.isTrue(ST, "begin returns a token", first > 0);
        qa.isTrue(ST, "busy", gate.isBusy());
        // BUSY: a second begin is refused, a stale or zero token cannot finish it.
        qa.equal(ST, "second begin refused", 0L, gate.begin());
        qa.isFalse(ST, "zero token", gate.finish(0));
        qa.isFalse(ST, "foreign token", gate.finish(first + 1));
        qa.isTrue(ST, "still busy after bad finish", gate.isBusy());
        // BUSY -> IDLE accepted
        qa.isTrue(ST, "finish accepted", gate.finish(first));
        qa.isFalse(ST, "idle again", gate.isBusy());
        qa.isFalse(ST, "double finish ignored", gate.finish(first));
        // IDLE -> BUSY -> CANCELLING -> IDLE rejected
        long second = gate.begin();
        qa.isTrue(ST, "tokens increase", second > first);
        gate.cancel();
        qa.isTrue(ST, "cancelling", gate.isCancelling());
        qa.equal(ST, "begin refused while cancelling", 0L, gate.begin());
        gate.cancel();
        qa.isTrue(ST, "cancel is idempotent", gate.isCancelling());
        qa.isFalse(ST, "cancelled result rejected", gate.finish(second));
        qa.isFalse(ST, "idle after cancelled finish", gate.isBusy() || gate.isCancelling());
        // A cancelled flag never leaks into the next attempt.
        long third = gate.begin();
        qa.isFalse(ST, "fresh attempt not cancelling", gate.isCancelling());
        qa.isTrue(ST, "fresh attempt accepted", gate.finish(third));
        qa.isFalse(EG, "stale token of an old attempt", gate.finish(second));
        // EG: concurrent taps start exactly one attempt.
        AuthAttemptGate shared = new AuthAttemptGate();
        AtomicInteger started = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) futures.add(pool.submit(() -> { go.await(); if (shared.begin() != 0) started.incrementAndGet(); return null; }));
        go.countDown();
        for (Future<?> f : futures) try { f.get(5, TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(e); }
        pool.shutdownNow();
        qa.equal(EG, "64 concurrent begins start one attempt", 1, started.get());
    }

    /** EMPTY --grant--> GRANTED --(15 min | session change | clear)--> denied; failed grant leaves EMPTY. */
    private static void readLease(QaCheck qa) {
        FamilyReadLease lease = new FamilyReadLease();
        qa.isFalse(ST, "empty lease denies", lease.permits("s1", 0));
        lease.grant("s1", FamilyRules.Role.VIEWER, 1_000, 1_000);
        qa.isTrue(ST, "granted lease permits", lease.permits("s1", 1_000));
        qa.isFalse(ST, "another session denied", lease.permits("s2", 1_000));
        qa.isTrue(BVA, "15 min - 1 ms", lease.permits("s1", 1_000 + 15 * MINUTE - 1));
        qa.isFalse(BVA, "exactly 15 min", lease.permits("s1", 1_000 + 15 * MINUTE));
        qa.isFalse(EG, "clock going backwards", lease.permits("s1", 999));
        lease.clear();
        qa.isFalse(ST, "cleared lease denies", lease.permits("s1", 1_000));
        // A failed grant must not keep the previous lease.
        lease.grant("s1", FamilyRules.Role.EDITOR, 0, 0);
        qa.raises(ST, "expired verification", IllegalArgumentException.class, () -> lease.grant("s1", FamilyRules.Role.EDITOR, 0, 15 * MINUTE));
        qa.isFalse(ST, "failed grant clears the old lease", lease.permits("s1", 1));
        qa.raises(ST, "no role", IllegalArgumentException.class, () -> lease.grant("s1", null, 0, 0));
        qa.raises(ST, "no session", IllegalArgumentException.class, () -> lease.grant(null, FamilyRules.Role.OWNER, 0, 0));
        qa.raises(BVA, "request started in the future", IllegalArgumentException.class, () -> lease.grant("s1", FamilyRules.Role.OWNER, 10, 9));
        // Re-grant replaces session and start time.
        lease.grant("s1", FamilyRules.Role.OWNER, 0, 0);
        lease.grant("s2", FamilyRules.Role.OWNER, 20 * MINUTE, 20 * MINUTE);
        qa.isFalse(ST, "old session replaced", lease.permits("s1", 20 * MINUTE));
        qa.isTrue(ST, "new session permitted", lease.permits("s2", 34 * MINUTE));
    }

    /** Decision table of the private cloud head: (base, remote, dirty, hasData) -> action. */
    private static void cloudSync(QaCheck qa) {
        qa.equal(ST, "in sync", CloudSyncRules.Action.NONE, CloudSyncRules.decide(3, 3, false, true));
        qa.equal(ST, "local change", CloudSyncRules.Action.PUSH, CloudSyncRules.decide(3, 3, true, true));
        qa.equal(ST, "remote change", CloudSyncRules.Action.PULL, CloudSyncRules.decide(3, 4, false, true));
        qa.equal(ST, "both changed", CloudSyncRules.Action.CONFLICT, CloudSyncRules.decide(3, 4, true, true));
        qa.equal(ST, "fresh device pulls", CloudSyncRules.Action.PULL, CloudSyncRules.decide(0, 4, true, false));
        qa.equal(ST, "fresh device with data conflicts", CloudSyncRules.Action.CONFLICT, CloudSyncRules.decide(0, 4, true, true));
        qa.equal(BVA, "first upload", CloudSyncRules.Action.PUSH, CloudSyncRules.decide(0, 0, true, true));
        qa.equal(BVA, "nothing anywhere", CloudSyncRules.Action.NONE, CloudSyncRules.decide(0, 0, false, false));
        qa.raises(BVA, "remote behind base", IllegalArgumentException.class, () -> CloudSyncRules.decide(4, 3, false, true));
        qa.raises(BVA, "negative base", IllegalArgumentException.class, () -> CloudSyncRules.decide(-1, 0, false, true));
        // Upload acknowledgement and dirty-flag clearing.
        String path = "users/u/snapshots/0a1b2c3d-0000-4000-8000-00000000abcd.zip";
        qa.isTrue(ST, "acknowledged", CloudSyncRules.acknowledged(path, path, "h", "h"));
        qa.isFalse(ST, "other hash", CloudSyncRules.acknowledged(path, path, "h", "g"));
        qa.isFalse(ST, "other path", CloudSyncRules.acknowledged(path, path.replace("abcd", "abce"), "h", "h"));
        qa.isFalse(EG, "nothing pending", CloudSyncRules.acknowledged("", "", "", ""));
        qa.isTrue(ST, "clear dirty at the staged revision", CloudSyncRules.canClearDirty(7, 7));
        qa.isFalse(ST, "edit during upload keeps dirty", CloudSyncRules.canClearDirty(7, 8));
        qa.isTrue(EP, "own path", CloudSyncRules.validPath("u", path));
        qa.isFalse(EP, "other user's path", CloudSyncRules.validPath("v", path));
        qa.isFalse(EG, "regex in uid is literal", CloudSyncRules.validPath("u.*", path));
        qa.isFalse(EG, "traversal", CloudSyncRules.validPath("u", "users/u/snapshots/../x.zip"));
    }

    private static void familyRevisions(QaCheck qa) {
        // EP: role resolution table.
        qa.equal(EP, "owner wins over membership", FamilyRules.Role.OWNER, FamilyRules.role("a", "a", "viewer", false));
        qa.equal(EP, "active viewer", FamilyRules.Role.VIEWER, FamilyRules.role("b", "a", "viewer", true));
        qa.equal(EP, "active editor", FamilyRules.Role.EDITOR, FamilyRules.role("b", "a", "editor", true));
        qa.equal(EP, "inactive member", null, FamilyRules.role("b", "a", "editor", false));
        qa.equal(EG, "membership claiming owner", null, FamilyRules.role("b", "a", "owner", true));
        qa.equal(EG, "unknown role", null, FamilyRules.role("b", "a", "admin", true));
        qa.equal(EG, "control character uid", null, FamilyRules.role("a\n", "a\n", "viewer", true));
        qa.equal(BVA, "uid of 128", FamilyRules.Role.OWNER, FamilyRules.role("u".repeat(128), "u".repeat(128), "", false));
        qa.equal(BVA, "uid of 129", null, FamilyRules.role("u".repeat(129), "u".repeat(129), "", false));
        // Permissions per role.
        for (FamilyRules.Role role : FamilyRules.Role.values()) {
            qa.isTrue(EP, role + " reads", FamilyRules.mayRead(role));
            qa.equal(EP, role + " edits", role != FamilyRules.Role.VIEWER, FamilyRules.mayEdit(role));
            qa.equal(EP, role + " manages", role == FamilyRules.Role.OWNER, FamilyRules.mayManage(role));
            qa.equal(EP, role + " exports", role == FamilyRules.Role.OWNER, FamilyRules.mayExport(role));
        }
        qa.isFalse(EP, "no role reads nothing", FamilyRules.mayRead(null) || FamilyRules.mayEdit(null));
        // ST: compare-and-swap revision.
        qa.equal(ST, "0 -> 1", 1L, FamilyRules.nextRevision(0, 0));
        qa.equal(ST, "41 -> 42", 42L, FamilyRules.nextRevision(41, 41));
        qa.raises(ST, "stale base", IllegalArgumentException.class, () -> FamilyRules.nextRevision(40, 41));
        qa.raises(ST, "base ahead", IllegalArgumentException.class, () -> FamilyRules.nextRevision(42, 41));
        qa.raises(BVA, "exhausted", IllegalArgumentException.class, () -> FamilyRules.nextRevision(Long.MAX_VALUE, Long.MAX_VALUE));
        qa.equal(BVA, "last revision", Long.MAX_VALUE, FamilyRules.nextRevision(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1));
        String id = "0a1b2c3d-0000-4000-8000-00000000abcd";
        qa.equal(EP, "archive path", "familyWardrobes/" + id + "/snapshots/" + id + ".zip", FamilyRules.archivePath(id, id));
        qa.raises(EG, "path injection", IllegalArgumentException.class, () -> FamilyRules.archivePath(id, "../../users/x"));
    }

    /** EMPTY --append--> [a] --undo--> [|a] --redo--> [a]; append after undo drops the redo branch. */
    private static void photoHistory(QaCheck qa) {
        PhotoEditHistory h = new PhotoEditHistory();
        qa.isFalse(ST, "empty: no undo", h.canUndo());
        qa.isFalse(ST, "empty: no redo", h.canRedo());
        qa.equal(ST, "empty: undo is a no-op", 0, h.undo().getCursor());
        qa.equal(ST, "empty: current", null, h.getCurrent());
        h = h.append("a").append("b");
        qa.equal(ST, "two steps", "b", h.getCurrent());
        PhotoEditHistory undone = h.undo();
        qa.equal(ST, "undo", "a", undone.getCurrent());
        qa.isTrue(ST, "redo available", undone.canRedo());
        qa.equal(ST, "redo", "b", undone.redo().getCurrent());
        qa.equal(ST, "redo at the end is a no-op", 2, h.redo().getCursor());
        PhotoEditHistory branched = undone.append("c");
        qa.equal(ST, "new action drops redo", Arrays.asList("a", "c"), Arrays.asList(branched.getSteps()));
        qa.isFalse(ST, "no redo after branch", branched.canRedo());
        qa.equal(ST, "original is immutable", 2, h.getSteps().length);
        // BVA: 128 steps and 160 000 characters.
        PhotoEditHistory full = new PhotoEditHistory();
        for (int i = 0; i < PhotoEditHistory.MAX_STEPS; i++) full = full.append("s");
        qa.equal(BVA, "128 steps", 128, full.getCursor());
        qa.isFalse(BVA, "129th step refused", full.canAppend("s"));
        final PhotoEditHistory fullHistory = full;
        qa.raises(BVA, "append when full", IllegalStateException.class, () -> fullHistory.append("s"));
        qa.isTrue(BVA, "undo frees a slot", full.undo().canAppend("s"));
        PhotoEditHistory big = new PhotoEditHistory().append("x".repeat(PhotoEditHistory.MAX_CHARACTERS - 1));
        qa.isTrue(BVA, "exactly the character limit", big.canAppend("y"));
        qa.isFalse(BVA, "one character over", big.canAppend("yz"));
        qa.isFalse(EP, "empty step", new PhotoEditHistory().canAppend(""));
        qa.isFalse(EP, "null step", new PhotoEditHistory().canAppend(null));
        // EG: restored (persisted) histories are validated.
        qa.raises(EG, "cursor beyond steps", IllegalArgumentException.class, () -> new PhotoEditHistory(new String[] {"a"}, 2));
        qa.raises(EG, "negative cursor", IllegalArgumentException.class, () -> new PhotoEditHistory(new String[] {"a"}, -1));
        qa.raises(EG, "empty restored step", IllegalArgumentException.class, () -> new PhotoEditHistory(new String[] {""}, 1));
        qa.raises(EG, "129 restored steps", IllegalArgumentException.class, () -> new PhotoEditHistory(new String[129], 0));
        String[] steps = {"a"};
        PhotoEditHistory copy = new PhotoEditHistory(steps, 1);
        steps[0] = "mutated";
        qa.equal(EG, "defensive copy", "a", copy.getCurrent());
    }

    private static void photoWorkGate(QaCheck qa) {
        PhotoWorkGate gate = new PhotoWorkGate();
        long token = gate.begin("editor:1", "photo:a");
        qa.isTrue(ST, "own result accepted", gate.accepts(token, "editor:1", "photo:a"));
        qa.isFalse(ST, "result for another photo", gate.accepts(token, "editor:1", "photo:b"));
        gate.activate("editor:1");
        qa.isTrue(ST, "re-activating the same editor keeps work", gate.accepts(token, "editor:1", "photo:a"));
        gate.activate("editor:2");
        qa.isFalse(ST, "switching editor rejects late result", gate.accepts(token, "editor:1", "photo:a"));
        long second = gate.begin("editor:2", "photo:c");
        gate.cancel();
        qa.isFalse(ST, "cancelled work rejected", gate.accepts(second, "editor:2", "photo:c"));
        long third = gate.begin("editor:2", "photo:c");
        qa.isFalse(ST, "older token after restart", gate.accepts(second, "editor:2", "photo:c"));
        qa.isTrue(ST, "restarted work accepted", gate.accepts(third, "editor:2", "photo:c"));
    }

    private static void importReview(QaCheck qa) {
        List<String> order = Arrays.asList("a", "b", "c", "d");
        Set<String> ready = new HashSet<>(Arrays.asList("a", "b", "d"));
        qa.equal(ST, "first ready", "a", ImportReviewRules.next(order, ready, Set.of(), null));
        qa.equal(ST, "skips current", "b", ImportReviewRules.next(order, ready, Set.of(), "a"));
        qa.equal(ST, "skips deferred and not ready", "d", ImportReviewRules.next(order, ready, Set.of("a", "b"), null));
        qa.equal(ST, "end of queue", null, ImportReviewRules.next(order, Set.of("a"), Set.of(), "a"));
        qa.equal(EG, "ready id outside the queue is ignored", null, ImportReviewRules.next(order, Set.of("z"), Set.of(), null));
    }
}
