package com.familywardrobe.app;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Host-executable production rules, independently checked against small raster oracles. */
public final class PhotoPrecisionCases {
    private static int checks;
    private static final Runnable ACTIVE=()->{};
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Precision check "+checks); }
    private static void rejects(Runnable block) { try { block.run(); throw new AssertionError("Must reject"); } catch (IllegalArgumentException expected) { checks++; } }
    private static float[] rectangle(float l,float t,float r,float b) { return new float[]{l,t,r,t,r,b,l,b}; }
    private static int[] oracle(int[] source,int[] original,byte[] protection,int w,int h,int offset,int feather) {
        int[] alpha=new int[source.length];for(int i=0;i<alpha.length;i++) alpha[i]=source[i]>>>24;
        if(offset!=0) {
            int[] changed=new int[alpha.length];int radius=Math.abs(offset);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int value=offset>0 ? 0 : 255;
                for(int dy=-radius;dy<=radius;dy++) for(int dx=-radius;dx<=radius;dx++) {
                    int a=x+dx<0 || x+dx>=w || y+dy<0 || y+dy>=h ? 0 : alpha[(y+dy)*w+x+dx];
                    value=offset>0 ? Math.max(value,a) : Math.min(value,a);
                }
                changed[y*w+x]=value;
            }
            alpha=changed;
        }
        for(int direction=0;feather>0 && direction<2;direction++) {
            int[] changed=new int[alpha.length];int divisor=2*feather+1;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int sum=0;
                for(int d=-feather;d<=feather;d++) {
                    int nx=x+(direction==0 ? d : 0),ny=y+(direction==1 ? d : 0);
                    if(nx>=0 && nx<w && ny>=0 && ny<h) sum+=alpha[ny*w+nx];
                }
                changed[y*w+x]=(sum+divisor/2)/divisor;
            }
            alpha=changed;
        }
        int[] result=source.clone();
        for(int i=0;i<result.length;i++) if(protection[i]==0) result[i]=(Math.min(alpha[i],original[i]>>>24)<<24)|(source[i]&0xffffff);
        return result;
    }
    private static void edges() {
        Random random=new Random(138);
        for(int sample=0;sample<120;sample++) {
            int w=1+random.nextInt(26),h=1+random.nextInt(24),offset=random.nextInt(17)-8,feather=random.nextInt(7);
            int[] source=new int[w*h],original=new int[w*h];byte[] protection=new byte[w*h];
            for(int i=0;i<source.length;i++) {
                int alpha=random.nextInt(256),rgb=random.nextInt(1<<24);
                original[i]=(alpha<<24)|rgb;source[i]=(random.nextInt(alpha+1)<<24)|rgb;
                if(random.nextInt(9)==0) protection[i]=(byte)255;
            }
            int[] before=source.clone();byte[] maskBefore=protection.clone();
            int[] result=PhotoPrecisionRules.edges(source,original,protection,w,h,offset,feather,ACTIVE);
            check(Arrays.equals(result,oracle(source,original,protection,w,h,offset,feather)));
            for(int i=0;i<source.length;i++) {
                check((result[i]&0xffffff)==(source[i]&0xffffff));
                check((result[i]>>>24)<=(original[i]>>>24));
                if(protection[i]!=0) check(result[i]==source[i]);
            }
            check(Arrays.equals(before,source));check(Arrays.equals(maskBefore,protection));
        }
        int[] dot=new int[81],original=new int[81];Arrays.fill(original,0xffeadcd0);dot[40]=original[40];
        int[] expanded=PhotoPrecisionRules.edges(dot,original,new byte[81],9,9,1,0,ACTIVE);
        check(Arrays.stream(expanded).filter(x->x>>>24>0).count()==9);
        int[] limited=PhotoPrecisionRules.edges(dot,dot,new byte[81],9,9,8,6,ACTIVE);
        for(int i=0;i<81;i++) if(i!=40) check(limited[i]>>>24==0);
        rejects(()->PhotoPrecisionRules.validateEdges(-9,0));rejects(()->PhotoPrecisionRules.validateEdges(0,7));
    }
    private static void polygon() {
        for(int w=1;w<=35;w++) for(int h=1;h<=29;h++) {
            byte[] all=PhotoPrecisionRules.polygon(w,h,rectangle(0,0,1,1),ACTIVE);
            byte[] half=PhotoPrecisionRules.polygon(w,h,rectangle(0,0,.5f,1),ACTIVE);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                check(all[y*w+x]!=0);
                check((half[y*w+x]!=0)==(x+.5<w*.5));
            }
        }
        byte[] triangle=PhotoPrecisionRules.polygon(10,10,new float[]{0,0,1,0,0,1},ACTIVE);
        check(triangle[0]!=0 && triangle[99]==0);
        byte[] flat=PhotoPrecisionRules.polygon(10,10,new float[]{0,.5f,.5f,.5f,1,.5f},ACTIVE);
        for(byte b:flat) check(b==0);
        rejects(()->PhotoPrecisionRules.polygon(10,10,new float[]{0,0,1,1},ACTIVE));
        rejects(()->PhotoPrecisionRules.validatePolygon(new float[]{0,0,1,0,Float.NaN,1}));
        rejects(()->PhotoSessionRules.Action.polygon(rectangle(0,0,1,1),"invalid"));
        rejects(()->PhotoPrecisionRules.validatePolygon(new float[260]));
    }
    private static void protection() {
        int w=10,h=8;int[] source=new int[w*h];Arrays.fill(source,0xff326591);
        PhotoSessionRules.Frame initial=PhotoSessionRules.start(source,w,h);
        PhotoSessionRules.Action protect=PhotoSessionRules.Action.polygon(rectangle(0,0,.5f,1),"protect");
        PhotoSessionRules.Frame locked=PhotoSessionRules.apply(initial,protect,ACTIVE);
        check(Arrays.equals(initial.pixels,locked.pixels));for(byte b:initial.protection) check(b==0);
        PhotoSessionRules.Frame erased=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.polygon(rectangle(0,0,1,1),"erase"),ACTIVE);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) check((erased.pixels[y*w+x]>>>24)==(x<5 ? 255 : 0));
        PhotoSessionRules.Frame brushed=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.stroke(List.of(new PhotoMaskRules.Stroke(.5f,.5f,.5f,.5f,.5f,false))),ACTIVE);
        for(int y=0;y<h;y++) for(int x=0;x<5;x++) check(brushed.pixels[y*w+x]==source[y*w+x]);
        PhotoSessionRules.Frame region=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.region(.9f,.5f,1f),ACTIVE);
        check(Arrays.equals(region.pixels,erased.pixels));
        check(PhotoRegionRules.select(locked.pixels,w,h,.2f,.5f,1f,locked.protection,ACTIVE).getCount()==0);
        PhotoSessionRules.Frame refined=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.edges(-8,6),ACTIVE);
        for(int y=0;y<h;y++) for(int x=0;x<5;x++) check(refined.pixels[y*w+x]==source[y*w+x]);
        PhotoSessionRules.Frame restored=PhotoSessionRules.apply(erased,PhotoSessionRules.Action.polygon(rectangle(0,0,1,1),"restore"),ACTIVE);
        check(Arrays.equals(restored.pixels,source));
        PhotoSessionRules.Frame turned=PhotoSessionRules.apply(erased,PhotoSessionRules.Action.rotate(1),ACTIVE);
        check(turned.width==h && turned.height==w);
        for(int y=0;y<w;y++) for(int x=0;x<h;x++) check((turned.protection[y*h+x]!=0)==(y<5));
        PhotoSessionRules.Frame cropped=PhotoSessionRules.apply(turned,PhotoSessionRules.Action.crop(0,0,1,.5f),ACTIVE);
        for(byte b:cropped.protection) check(b!=0);
        PhotoSessionRules.Frame cleared=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.clearProtection(),ACTIVE);
        for(byte b:cleared.protection) check(b==0);
        PhotoSessionRules.Frame unlocked=PhotoSessionRules.apply(locked,PhotoSessionRules.Action.polygon(rectangle(0,0,1,1),"unprotect"),ACTIVE);
        check(Arrays.equals(unlocked.protection,initial.protection));check(Arrays.equals(unlocked.pixels,source));
        check(Arrays.equals(PhotoSessionRules.replay(source,w,h,List.of(protect,PhotoSessionRules.Action.rotate(1)),ACTIVE).protection,turned.protection));
        float[] middle=rectangle(.4f,0,.6f,1);
        PhotoSessionRules.Frame barrier=PhotoSessionRules.apply(initial,PhotoSessionRules.Action.polygon(middle,"protect"),ACTIVE);
        PhotoRegionRules.Selection left=PhotoRegionRules.select(source,w,h,0,.5f,1f,barrier.protection,ACTIVE);
        check(left.getCount()==32);check(!left.contains(9,4));
    }
    private static void cancellationAndLargeImage() {
        int[] source=new int[2048*2048];Arrays.fill(source,0xff998866);
        PhotoSessionRules.Frame frame=PhotoSessionRules.start(source,2048,2048);
        long started=System.nanoTime();
        PhotoSessionRules.Frame result=PhotoSessionRules.apply(frame,PhotoSessionRules.Action.edges(-2,2),ACTIVE);
        check(result.pixels.length==source.length);check(result.pixels[1024*2048+1024]==source[0]);
        for(Runnable operation:List.<Runnable>of(
            ()->PhotoPrecisionRules.polygon(10,10,rectangle(0,0,1,1),()->{throw new CancellationException();}),
            ()->PhotoSessionRules.apply(frame,PhotoSessionRules.Action.edges(1,1),()->{throw new CancellationException();}))) {
            try {operation.run();throw new AssertionError("Must cancel");}catch(CancellationException expected){checks++;}
        }
        System.out.println("Synthetic 2048-square edge pass under 256 MiB: "+((System.nanoTime()-started)/1_000_000)+" ms (host, not Android)");
    }
    public static void main(String[] args) {
        edges();polygon();protection();cancellationAndLargeImage();
        System.out.println("PASS: "+checks+" precision assertions (edge oracle, polygon coverage, protection, geometry, cancellation)");
    }
}
