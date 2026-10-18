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
        for (String key : index.keySet()) owner(key, index, owners, new HashSet<>());
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

    private static String owner(String key, Map<String, Row> index, Map<String, String> owners, Set<String> visiting) {
        if (owners.containsKey(key)) return owners.get(key);
        Row row = index.get(key);
        if (row == null || !visiting.add(key) || visiting.size() > 8)
            throw new IllegalArgumentException("Missing or cyclic owner");
        String result;
        if (row.parent == null) {
            if (!key.startsWith("wardrobe:") || !FamilyRules.validId(key.substring(9)))
                throw new IllegalArgumentException("Invalid ownership root");
            result = key;
        } else result = owner(row.parent, index, owners, visiting);
        visiting.remove(key); owners.put(key, result); return result;
    }
}
