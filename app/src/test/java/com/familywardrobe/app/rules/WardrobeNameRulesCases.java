package com.familywardrobe.app.rules;

import java.util.List;

/** QA-61-06: duplicate wardrobe names (boundary values and equivalence classes). */
public final class WardrobeNameRulesCases {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) {
        List<String> others = List.of("Мій гардероб", "test", "Дача  літо");
        check(WardrobeNameRules.taken(others, "test"), "the same name");
        check(WardrobeNameRules.taken(others, " TEST "), "case and outer spaces do not make a new name");
        check(WardrobeNameRules.taken(others, "мій ГАРДЕРОБ"), "Cyrillic case");
        check(WardrobeNameRules.taken(others, "Дача літо"), "inner runs of spaces");
        check(!WardrobeNameRules.taken(others, "test2"), "a different name");
        check(!WardrobeNameRules.taken(others, "   "), "a blank name is refused elsewhere, not reported as taken");
        check(!WardrobeNameRules.taken(List.of(), "test"), "no other wardrobes");
        check(WardrobeNameRules.key(null).isEmpty(), "null is empty");
        System.out.println("PASS wardrobe name rules: " + checks + " checks");
    }
}
