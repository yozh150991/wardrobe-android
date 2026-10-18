package com.familywardrobe.app;
import java.util.*;

/** Only ready IDs supplied from the current wardrobe's fresh queue may advance a review session. */
public final class ImportReviewRules {
    private ImportReviewRules() {}
    public static String next(List<String> order, Set<String> ready, Set<String> deferred, String current) {
        for (String id:order) if (!id.equals(current) && ready.contains(id) && !deferred.contains(id)) return id;
        return null;
    }
}
