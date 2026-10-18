package com.familywardrobe.app;
import java.util.*;

/** Curated wardrobe ideas with stable IDs. These are examples, never owned garments. */
public final class InspirationCatalog {
    public static final class Idea {
        public final String key,category,color,season,style,name;
        Idea(String category,String name,String color,String season,String style) {
            this.key="idea:"+category+"_"+name+"_"+color;this.category=category;this.name=name;this.color=color;this.season=season;this.style=style;
        }
    }
    private static final List<Idea> ALL;
    static {
        List<Idea> v=new ArrayList<>();
        add(v,"top","shirt","all","formal","white","blue","beige");
        add(v,"top","tee","summer","casual","white","black","grey");
        add(v,"top","knit","winter","casual","beige","grey","navy");
        add(v,"top","sporttop","all","sport","black","blue","grey");
        add(v,"bottom","trousers","all","formal","navy","black","grey");
        add(v,"bottom","jeans","all","casual","blue","black","navy");
        add(v,"bottom","shorts","summer","casual","beige","navy","olive");
        add(v,"bottom","joggers","all","sport","grey","black","navy");
        add(v,"dress","dress","summer","casual","blue","beige","red");
        add(v,"dress","formaldress","all","formal","black","navy","red");
        add(v,"jumpsuit","jumpsuit","all","casual","black","olive","navy");
        add(v,"shoes","sneakers","all","casual","white","black","grey");
        add(v,"shoes","loafers","all","formal","black","brown","beige");
        add(v,"shoes","boots","winter","casual","black","brown","beige");
        add(v,"shoes","trainers","all","sport","white","black","blue");
        add(v,"outerwear","jacket","midseason","casual","olive","navy","beige");
        add(v,"outerwear","coat","winter","formal","black","brown","grey");
        add(v,"outerwear","raincoat","midseason","sport","navy","black","olive");
        add(v,"layer","cardigan","all","casual","beige","grey","navy");
        add(v,"layer","blazer","all","formal","black","navy","beige");
        add(v,"bag","bag","all","casual","black","brown","beige");
        add(v,"hat","hat","winter","casual","black","grey","beige");
        add(v,"accessory","scarf","winter","casual","grey","beige","navy");
        ALL=Collections.unmodifiableList(v);
    }
    private static void add(List<Idea> v,String category,String name,String season,String style,String... colors) {
        for(String color:colors) v.add(new Idea(category,name,color,season,style));
    }
    public static List<Idea> all(){return ALL;}
    public static List<Idea> forContext(String occasion,String weather) {
        String season=weather.equals("warm")?"summer":weather.equals("cold")?"winter":weather.equals("any_weather")?"all":"midseason";
        String style=occasion.equals("active_day")?"sport":occasion.equals("work")||occasion.equals("occasion")?"formal":"casual";
        List<Idea> result=new ArrayList<>();
        for(Idea p:ALL) if(WardrobeRules.compatibleSeasons(p.season,season)) result.add(p);
        result.sort(Comparator.comparingInt(p -> p.style.equals(style)?0:1));
        return result;
    }
    public static boolean validKey(String key,String category) {
        if(key.equals("idea:"+category) && WardrobeRules.CATEGORIES.contains(category))return true;
        for(Idea p:ALL) if(p.key.equals(key)&&p.category.equals(category))return true;
        return false;
    }
}
