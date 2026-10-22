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
    private static final String ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    /**
     * RFC 5321 dot-atom local part (no leading, trailing or double dots) and RFC 1035 labels of at most
     * 63 characters that neither start nor end with a hyphen (QA-11). The server uses the same expression.
     */
    private static final Pattern EMAIL = Pattern.compile(
        ATOM + "(?:\\." + ATOM + ")*@(?:" + LABEL + "\\.)+(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})");
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
        return normalized.length() <= 254 && local.length() <= 64 && EMAIL.matcher(normalized).matches() ? normalized : "";
    }
    public static boolean validEmail(String value) {
        return !serverEmail(value).isEmpty();
    }
    /** "12 to 4096 characters": the minimum counts characters (code points), so six emoji are six (QA-12). */
    public static boolean validNewPassword(String value) {
        return value != null && value.codePointCount(0, value.length()) >= 12 && value.length() <= 4096;
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
