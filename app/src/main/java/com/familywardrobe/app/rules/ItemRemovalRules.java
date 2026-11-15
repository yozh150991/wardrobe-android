package com.familywardrobe.app.rules;

/**
 * UX-15 (0.14.49), the user's decision of 14.11: an item may be deleted for good only when nothing was built on it —
 * it was never marked as worn and is in no saved outfit. Anything else goes to the archive, so the wear history, the
 * statistics and saved outfits never lose a piece. An item bought from the wish list keeps its purchase record, so it
 * is archived too.
 */
public final class ItemRemovalRules {
    private ItemRemovalRules() {}
    public enum Blocker { WORN, OUTFITS, PURCHASE }

    /** Why the item can not be deleted, or null when it can. Wear is named first: it is what people expect to keep. */
    public static Blocker blocker(int wears, int outfits, boolean bought) {
        if (wears < 0 || outfits < 0) throw new IllegalArgumentException();
        return wears > 0 ? Blocker.WORN : outfits > 0 ? Blocker.OUTFITS : bought ? Blocker.PURCHASE : null;
    }
}
