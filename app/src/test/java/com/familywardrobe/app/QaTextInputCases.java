package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;
import static com.familywardrobe.app.QaCheck.expect;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Text entry rules shared by every form: grapheme-safe limits, visible names, money, links, e-mail,
 * passwords, custom labels and look names. Techniques: EP, BVA, EG (+ open bugs QA-02, QA-07, QA-10, QA-11, QA-12).
 */
public final class QaTextInputCases {
    private QaTextInputCases() {}
    private static final String EMOJI = "😀";             // 😀 one code point, two UTF-16 units
    private static final String FLAG_UA = "🇺🇦"; // 🇺🇦 two regional indicators, four units
    private static final String FAMILY = "👨‍👩‍👧"; // ZWJ sequence, eight units

    public static void main(String[] args) { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaTextInputCases");
        textLimits(qa);
        visibleNames(qa);
        money(qa);
        links(qa);
        email(qa);
        passwordsAndAccounts(qa);
        customLabels(qa);
        lookNames(qa);
        qa.done();
    }

    private static void textLimits(QaCheck qa) {
        // EP: every class of input keeps whole graphemes.
        qa.equal(EP, "null is empty", "", TextLimits.take(null, 10));
        qa.equal(EP, "ASCII under limit unchanged", "Shirt", TextLimits.take("Shirt", 10));
        qa.equal(EP, "Cyrillic counts one unit per letter", "Сорочка", TextLimits.take("Сорочка біла", 7));
        qa.equal(EP, "combining accent stays with its letter", "Café", TextLimits.take("Café noir", 5));
        qa.equal(EP, "CRLF is one grapheme", "a", TextLimits.take("a\r\nb", 2));
        // BVA: limit -1, 0, 1 and exactly at / one past the text length.
        qa.equal(BVA, "negative limit", "", TextLimits.take("abc", -1));
        qa.equal(BVA, "zero limit", "", TextLimits.take("abc", 0));
        qa.equal(BVA, "limit 1", "a", TextLimits.take("abc", 1));
        qa.equal(BVA, "limit == length", "abc", TextLimits.take("abc", 3));
        qa.equal(BVA, "limit == length - 1", "ab", TextLimits.take("abc", 2));
        // BVA on multi-unit graphemes: one unit short of a whole cluster drops the cluster.
        qa.equal(BVA, "surrogate pair needs 2 units", "a", TextLimits.take("a" + EMOJI, 2));
        qa.equal(BVA, "surrogate pair fits at 3", "a" + EMOJI, TextLimits.take("a" + EMOJI, 3));
        qa.equal(BVA, "flag needs 4 units", "x", TextLimits.take("x" + FLAG_UA, 4));
        qa.equal(BVA, "flag fits at 5", "x" + FLAG_UA, TextLimits.take("x" + FLAG_UA, 5));
        qa.equal(BVA, "ZWJ family 7 of 8 units", "", TextLimits.take(FAMILY, 7));
        qa.equal(BVA, "ZWJ family 8 units", FAMILY, TextLimits.take(FAMILY, 8));
        qa.equal(BVA, "three flags cut after the second pair", FLAG_UA + FLAG_UA, TextLimits.take(FLAG_UA + FLAG_UA + FLAG_UA, 11));
        // Every prefix produced by take() is valid UTF-16 (no lone surrogate), for every limit.
        String mixed = "Ж" + EMOJI + FLAG_UA + "é" + FAMILY + "❤️" + "z";
        for (int limit = 0; limit <= mixed.length() + 1; limit++) {
            String cut = TextLimits.take(mixed, limit);
            qa.isTrue(BVA, "prefix within limit " + limit, cut.length() <= Math.max(0, limit) && mixed.startsWith(cut));
            qa.isFalse(EG, "no lone surrogate at " + limit, !cut.isEmpty() && Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
        }
        // EG: pasted bidi controls are removed before the limit is applied.
        qa.equal(EG, "input strips RLO", "abc", TextLimits.input("a‮b⁦c", 10));
        qa.equal(EG, "input strips then limits", "ab", TextLimits.input("‮‮ab", 2));
        qa.equal(EG, "takeTrimmed never ends with a space", "a b", TextLimits.takeTrimmed("  a b  c", 4));
        qa.equal(EG, "takeTrimmed of null", "", TextLimits.takeTrimmed(null, 5));
    }

    private static void visibleNames(QaCheck qa) {
        // EP valid classes.
        for (String name : Arrays.asList("Shirt", "Сукня", "7", EMOJI, FAMILY, FLAG_UA, "é", " a ", "あ"))
            qa.isTrue(EP, "visible: " + name, TextLimits.hasVisibleText(name));
        // EP invalid classes: empty, whitespace, format, marks, selectors, bidi (even next to letters).
        for (String name : Arrays.asList(null, "", " ", "\t\n", " ", "　", "​", "‍", "́", "️",
                "⃝", " ", "Bad‮name", "⁦x⁩"))
            qa.isFalse(EP, "invisible or bidi: " + (name == null ? "null" : codes(name)), TextLimits.hasVisibleText(name));
    }

    private static void money(QaCheck qa) {
        // EP valid classes: integer, comma/dot decimals, grouped thousands (space, NBSP, narrow NBSP).
        qa.equal(EP, "integer", 22900L, WishRules.money("229"));
        qa.equal(EP, "comma decimal", 35050L, WishRules.money("350,50"));
        qa.equal(EP, "dot decimal one digit", 35050L, WishRules.money("350.5"));
        qa.equal(EP, "grouped with space", 123456L, WishRules.money("1 234,56"));
        qa.equal(EP, "grouped with NBSP", 123456L, WishRules.money("1 234,56"));
        qa.equal(EP, "surrounding spaces", 100L, WishRules.money("  1  "));
        qa.equal(EP, "blank is no price", null, WishRules.money("   "));
        // EP invalid classes.
        for (String bad : Arrays.asList("-1", "+5", "1e2", "0x10", "1,2,3", "1.2.3", "12 zł", "€12", "١٢", "１２", "NaN", "∞", "1..2", "1,,2"))
            qa.raises(EP, "invalid amount " + bad, IllegalArgumentException.class, () -> WishRules.money(bad));
        // BVA around 0, two decimals and nine integer digits.
        qa.equal(BVA, "minimum 0", 0L, WishRules.money("0"));
        qa.equal(BVA, "smallest positive", 1L, WishRules.money("0,01"));
        qa.equal(BVA, "maximum", WishRules.MAX_MINOR, WishRules.money("999999999,99"));
        qa.raises(BVA, "ten integer digits", IllegalArgumentException.class, () -> WishRules.money("1000000000"));
        qa.raises(BVA, "three decimals", IllegalArgumentException.class, () -> WishRules.money("1,001"));
        qa.raises(BVA, "leading separator", IllegalArgumentException.class, () -> WishRules.money(",5"));
        qa.raises(BVA, "trailing separator", IllegalArgumentException.class, () -> WishRules.money("5,"));
        qa.isTrue(BVA, "validMinor null", WishRules.validMinor(null));
        qa.isTrue(BVA, "validMinor 0", WishRules.validMinor(0L));
        qa.isTrue(BVA, "validMinor max", WishRules.validMinor(WishRules.MAX_MINOR));
        qa.isFalse(BVA, "validMinor -1", WishRules.validMinor(-1L));
        qa.isFalse(BVA, "validMinor max+1", WishRules.validMinor(WishRules.MAX_MINOR + 1));
        // EG: leading zeros are accepted and normalised, not read as octal.
        qa.equal(EG, "leading zeros", 750L, WishRules.money("0007,5"));
        qa.equal(EG, "tab is not a group separator", "invalid", invalidMoney("1\t234"));
    }

    private static String invalidMoney(String value) {
        try { WishRules.money(value); return "valid"; } catch (IllegalArgumentException e) { return "invalid"; }
    }

    private static void links(QaCheck qa) {
        // EP valid / invalid link classes.
        for (String ok : Arrays.asList("", "   ", "https://shop.pl", "http://shop.pl/a?b=1#c", "HTTPS://Shop.PL/x",
                "https://shop.pl:8443/x", " https://shop.pl/trimmed "))
            qa.isTrue(EP, "valid link " + ok, WishRules.validLink(ok));
        for (String bad : Arrays.asList("shop.pl", "ftp://shop.pl/a", "mailto:a@b.pl", "javascript:alert(1)", "data:text/html,x",
                "https://", "https://user@shop.pl", "https://u:p@shop.pl", "https://sh op.pl", "file:///etc/passwd"))
            qa.isFalse(EP, "invalid link " + bad, WishRules.validLink(bad));
        // BVA: 2000 characters is the documented maximum (server LIMITS link=2000).
        String prefix = "https://shop.pl/";
        String at2000 = prefix + "a".repeat(2000 - prefix.length());
        qa.equal(BVA, "boundary string length", 2000, at2000.length());
        qa.isTrue(BVA, "link of 2000 characters", WishRules.validLink(at2000));
        qa.isFalse(BVA, "link of 2001 characters", WishRules.validLink(at2000 + "a"));
        // QA-07: an internationalised shop domain (.укр, .рф, café.fr) is a valid web link, and the same
        // app already accepts .укр e-mail addresses (BUG-08). java.net.URI returns a null host for it.
        qa.knownBug(EP, "QA-07", "IDN shop links rejected", () -> {
            expect(WishRules.validLink("https://магазин.укр/сукня"), "validLink rejects an IDN host");
            expect(!ShopImportRules.webUrl("https://магазин.укр/сукня").isEmpty(), "webUrl rejects an IDN host");
            expect(!ShopImportRules.urls("Глянь https://магазин.укр/сукня").isEmpty(), "shared IDN link is dropped");
        });
    }

    private static void email(QaCheck qa) {
        // EP valid classes with normalisation.
        qa.equal(EP, "lower-cased", "anna@example.com", AuthRules.serverEmail("Anna@Example.COM"));
        qa.equal(EP, "trimmed", "anna@example.com", AuthRules.serverEmail("  anna@example.com \n"));
        qa.equal(EP, "plus tag kept", "anna+shop@example.com", AuthRules.serverEmail("anna+shop@example.com"));
        qa.equal(EP, "IDN domain to punycode", AuthRules.serverEmail("ivan@xn--80a1acn3a.xn--j1amh"), AuthRules.serverEmail("ivan@Пошта.УКР"));
        qa.isTrue(EP, "IDN form accepted", AuthRules.validEmail("ivan@пошта.укр"));
        // EP invalid classes.
        for (String bad : Arrays.asList(null, "", "anna", "@example.com", "anna@", "an na@example.com", "анна@example.com",
                "anna@localhost", "anna@example.1", "anna@@example.com", "anna@exa mple.com", "anna@-example.com"))
            qa.isFalse(EP, "invalid e-mail " + bad, AuthRules.validEmail(bad));
        // BVA: total length 254/255 and TLD length 2/1 and 63/64.
        String domain = "@x.com";
        qa.isTrue(BVA, "254 characters", AuthRules.validEmail("a".repeat(254 - domain.length()) + domain));
        qa.isFalse(BVA, "255 characters", AuthRules.validEmail("a".repeat(255 - domain.length()) + domain));
        qa.isTrue(BVA, "TLD of 2 letters", AuthRules.validEmail("a@x.pl"));
        qa.isFalse(BVA, "TLD of 1 letter", AuthRules.validEmail("a@x.p"));
        qa.isTrue(BVA, "TLD of 63 letters", AuthRules.validEmail("a@x." + "p".repeat(63)));
        qa.isFalse(BVA, "TLD of 64 letters", AuthRules.validEmail("a@x." + "p".repeat(64)));
        // QA-11: addresses no mail system accepts pass the shared client/server rule, so invitations can
        // be sent to unreachable mailboxes (RFC 5321/1035: dot rules, label hyphens, local part <= 64).
        qa.knownBug(EG, "QA-11", "RFC-invalid e-mail addresses accepted", () -> {
            for (String bad : Arrays.asList("a..b@example.com", ".a@example.com", "a.@example.com", "a@x.-y.com",
                    "a".repeat(65) + "@example.com"))
                expect(!AuthRules.validEmail(bad), "accepted " + bad);
        });
    }

    private static void passwordsAndAccounts(QaCheck qa) {
        // BVA 12..4096.
        qa.isFalse(BVA, "11 characters", AuthRules.validNewPassword("a".repeat(11)));
        qa.isTrue(BVA, "12 characters", AuthRules.validNewPassword("a".repeat(12)));
        qa.isTrue(BVA, "4096 characters", AuthRules.validNewPassword("a".repeat(4096)));
        qa.isFalse(BVA, "4097 characters", AuthRules.validNewPassword("a".repeat(4097)));
        qa.isFalse(EP, "null password", AuthRules.validNewPassword(null));
        qa.isTrue(EP, "spaces are allowed", AuthRules.validNewPassword("correct horse battery"));
        // QA-12: the hint promises "12 to 4096 characters", but length() counts UTF-16 units: six emoji pass.
        qa.knownBug(BVA, "QA-12", "password length counts UTF-16 units", () ->
            expect(!AuthRules.validNewPassword(EMOJI.repeat(6)), "6 visible characters accepted as 12"));
        // Account keys: deterministic, path-safe, unambiguous.
        String key = AuthRules.accountKey("project", "uid");
        qa.isTrue(EP, "account key is 64 hex", AuthRules.validAccountKey(key));
        qa.equal(EP, "account key deterministic", key, AuthRules.accountKey("project", "uid"));
        qa.isFalse(EG, "separator ambiguity", AuthRules.accountKey("ab", "c").equals(AuthRules.accountKey("a", "bc")));
        qa.isFalse(EG, "colon ambiguity", AuthRules.accountKey("a:1", "b").equals(AuthRules.accountKey("a", "1:b")));
        qa.raises(EP, "empty uid", IllegalArgumentException.class, () -> AuthRules.accountKey("project", ""));
        qa.raises(EP, "null project", IllegalArgumentException.class, () -> AuthRules.accountKey(null, "uid"));
        qa.isFalse(BVA, "63 hex", AuthRules.validAccountKey(key.substring(1)));
        qa.isFalse(EP, "upper-case hex", AuthRules.validAccountKey(key.toUpperCase(java.util.Locale.ROOT)));
        qa.isFalse(EG, "path traversal key", AuthRules.validAccountKey("../" + key.substring(3)));
    }

    private static void customLabels(QaCheck qa) {
        // EP: normalisation classes.
        qa.equal(EP, "NFKC full-width", "AB", CategoryRules.clean("ＡＢ"));
        qa.equal(EP, "collapse inner spaces and NBSP", "Ink blue", CategoryRules.clean("  Ink   blue "));
        qa.equal(EP, "key lower-case", "ink blue", CategoryRules.key("INK Blue"));
        qa.equal(EP, "key unifies apostrophes", CategoryRules.key("м'ята"), CategoryRules.key("м’ята"));
        qa.equal(EP, "key unifies modifier apostrophe", CategoryRules.key("м'ята"), CategoryRules.key("мʼята"));
        // BVA 60 characters after cleaning.
        qa.isTrue(BVA, "60 characters", CategoryRules.validName("a".repeat(60)));
        qa.isFalse(BVA, "61 characters", CategoryRules.validName("a".repeat(61)));
        qa.isTrue(BVA, "60 after collapsing", CategoryRules.validName("  " + "a".repeat(60) + "   "));
        qa.isTrue(BVA, "1 character", CategoryRules.validName("a"));
        qa.isFalse(BVA, "0 characters", CategoryRules.validName("   "));
        // EP invalid classes.
        for (String bad : Arrays.asList("a\u0000b", "a\u0007", "​", "a‮b", "⁦x⁩", "a‍b"))
            qa.isFalse(EP, "invalid label " + codes(bad), CategoryRules.validName(bad));
        // QA-02: invisible-only names (combining marks, variation selectors) pass on the phone but the family
        // server refuses them (schema.visible_name), which aborts the whole shared-wardrobe transfer.
        qa.knownBug(EP, "QA-02", "custom label of invisible marks accepted", () -> {
            for (String invisible : Arrays.asList("́", "️", "́́", "⃝"))
                expect(!CategoryRules.validName(invisible), "accepted " + codes(invisible));
        });
        // Search: tokens, case and apostrophes.
        qa.isTrue(EP, "empty query matches", CategoryRules.matches("", "anything"));
        qa.isTrue(EP, "token match across fields", CategoryRules.matches("blue shirt", "Shirt", "Blue"));
        qa.isFalse(EP, "missing token", CategoryRules.matches("red shirt", "Shirt", "Blue"));
        qa.isTrue(EG, "apostrophe variants match", CategoryRules.matches("м’ята", "М'ята"));
        qa.isTrue(EG, "subcategory filter ignores case and spaces", CategoryRules.subcategoryMatches(" Polo ", "polo"));
    }

    private static void lookNames(QaCheck qa) {
        List<String> none = Collections.emptyList();
        qa.equal(EP, "new name trimmed", "Office", LookNameRules.unique("  Office  ", none));
        qa.equal(EP, "duplicate gets suffix", "Office (2)", LookNameRules.unique("Office", Arrays.asList("office")));
        qa.equal(EP, "next free suffix", "Office (3)", LookNameRules.unique("Office", Arrays.asList("Office", "OFFICE (2)")));
        qa.raises(EP, "blank name", IllegalArgumentException.class, () -> LookNameRules.unique("   ", none));
        String n80 = "a".repeat(80);
        qa.equal(BVA, "80 characters kept", n80, LookNameRules.unique(n80, none));
        qa.equal(BVA, "81 characters cut to 80", n80, LookNameRules.unique(n80 + "b", none));
        String suffixed = LookNameRules.unique(n80, Arrays.asList(n80));
        qa.equal(BVA, "suffix keeps the 80 limit", 80, suffixed.length());
        qa.isTrue(BVA, "suffix appended", suffixed.endsWith(" (2)"));
        String emojiAtEdge = "a".repeat(79) + EMOJI;
        qa.equal(EG, "half emoji never kept", "a".repeat(79), LookNameRules.unique(emojiAtEdge, none));
        qa.equal(EG, "name equal to an existing name with spaces", "Look (2)", LookNameRules.unique("Look", Arrays.asList("  Look ")));
        // QA-10: "Café" typed with a combining accent (iOS/macOS keyboards, pasted text) and "Café" with é are
        // the same visible name, but the duplicate check compares code points without normalisation.
        qa.knownBug(EG, "QA-10", "look names differ only by Unicode normalisation", () ->
            expect(!LookNameRules.unique("Café", Arrays.asList("Café")).equals("Café"), "visual duplicate accepted"));
    }

    static String codes(String text) {
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(c -> out.append(out.length() == 0 ? "" : " ").append(String.format("U+%04X", c)));
        return out.toString();
    }
}
