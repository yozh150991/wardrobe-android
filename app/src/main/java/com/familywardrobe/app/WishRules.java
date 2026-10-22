package com.familywardrobe.app;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;

/** Shared by native forms, persistence and executable host tests. No floating point money. */
public final class WishRules {
    private WishRules() {}
    public static final long MAX_MINOR = 99999999999L;
    public static final Set<String> CURRENCIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("PLN", "EUR", "USD", "UAH", "GBP")));
    public static Long money(String input) {
        String value = input.trim().replace(" ", "").replace("\u00a0", "").replace("\u202f", "");
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{1,9}([.,][0-9]{1,2})?")) throw new IllegalArgumentException("Invalid amount");
        long minor = new BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact();
        if (minor > MAX_MINOR) throw new IllegalArgumentException("Amount too large");
        return minor;
    }
    /**
     * The same link with an internationalised host (магазин.укр, café.fr) in its ASCII (punycode) form, so
     * java.net.URI can parse the host (QA-07). Everything else is returned unchanged.
     */
    public static String asciiHost(String text) {
        String value = text.trim();
        int scheme = value.indexOf("://");
        if (scheme < 0) return value;
        int start = scheme + 3, end = start;
        while (end < value.length() && "/?#".indexOf(value.charAt(end)) < 0) end++;
        String authority = value.substring(start, end);
        if (authority.chars().allMatch(c -> c < 128)) return value;
        int at = authority.lastIndexOf('@');
        String hostPort = authority.substring(at + 1), host = hostPort, port = "";
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && hostPort.substring(colon + 1).chars().allMatch(Character::isDigit)) {
            host = hostPort.substring(0, colon); port = hostPort.substring(colon);
        }
        try {
            host = java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException invalid) {
            return value;
        }
        return value.substring(0, start) + authority.substring(0, at + 1) + host + port + value.substring(end);
    }
    public static boolean validLink(String text) {
        if (text.trim().isEmpty()) return true;
        if (text.length() > 2000) return false;
        try {
            URI uri = new URI(asciiHost(text));
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }
    public static boolean validMinor(Long value) { return value == null || (value >= 0 && value <= MAX_MINOR); }
}
