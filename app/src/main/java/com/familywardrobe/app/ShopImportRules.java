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
    /** Locale quotes, guillemets and the ellipsis are never part of a shared link (BUG-01). */
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"'\u00ab\u00bb\u201c\u201d\u201e\u201f\u2039\u203a\u2026]+", Pattern.CASE_INSENSITIVE);
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
            String url = webUrl(trimShared(matcher.group()));
            if (!url.isEmpty()) found.add(url);
        }
        return new ArrayList<>(found);
    }
    /** Trailing sentence punctuation and unbalanced closing brackets belong to the message, not the URL. */
    static String trimShared(String value) {
        String result = value;
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (".,;:!?'\u2018\u2019".indexOf(last) >= 0) { result = result.substring(0, result.length() - 1); continue; }
            int closing = ")]}".indexOf(last);
            // Balanced URL brackets are retained (BUG-02: "[https://…]" loses only the outer bracket).
            if (closing >= 0 && count(result, last) > count(result, "([{".charAt(closing))) { result = result.substring(0, result.length() - 1); continue; }
            break;
        }
        return result;
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
                if(part.isEmpty())continue; // "item?" and "item?&" are the same page (BUG-14)
                String name=part.split("=",2)[0].toLowerCase(Locale.ROOT);
                if(!name.startsWith("utm_") && !name.equals("fbclid") && !name.equals("gclid"))query.add(part);
            }
            java.util.Collections.sort(query);
            String scheme=u.getScheme().toLowerCase(Locale.ROOT);
            String host=u.getHost().toLowerCase(Locale.ROOT);
            if(host.endsWith(".")&&host.length()>1)host=host.substring(0,host.length()-1);
            int port=u.getPort();
            // Default ports and the fragment never identify a different product (BUG-14).
            boolean defaultPort=port<0||(scheme.equals("https")&&port==443)||(scheme.equals("http")&&port==80);
            return scheme+"://"+host+(defaultPort?"":":"+port)+
                (u.getRawPath().isEmpty()?"/":u.getRawPath())+(query.isEmpty()?"":"?"+String.join("&",query));
        }catch(Exception e){return valid;}
    }
}
