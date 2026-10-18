package com.familywardrobe.app;

public final class ColorCases {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ColorRules.matchesFilter("", "red", "powder"), "All includes custom");
        check(ColorRules.matchesFilter("", "red", null), "All includes basic");
        check(ColorRules.matchesFilter("red", "red", "powder"), "Base includes shade");
        check(ColorRules.matchesFilter("red", "red", "cherry"), "Base includes second shade");
        check(ColorRules.matchesFilter("red", "red", null), "Base includes unassigned");
        check(ColorRules.matchesFilter("powder", "red", "powder"), "Exact custom selection");
        check(!ColorRules.matchesFilter("powder", "red", "cherry"), "Same base is not exact shade");
        check(!ColorRules.matchesFilter("powder", "red", null), "Unassigned excluded");
        check(!ColorRules.matchesFilter("blue", "red", "powder"), "Other base excluded");
        check(!ColorRules.matchesFilter("missing", "red", "powder"), "Unknown selection returns no results");
        check(CategoryRules.clean("  Пудровий\u00a0 рожевий  ").equals("Пудровий рожевий"), "Normalize colour name");
        check(CategoryRules.key("ПУДРОВИЙ РОЖЕВИЙ").equals(CategoryRules.key("Пудровий рожевий")), "Deduplicate case");
        check(CategoryRules.key("PUDROWY RÓŻ").equals(CategoryRules.key("Pudrowy róż")), "Polish case");
        check(CategoryRules.matches("пудр", "Пудровий рожевий"), "Partial colour search");
        check(CategoryRules.matches("пудр сорочка", "Сорочка", "Пудровий рожевий"), "Colour and item tokens");
        check(!CategoryRules.matches("пудр взуття", "Сорочка", "Пудровий рожевий"), "All tokens required");
        check(CategoryRules.matches("син", "Сорочка", "Синій"), "Localized basic colour search");
        for (String bad : new String[]{"", "\u00a0 ", "x".repeat(61), "x\u0000", "x\u200b"})
            check(!CategoryRules.validName(bad), "Invalid custom colour name");
        System.out.println("PASS: " + checks + " production colour filter/name/search assertions.");
    }
}
