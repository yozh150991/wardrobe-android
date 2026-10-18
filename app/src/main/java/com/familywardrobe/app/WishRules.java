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
    public static boolean validLink(String text) {
        if (text.trim().isEmpty()) return true;
        if (text.length() > 2000) return false;
        try {
            URI uri = new URI(text.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }
    public static boolean validMinor(Long value) { return value == null || (value >= 0 && value <= MAX_MINOR); }
}
