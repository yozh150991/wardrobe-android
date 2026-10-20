package com.familywardrobe.app;

/**
 * Length limits stay in UTF-16 units (the same units the backend counts), but a cut never
 * lands inside a surrogate pair or a grapheme cluster (flags, ZWJ emoji, combining marks).
 * No Android dependencies: used by Compose fields, import, look names and host tests.
 */
public final class TextLimits {
    private TextLimits() {}

    /** The longest prefix of whole graphemes that fits in {@code maximum} UTF-16 units. */
    public static String take(String text, int maximum) {
        if (text == null) return "";
        if (maximum <= 0) return "";
        if (text.length() <= maximum) return text;
        int end = 0;
        for (int next = nextBoundary(text, 0); next <= maximum; next = nextBoundary(text, next)) {
            end = next;
            if (next >= text.length()) break;
        }
        return text.substring(0, end);
    }

    /**
     * End of the grapheme cluster that starts at {@code start}. A small, JDK-independent subset of
     * UAX #29 (java.text.BreakIterator only follows it from Java 20, CI runs 17): surrogate pairs,
     * combining marks, variation selectors, emoji modifiers and tags, ZWJ sequences and flag pairs.
     */
    static int nextBoundary(String text, int start) {
        int length = text.length();
        if (start >= length) return length;
        int first = text.codePointAt(start);
        int i = start + Character.charCount(first);
        if (first == '\r' && i < length && text.charAt(i) == '\n') return i + 1;
        boolean flag = regional(first);
        if (flag && i < length && regional(text.codePointAt(i))) i += Character.charCount(text.codePointAt(i));
        while (i < length) {
            int c = text.codePointAt(i);
            if (extend(c)) { i += Character.charCount(c); continue; }
            if (text.codePointBefore(i) == 0x200D && !Character.isWhitespace(c)) {  // ZWJ joins the next emoji
                i += Character.charCount(c); continue;
            }
            break;
        }
        return i;
    }

    private static boolean regional(int c) { return c >= 0x1F1E6 && c <= 0x1F1FF; }

    private static boolean extend(int c) {
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
            || c == 0x200D || (c >= 0xFE00 && c <= 0xFE0F) || (c >= 0xE0100 && c <= 0xE01EF)
            || (c >= 0x1F3FB && c <= 0x1F3FF) || (c >= 0xE0020 && c <= 0xE007F);
    }

    /** Bidi embedding, override and isolate controls: they let one name display as another (BUG-12). */
    public static boolean isBidiControl(int codePoint) {
        return (codePoint >= 0x202A && codePoint <= 0x202E) || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    public static String stripBidiControls(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i); i += Character.charCount(c);
            if (!isBidiControl(c)) out.appendCodePoint(c);
        }
        return out.toString();
    }

    /**
     * True when the text has no bidi controls and at least one visible character: a name made only
     * of spaces, format characters (ZWJ), combining marks or variation selectors is not a name.
     * Mirrors {@code schema.visible_name()} on the family server.
     */
    public static boolean hasVisibleText(String text) {
        if (text == null) return false;
        boolean visible = false;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i); i += Character.charCount(c);
            if (isBidiControl(c)) return false;
            int type = Character.getType(c);
            boolean invisible = Character.isWhitespace(c) || Character.isSpaceChar(c) || type == Character.CONTROL ||
                type == Character.FORMAT || type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK ||
                (c >= 0xFE00 && c <= 0xFE0F) || (c >= 0xE0100 && c <= 0xE01EF);
            if (!invisible) visible = true;
        }
        return visible;
    }

    /** What a text field keeps from typing or pasting: no bidi controls, whole graphemes, at most {@code maximum} units. */
    public static String input(String text, int maximum) {
        return take(stripBidiControls(text), maximum);
    }

    /** Truncate on a grapheme boundary and then trim, so a cut never leaves trailing spaces. */
    public static String takeTrimmed(String text, int maximum) {
        return take(text == null ? "" : text.trim(), maximum).trim();
    }
}
