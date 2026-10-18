package com.familywardrobe.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Shared production validation and stable, path-safe account identity. Never stores credentials. */
public final class AuthRules {
    private AuthRules() {}
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$");
    public static String email(String value) { return value == null ? "" : value.trim(); }
    public static boolean validEmail(String value) {
        String normalized = email(value);
        return normalized.length() <= 254 && EMAIL.matcher(normalized).matches();
    }
    public static boolean validNewPassword(String value) {
        return value != null && value.length() >= 12 && value.length() <= 4096;
    }
    public static String accountKey(String project, String uid) {
        if (project == null || project.isEmpty() || uid == null || uid.isEmpty())
            throw new IllegalArgumentException("An authenticated project and UID are required");
        try {
            // Length prefixes make the pair unambiguous even if the IDs contain separators.
            String identity = project.length() + ":" + project + uid.length() + ":" + uid;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean validAccountKey(String key) { return key != null && key.matches("[0-9a-f]{64}"); }
}
