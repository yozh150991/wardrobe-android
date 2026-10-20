package com.familywardrobe.app;

/** Normalized centres and sizes are independent of screen density and orientation. */
public final class CanvasRules {
    private CanvasRules() {}
    private static final java.util.regex.Pattern PIECE = java.util.regex.Pattern.compile("(owned|wish):[a-f0-9-]{36}");
    /** Owned and wish keys, a generic idea category, or any key of the curated inspiration catalog. */
    public static boolean validKey(String key) {
        if (key == null || key.length() > 60) return false;
        if (PIECE.matcher(key).matches()) return true;
        if (!key.startsWith("idea:")) return false;
        for (String category : WardrobeRules.CATEGORIES) if (InspirationCatalog.validKey(key, category)) return true;
        return false;
    }
    public static float width(String category,float scale) {
        return (category.equals("dress") || category.equals("jumpsuit") ? .42f : category.equals("bottom") ? .28f :
            category.equals("top") || category.equals("outerwear") || category.equals("layer") ? .36f : category.equals("shoes") ? .38f : .22f)*scale;
    }
    public static float height(String category,float scale) {
        return (category.equals("dress") || category.equals("jumpsuit") ? .68f : category.equals("bottom") ? .52f :
            category.equals("outerwear") || category.equals("layer") ? .50f : category.equals("top") ? .38f : .20f)*scale;
    }
    public static float[] constrain(String category,float x,float y,float scale) {
        if(!WardrobeRules.CATEGORIES.contains(category) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale)) throw new IllegalArgumentException();
        float maximum=Math.min(1.8f,Math.min(.94f/width(category,1),.94f/height(category,1)));
        float s=Math.max(.6f,Math.min(maximum,scale));float halfW=width(category,s)/2,halfH=height(category,s)/2;
        return new float[]{Math.max(halfW,Math.min(1-halfW,x)),Math.max(halfH,Math.min(1-halfH,y)),s};
    }
    public static float[] initial(String category,int index,int total) {
        if(index<0 || total<1 || index>=total || total>WardrobeRules.MAX_PARTS) throw new IllegalArgumentException();
        if(total>5) return constrain(category,.17f+(index%3)*.33f,.13f+(index/3)*.24f,.6f);
        float x=.80f,y=.74f,s=1;
        switch(category) {
            case "top": x=.27f;y=.25f;break;
            case "bottom": x=.66f;y=.36f;break;
            case "dress": case "jumpsuit":x=.35f;y=.38f;break;
            case "shoes":x=.32f;y=.83f;break;
            case "layer": case "outerwear":x=.76f;y=.31f;s=.8f;break;
            default:x=.78f;y=.61f+(index%3)*.12f;s=.8f;
        }
        return constrain(category,x,y,s);
    }
}
