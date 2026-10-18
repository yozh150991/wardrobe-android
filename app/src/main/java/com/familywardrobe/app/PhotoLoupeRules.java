package com.familywardrobe.app;

/** Lens bounds stay inside the canvas and prefer space above the touch point. */
public final class PhotoLoupeRules {
    private PhotoLoupeRules() {}
    public static float[] bounds(float width,float height,float x,float y,float requestedSize,float gap) {
        for(float v:new float[]{width,height,x,y,requestedSize,gap}) if(!Float.isFinite(v)) throw new IllegalArgumentException();
        if(width<=0 || height<=0 || requestedSize<=0 || gap<0) throw new IllegalArgumentException();
        float size=Math.min(requestedSize,Math.min(width*.45f,height*.4f));
        float left=Math.max(0,Math.min(width-size,x-size/2));
        float top=y-gap-size;
        if(top<0) top=y+gap;
        if(top+size>height) {
            top=Math.max(0,Math.min(height-size,y-size/2));
            left=x<width/2 ? width-size : 0;
        }
        return new float[]{left,Math.max(0,Math.min(height-size,top)),size};
    }
}
