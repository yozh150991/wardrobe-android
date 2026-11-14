package com.familywardrobe.app.rules;

/** UX-11: an item without a typed name is called by its (sub)category and colour. Run by tools/check_ux_review.py. */
public final class ItemNameRulesCases {
    private static int checks = 0;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Name check " + checks + ": " + what); }

    public static void main(String[] args) {
        check(ItemNameRules.suggest("", "Верх", "Білий").equals("Верх, білий"), "category and colour");
        check(ItemNameRules.suggest("Сорочка", "Верх", "Білий").equals("Сорочка, білий"), "the subcategory is more exact than the category");
        check(ItemNameRules.suggest("  ", "Взуття", "").equals("Взуття") && ItemNameRules.suggest(null, "Взуття", null).equals("Взуття"), "no colour («Інший») — the category alone");
        check(ItemNameRules.suggest("", "", "Білий").isEmpty() && ItemNameRules.suggest(null, null, null).isEmpty(), "no category — no name (the category is required)");
        check(ItemNameRules.suggest(" Літня   сукня ", "Сукня", " Темно-Синій ").equals("Літня сукня, темно-синій"), "spaces are tidied, the colour is lower case");
        check(ItemNameRules.suggest("Koszula", "Góra", "BIAŁY").equals("Koszula, biały"), "other alphabets");
        String longName = ItemNameRules.suggest("x".repeat(200), "", "white");
        check(longName.length() == ItemNameRules.MAX && TextLimits.hasVisibleText(longName), "at most 120 characters");
        check(ItemNameRules.suggest("a".repeat(118), "", "white").length() <= ItemNameRules.MAX, "the colour never pushes it over the limit");

        check(ItemNameRules.effective("  Моя сорочка ", "Верх, білий").equals("Моя сорочка"), "a typed name wins");
        check(ItemNameRules.effective("", "Верх, білий").equals("Верх, білий") && ItemNameRules.effective("   ", "Верх").equals("Верх")
            && ItemNameRules.effective(null, "Верх").equals("Верх"), "a blank name takes the suggestion");
        check(ItemNameRules.effective("​", "Верх").equals("Верх"), "invisible characters are not a name");
        check(ItemNameRules.effective("", "").isEmpty() && ItemNameRules.effective("", null).isEmpty(), "nothing typed and nothing chosen — still no name");
        System.out.println("PASS: " + checks + " item name assertions (suggestion, tidy text, limit, typed name wins).");
    }
}
