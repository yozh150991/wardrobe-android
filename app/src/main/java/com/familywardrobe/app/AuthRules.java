package com.familywardrobe.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Shared production validation and stable, path-safe account identity. Never stores credentials. */
public final class AuthRules {
    private AuthRules() {}
    /**
     * Mirrors {@code membership.email()} on the family server (BUG-13): ASCII local part, an
     * IDN domain is checked in its punycode form (BUG-08), TLD letters or {@code xn--}.
     */
    private static final Pattern EMAIL = Pattern.compile(
        "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})");
    public static String email(String value) { return value == null ? "" : value.trim(); }
    /** Lower-case address with the domain in ASCII (punycode), or "" when the server would refuse it. */
    public static String serverEmail(String value) {
        if (value == null || value.length() > 254) return "";
        String trimmed = email(value);
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) return "";
        String local = trimmed.substring(0, at), domain = trimmed.substring(at + 1);
        for (int i = 0; i < local.length(); i++) if (local.charAt(i) > 127) return "";
        boolean ascii = true;
        for (int i = 0; i < domain.length(); i++) if (domain.charAt(i) > 127) { ascii = false; break; }
        if (!ascii) {
            try { domain = java.net.IDN.toASCII(domain); } catch (IllegalArgumentException e) { return ""; }
        }
        String normalized = (local + "@" + domain).toLowerCase(java.util.Locale.ROOT);
        return normalized.length() <= 254 && EMAIL.matcher(normalized).matches() ? normalized : "";
    }
    public static boolean validEmail(String value) {
        return !serverEmail(value).isEmpty();
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
