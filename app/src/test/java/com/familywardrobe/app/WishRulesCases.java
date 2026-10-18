package com.familywardrobe.app;
import java.util.Objects;

public final class WishRulesCases {
    private static int checks;
    private static void equal(Object expected, Object actual) { checks++; if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual); }
    private static void invalid(String value) { checks++; try { WishRules.money(value); throw new AssertionError("Accepted " + value); } catch (IllegalArgumentException expected) {} }
    public static void main(String[] args) {
        equal(null, WishRules.money("")); equal(null, WishRules.money("  "));
        equal(0L, WishRules.money("0")); equal(1L, WishRules.money("0,01"));
        equal(22900L, WishRules.money("229")); equal(35050L, WishRules.money("350,50"));
        equal(123456L, WishRules.money("1\u00a0234.56")); equal(123456L, WishRules.money("1\u202f234,56"));
        equal(99999999999L, WishRules.money("999999999.99"));
        for (String bad : new String[] {"-1", "+1", "1.001", "1,2.3", "1e3", "NaN", "Infinity", ".50", "1000000000", "1,", "zł 229", "１２"}) invalid(bad);
        for (long cents = 0; cents <= 2000; cents++) equal(cents, WishRules.money((cents / 100) + "." + String.format(java.util.Locale.ROOT, "%02d", cents % 100)));
        equal(true, WishRules.validLink("")); equal(true, WishRules.validLink("https://example.org/a?q=1#color"));
        equal(true, WishRules.validLink("http://example.org"));
        for (String bad : new String[] {"javascript:alert(1)", "file:///tmp/a", "data:text/html,test", "example.org", "https://", "https://user:password@example.org", "https://exa mple.org"}) equal(false, WishRules.validLink(bad));
        equal(false, WishRules.validMinor(-1L)); equal(false, WishRules.validMinor(WishRules.MAX_MINOR + 1)); equal(true, WishRules.validMinor(null));
        System.out.println("PASS: " + checks + " production money and optional-link assertions.");
    }
}
