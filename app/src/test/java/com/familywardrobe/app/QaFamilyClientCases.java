package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * Phone side of family wardrobes: ownership scoping before export, downloaded-archive verification and
 * extraction into a private cache. Techniques: EP, BVA, EG (crafted archives, cycles, traversal, bombs).
 */
public final class QaFamilyClientCases {
    private QaFamilyClientCases() {}
    private static final String WARDROBE = "00000000-0000-4000-8000-000000000001";
    private static final String OTHER = "00000000-0000-4000-8000-000000000002";
    private static final String PHOTO = "00000000-0000-4000-8000-0000000000aa";

    public static void main(String[] args) throws Exception { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaFamilyClientCases");
        try {
            scope(qa);
            verifiedInput(qa);
            cacheZip(qa);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        qa.done();
    }

    private static WardrobeScope.Row row(String key, String parent, List<String> refs, List<String> photos) {
        return new WardrobeScope.Row(key, parent, refs, photos);
    }
    private static WardrobeScope.Row row(String key, String parent) { return row(key, parent, List.of(), List.of()); }

    /** A chain wardrobe -> r1 -> ... -> rN (rN at depth N). */
    private static List<WardrobeScope.Row> chain(int depth, boolean reversed) {
        List<WardrobeScope.Row> rows = new ArrayList<>();
        rows.add(row("wardrobe:" + WARDROBE, null));
        for (int d = 1; d <= depth; d++) rows.add(row("r" + d, d == 1 ? "wardrobe:" + WARDROBE : "r" + (d - 1)));
        if (reversed) Collections.reverse(rows);
        return rows;
    }

    private static void scope(QaCheck qa) {
        // EP: rows of the selected wardrobe and of another wardrobe.
        List<WardrobeScope.Row> rows = new ArrayList<>(List.of(
            row("wardrobe:" + WARDROBE, null), row("wardrobe:" + OTHER, null),
            row("garment:1", "wardrobe:" + WARDROBE, List.of(), List.of(PHOTO)),
            row("outfit:1", "wardrobe:" + WARDROBE, List.of("garment:1"), List.of()),
            row("garment:2", "wardrobe:" + OTHER)));
        WardrobeScope.Selection s = WardrobeScope.select(WARDROBE, rows);
        qa.equal(EP, "own rows only", Set.of("wardrobe:" + WARDROBE, "garment:1", "outfit:1"), s.keys);
        qa.equal(EP, "own photos only", Set.of(PHOTO), s.photos);
        // EG: a reference to another wardrobe, a missing row, a cycle, a duplicate, a bad photo id.
        List<WardrobeScope.Row> cross = new ArrayList<>(rows);
        cross.add(row("outfit:2", "wardrobe:" + WARDROBE, List.of("garment:2"), List.of()));
        qa.raises(EG, "cross-wardrobe reference", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE, cross));
        List<WardrobeScope.Row> missing = new ArrayList<>(rows);
        missing.add(row("outfit:3", "wardrobe:" + WARDROBE, List.of("garment:404"), List.of()));
        qa.raises(EG, "missing reference", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE, missing));
        qa.raises(EG, "parent cycle", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE,
            List.of(row("wardrobe:" + WARDROBE, null), row("a", "b"), row("b", "a"))));
        qa.raises(EG, "self parent", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE,
            List.of(row("wardrobe:" + WARDROBE, null), row("a", "a"))));
        qa.raises(EG, "duplicate key", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE,
            List.of(row("wardrobe:" + WARDROBE, null), row("a", "wardrobe:" + WARDROBE), row("a", "wardrobe:" + WARDROBE))));
        qa.raises(EG, "photo path traversal", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE,
            List.of(row("wardrobe:" + WARDROBE, null), row("g", "wardrobe:" + WARDROBE, List.of(), List.of("../" + PHOTO)))));
        qa.raises(EG, "fake root", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE,
            List.of(row("wardrobe:" + WARDROBE, null), row("garment:9", null))));
        qa.raises(EP, "missing root", IllegalArgumentException.class, () -> WardrobeScope.select(WARDROBE, List.of(row("wardrobe:" + OTHER, null))));
        qa.raises(EP, "invalid wardrobe id", IllegalArgumentException.class, () -> WardrobeScope.select("home", List.of()));
        // BVA: nesting depth 7 is accepted and 8 refused, in either row order (BUG-16 regression).
        for (boolean reversed : new boolean[] {false, true}) {
            qa.equal(BVA, "depth 7 order " + reversed, 8, WardrobeScope.select(WARDROBE, chain(WardrobeScope.MAX_DEPTH, reversed)).keys.size());
            qa.raises(BVA, "depth 8 order " + reversed, IllegalArgumentException.class,
                () -> WardrobeScope.select(WARDROBE, chain(WardrobeScope.MAX_DEPTH + 1, reversed)));
        }
    }

    private static final Runnable NOTHING = () -> {};

    private static void verifiedInput(QaCheck qa) throws IOException {
        byte[] data = "family archive bytes".getBytes(StandardCharsets.UTF_8);
        // BVA: maximum == size is fine, size - 1 fails.
        try (VerifiedArchiveInput in = new VerifiedArchiveInput(new ByteArrayInputStream(data), data.length, NOTHING)) {
            qa.equal(BVA, "size == limit", sha256(data), in.finish());
        }
        qa.raises(BVA, "size == limit + 1", IOException.class, () -> {
            try (VerifiedArchiveInput in = new VerifiedArchiveInput(new ByteArrayInputStream(data), data.length - 1, NOTHING)) { in.finish(); }
        });
        qa.raises(BVA, "zero limit", IllegalArgumentException.class, () -> new VerifiedArchiveInput(new ByteArrayInputStream(data), 0, NOTHING));
        // EP: the fingerprint covers every byte however it was read (single bytes, skip, bulk, tail).
        try (VerifiedArchiveInput in = new VerifiedArchiveInput(new ByteArrayInputStream(data), 1000, NOTHING)) {
            in.read(); in.skip(5); in.read(new byte[3], 0, 3);
            qa.equal(EP, "mixed reads hash the whole stream", sha256(data), in.finish());
            qa.equal(EP, "finish is idempotent", sha256(data), in.finish());
        }
        qa.equal(EG, "skip of nothing", 0L, new VerifiedArchiveInput(new ByteArrayInputStream(data), 1000, NOTHING).skip(-4));
        qa.isFalse(EG, "mark unsupported", new VerifiedArchiveInput(new ByteArrayInputStream(data), 1000, NOTHING).markSupported());
        qa.raises(EG, "no rewind", IOException.class, () -> new VerifiedArchiveInput(new ByteArrayInputStream(data), 1000, NOTHING).reset());
        // EG: cancellation is honoured on every read.
        qa.raises(EG, "cancelled read", IllegalStateException.class, () -> new VerifiedArchiveInput(new ByteArrayInputStream(data), 1000,
            () -> { throw new IllegalStateException("cancelled"); }).read());
    }

    private static String sha256(byte[] data) {
        try {
            StringBuilder s = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(data)) s.append(String.format(Locale.ROOT, "%02x", b & 255));
            return s.toString();
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static File zip(File dir, String name, Map<String, byte[]> entries) throws IOException {
        File file = new File(dir, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry();
            }
        }
        return file;
    }

    private static String extract(File archive, File destination) throws IOException {
        return FamilyCacheZip.extract(archive, archive.length(), FamilyCacheZip.hash(archive, NOTHING), destination, NOTHING).manifest;
    }

    private static void cacheZip(QaCheck qa) throws IOException {
        File dir = Files.createTempDirectory("qa-family").toFile();
        byte[] manifest = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> good = new LinkedHashMap<>();
        good.put("manifest.json", manifest);
        good.put("photos/" + PHOTO + ".png", new byte[] {1, 2, 3});
        File archive = zip(dir, "good.zip", good);
        // EP: the valid archive.
        qa.equal(EP, "manifest text", "{\"ok\":true}", extract(archive, new File(dir, "out1")));
        qa.isTrue(EP, "photo extracted flat", new File(dir, "out1/photos/" + PHOTO + ".png").isFile());
        // EG: crafted archives.
        Map<String, Map<String, byte[]>> bad = new LinkedHashMap<>();
        bad.put("traversal", Map.of("manifest.json", manifest, "photos/../../evil.png", new byte[] {1}));
        bad.put("nested path", Map.of("manifest.json", manifest, "photos/a/" + PHOTO + ".png", new byte[] {1}));
        bad.put("upper-case id", Map.of("manifest.json", manifest, "photos/" + PHOTO.toUpperCase(Locale.ROOT) + ".png", new byte[] {1}));
        bad.put("other extension", Map.of("manifest.json", manifest, "photos/" + PHOTO + ".gif", new byte[] {1}));
        bad.put("extra file", Map.of("manifest.json", manifest, "readme.txt", new byte[] {1}));
        bad.put("no manifest", Map.of("photos/" + PHOTO + ".png", new byte[] {1}));
        bad.put("empty photo", Map.of("manifest.json", manifest, "photos/" + PHOTO + ".png", new byte[0]));
        bad.put("non-UTF-8 manifest", Map.of("manifest.json", new byte[] {(byte) 0xC3, (byte) 0x28}));
        int n = 0;
        for (Map.Entry<String, Map<String, byte[]>> e : bad.entrySet()) {
            File crafted = zip(dir, "bad" + (++n) + ".zip", e.getValue());
            File out = new File(dir, "bad-out" + n);
            qa.raises(EG, e.getKey(), IOException.class, () -> extract(crafted, out));
        }
        // EG: checksum, size and staging-directory preconditions.
        String hash = FamilyCacheZip.hash(archive, NOTHING);
        qa.raises(EG, "wrong checksum", IOException.class, () -> FamilyCacheZip.extract(archive, archive.length(), "0".repeat(64), new File(dir, "x1"), NOTHING));
        qa.raises(BVA, "declared size + 1", IOException.class, () -> FamilyCacheZip.extract(archive, archive.length() + 1, hash, new File(dir, "x2"), NOTHING));
        qa.raises(BVA, "declared size 0", IOException.class, () -> FamilyCacheZip.extract(archive, 0, hash, new File(dir, "x3"), NOTHING));
        qa.raises(BVA, "declared size over 128 MiB", IOException.class, () -> FamilyCacheZip.extract(archive, FamilyCacheZip.MAX_ARCHIVE + 1, hash, new File(dir, "x4"), NOTHING));
        File existing = new File(dir, "existing"); existing.mkdirs();
        qa.raises(EG, "staging directory must be new", IOException.class, () -> FamilyCacheZip.extract(archive, archive.length(), hash, existing, NOTHING));
        qa.raises(EG, "cancelled extraction", IllegalStateException.class, () -> FamilyCacheZip.extract(archive, archive.length(), hash,
            new File(dir, "x5"), () -> { throw new IllegalStateException("cancelled"); }));
        // BVA: copy limit.
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        qa.equal(BVA, "copy exactly the limit", 4L, FamilyCacheZip.copy(new ByteArrayInputStream(new byte[4]), sink, 4, NOTHING));
        qa.raises(BVA, "copy limit + 1", IOException.class, () -> FamilyCacheZip.copy(new ByteArrayInputStream(new byte[5]), new ByteArrayOutputStream(), 4, NOTHING));
        qa.raises(BVA, "negative limit", IOException.class, () -> FamilyCacheZip.copy(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), -1, NOTHING));
        // BVA: a photo of exactly 12 MiB is accepted, one byte more is refused.
        Map<String, byte[]> big = new LinkedHashMap<>();
        big.put("manifest.json", manifest);
        big.put("photos/" + PHOTO + ".jpg", new byte[(int) FamilyCacheZip.MAX_PHOTO]);
        File atLimit = zip(dir, "photo-limit.zip", big);
        qa.equal(BVA, "12 MiB photo", "{\"ok\":true}", extract(atLimit, new File(dir, "big1")));
        big.put("photos/" + PHOTO + ".jpg", new byte[(int) FamilyCacheZip.MAX_PHOTO + 1]);
        File overLimit = zip(dir, "photo-over.zip", big);
        qa.raises(BVA, "12 MiB + 1 photo", IOException.class, () -> extract(overLimit, new File(dir, "big2")));
        deleteTree(dir);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
