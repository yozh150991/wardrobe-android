package com.familywardrobe.app;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Bounded, offline parsing of public JSON-LD Product and Open Graph metadata. Never executes HTML/JS. */
public final class ShopMetadata {
    public static final int MAX_HTML=2*1024*1024;
    private static final Pattern META=Pattern.compile("<meta\\b([^>]{0,16000})>",Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT=Pattern.compile("<script\\b([^>]{0,4000})>([\\s\\S]*?)</script\\s*>",Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR=Pattern.compile("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern ENTITY=Pattern.compile("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,31});");
    /** Named entities seen on PL/UA/EU shop pages: HTML 4 Latin-1, typography, Polish and Czech letters. */
    private static final Map<String,Integer> NAMED=new HashMap<>();
    static {
        String[] latin1={"nbsp","iexcl","cent","pound","curren","yen","brvbar","sect","uml","copy","ordf","laquo","not","shy","reg","macr",
            "deg","plusmn","sup2","sup3","acute","micro","para","middot","cedil","sup1","ordm","raquo","frac14","frac12","frac34","iquest",
            "Agrave","Aacute","Acirc","Atilde","Auml","Aring","AElig","Ccedil","Egrave","Eacute","Ecirc","Euml","Igrave","Iacute","Icirc","Iuml",
            "ETH","Ntilde","Ograve","Oacute","Ocirc","Otilde","Ouml","times","Oslash","Ugrave","Uacute","Ucirc","Uuml","Yacute","THORN","szlig",
            "agrave","aacute","acirc","atilde","auml","aring","aelig","ccedil","egrave","eacute","ecirc","euml","igrave","iacute","icirc","iuml",
            "eth","ntilde","ograve","oacute","ocirc","otilde","ouml","divide","oslash","ugrave","uacute","ucirc","uuml","yacute","thorn","yuml"};
        for(int n=0;n<latin1.length;n++)NAMED.put(latin1[n],160+n);
        Object[] other={"amp",38,"AMP",38,"quot",34,"QUOT",34,"apos",39,"lt",60,"LT",60,"gt",62,"GT",62,
            "ndash",0x2013,"mdash",0x2014,"lsquo",0x2018,"rsquo",0x2019,"sbquo",0x201a,"ldquo",0x201c,"rdquo",0x201d,"bdquo",0x201e,
            "hellip",0x2026,"bull",0x2022,"prime",0x2032,"lsaquo",0x2039,"rsaquo",0x203a,"euro",0x20ac,"trade",0x2122,"minus",0x2212,
            "thinsp",0x2009,"ensp",0x2002,"emsp",0x2003,"zwnj",0x200c,"zwj",0x200d,"OElig",0x152,"oelig",0x153,"Scaron",0x160,"scaron",0x161,
            "Yuml",0x178,"Zcaron",0x17d,"zcaron",0x17e,"Aogon",0x104,"aogon",0x105,"Cacute",0x106,"cacute",0x107,"Ccaron",0x10c,"ccaron",0x10d,
            "Eogon",0x118,"eogon",0x119,"Ecaron",0x11a,"ecaron",0x11b,"Lstrok",0x141,"lstrok",0x142,"Nacute",0x143,"nacute",0x144,
            "Rcaron",0x158,"rcaron",0x159,"Sacute",0x15a,"sacute",0x15b,"Zacute",0x179,"zacute",0x17a,"Zdot",0x17b,"zdot",0x17c,
            "Uring",0x16e,"uring",0x16f};
        for(int n=0;n<other.length;n+=2)NAMED.put((String)other[n],(Integer)other[n+1]);
    }
    private ShopMetadata() {}
    public static final class Product {
        public final String name, url, price, currency;
        public final List<String> images;
        public final boolean uncertainPrice;
        Product(String name,String url,List<String> images,String price,String currency,boolean uncertainPrice){
            this.name=name;this.url=url;this.images=Collections.unmodifiableList(images);this.price=price;this.currency=currency;this.uncertainPrice=uncertainPrice;
        }
    }
    public static List<Product> parse(String html,String page) {
        if(html==null || ShopImportRules.webUrl(page).isEmpty())throw new IllegalArgumentException("Invalid page");
        // Product metadata lives in <head>; a heavy page is parsed up to the limit instead of refused (BUG-04).
        if(html.length()>MAX_HTML)html=TextLimits.take(html,MAX_HTML);
        List<Product> products=new ArrayList<>();
        Matcher scripts=SCRIPT.matcher(html);int count=0;
        // Only JSON-LD blocks count toward the bound: Next.js/Nuxt pages carry dozens of ordinary scripts (BUG-03).
        while(count<64 && products.size()<20 && scripts.find()){
            Map<String,String> attributes=attributes(scripts.group(1));
            if(!attributes.getOrDefault("type","").split(";",2)[0].trim().equalsIgnoreCase("application/ld+json"))continue;
            count++;
            String input=scripts.group(2).trim();if(input.length()>512000)continue;
            try{collect(new Json(input).read(),page,products,0);}catch(IllegalArgumentException ignored){/* Broken metadata has a manual fallback. */}
        }
        if(!products.isEmpty()) {
            LinkedHashMap<String,Product> unique=new LinkedHashMap<>();
            for(Product p:products)unique.putIfAbsent(p.name+"\n"+p.url+"\n"+p.price+"\n"+p.currency+"\n"+p.images,p);
            return new ArrayList<>(unique.values());
        }
        Map<String,String> tags=new LinkedHashMap<>(); List<String> images=new ArrayList<>();
        Matcher meta=META.matcher(html);count=0;
        while(meta.find() && count++<1000){
            Map<String,String>a=attributes(meta.group(1));String key=a.getOrDefault("property",a.getOrDefault("name","")).toLowerCase(Locale.ROOT);
            String content=a.getOrDefault("content","");if(content.length()>4000)continue;
            if(key.equals("og:image") || key.equals("og:image:secure_url"))addImage(images,page,content);  // decoded once
            tags.putIfAbsent(key,content);
        }
        String title=clean(tags.getOrDefault("og:title",tags.getOrDefault("twitter:title","")),120);  // already decoded by attributes()
        if(title.isEmpty()){
            Matcher m=Pattern.compile("<title\\b[^>]*>([^<]{0,2000})</title\\s*>",Pattern.CASE_INSENSITIVE).matcher(html);
            if(m.find())title=clean(decode(m.group(1)),120);
        }
        if(images.isEmpty())addImage(images,page,tags.getOrDefault("twitter:image",""));
        String amount=tags.getOrDefault("product:price:amount",tags.getOrDefault("og:price:amount",""));
        String currency=tags.getOrDefault("product:price:currency",tags.getOrDefault("og:price:currency","")).toUpperCase(Locale.ROOT).trim();
        String price=price(amount,currency);
        // Ignore sale ranges/old-price fields and currency-free prices.
        products.add(new Product(title,page,images,price,price.isEmpty()?"":currency,!amount.isEmpty()&&price.isEmpty()));
        return products;
    }
    private static Map<String,String> attributes(String text){
        Map<String,String> result=new HashMap<>();Matcher m=ATTR.matcher(text);
        while(m.find())result.put(m.group(1).toLowerCase(Locale.ROOT),decode(m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4)));
        return result;
    }
    private static void collect(Object value,String page,List<Product> result,int depth){
        if(depth>24 || result.size()>=20)return;
        if(value instanceof Map){
            Map<?,?> m=(Map<?,?>)value;
            if(type(m.get("@type"),"Product"))result.add(product(m,page));
            for(Object nested:m.values())collect(nested,page,result,depth+1);
        }else if(value instanceof List)for(Object nested:(List<?>)value)collect(nested,page,result,depth+1);
    }
    private static boolean type(Object v,String name){
        if(v instanceof List){for(Object x:(List<?>)v)if(type(x,name))return true;return false;}
        String text=string(v);return text.equalsIgnoreCase(name)||text.endsWith("/"+name)||text.endsWith("#"+name);
    }
    private static Product product(Map<?,?> m,String page){
        String url=ShopImportRules.resolve(page,string(m.get("url")));if(url.isEmpty())url=page;
        List<String> images=new ArrayList<>();images(m.get("image"),page,images,0);
        List<Map<?,?>> offers=new ArrayList<>();Object o=m.get("offers");
        if(o instanceof Map)offers.add((Map<?,?>)o);
        else if(o instanceof List)for(Object x:(List<?>)o)if(x instanceof Map)offers.add((Map<?,?>)x);
        Set<String> amounts=new LinkedHashSet<>();boolean uncertain=false;
        for(Map<?,?> offer:offers){
            String currency=string(offer.get("priceCurrency")).trim().toUpperCase(Locale.ROOT);
            String price=price(string(offer.get("price")),currency);
            if(type(offer.get("@type"),"AggregateOffer")||price.isEmpty())uncertain=true;
            else amounts.add(price+" "+currency);
        }
        String amount="",currency="";
        if(amounts.size()==1&&!uncertain){String[] pair=amounts.iterator().next().split(" ");amount=pair[0];currency=pair[1];}
        else if(!offers.isEmpty())uncertain=true;
        // JSON strings are not HTML-decoded by the browser, but shops often put entities in them: decode exactly once.
        return new Product(clean(decode(string(m.get("name"))),120),url,images,amount,currency,uncertain);
    }
    private static void images(Object value,String page,List<String> result,int depth){
        if(depth>5||result.size()>=8)return;
        if(value instanceof List)for(Object image:(List<?>)value)images(image,page,result,depth+1);
        else if(value instanceof Map){Map<?,?> m=(Map<?,?>)value;images(m.containsKey("contentUrl")?m.get("contentUrl"):m.get("url"),page,result,depth+1);}
        else addImage(result,page,decode(string(value)));
    }
    private static void addImage(List<String> list,String page,String value){String url=ShopImportRules.resolve(page,value);if(!url.isEmpty()&&!list.contains(url)&&list.size()<8)list.add(url);}
    private static String string(Object value){return value instanceof String?(String)value:value instanceof BigDecimal?((BigDecimal)value).toPlainString():"";}
    private static String price(String amount,String currency){
        if(!WishRules.CURRENCIES.contains(currency))return "";
        try {Long minor=WishRules.money(amount);return minor==null?"":BigDecimal.valueOf(minor,2).toPlainString();}catch(Exception e){return "";}
    }
    /** Callers decode first; cleaning only folds whitespace and cuts on a grapheme boundary (BUG-05/06). */
    private static String clean(String text,int max){return cleanName(text,max);}
    /**
     * A single-line name from untrusted text (a shop page or a shared message): no bidi controls, whitespace
     * folded, whole graphemes, and "" unless a visible character remains, as the family server requires (QA-06).
     */
    public static String cleanName(String text,int max){
        if(text==null)return "";
        String v=TextLimits.stripBidiControls(text).replaceAll("[\\p{Cntrl}\\s\\u2028\\u2029]+"," ").trim();
        v=TextLimits.takeTrimmed(v,max);
        return TextLimits.hasVisibleText(v)?v:"";
    }
    public static String decode(String input){
        Matcher m=ENTITY.matcher(input);StringBuffer out=new StringBuffer();
        while(m.find()){
            String name=m.group(1),value=m.group();
            try{
                if(name.startsWith("#")){boolean hex=name.length()>1&&(name.charAt(1)=='x'||name.charAt(1)=='X');int n=Integer.parseInt(name.substring(hex?2:1),hex?16:10);if(Character.isValidCodePoint(n)&&n!=0&&!(n>=0xd800&&n<=0xdfff))value=new String(Character.toChars(n));}
                else{Integer n=NAMED.get(name);if(n!=null)value=n==160?" ":new String(Character.toChars(n));}
            }catch(Exception ignored){}
            m.appendReplacement(out,Matcher.quoteReplacement(value));
        }m.appendTail(out);return out.toString();
    }
    /** Small bounded JSON reader; no expression evaluation or remote contexts. */
    private static final class Json {
        final String s;int i,nodes;Json(String s){this.s=s;}
        Object read(){Object v=value(0);space();if(i!=s.length())throw bad();return v;}
        void space(){while(i<s.length()&&" \r\n\t".indexOf(s.charAt(i))>=0)i++;}
        IllegalArgumentException bad(){return new IllegalArgumentException("Invalid metadata JSON");}
        Object value(int depth){
            if(depth>32||++nodes>10000)throw bad();space();if(i>=s.length())throw bad();char c=s.charAt(i);
            if(c=='"')return text();
            if(c=='{'){i++;Map<String,Object> m=new LinkedHashMap<>();space();if(take('}'))return m;do{space();if(i>=s.length()||s.charAt(i)!='"')throw bad();String k=text();space();if(!take(':'))throw bad();m.put(k,value(depth+1));space();if(take('}'))return m;}while(take(','));throw bad();}
            if(c=='['){i++;List<Object>a=new ArrayList<>();space();if(take(']'))return a;do{a.add(value(depth+1));space();if(take(']'))return a;}while(take(','));throw bad();}
            for(String literal:Arrays.asList("true","false","null"))if(s.startsWith(literal,i)){i+=literal.length();return literal.equals("null")?null:literal.equals("true");}
            int start=i;while(i<s.length()&&"-+0123456789.eE".indexOf(s.charAt(i))>=0)i++;
            String number=s.substring(start,i);if(number.length()>64||!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw bad();
            try {BigDecimal b=new BigDecimal(number);if(b.scale() < -10 || b.scale() > 10)throw bad();return b;}catch(NumberFormatException e){throw bad();}
        }
        boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
        String text(){
            i++;StringBuilder b=new StringBuilder();while(i<s.length()){
                char c=s.charAt(i++);if(c=='"')return b.toString();if(c<32)throw bad();
                if(c=='\\'){
                    if(i>=s.length())throw bad();c=s.charAt(i++);
                    switch(c){case '"':case '\\':case '/':b.append(c);break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;
                        case 'u':if(i+4>s.length())throw bad();try{b.append((char)Integer.parseInt(s.substring(i,i+4),16));}catch(NumberFormatException e){throw bad();}i+=4;break;default:throw bad();}
                }else b.append(c);
                if(b.length()>64000)throw bad();
            }throw bad();
        }
    }
}
