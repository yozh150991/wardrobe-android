package com.familywardrobe.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

public final class RoadmapRules {
    public static boolean validDate(String date) {
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try { LocalDate d = LocalDate.parse(date); return d.getYear() >= 1900 && d.getYear() <= 2200; } catch (RuntimeException e) { return false; }
    }
    public static Long costPerWear(Long price, int wearDays) {
        if (price == null || wearDays == 0) return null;
        if (wearDays < 0 || !WishRules.validMinor(price)) throw new IllegalArgumentException();
        return BigDecimal.valueOf(price).divide(BigDecimal.valueOf(wearDays), 0, RoundingMode.HALF_UP).longValueExact();
    }
    public static String weatherMode(double feelsLike) {
        if (!Double.isFinite(feelsLike) || feelsLike < -90 || feelsLike > 65) throw new IllegalArgumentException();
        return feelsLike >= 22 ? "warm" : feelsLike < 8 ? "cold" : "cool";
    }
    public static List<String> uniquePackingItems(Collection<List<String>> outfits, Collection<String> manual) {
        Set<String> ids = new TreeSet<>(manual); for (List<String> outfit : outfits) ids.addAll(outfit); return new ArrayList<>(ids);
    }
    public static boolean period(String start, String end) {
        return start.isEmpty() && end.isEmpty() || validDate(start) && validDate(end) && start.compareTo(end) <= 0;
    }
}
