package com.familywardrobe.app;

/** Executable against the production Java classes, without Android or mock replacements. */
public final class AuthRulesCases {
    private static int assertions;
    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }
    public static void run() {
        check(AuthRules.email("  me@example.com  ").equals("me@example.com"), "email edge spaces normalized");
        check(AuthRules.validEmail(" me+wardrobe@example.co.uk "), "email alias and domain");
        for (String value : new String[]{"", "me", "me@", "@example.com", "a@@example.com", "a b@example.com", "a@example..com", "a@example.com\nother"})
            check(!AuthRules.validEmail(value), "invalid email");
        check(!AuthRules.validEmail("x".repeat(250) + "@example.com"), "email length bounded");
        check(AuthRules.serverEmail("olena@sait.xn--j1amh").equals("olena@sait.xn--j1amh"), "BUG-08 punycode TLD");
        check(AuthRules.serverEmail(" Ivan@Пошта.укр ").equals("ivan@xn--80a1acn3a.xn--j1amh"), "BUG-08 IDN domain compared in punycode");
        check(AuthRules.serverEmail(" ivan@example.com").equals("ivan@example.com"), "BUG-13 edge spaces are trimmed before sending");
        for (String value : new String[]{"іван@example.com", "é@example.com", "a@b", "a@example.c", "a@-example.com", "a@example.com\nX"})
            check(!AuthRules.validEmail(value), "BUG-13 client refuses what the server refuses: " + value);
        check(!AuthRules.validNewPassword("12345678901"), "11 characters rejected on signup");
        check(AuthRules.validNewPassword("123456789012"), "12 characters accepted");
        check(AuthRules.validNewPassword(" a long passphrase "), "spaces preserved and accepted");
        check(AuthRules.validNewPassword("x".repeat(4096)), "maximum length accepted");
        check(!AuthRules.validNewPassword("x".repeat(4097)), "over maximum rejected");
        String alice = AuthRules.accountKey("project-a", "alice");
        check(AuthRules.validAccountKey(alice), "path-safe key");
        check(alice.equals(AuthRules.accountKey("project-a", "alice")), "stable across sessions");
        check(!alice.equals(AuthRules.accountKey("project-a", "bob")), "different users separated");
        check(!alice.equals(AuthRules.accountKey("project-b", "alice")), "different projects separated");
        check(!AuthRules.accountKey("a:b", "c").equals(AuthRules.accountKey("a", "b:c")), "identity pair unambiguous");
        check(AuthRules.validAccountKey(AuthRules.accountKey("p", "../../wardrobe.db")), "UID cannot become a path");
        check(!AuthRules.validAccountKey("../wardrobe"), "unsafe key rejected");
        boolean failed = false;
        try { AuthRules.accountKey("", "a"); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "missing project rejected");
        AuthAttemptGate gate = new AuthAttemptGate();
        check(!gate.isBusy(), "starts logged out and idle");
        long first = gate.begin();
        check(first > 0 && gate.isBusy(), "request starts");
        check(gate.begin() == 0, "double submit rejected");
        gate.cancel();
        check(gate.isBusy() && gate.isCancelling(), "cancel waits for non-cancellable SDK task");
        check(gate.begin() == 0, "new request cannot race an old SDK sign-in");
        check(!gate.finish(first), "late success cannot unlock after cancellation");
        check(!gate.isBusy() && !gate.isCancelling(), "settled cancellation allows retry");
        long second = gate.begin();
        check(second > first, "retry gets a different identity");
        check(!gate.finish(first) && gate.isBusy(), "stale completion cannot settle newer request");
        check(gate.finish(second), "accepted request may unlock exactly once");
        check(!gate.finish(second), "duplicate callback cannot unlock twice");
        check(!gate.finish(0), "invalid attempt cannot unlock");
        gate.cancel();
        check(!gate.isBusy() && !gate.isCancelling(), "sign-out with no request is idle");
        long third = gate.begin();
        gate.cancel(); gate.cancel();
        check(!gate.finish(third), "repeated cancellation remains cancelled");
        System.out.println("PASS: " + assertions + " production auth validation, identity and cancellation assertions.");
    }
    public static void main(String[] args) { run(); }
}
