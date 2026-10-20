package com.familywardrobe.app;

import java.util.List;

/** The same fixtures run as JVM/JUnit tests and directly with javac when Gradle is unavailable. */
public final class ShopMetadataCases {
    private static int checks;
    private static void check(boolean ok,String scenario){checks++;if(!ok)throw new AssertionError(scenario);}
    private static ShopMetadata.Product first(String html){return ShopMetadata.parse(html,"https://shop.example/item").get(0);}
    private static String ld(String json){return "<script type='application/ld+json'>"+json+"</script>";}
    public static void main(String[] args){run();System.out.println("PASS: "+checks+" shop metadata and input assertions");}
    public static void run(){
        checks=0;
        check(ShopImportRules.urls("Coat https://shop.example/coat. More https://shop.example/shoes").size()==2,"multiple shared links");
        check(ShopImportRules.urls("See (https://shop.example/coat)").get(0).equals("https://shop.example/coat"),"outer closing punctuation");
        check(ShopImportRules.urls("https://shop.example/a(b)").get(0).endsWith("a(b)"),"balanced URL parentheses");
        check(ShopImportRules.webUrl("javascript:alert(1)").isEmpty(),"no script URL");
        check(ShopImportRules.webUrl("https://user:password@shop.example/x").isEmpty(),"no embedded credentials");
        check(ShopImportRules.webUrl("file:///private/photo").isEmpty(),"no file URL");
        check(ShopImportRules.urls("x".repeat(12001)).isEmpty(),"bounded intake");
        check(ShopImportRules.linkKey("https://shop.example/item?size=M&utm_source=ad#part").equals("https://shop.example/item?size=M"),"tracking only normalization");
        check(!ShopImportRules.linkKey("https://shop.example/item?size=M").equals(ShopImportRules.linkKey("https://shop.example/item?size=L")),"variant must survive");
        ShopMetadata.Product p=first(ld("{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"name\":\"Coat\",\"image\":\"/coat.jpg\",\"offers\":{\"@type\":\"Offer\",\"price\":\"129.95\",\"priceCurrency\":\"PLN\"}}"));
        check(p.name.equals("Coat"),"JSON-LD name");check(p.price.equals("129.95")&&p.currency.equals("PLN"),"paired exact price");
        check(p.images.get(0).equals("https://shop.example/coat.jpg"),"relative image");
        p=first(ld("{\"@graph\":[{\"@type\":[\"Thing\",\"Product\"],\"name\":\"Shoe\\u0020A\",\"image\":[{\"url\":\"/1.jpg\"},{\"contentUrl\":\"/2.jpg\"}]}]}"));
        check(p.name.equals("Shoe A")&&p.images.size()==2,"graph type list and ImageObject");
        List<ShopMetadata.Product> many=ShopMetadata.parse(ld("[{\"@type\":\"Product\",\"name\":\"A\"},{\"@type\":\"Product\",\"name\":\"B\"}]"),"https://shop.example/");
        check(many.size()==2,"do not silently choose first product");
        many=ShopMetadata.parse(ld("[{\"@type\":\"Product\",\"name\":\"Coat\",\"offers\":{\"price\":20,\"priceCurrency\":\"EUR\"}},{\"@type\":\"Product\",\"name\":\"Coat\",\"offers\":{\"price\":20,\"priceCurrency\":\"USD\"}}]"),"https://shop.example/item");
        check(many.size()==2,"currency variants must not be merged");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":\"59\"}}"));check(p.price.isEmpty()&&p.uncertainPrice,"missing currency");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":\"59\",\"priceCurrency\":\"JPY\"}}"));check(p.price.isEmpty(),"unsupported currency not relabelled");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"@type\":\"AggregateOffer\",\"lowPrice\":\"20\",\"highPrice\":\"80\",\"priceCurrency\":\"EUR\"}}"));check(p.price.isEmpty()&&p.uncertainPrice,"range not an exact price");
        p=first(ld("{\"@type\":\"Product\",\"offers\":[{\"price\":\"20\",\"priceCurrency\":\"EUR\"},{\"price\":\"21\",\"priceCurrency\":\"EUR\"}]}"));check(p.price.isEmpty(),"multiple offer prices");
        p=first(ld("{\"@type\":\"Product\",\"offers\":[{\"price\":20,\"priceCurrency\":\"EUR\"},{\"price\":\"20.00\",\"priceCurrency\":\"EUR\"}]}"));check(p.price.equals("20.00"),"identical offer price");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":0,\"priceCurrency\":\"EUR\"}}"));check(p.price.equals("0.00"),"zero does not mean missing");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":-20,\"priceCurrency\":\"EUR\"}}"));check(p.price.isEmpty(),"negative price rejected");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":\"29.999\",\"priceCurrency\":\"EUR\"}}"));check(p.price.isEmpty(),"no silent fractional cent rounding");
        p=first("<meta content='Blue &amp; White &#x1f455;' property='og:title'><meta property=og:image content='/pic.jpg'><meta property='product:price:amount' content='49,90'><meta property='product:price:currency' content='PLN'>");
        check(p.name.equals("Blue & White 👕"),"HTML entities and attribute order");check(p.price.equals("49.90"),"OG price/currency");
        p=first("<meta property='og:price:amount' content='99'><title>Fallback title</title>");check(p.price.isEmpty()&&p.name.equals("Fallback title"),"OG missing currency and title fallback");
        p=first(ld("{broken")+"<meta property='og:title' content='Manual fallback'>");check(p.name.equals("Manual fallback"),"broken JSON-LD fallback");
        p=first(ld("{\"@type\":\"Product\",\"name\":\"Manual\",\"image\":\"javascript:alert(1)\"}"));check(p.images.isEmpty(),"unsafe image URL");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":1e999999999,\"priceCurrency\":\"EUR\"}}")+"<title>Safe</title>");check(p.name.equals("Safe"),"huge exponent bounded");
        p=first(ld("{\"@type\":\"Product\",\"offers\":{\"price\":1e-2147483648,\"priceCurrency\":\"EUR\"}}")+"<title>Bounded</title>");check(p.name.equals("Bounded"),"scale overflow bounded");
        p=first(ld("[".repeat(40)+"null"+"]".repeat(40))+"<title>Depth fallback</title>");check(p.name.equals("Depth fallback"),"depth bounded");
        p=first("<script>window.product={price:0}; throw new Error('must not run')</script><title>Static only</title>");check(p.name.equals("Static only"),"scripts not evaluated");
        check(first("").name.isEmpty(),"empty page remains editable");
        // QA 0.14.12 regressions.
        String shared=ShopImportRules.urls("Глянь «https://shop.pl/sukienka-123» 😍").get(0);
        check(shared.equals("https://shop.pl/sukienka-123"),"BUG-01 guillemets are not part of the link: "+shared);
        check(ShopImportRules.urls("„https://shop.pl/a“ i “https://shop.pl/b”").equals(List.of("https://shop.pl/a","https://shop.pl/b")),"BUG-01 low and high quotes");
        check(ShopImportRules.urls("https://shop.pl/item…").get(0).equals("https://shop.pl/item"),"BUG-01 truncated preview ellipsis");
        check(ShopImportRules.urls("Look: ‹https://shop.pl/x›, ok?").get(0).equals("https://shop.pl/x"),"BUG-01 single guillemets and punctuation");
        check(ShopImportRules.urls("[https://shop.pl/item]").equals(List.of("https://shop.pl/item")),"BUG-02 square brackets");
        check(ShopImportRules.urls("[https://shop.pl/a] [https://shop.pl/b]").size()==2,"BUG-02 several bracketed links");
        check(ShopImportRules.urls("(see https://shop.pl/a(b)).").get(0).endsWith("a(b)"),"balanced parentheses retained after trimming");
        StringBuilder heavy=new StringBuilder();for(int i=0;i<64;i++)heavy.append("<script src=\"/_next/static/chunk-").append(i).append(".js\"></script>");
        p=first(heavy+ld("{\"@type\":\"Product\",\"name\":\"Sukienka midi\",\"offers\":{\"price\":\"199.99\",\"priceCurrency\":\"PLN\"}}"));
        check(p.name.equals("Sukienka midi")&&p.price.equals("199.99"),"BUG-03 JSON-LD after 64 ordinary scripts");
        String big="<head><meta property='og:title' content='Sukienka'></head>"+"a".repeat(ShopMetadata.MAX_HTML);
        check(first(big).name.equals("Sukienka"),"BUG-04 a page over the limit still yields head metadata");
        String hugeName="x".repeat(119)+"👗 sukienka";
        p=first(ld("{\"@type\":\"Product\",\"name\":\""+hugeName+"\"}"));
        check(p.name.equals("x".repeat(119))&&!Character.isHighSurrogate(p.name.charAt(p.name.length()-1)),"BUG-05 no half emoji in a truncated name");
        check(TextLimits.take("x".repeat(116)+"🇺🇦",120).equals("x".repeat(116)+"🇺🇦")&&TextLimits.take("x".repeat(118)+"🇺🇦",120).equals("x".repeat(118)),"BUG-05 flags are whole graphemes");
        p=first("<meta property='og:title' content='Fish &amp;amp; Chips'>");check(p.name.equals("Fish &amp; Chips"),"BUG-06 decoded exactly once: "+p.name);
        p=first("<meta property='og:title' content='Sukienka &ndash; Kraków &oacute; &euro; &lstrok;&oacute;d&zacute;'>");check(p.name.equals("Sukienka – Kraków ó € łódź"),"BUG-06 named entities: "+p.name);
        p=first("<title>A &amp;amp; B &unknown; C</title>");check(p.name.equals("A &amp; B &unknown; C"),"BUG-06 title decoded once, unknown entities kept");
        p=first(ld("{\"@type\":\"Product\",\"name\":\"Kurtka &amp; czapka\"}"));check(p.name.equals("Kurtka & czapka"),"JSON-LD name decoded once");
        String key=ShopImportRules.linkKey("https://shop.pl/item");
        for(String same:new String[]{"https://shop.pl:443/item","https://shop.pl/item?","https://shop.pl/item#reviews","https://shop.pl/item#%D1%8F%D0%BA%D1%96%D1%80","https://SHOP.pl/item?utm_source=x","https://shop.pl./item"})
            check(ShopImportRules.linkKey(same).equals(key),"BUG-14 equivalent link "+same+" -> "+ShopImportRules.linkKey(same));
        check(!ShopImportRules.linkKey("https://shop.pl:8443/item").equals(key),"non-default port is a different site");
        check(ShopImportRules.linkKey("http://shop.pl:80/item").equals(ShopImportRules.linkKey("http://shop.pl/item")),"http default port");
    }
}
