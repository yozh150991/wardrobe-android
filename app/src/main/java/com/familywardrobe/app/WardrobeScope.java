package com.familywardrobe.app;

import java.util.*;

/** Resolves ownership through parent links; every reference must stay inside its wardrobe. */
public final class WardrobeScope {
    private WardrobeScope() {}

    public static final class Row {
        public final String key, parent;
        public final List<String> references, photos;
        public Row(String key, String parent, Collection<String> references, Collection<String> photos) {
            if (key == null || key.isEmpty()) throw new IllegalArgumentException("Missing row key");
            this.key = key; this.parent = parent;
            this.references = Collections.unmodifiableList(new ArrayList<>(references));
            this.photos = Collections.unmodifiableList(new ArrayList<>(photos));
        }
    }
    public static final class Selection {
        public final Set<String> keys, photos;
        private Selection(Set<String> keys, Set<String> photos) {
            this.keys = Collections.unmodifiableSet(keys);
            this.photos = Collections.unmodifiableSet(photos);
        }
    }

    public static Selection select(String wardrobeId, Collection<Row> rows) {
        if (!FamilyRules.validId(wardrobeId)) throw new IllegalArgumentException("Invalid wardrobe");
        Map<String, Row> index = new LinkedHashMap<>();
        for (Row row : rows) {
            if (index.put(row.key, row) != null) throw new IllegalArgumentException("Duplicate row");
        }
        String root = "wardrobe:" + wardrobeId;
        if (!index.containsKey(root) || index.get(root).parent != null) throw new IllegalArgumentException("Missing wardrobe root");
        Map<String, String> owners = new HashMap<>();
        Map<String, Integer> depths = new HashMap<>();
        for (String key : index.keySet()) owner(key, index, owners, depths, new HashSet<>());
        Set<String> selected = new LinkedHashSet<>(), photos = new LinkedHashSet<>();
        for (Row row : index.values()) {
            String owner = owners.get(row.key);
            for (String ref : row.references) {
                if (!owner.equals(owners.get(ref))) throw new IllegalArgumentException("Cross-wardrobe or missing reference");
            }
            for (String photo : row.photos) {
                if (!FamilyRules.validId(photo)) throw new IllegalArgumentException("Invalid photo");
            }
            if (root.equals(owner)) { selected.add(row.key); photos.addAll(row.photos); }
        }
        return new Selection(selected, photos);
    }

    /** Rows nested deeper than this below the wardrobe root are refused, whatever the row order (BUG-16). */
    static final int MAX_DEPTH = 7;

    private static String owner(String key, Map<String, Row> index, Map<String, String> owners,
                                Map<String, Integer> depths, Set<String> visiting) {
        if (owners.containsKey(key)) return owners.get(key);
        Row row = index.get(key);
        // A chain of more than MAX_DEPTH + 1 unresolved rows already contains a row that is too deep,
        // so this bound keeps recursion short without making the verdict depend on row order.
        if (row == null || !visiting.add(key) || visiting.size() > MAX_DEPTH + 1)
            throw new IllegalArgumentException("Missing or cyclic owner");
        String result; int depth;
        if (row.parent == null) {
            if (!key.startsWith("wardrobe:") || !FamilyRules.validId(key.substring(9)))
                throw new IllegalArgumentException("Invalid ownership root");
            result = key; depth = 0;
        } else {
            result = owner(row.parent, index, owners, depths, visiting);
            // The depth is measured from the root, not from the first already-resolved ancestor.
            depth = depths.get(row.parent) + 1;
        }
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Missing or cyclic owner");
        visiting.remove(key); owners.put(key, result); depths.put(key, depth); return result;
    }
}
