package com.familywardrobe.app.rules;

/** UX-15: delete only what nothing was built on. Run by tools/check_ux_review.py. */
public final class ItemRemovalRulesCases {
    private static int checks = 0;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Removal check " + checks + ": " + what); }
    private static boolean refused(Runnable r) { try { r.run(); return false; } catch (IllegalArgumentException e) { return true; } }

    public static void main(String[] args) {
        check(ItemRemovalRules.blocker(0, 0, false) == null, "never worn, in no outfit, not bought from a wish: can be deleted");
        check(ItemRemovalRules.blocker(1, 0, false) == ItemRemovalRules.Blocker.WORN, "one day of wear keeps it");
        check(ItemRemovalRules.blocker(0, 1, false) == ItemRemovalRules.Blocker.OUTFITS, "one saved outfit keeps it");
        check(ItemRemovalRules.blocker(0, 0, true) == ItemRemovalRules.Blocker.PURCHASE, "a purchase record keeps it");
        check(ItemRemovalRules.blocker(3, 2, true) == ItemRemovalRules.Blocker.WORN && ItemRemovalRules.blocker(0, 2, true) == ItemRemovalRules.Blocker.OUTFITS,
            "wear is named first, then outfits, then the purchase");
        for (int wears = 0; wears <= 3; wears++) for (int outfits = 0; outfits <= 3; outfits++) for (boolean bought : new boolean[] { false, true })
            check((ItemRemovalRules.blocker(wears, outfits, bought) == null) == (wears == 0 && outfits == 0 && !bought), "deleted only when nothing refers to it");
        check(refused(() -> ItemRemovalRules.blocker(-1, 0, false)) && refused(() -> ItemRemovalRules.blocker(0, -1, false)), "negative counts are refused");
        System.out.println("PASS: " + checks + " item removal assertions.");
    }
}
