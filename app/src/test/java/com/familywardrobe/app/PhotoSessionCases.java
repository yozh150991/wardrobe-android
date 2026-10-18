package com.familywardrobe.app;

import java.util.*;
import java.util.concurrent.CancellationException;

public final class PhotoSessionCases {
    private static int checks;
    private static void check(boolean value) { checks++;if(!value) throw new AssertionError("Photo session check "+checks); }
    private static void rejects(Runnable action) { try { action.run();throw new AssertionError("Expected invalid argument"); } catch(IllegalArgumentException expected) { checks++; } }
    private static final Runnable ACTIVE=()->{};
    private static PhotoMaskRules.Stroke dot(float radius,boolean restore,float hardness,float strength) {
        return new PhotoMaskRules.Stroke(.5f,.5f,.5f,.5f,radius,restore,hardness,strength);
    }
    public static void softBrush() {
        int side=101;int[] source=new int[side*side];Arrays.fill(source,0xff6b8194);
        PhotoMaskRules.Stroke partial=dot(.08f,false,1f,.4f);
        int center=50*side+50;
        int[] once=PhotoMaskRules.paintStroke(source,source,side,side,List.of(partial),ACTIVE);
        check((once[center]>>>24)==153);
        int[] sameGesture=PhotoMaskRules.paintStroke(source,source,side,side,Collections.nCopies(100,partial),ACTIVE);
        check(Arrays.equals(once,sameGesture));
        int[] twice=PhotoMaskRules.paintStroke(once,source,side,side,List.of(partial),ACTIVE);
        check((twice[center]>>>24)==92);
        int[] restored=PhotoMaskRules.paintStroke(twice,source,side,side,List.of(dot(.08f,true,1f,.4f)),ACTIVE);
        check((restored[center]>>>24)==157);
        int[] full=PhotoMaskRules.paintStroke(restored,source,side,side,List.of(dot(.08f,true,1f,1f)),ACTIVE);
        check(Arrays.equals(full,source));
        int[] soft=PhotoMaskRules.paintStroke(source,source,side,side,List.of(dot(.08f,false,0f,1f)),ACTIVE);
        check((soft[center]>>>24)==0);
        int previous=0;
        for(int dx=1;dx<=9;dx++) {
            int alpha=soft[center+dx]>>>24;check(alpha>=previous);previous=alpha;
            check(alpha==(soft[center-dx]>>>24));
        }
        check((soft[center+4]>>>24)>100 && (soft[center+4]>>>24)<150);
        check((soft[center+9]>>>24)==255);
        int[] hard=PhotoMaskRules.paintStroke(source,source,side,side,List.of(dot(.08f,false,1f,1f)),ACTIVE);
        check((hard[center+7]>>>24)==0 && (soft[center+7]>>>24)>200);
        PhotoMaskRules.Stroke oneLine=new PhotoMaskRules.Stroke(.2f,.5f,.8f,.5f,.08f,false,.3f,.4f);
        List<PhotoMaskRules.Stroke> split=new ArrayList<>();
        for(int i=2;i<8;i++) split.add(new PhotoMaskRules.Stroke(i/10f,.5f,(i+1)/10f,.5f,.08f,false,.3f,.4f));
        int[] line=PhotoMaskRules.paintStroke(source,source,side,side,List.of(oneLine),ACTIVE);
        int[] splitLine=PhotoMaskRules.paintStroke(source,source,side,side,split,ACTIVE);
        check(Arrays.equals(line,splitLine));
        for(int i=0;i<source.length;i++) { check((soft[i]&0xffffff)==(source[i]&0xffffff));check(source[i]==0xff6b8194); }
        int[] transparent=source.clone();transparent[center]=0x006b8194;
        int[] noNewPixels=PhotoMaskRules.paintStroke(transparent,transparent,side,side,List.of(dot(.08f,true,0f,1f)),ACTIVE);
        check((noNewPixels[center]>>>24)==0);
        check(Arrays.equals(source,PhotoMaskRules.paintStroke(source,source,side,side,List.of(dot(.08f,false,.5f,0f)),ACTIVE)));
        rejects(()->dot(.08f,false,-.1f,1f));rejects(()->dot(.08f,false,Float.NaN,1f));rejects(()->dot(.08f,false,0f,1.1f));
        rejects(()->PhotoMaskRules.paintStroke(source,source,side,side,List.of(partial,dot(.08f,true,1f,1f)),ACTIVE));
        try { PhotoMaskRules.paintStroke(source,source,side,side,List.of(partial),()->{throw new CancellationException();});throw new AssertionError(); }
        catch(CancellationException expected) { checks++; }
    }
    public static void unifiedHistory() {
        int[] source=new int[24];for(int i=0;i<24;i++) source[i]=0xff000000|(i+1);
        PhotoMaskRules.Stroke erase=new PhotoMaskRules.Stroke(0,0,0,0,.125f,false);
        PhotoMaskRules.Stroke restore=new PhotoMaskRules.Stroke(1,0,1,0,1/6f,true);
        Map<String,PhotoSessionRules.Action> actions=Map.of(
            "stroke",PhotoSessionRules.Action.stroke(List.of(erase)), "rotate",PhotoSessionRules.Action.rotate(1),
            "crop",PhotoSessionRules.Action.crop(.25f,0,1,.5f), "restore",PhotoSessionRules.Action.stroke(List.of(restore)));
        PhotoEditHistory history=new PhotoEditHistory().append("stroke").append("rotate").append("crop").append("restore");
        PhotoSessionRules.Frame finalFrame=replay(source,history,actions);
        check(finalFrame.width==3 && finalFrame.height==3);
        int[] golden={13,7,1,14,8,2,15,9,3};
        for(int i=0;i<golden.length;i++) check(finalFrame.pixels[i]==(0xff000000|golden[i]));
        history=history.undo();PhotoSessionRules.Frame cropped=replay(source,history,actions);
        check(cropped.pixels[2]==1 && cropped.original[2]==0xff000001);
        history=history.undo();PhotoSessionRules.Frame rotated=replay(source,history,actions);
        check(rotated.width==4 && rotated.height==6 && rotated.pixels[3]==1);
        history=history.undo();PhotoSessionRules.Frame erased=replay(source,history,actions);
        check(erased.width==6 && erased.height==4 && erased.pixels[0]==1);
        history=history.undo();check(Arrays.equals(source,replay(source,history,actions).pixels));
        for(int i=0;i<4;i++) history=history.redo();
        check(Arrays.equals(finalFrame.pixels,replay(source,history,actions).pixels));
        PhotoEditHistory branch=history.undo().undo().append("rotate");
        check(!branch.canRedo());
        PhotoSessionRules.Frame branched=replay(source,branch,actions);
        check(branched.width==6 && branched.height==4 && branched.pixels[23]==1);
        PhotoEditHistory restoredHistory=new PhotoEditHistory(history.undo().getSteps(),3);
        check(Arrays.equals(cropped.pixels,replay(source,restoredHistory,actions).pixels));
        check(Arrays.equals(finalFrame.pixels,replay(source,restoredHistory.redo(),actions).pixels));
        PhotoSessionRules.Frame start=PhotoSessionRules.start(source,6,4), turn=start;
        for(int i=0;i<4;i++) turn=PhotoSessionRules.apply(turn,PhotoSessionRules.Action.rotate(1),ACTIVE);
        check(Arrays.equals(source,turn.pixels));
        check(Arrays.equals(source,start.pixels) && source[0]==0xff000001);
        PhotoSessionRules.Frame minimum=PhotoSessionRules.apply(start,PhotoSessionRules.Action.crop(.99f,.99f,1,1),ACTIVE);
        check(minimum.width==1 && minimum.height==1 && minimum.pixels[0]==0xff000018);
        rejects(()->PhotoSessionRules.Action.crop(.8f,0,.2f,1));
        rejects(()->PhotoSessionRules.start(source,0,24));
        rejects(()->PhotoSessionRules.replay(source,6,4,Collections.nCopies(129,PhotoSessionRules.Action.rotate(1)),ACTIVE));
        rejects(()->PhotoSessionRules.replay(source,6,4,List.of(PhotoSessionRules.Action.stroke(Collections.nCopies(2048,erase)),PhotoSessionRules.Action.stroke(List.of(erase))),ACTIVE));
        try { PhotoSessionRules.replay(source,6,4,List.of(PhotoSessionRules.Action.rotate(1)),()->{throw new CancellationException();});throw new AssertionError(); }
        catch(CancellationException expected) { checks++; }
    }
    private static PhotoSessionRules.Frame replay(int[] source,PhotoEditHistory history,Map<String,PhotoSessionRules.Action> all) {
        List<PhotoSessionRules.Action> list=new ArrayList<>();for(String id:history.getAppliedSteps()) list.add(all.get(id));
        return PhotoSessionRules.replay(source,6,4,list,ACTIVE);
    }
    public static void loupe() {
        for(float width:new float[]{100,320,720}) for(float height:new float[]{50,180,330})
            for(int ix=0;ix<=10;ix++) for(int iy=0;iy<=10;iy++) {
                float x=ix*width/10,y=iy*height/10;float[] b=PhotoLoupeRules.bounds(width,height,x,y,112,36);
                check(b[2]>0 && b[0]>=0 && b[1]>=0 && b[0]+b[2]<=width+.001 && b[1]+b[2]<=height+.001);
                check(x<b[0] || x>b[0]+b[2] || y<b[1] || y>b[1]+b[2]);
            }
        float[] above=PhotoLoupeRules.bounds(320,330,160,250,112,36);
        check(above[1]+above[2]<=214);
        float[] below=PhotoLoupeRules.bounds(320,330,160,0,112,36);
        check(below[1]>=36);
        rejects(()->PhotoLoupeRules.bounds(0,330,0,0,112,36));
        rejects(()->PhotoLoupeRules.bounds(320,330,Float.NaN,0,112,36));
    }
    public static void cameraFrame() {
        int width=1600,height=1200;int[] source=new int[width*height];Arrays.fill(source,0xff6b8194);
        List<PhotoSessionRules.Action> actions=new ArrayList<>();
        for(int i=1;i<=32;i++) {
            float x=i/33f;
            actions.add(PhotoSessionRules.Action.stroke(List.of(new PhotoMaskRules.Stroke(x,.5f,x,.5f,1/2400f,false,0f,.4f))));
        }
        PhotoSessionRules.Frame frame=PhotoSessionRules.replay(source,width,height,actions,ACTIVE);
        int changed=0;for(int pixel:frame.pixels) if((pixel>>>24)<255) changed++;
        check(changed==32);check(frame.width==width && frame.height==height);check(Arrays.equals(source,frame.original));
    }
    public static void main(String[] args) {
        softBrush();unifiedHistory();loupe();cameraFrame();
        System.out.println("PASS: "+checks+" photo session assertions (soft brush, event-rate independence, cross-tool history, restoration and loupe)");
    }
}
