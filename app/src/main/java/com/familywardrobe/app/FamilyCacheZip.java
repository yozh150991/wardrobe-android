package com.familywardrobe.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

/** Bounded, cancellable extraction into a fresh private directory, never into a live photo store. */
public final class FamilyCacheZip {
    private FamilyCacheZip() {}
    public static final long MAX_ARCHIVE = 128L * 1024 * 1024;
    public static final long MAX_EXPANDED = 256L * 1024 * 1024;
    public static final long MAX_PHOTO = 12L * 1024 * 1024;
    public static final int MAX_MANIFEST = 8 * 1024 * 1024;
    public static final int MAX_PHOTOS = 5000;
    public static final class Extracted {
        public final String manifest;
        public final Set<String> names;
        private Extracted(String manifest, Set<String> names) {
            this.manifest = manifest;
            this.names = Collections.unmodifiableSet(new HashSet<>(names));
        }
    }
    public static long copy(InputStream input, OutputStream output, long limit, Runnable checkpoint) throws IOException {
        if (limit < 0) throw new IOException("Invalid family limit");
        byte[] buffer = new byte[65536]; long total = 0;
        while (true) {
            checkpoint.run(); int n = input.read(buffer);
            if (n == -1) return total;
            if (n > limit - total) throw new IOException("Family byte limit exceeded");
            output.write(buffer, 0, n); total += n;
        }
    }
    public static String hash(File file, Runnable checkpoint) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[65536];
                while (true) { checkpoint.run(); int n = input.read(buffer); if (n == -1) break; digest.update(buffer, 0, n); }
            }
            StringBuilder text = new StringBuilder();
            for (byte b : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Extracted extract(File archive, long expectedSize, String expectedHash, File destination, Runnable checkpoint) throws IOException {
        checkpoint.run();
        if (expectedSize <= 0 || expectedSize > MAX_ARCHIVE || archive.length() != expectedSize ||
                !FamilyRules.validHash(expectedHash) || !hash(archive, checkpoint).equals(expectedHash))
            throw new IOException("Family archive checksum or size mismatch");
        if (destination.exists() || !destination.mkdirs()) throw new IOException("Family staging must be new");
        File photos = new File(destination, "photos");
        if (!photos.mkdir()) throw new IOException("Cannot create family staging");
        Set<String> names = new HashSet<>(); byte[] manifest = null; long expanded = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkpoint.run(); ZipEntry entry = entries.nextElement(); String name = entry.getName();
                boolean isManifest = name.equals("manifest.json");
                if (entry.isDirectory() || !names.add(name) || names.size() > MAX_PHOTOS + 1 ||
                        (!isManifest && !name.matches("photos/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)")))
                    throw new IOException("Unexpected family ZIP entry");
                long limit = Math.min(isManifest ? MAX_MANIFEST : MAX_PHOTO, MAX_EXPANDED - expanded);
                if (entry.getSize() <= 0 || entry.getSize() > limit) throw new IOException("Family ZIP size limit");
                CRC32 crc = new CRC32(); long count;
                try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(entry), crc)) {
                    if (isManifest) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        count = copy(input, output, limit, checkpoint); manifest = output.toByteArray();
                    } else {
                        // The strict flat filename grammar above excludes paths, traversal and aliases.
                        try (OutputStream output = new FileOutputStream(new File(photos, name.substring(7)))) {
                            count = copy(input, output, limit, checkpoint);
                        }
                    }
                }
                if (count != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Family ZIP CRC mismatch");
                expanded += count;
            }
        }
        checkpoint.run();
        if (manifest == null || names.size() - 1 > MAX_PHOTOS) throw new IOException("Family manifest missing");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(manifest)).toString();
        return new Extracted(text, names);
    }
}
