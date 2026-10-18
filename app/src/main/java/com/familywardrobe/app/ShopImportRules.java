package com.familywardrobe.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** No Android dependencies: used by intake, metadata, review and executable tests. */
public final class ShopImportRules {
    private ShopImportRules() {}
    public static final int MAX_TEXT = 12000;
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);
    public static String webUrl(String value) {
        if (value == null || value.length() > 2000) return "";
        try {
            URI u = new URI(value.trim());
            String scheme = u.getScheme(), host = u.getHost();
            if (scheme == null || host == null || host.isEmpty() || u.getRawUserInfo() != null ||
                    !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return "";
            return u.toASCIIString();
        } catch (Exception e) { return ""; }
    }
    public static List<String> urls(String text) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        if (text == null || text.length() > MAX_TEXT) return new ArrayList<>();
        Matcher matcher = URL.matcher(text);
        while (matcher.find() && found.size() < 10) {
            String value = matcher.group().replaceAll("[.,;!]+$", "");
            // A closing bracket outside the URL is common in shares; balanced URL brackets are retained.
            while (value.endsWith(")") && count(value, ')') > count(value, '(')) value = value.substring(0, value.length() - 1);
            String url = webUrl(value);
            if (!url.isEmpty()) found.add(url);
        }
        return new ArrayList<>(found);
    }
    private static int count(String s, char c) { int n=0; for (int i=0;i<s.length();i++) if(s.charAt(i)==c)n++; return n; }
    public static String resolve(String page, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try { return webUrl(new URI(page).resolve(value.trim()).toString()); } catch (Exception e) { return ""; }
    }
    /** Preserve variant parameters. Only common tracking parameters are ignored for duplicate hints. */
    public static String linkKey(String value) {
        String valid=webUrl(value); if(valid.isEmpty())return "";
        try {
            URI u=new URI(valid); List<String> query=new ArrayList<>();
            if(u.getRawQuery()!=null) for(String part:u.getRawQuery().split("&")) {
                String name=part.split("=",2)[0].toLowerCase(Locale.ROOT);
                if(!name.startsWith("utm_") && !name.equals("fbclid") && !name.equals("gclid"))query.add(part);
            }
            java.util.Collections.sort(query);
            return u.getScheme().toLowerCase(Locale.ROOT)+"://"+u.getRawAuthority().toLowerCase(Locale.ROOT)+
                (u.getRawPath().isEmpty()?"/":u.getRawPath())+(query.isEmpty()?"":"?"+String.join("&",query));
        }catch(Exception e){return valid;}
    }
}
