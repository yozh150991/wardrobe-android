package com.familywardrobe.app;

import static com.familywardrobe.app.QaCheck.Technique.*;
import static com.familywardrobe.app.QaCheck.expect;

import java.util.Arrays;
import java.util.List;

/** Android Share intake, duplicate hints and offline product metadata. EP, BVA, EG (+ QA-06, QA-08). */
public final class QaShopImportCases {
    private QaShopImportCases() {}
    private static final String PAGE = "https://shop.pl/p/1";

    public static void main(String[] args) { run(); }

    public static void run() {
        QaCheck qa = new QaCheck("QaShopImportCases");
        sharedText(qa);
        duplicateKeys(qa);
        metadataClasses(qa);
        metadataBounds(qa);
        metadataErrors(qa);
        qa.done();
    }

    private static void sharedText(QaCheck qa) {
        // EP: where a link sits in the shared message.
        qa.equal(EP, "plain link", List.of("https://shop.pl/a"), ShopImportRules.urls("https://shop.pl/a"));
        qa.equal(EP, "link after text", List.of("https://shop.pl/a"), ShopImportRules.urls("Глянь: https://shop.pl/a"));
        qa.equal(EP, "guillemets", List.of("https://shop.pl/a"), ShopImportRules.urls("«https://shop.pl/a»"));
        qa.equal(EP, "German quotes", List.of("https://shop.pl/a"), ShopImportRules.urls("„https://shop.pl/a“"));
        qa.equal(EP, "sentence punctuation", List.of("https://shop.pl/a"), ShopImportRules.urls("Look https://shop.pl/a!?."));
        qa.equal(EP, "markdown link", List.of("https://shop.pl/a"), ShopImportRules.urls("[buy](https://shop.pl/a)"));
        qa.equal(EP, "balanced brackets kept", List.of("https://shop.pl/a_(b)"), ShopImportRules.urls("see https://shop.pl/a_(b)."));
        qa.equal(EP, "duplicates once", List.of("https://shop.pl/a"), ShopImportRules.urls("https://shop.pl/a https://shop.pl/a"));
        qa.equal(EP, "upper-case scheme", List.of("HTTPS://shop.pl/a"), ShopImportRules.urls("HTTPS://shop.pl/a"));
        qa.equal(EP, "non-ASCII path encoded", List.of("https://sklep.pl/sukienka-%C5%BC%C3%B3%C5%82ta"),
            ShopImportRules.urls("https://sklep.pl/sukienka-żółta"));
        for (String bad : Arrays.asList("javascript:alert(1)", "ftp://shop.pl/a", "https://user:pw@shop.pl/a", "www.shop.pl/a", "https:///a"))
            qa.equal(EP, "rejected " + bad, List.of(), ShopImportRules.urls("x " + bad + " y"));
        qa.equal(EP, "null text", List.of(), ShopImportRules.urls(null));
        // BVA: at most 10 links; message limit 12000 characters; link limit 2000 characters.
        StringBuilder eleven = new StringBuilder();
        for (int i = 0; i < 11; i++) eleven.append("https://shop.pl/").append(i).append(' ');
        qa.equal(BVA, "10 of 11 links kept", 10, ShopImportRules.urls(eleven.toString()).size());
        String link = " https://shop.pl/a";
        qa.equal(BVA, "12000-character message scanned", 1,
            ShopImportRules.urls("x".repeat(ShopImportRules.MAX_TEXT - link.length()) + link).size());
        qa.equal(BVA, "12001-character message ignored as a whole", 0,
            ShopImportRules.urls("x".repeat(ShopImportRules.MAX_TEXT + 1 - link.length()) + link).size());
        String prefix = "https://shop.pl/";
        String at2000 = prefix + "a".repeat(2000 - prefix.length());
        qa.isFalse(BVA, "web link of 2000", ShopImportRules.webUrl(at2000).isEmpty());
        qa.isTrue(BVA, "web link of 2001", ShopImportRules.webUrl(at2000 + "a").isEmpty());
        // Relative image URLs.
        qa.equal(EP, "relative image", "https://shop.pl/img/1.jpg", ShopImportRules.resolve(PAGE, "/img/1.jpg"));
        qa.equal(EP, "protocol-relative image", "https://cdn.shop.pl/1.jpg", ShopImportRules.resolve(PAGE, "//cdn.shop.pl/1.jpg"));
        qa.equal(EG, "script image refused", "", ShopImportRules.resolve(PAGE, "javascript:alert(1)"));
        qa.equal(EG, "data image refused", "", ShopImportRules.resolve(PAGE, "data:image/png;base64,AAAA"));
        qa.equal(EG, "blank image", "", ShopImportRules.resolve(PAGE, "  "));
    }

    private static void duplicateKeys(QaCheck qa) {
        String key = ShopImportRules.linkKey("https://shop.pl/p?id=7&size=m");
        // EP: equivalent spellings of one product page.
        for (String same : Arrays.asList("https://SHOP.pl/p?size=m&id=7", "https://shop.pl:443/p?id=7&size=m",
                "https://shop.pl./p?id=7&size=m#reviews", "https://shop.pl/p?utm_source=x&id=7&size=m&fbclid=1",
                "https://shop.pl/p?id=7&&size=m&UTM_Medium=y&gclid=2"))
            qa.equal(EP, "same product " + same, key, ShopImportRules.linkKey(same));
        // EP: different products / variants.
        for (String other : Arrays.asList("https://shop.pl/p?id=7&size=l", "https://shop.pl/P?id=7&size=m",
                "https://shop.pl:8443/p?id=7&size=m", "https://other.pl/p?id=7&size=m"))
            qa.isFalse(EP, "different product " + other, key.equals(ShopImportRules.linkKey(other)));
        qa.equal(EP, "invalid link has no key", "", ShopImportRules.linkKey("not a link"));
        qa.equal(BVA, "empty query equals no query", ShopImportRules.linkKey("https://shop.pl/p"), ShopImportRules.linkKey("https://shop.pl/p?"));
        qa.equal(BVA, "empty path is root", ShopImportRules.linkKey("https://shop.pl/"), ShopImportRules.linkKey("https://shop.pl"));
        // QA-08: the import loads http links over https, and shops redirect www/non-www, yet these spellings of
        // one page get different duplicate keys, so the "already on your list" hint is missed.
        qa.knownBug(EG, "QA-08", "duplicate hint misses http/https and www variants", () -> {
            expect(ShopImportRules.linkKey("http://shop.pl/p?id=7&size=m").equals(key), "http vs https");
            expect(ShopImportRules.linkKey("https://www.shop.pl/p?id=7&size=m").equals(key), "www vs bare host");
        });
    }

    private static String ld(String json) { return "<script type=\"application/ld+json\">" + json + "</script>"; }
    private static ShopMetadata.Product one(String html) {
        List<ShopMetadata.Product> products = ShopMetadata.parse(html, PAGE);
        expect(products.size() == 1, "expected one product, got " + products.size());
        return products.get(0);
    }

    private static void metadataClasses(QaCheck qa) {
        // EP: single offer → exact price.
        ShopMetadata.Product p = one(ld("{\"@type\":\"Product\",\"name\":\"Sukienka\",\"image\":\"/a.jpg\",\"offers\":{\"price\":\"229.90\",\"priceCurrency\":\"pln\"}}"));
        qa.equal(EP, "name", "Sukienka", p.name);
        qa.equal(EP, "price", "229.90", p.price);
        qa.equal(EP, "currency upper-cased", "PLN", p.currency);
        qa.equal(EP, "image resolved", List.of("https://shop.pl/a.jpg"), p.images);
        qa.isFalse(EP, "certain price", p.uncertainPrice);
        // EP: numeric JSON price and @graph nesting with @type array.
        p = one(ld("{\"@graph\":[{\"@type\":[\"Thing\",\"Product\"],\"name\":\"Coat\",\"offers\":[{\"price\":89,\"priceCurrency\":\"EUR\"}]}]}"));
        qa.equal(EP, "numeric price", "89.00", p.price);
        qa.equal(EP, "graph product", "Coat", p.name);
        // EP: price uncertainty classes.
        qa.isTrue(EP, "aggregate offer uncertain", one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"offers\":{\"@type\":\"AggregateOffer\",\"lowPrice\":\"10\",\"priceCurrency\":\"PLN\"}}")).uncertainPrice);
        qa.isTrue(EP, "two prices uncertain", one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"offers\":[{\"price\":\"10\",\"priceCurrency\":\"PLN\"},{\"price\":\"12\",\"priceCurrency\":\"PLN\"}]}")).uncertainPrice);
        p = one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"offers\":[{\"price\":\"10\",\"priceCurrency\":\"PLN\"},{\"price\":\"10.00\",\"priceCurrency\":\"PLN\"}]}"));
        qa.isFalse(EP, "same price twice certain", p.uncertainPrice);
        qa.equal(EP, "same price twice value", "10.00", p.price);
        p = one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"offers\":{\"price\":\"10\",\"priceCurrency\":\"CHF\"}}"));
        qa.isTrue(EP, "unsupported currency uncertain", p.uncertainPrice);
        qa.equal(EP, "unsupported currency no price", "", p.price);
        qa.isFalse(EP, "no offers is not uncertain", one(ld("{\"@type\":\"Product\",\"name\":\"A\"}")).uncertainPrice);
        // EP: Open Graph fallback and its precedence.
        p = one("<meta property=\"og:title\" content=\"Kurtka &amp; czapka\"><meta property=\"og:image\" content=\"/k.jpg\">"
            + "<meta property=\"product:price:amount\" content=\"199,99\"><meta property=\"product:price:currency\" content=\"PLN\"><title>Ignored</title>");
        qa.equal(EP, "og title decoded once", "Kurtka & czapka", p.name);
        qa.equal(EP, "og price", "199.99", p.price);
        qa.equal(EP, "og image", List.of("https://shop.pl/k.jpg"), p.images);
        qa.equal(EP, "title fallback", "Tytuł", one("<title>Tytu&lstrok;</title>").name);
        qa.equal(EP, "twitter image fallback", List.of("https://shop.pl/t.jpg"), one("<meta name=\"twitter:image\" content=\"/t.jpg\">").images);
        qa.isTrue(EP, "og price without currency is uncertain", one("<meta property=\"og:price:amount\" content=\"10\">").uncertainPrice);
    }

    private static void metadataBounds(QaCheck qa) {
        // BVA: only the first 64 JSON-LD blocks are read.
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 63; i++) html.append(ld("{\"@type\":\"Organization\"}"));
        String product = ld("{\"@type\":\"Product\",\"name\":\"Edge\"}");
        qa.equal(BVA, "product in 64th block", "Edge", one(html + product).name);
        qa.equal(BVA, "product in 65th block ignored", "", one(html + ld("{\"@type\":\"Organization\"}") + product).name);
        // Ordinary scripts do not consume the JSON-LD budget (BUG-03 regression).
        qa.equal(BVA, "100 ordinary scripts first", "Edge", one("<script>var a=1</script>".repeat(100) + product).name);
        // BVA: at most 20 products and 8 images.
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 25; i++) many.append(i == 0 ? "" : ",").append("{\"@type\":\"Product\",\"name\":\"P").append(i).append("\"}");
        qa.equal(BVA, "20 of 25 products", 20, ShopMetadata.parse(ld(many + "]"), PAGE).size());
        StringBuilder images = new StringBuilder("[");
        for (int i = 0; i < 9; i++) images.append(i == 0 ? "" : ",").append("\"/").append(i).append(".jpg\"");
        qa.equal(BVA, "8 of 9 images", 8, one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"image\":" + images + "]}")).images.size());
        // BVA: name 120 characters.
        qa.equal(BVA, "name of 120 kept", 120, one(ld("{\"@type\":\"Product\",\"name\":\"" + "a".repeat(120) + "\"}")).name.length());
        qa.equal(BVA, "name of 121 cut", 120, one(ld("{\"@type\":\"Product\",\"name\":\"" + "a".repeat(121) + "\"}")).name.length());
        // BVA: a page larger than 2 MiB is parsed up to the limit (BUG-04 regression).
        String head = "<title>Big</title>";
        String filler = "x".repeat(ShopMetadata.MAX_HTML);
        qa.equal(BVA, "head of a 2 MiB+ page", "Big", one(head + filler).name);
        qa.equal(BVA, "metadata after 2 MiB ignored", "", one(filler + head).name);
        // BVA on JSON nesting: depth 32 parses, a deeper document is ignored without an exception.
        String deep = "[".repeat(40) + "]".repeat(40);
        qa.equal(BVA, "deep JSON falls back to page", 1, ShopMetadata.parse(ld(deep), PAGE).size());
    }

    private static void metadataErrors(QaCheck qa) {
        qa.raises(EG, "null page html", IllegalArgumentException.class, () -> ShopMetadata.parse(null, PAGE));
        qa.raises(EG, "non-web page URL", IllegalArgumentException.class, () -> ShopMetadata.parse("", "file:///a"));
        qa.equal(EG, "broken JSON-LD falls back to OG", "OG", one(ld("{\"@type\":\"Product\",") + "<meta property=\"og:title\" content=\"OG\">").name);
        qa.equal(EG, "type with charset parameter", "C", one("<script type=\"application/ld+json; charset=utf-8\">{\"@type\":\"Product\",\"name\":\"C\"}</script>").name);
        qa.equal(EG, "double-escaped entity decoded once", "&amp;", one("<meta property=\"og:title\" content=\"&amp;amp;\">").name);
        qa.equal(EG, "numeric entities", "Żółw", ShopMetadata.decode("&#379;&#xF3;&#x142;w"));
        qa.equal(EG, "NUL entity left as text", "&#0;", ShopMetadata.decode("&#0;"));
        qa.equal(EG, "surrogate entity left as text", "&#xD800;", ShopMetadata.decode("&#xD800;"));
        qa.equal(EG, "unknown entity left as text", "&bogus;", ShopMetadata.decode("&bogus;"));
        qa.equal(EG, "huge exponent price ignored", "", one(ld("{\"@type\":\"Product\",\"name\":\"A\",\"offers\":{\"price\":1e999,\"priceCurrency\":\"PLN\"}}")).price);
        qa.equal(EG, "control characters folded", "a b", one(ld("{\"@type\":\"Product\",\"name\":\"a\\n\\tb\"}")).name);
        qa.equal(EG, "markup in a name stays plain text", "<b>x</b>", one(ld("{\"@type\":\"Product\",\"name\":\"<b>x</b>\"}")).name);
        qa.equal(EG, "closing script tag inside JSON ends the block like a browser", "",
            one(ld("{\"@type\":\"Product\",\"name\":\"<script>x</script>\"}")).name);
        // QA-06: product names come from untrusted pages; bidi overrides survive cleaning, are pre-filled into
        // the wish, and the family server later refuses the whole wardrobe (schema.visible_name, BUG-12).
        qa.knownBug(EG, "QA-06", "bidi controls kept in imported product names", () -> {
            String name = one("<meta property=\"og:title\" content=\"&#x202E;Sukienka\">").name;
            expect(name.codePoints().noneMatch(TextLimits::isBidiControl), "name contains a bidi control");
            expect(TextLimits.hasVisibleText(name), "imported name must be acceptable to the family server");
        });
    }
}
