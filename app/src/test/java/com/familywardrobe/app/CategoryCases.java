package com.familywardrobe.app;

import java.util.Locale;

public final class CategoryCases {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(CategoryRules.clean("  Для\u00a0\u2003бігу\t ").equals("Для бігу"), "Unicode whitespace");
        check(CategoryRules.key("ДЛЯ БІГУ").equals(CategoryRules.key("Для бігу")), "Ukrainian case");
        check(CategoryRules.key("PŁASZCZE").equals("płaszcze"), "Polish case");
        check(CategoryRules.key("\uFF34\uFF4F\uFF50").equals("top"), "Compatibility normalization");
        check(CategoryRules.key("П’ЯТА").equals(CategoryRules.key("п'ята")), "Apostrophe variants");
        Locale before = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr")); check(CategoryRules.key("TRAINING").equals("training"), "Device locale independent"); }
        finally { Locale.setDefault(before); }
        for (String bad : new String[]{"", " \u00a0 ", "x".repeat(61), "hidden\u0000", "hidden\u200b"})
            check(!CategoryRules.validName(bad), "Invalid name");
        for (String good : new String[]{"Для бігу", "Płaszcze", "Rain gear", "x".repeat(60)})
            check(CategoryRules.validName(good), "Valid name");
        check(CategoryRules.matches("  БІГ   КРОС ", "Nike", "Кросівки", "Взуття", "Для бігу"), "Cross-field tokens");
        check(CategoryRules.matches("wodoodporne", "Kurtka", "Wodoodporne"), "Subcategory text");
        check(CategoryRules.matches("взут", "Nike", "Взуття"), "Localized base category");
        check(CategoryRules.matches("бігу", "Nike", "Для бігу"), "Custom category text");
        check(!CategoryRules.matches("бігу зимові", "Nike", "Для бігу", "Літні"), "All tokens required");
        check(CategoryRules.matches(" ", (String) null), "Empty query includes all");
        check(!CategoryRules.matches("shirt", (String) null), "Missing optional field");
        check(CategoryRules.categoryMatches("", "shoes", "custom-a"), "All categories");
        check(CategoryRules.categoryMatches("shoes", "shoes", "custom-a"), "Base includes custom children");
        check(CategoryRules.categoryMatches("custom-a", "shoes", "custom-a"), "Custom exact filter");
        check(!CategoryRules.categoryMatches("custom-a", "shoes", "custom-b"), "Other custom excluded");
        check(!CategoryRules.categoryMatches("custom-a", "shoes", null), "Unassigned excluded");
        check(CategoryRules.subcategoryMatches("", ""), "All subcategories");
        check(CategoryRules.subcategoryMatches(" ТРЕЙЛОВІ  КРОСІВКИ ", "Трейлові\u00a0кросівки"), "Normalized selection");
        check(!CategoryRules.subcategoryMatches("кросівки", "Трейлові кросівки"), "Facet exact, query partial");
        check(!CategoryRules.subcategoryMatches("Кросівки", ""), "Blank subcategory excluded");
        System.out.println("PASS: " + checks + " production category/search assertions.");
    }
}
