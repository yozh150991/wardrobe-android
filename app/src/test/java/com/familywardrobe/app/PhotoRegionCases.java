package com.familywardrobe.app;

import java.util.*;
import java.util.concurrent.CancellationException;

public final class PhotoRegionCases {
    private static int checks;
    private static final Runnable ACTIVE=() -> {};
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejected(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static PhotoRegionRules.Selection select(int[] p,int w,int h,float x,float y,float t) {
        return PhotoRegionRules.select(p,w,h,x,y,t,ACTIVE);
    }
    public static void boundariesAndColour() {
        int white=0xffeeeeee, blue=0xff003366;
        int[] pixels={white,white,blue, white,blue,white, blue,white,white};
        int[] unchanged=pixels.clone();
        PhotoRegionRules.Selection s=select(pixels,3,3,0,0,0);
        check(s.getCount()==3,"diagonal areas stay separate");
        check(s.leavesVisiblePixels(pixels,ACTIVE),"subject remains");
        check(!s.contains(-1,0) && !s.contains(3,2) && !s.contains(2,2),"bounds/disconnected area");
        int[] erased=s.erase(pixels,ACTIVE);
        for(int i=0;i<pixels.length;i++) {
            check((erased[i]&0xffffff)==(pixels[i]&0xffffff),"preserve RGB");
            check((erased[i]>>>24)==(i==0||i==1||i==3?0:255),"only selected alpha");
        }
        byte[] mask=s.alphaMask(4,ACTIVE);
        check(Arrays.equals(mask,new byte[]{-1,-1,0,0,-1,0,0,0,0,0,0,0}),"row padding");
        mask[0]=0; erased[0]=0;
        check(s.contains(0,0) && (s.alphaMask(4,ACTIVE)[0]&255)==255,"defensive selection");
        check(Arrays.equals(pixels,unchanged),"input unchanged");
        int[] transparent={0xff123456,0x00123456,0x80123456};
        check(select(transparent,3,1,0,0,1).getCount()==1,"transparent barrier even at max tolerance");
        check(select(transparent,3,1,.5f,0,0).getCount()==0,"transparent seed is empty");
        check(select(new int[]{0x01123456,0x80123456,0xff123456},3,1,0,0,0).getCount()==3,"alpha is not colour");
        check(select(new int[]{white,blue,blue,white},2,2,1,0,0).getCount()==1,"rows cannot wrap");
        int[] gradient={0xff000000,0xff0a0a0a,0xff141414,0xff1e1e1e,0xff282828};
        check(select(gradient,5,1,0,0,15/255f).getCount()==2,"fixed seed prevents gradient drift");
        check(select(gradient,5,1,1,0,15/255f).getCount()==2,"right endpoint");
        s=select(gradient,5,1,0,0,1);
        check(s.getCount()==5 && !s.leavesVisiblePixels(gradient,ACTIVE),"full-image removal is identified");
        check(select(new int[]{white},1,1,1,1,0).getCount()==1,"single pixel");
        rejected(() -> select(null,1,1,0,0,0));
        rejected(() -> select(new int[1],2,1,0,0,0));
        rejected(() -> select(new int[1],16_000_001,1,0,0,0));
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f}) {
            rejected(() -> select(pixels,3,3,v,0,0));
            rejected(() -> select(pixels,3,3,0,v,0));
            rejected(() -> select(pixels,3,3,0,0,v));
            rejected(() -> PhotoSessionRules.Action.region(0,0,v));
        }
        PhotoRegionRules.Selection fixed=select(pixels,3,3,0,0,0);
        rejected(() -> fixed.alphaMask(2,ACTIVE));
        rejected(() -> fixed.erase(new int[1],ACTIVE));
    }
    private static int root(int[] parents,int x) { while(parents[x]!=x) x=parents[x]; return x; }
    private static void join(int[] parents,int a,int b) { parents[root(parents,a)]=root(parents,b); }
    public static void allSmallTopologies() {
        // Independent connected-component oracle: union all equal-colour neighbours, then inspect each seed.
        for(int pattern=0;pattern<512;pattern++) {
            int[] p=new int[9], parents=new int[9];
            for(int i=0;i<9;i++) { p[i]=((pattern>>i)&1)==0?0xffe8e8e8:0xff112244;parents[i]=i; }
            for(int i=0;i<9;i++) {
                if(i%3!=0 && p[i]==p[i-1]) join(parents,i,i-1);
                if(i>=3 && p[i]==p[i-3]) join(parents,i,i-3);
            }
            for(int seed=0;seed<9;seed++) {
                PhotoRegionRules.Selection s=select(p,3,3,(seed%3)/2f,(seed/3)/2f,0);
                int expected=0;
                for(int i=0;i<9;i++) {
                    boolean same=root(parents,seed)==root(parents,i);
                    if(same) expected++;
                    check(s.contains(i%3,i/3)==same,"four-connected topology");
                }
                check(s.getCount()==expected,"component count");
            }
        }
    }
    public static void historyAndCancellation() {
        int[] source=new int[35];Arrays.fill(source,0xffdddddd);
        for(int y=1;y<4;y++) for(int x=2;x<5;x++) source[y*7+x]=0xff3b6982;
        PhotoSessionRules.Action[] actions={PhotoSessionRules.Action.region(0,0,0),PhotoSessionRules.Action.rotate(1),
            PhotoSessionRules.Action.crop(0,.1f,1,.9f),PhotoSessionRules.Action.stroke(Collections.singletonList(
                new PhotoMaskRules.Stroke(0,0,0,0,.15f,true)))};
        List<PhotoSessionRules.Frame> frames=new ArrayList<>();
        PhotoSessionRules.Frame frame=PhotoSessionRules.start(source,7,5);frames.add(frame);
        PhotoEditHistory history=new PhotoEditHistory();
        for(int i=0;i<actions.length;i++) {
            frame=PhotoSessionRules.apply(frame,actions[i],ACTIVE);frames.add(frame);history=history.append(Integer.toString(i));
        }
        check(frames.get(1).pixels[0]>>>24==0 && frames.get(1).pixels[9]>>>24==255,"region removes background only");
        check(Arrays.equals(frames.get(1).original,source),"restore source retained");
        check(frames.get(4).pixels[0]>>>24==255,"brush restores region after geometry");
        PhotoEditHistory restored=new PhotoEditHistory(history.getSteps(),history.getCursor());
        for(int n=4;n>=0;n--) {
            assertReplay(source,restored,actions,frames.get(n)); restored=restored.undo();
        }
        for(int n=0;n<=4;n++) {
            assertReplay(source,restored,actions,frames.get(n)); restored=restored.redo();
        }
        check(!history.undo().append("0").canRedo(),"new region branches history");
        int[] large=new int[128*128];Arrays.fill(large,0xffeeeeee);
        int[] calls={0};
        try { PhotoRegionRules.select(large,128,128,0,0,0,() -> { if(++calls[0]==3) throw new CancellationException(); });throw new AssertionError("Not cancelled"); }
        catch(CancellationException expected) { checks++; }
        check(large[0]==0xffeeeeee && large[large.length-1]==0xffeeeeee,"cancel preserves source");
        PhotoRegionRules.Selection selected=select(large,128,128,0,0,0);
        for(Runnable operation:Arrays.<Runnable>asList(
            () -> selected.erase(large,() -> {throw new CancellationException();}),
            () -> selected.alphaMask(128,() -> {throw new CancellationException();}))) {
            try {operation.run();throw new AssertionError("Not cancelled");}catch(CancellationException expected){checks++;}
        }
    }
    private static void assertReplay(int[] source,PhotoEditHistory history,PhotoSessionRules.Action[] actions,PhotoSessionRules.Frame expected) {
        List<PhotoSessionRules.Action> applied=new ArrayList<>();
        for(String step:history.getAppliedSteps()) applied.add(actions[Integer.parseInt(step)]);
        PhotoSessionRules.Frame actual=PhotoSessionRules.replay(source,7,5,applied,ACTIVE);
        check(expected.width==actual.width && expected.height==actual.height,"history dimensions");
        check(Arrays.equals(expected.pixels,actual.pixels),"history pixels");
        check(Arrays.equals(expected.original,actual.original),"history restoration source");
    }
    public static void cameraAndInspection() {
        int w=1600,h=1200;int[] source=new int[w*h];Arrays.fill(source,0xffeeeeee);
        for(int y=200;y<1100;y++) Arrays.fill(source,y*w+500,y*w+900,0xff316292);
        PhotoRegionRules.Selection selected=select(source,w,h,0,0,0);
        check(selected.getCount()==w*h-400*900,"camera region, queue grows and wraps");
        check(selected.leavesVisiblePixels(source,ACTIVE),"camera subject retained");
        int[] output=selected.erase(source,ACTIVE);
        check(output[0]>>>24==0 && output[w*h-1]>>>24==0 && output[700*w+700]==0xff316292,"camera output");
        check(source[0]==0xffeeeeee,"camera input unchanged");
        for(float canvas:new float[]{100,320,720}) for(float left:new float[]{-1000,-100,0,20,400})
            for(float width:new float[]{1,80,800,4000}) {
                float previous=-1;
                for(int percent=0;percent<=100;percent++) {
                    float split=PhotoInspectionRules.split(left,width,canvas,percent/100f);
                    check(split>=0 && split<=canvas && split>=previous,"divider bounded/monotonic at zoom and pan");previous=split;
                }
                check(PhotoInspectionRules.split(left,width,canvas,0)==Math.max(0,Math.min(canvas,left)),"zero shows result");
                check(PhotoInspectionRules.split(left,width,canvas,1)==Math.max(Math.max(0,Math.min(canvas,left)),Math.min(canvas,left+width)),"one shows source");
            }
        rejected(() -> PhotoInspectionRules.split(0,0,320,.5f));
        rejected(() -> PhotoInspectionRules.split(0,100,320,Float.NaN));
    }
    public static void main(String[] args) {
        boundariesAndColour();allSmallTopologies();historyAndCancellation();cameraAndInspection();
        System.out.println("PASS: "+checks+" photo region/inspection assertions (connectivity, tolerance, transparency, history, cancellation, camera and divider)");
    }
}
