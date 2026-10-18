package com.familywardrobe.app;

import java.util.*;

/** Alpha-only brush: erasing and restoring never recolour or generate garment pixels. */
public final class PhotoMaskRules {
    private PhotoMaskRules() {}
    public static final class Stroke {
        public final float x1,y1,x2,y2,radius,hardness,strength;
        public final boolean restore;
        public Stroke(float x1,float y1,float x2,float y2,float radius,boolean restore) {
            this(x1,y1,x2,y2,radius,restore,1f,1f);
        }
        public Stroke(float x1,float y1,float x2,float y2,float radius,boolean restore,float hardness,float strength) {
            for (float v : new float[]{x1,y1,x2,y2}) if (!Float.isFinite(v) || v<0 || v>1) throw new IllegalArgumentException();
            if (!Float.isFinite(radius) || radius<=0f || radius>.5f) throw new IllegalArgumentException();
            if (!Float.isFinite(hardness) || hardness<0 || hardness>1 || !Float.isFinite(strength) || strength<0 || strength>1) throw new IllegalArgumentException();
            this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.radius=radius;this.restore=restore;
            this.hardness=hardness;this.strength=strength;
        }
    }
    public static int[] paint(int[] source,int width,int height,List<Stroke> strokes) {
        return paint(source,width,height,strokes,() -> {});
    }
    public static int[] paint(int[] source,int width,int height,List<Stroke> strokes,Runnable checkpoint) {
        validate(source,source,width,height,strokes);
        int[] result=source;
        for (Stroke s:strokes) {
            result=paintStroke(result,source,width,height,Collections.singletonList(s),checkpoint);
        }
        return result==source ? source.clone() : result;
    }
    private static void validate(int[] current,int[] original,int width,int height,List<Stroke> strokes) {
        if (width<1 || height<1 || (long)width*height>16_000_000 || current==null || original==null ||
            current.length!=(long)width*height || original.length!=current.length || strokes==null || strokes.size()>2048) throw new IllegalArgumentException();
    }
    /** A single gesture has maximum coverage, so overlapping pointer events do not over-erase. */
    public static int[] paintStroke(int[] current,int[] original,int width,int height,List<Stroke> strokes,Runnable checkpoint) {
        validate(current,original,width,height,strokes);
        int[] result=current.clone();
        if (strokes.isEmpty()) return result;
        boolean restore=strokes.get(0).restore;
        byte[] coverage=new byte[current.length];
        for (Stroke s:strokes) {
            if (s.restore!=restore) throw new IllegalArgumentException("Mixed brush modes in one gesture");
            checkpoint.run();
            double ax=s.x1*(width-1), ay=s.y1*(height-1), bx=s.x2*(width-1), by=s.y2*(height-1);
            double r=Math.max(.5, s.radius*Math.min(width,height));
            // A one-pixel tap always hits the nearest pixel, including half-pixel coordinates.
            if (r<=.501) { ax=Math.round(ax);ay=Math.round(ay);bx=Math.round(bx);by=Math.round(by); }
            double dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
            int l=Math.max(0,(int)Math.floor(Math.min(ax,bx)-r)), rr=Math.min(width-1,(int)Math.ceil(Math.max(ax,bx)+r));
            int t=Math.max(0,(int)Math.floor(Math.min(ay,by)-r)), b=Math.min(height-1,(int)Math.ceil(Math.max(ay,by)+r));
            for (int y=t;y<=b;y++) { if ((y & 31)==0) checkpoint.run(); for(int x=l;x<=rr;x++) {
                double f=len==0 ? 0 : Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/len));
                double ex=x-(ax+f*dx),ey=y-(ay+f*dy);
                double distance=Math.sqrt(ex*ex+ey*ey);
                if (distance<=r) {
                    double inner=s.hardness*r;
                    double weight=distance<=inner || s.hardness==1f ? 1 : (r-distance)/(r-inner);
                    weight=weight*weight*(3-2*weight);
                    int amount=(int)Math.round(255*s.strength*weight), i=y*width+x;
                    if (amount>(coverage[i]&255)) coverage[i]=(byte)amount;
                }
            } }
        }
        for (int i=0;i<result.length;i++) {
            if ((i & 4095)==0) checkpoint.run();
            int amount=coverage[i]&255;
            if (amount==0) continue;
            int alpha=current[i]>>>24;
            int target=restore ? Math.max(alpha,original[i]>>>24) : 0;
            int next=(int)Math.round(alpha+(target-alpha)*(amount/255.0));
            result[i]=(next<<24)|(current[i]&0x00ffffff);
        }
        return result;
    }
}
