package com.familywardrobe.app;

import java.text.Normalizer;
import java.util.Locale;

/** The same Unicode rules are used by category uniqueness, suggestions and search. */
public final class CategoryRules {
    private CategoryRules() {}

    public static String clean(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = out.length() > 0;
            } else {
                if (space) out.append(' ');
                space = false;
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    public static String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replace('\u2019', '\'')
            .replace('\u02bc', '\'').replace('\u2018', '\'');
    }

    /** A new custom label: what the family server accepts too, so it needs a visible character (QA-02). */
    public static boolean validName(String value) {
        return validStoredName(value) && TextLimits.hasVisibleText(clean(value));
    }

    /**
     * Restore/backup check for labels saved before QA-02. Not stricter than older releases, so an existing
     * backup never becomes unrestorable; new labels must pass {@link #validName}.
     */
    public static boolean validStoredName(String value) {
        String name = clean(value);
        return !name.isEmpty() && name.length() <= 60 &&
            name.codePoints().noneMatch(cp -> Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT);
    }

    public static boolean matches(String query, String... fields) {
        String q = key(query);
        if (q.isEmpty()) return true;
        StringBuilder haystack = new StringBuilder();
        for (String field : fields) haystack.append(key(field)).append(' ');
        for (String token : q.split(" ")) if (haystack.indexOf(token) < 0) return false;
        return true;
    }

    public static boolean categoryMatches(String filter, String base, String customId) {
        return filter == null || filter.isEmpty() || filter.equals(base) || filter.equals(customId);
    }

    public static boolean subcategoryMatches(String filter, String value) {
        return key(filter).isEmpty() || key(filter).equals(key(value));
    }
}
