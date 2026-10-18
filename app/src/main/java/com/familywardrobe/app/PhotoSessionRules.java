package com.familywardrobe.app;

import java.util.*;

/** Replayable photo operations. Tool navigation is deliberately not an operation. */
public final class PhotoSessionRules {
    private PhotoSessionRules() {}
    public static final class Frame {
        public final int width,height;
        public final int[] pixels,original;
        public final byte[] protection;
        private Frame(int width,int height,int[] pixels,int[] original,byte[] protection) {
            this.width=width;this.height=height;this.pixels=pixels;this.original=original;this.protection=protection;
        }
    }
    public static final class Action {
        private final int turns;
        private final float[] crop;
        private final List<PhotoMaskRules.Stroke> stroke;
        private final float[] region;
        private final float[] polygon;
        private final String mode;
        private final int[] edges;
        private Action(int turns,float[] crop,List<PhotoMaskRules.Stroke> stroke) {
            this(turns,crop,stroke,null);
        }
        private Action(int turns,float[] crop,List<PhotoMaskRules.Stroke> stroke,float[] region) {
            this(turns,crop,stroke,region,null,null,null);
        }
        private Action(int turns,float[] crop,List<PhotoMaskRules.Stroke> stroke,float[] region,float[] polygon,String mode,int[] edges) {
            this.turns=turns;this.crop=crop;this.stroke=stroke;this.region=region;this.polygon=polygon;this.mode=mode;this.edges=edges;
        }
        public static Action rotate(int turns) { return new Action(PhotoGeometry.turns(turns),null,null); }
        public static Action crop(float left,float top,float right,float bottom) {
            PhotoGeometry.crop(1,1,left,top,right,bottom);
            return new Action(0,new float[]{left,top,right,bottom},null);
        }
        public static Action stroke(List<PhotoMaskRules.Stroke> stroke) {
            if (stroke==null || stroke.isEmpty() || stroke.size()>2048) throw new IllegalArgumentException();
            return new Action(0,null,Collections.unmodifiableList(new ArrayList<>(stroke)));
        }
        public static Action region(float x,float y,float tolerance) {
            PhotoRegionRules.validatePoint(x,y,tolerance);
            return new Action(0,null,null,new float[]{x,y,tolerance});
        }
        public static Action polygon(float[] points,String mode) {
            PhotoPrecisionRules.validatePolygon(points);
            if (!Arrays.asList("erase","restore","protect","unprotect").contains(mode)) throw new IllegalArgumentException();
            return new Action(0,null,null,null,points.clone(),mode,null);
        }
        public static Action clearProtection() { return new Action(0,null,null,null,null,"clear",null); }
        public static Action edges(int offset,int feather) {
            PhotoPrecisionRules.validateEdges(offset,feather);
            return new Action(0,null,null,null,null,null,new int[]{offset,feather});
        }
    }
    public static Frame start(int[] source,int width,int height) {
        if (source==null || width<1 || height<1 || (long)width*height>16_000_000 || source.length!=(long)width*height) throw new IllegalArgumentException();
        int[] pixels=source.clone();
        return new Frame(width,height,pixels,pixels,new byte[pixels.length]);
    }
    public static Frame apply(Frame frame,Action action,Runnable checkpoint) {
        checkpoint.run();
        if (action.stroke!=null) return new Frame(frame.width,frame.height,
            protectedResult(frame,PhotoMaskRules.paintStroke(frame.pixels,frame.original,frame.width,frame.height,action.stroke,checkpoint),checkpoint),frame.original,frame.protection);
        if (action.region!=null) {
            float[] r=action.region;
            PhotoRegionRules.Selection selection=PhotoRegionRules.select(frame.pixels,frame.width,frame.height,r[0],r[1],r[2],frame.protection,checkpoint);
            return new Frame(frame.width,frame.height,selection.erase(frame.pixels,checkpoint),frame.original,frame.protection);
        }
        if (action.edges!=null) return new Frame(frame.width,frame.height,
            PhotoPrecisionRules.edges(frame.pixels,frame.original,frame.protection,frame.width,frame.height,action.edges[0],action.edges[1],checkpoint),frame.original,frame.protection);
        if ("clear".equals(action.mode)) return new Frame(frame.width,frame.height,frame.pixels,frame.original,new byte[frame.pixels.length]);
        if (action.polygon!=null) {
            byte[] selection=PhotoPrecisionRules.polygon(frame.width,frame.height,action.polygon,checkpoint);
            boolean protect="protect".equals(action.mode), unprotect="unprotect".equals(action.mode);
            int[] pixels=protect || unprotect ? frame.pixels : frame.pixels.clone();
            byte[] protection=protect || unprotect ? frame.protection.clone() : frame.protection;
            for (int i=0;i<pixels.length;i++) {
                if ((i & 4095)==0) checkpoint.run();
                if (selection[i]==0) continue;
                if (protect || unprotect) protection[i]=protect ? (byte)255 : 0;
                else if (protection[i]==0) pixels[i]="restore".equals(action.mode) ? frame.original[i] : pixels[i]&0xffffff;
            }
            return new Frame(frame.width,frame.height,pixels,frame.original,protection);
        }
        if (action.crop!=null) {
            float[] c=action.crop;int[] box=PhotoGeometry.crop(frame.width,frame.height,c[0],c[1],c[2],c[3]);
            int[] pixels=crop(frame.pixels,frame.width,box,checkpoint);
            int[] original=frame.original==frame.pixels ? pixels : crop(frame.original,frame.width,box,checkpoint);
            return new Frame(box[2],box[3],pixels,original,cropMask(frame.protection,frame.width,box,checkpoint));
        }
        int turns=action.turns;
        if (turns==0) return frame;
        int[] pixels=rotate(frame.pixels,frame.width,frame.height,turns,checkpoint);
        int[] original=frame.original==frame.pixels ? pixels : rotate(frame.original,frame.width,frame.height,turns,checkpoint);
        return new Frame(turns%2==0 ? frame.width : frame.height,turns%2==0 ? frame.height : frame.width,pixels,original,rotateMask(frame.protection,frame.width,frame.height,turns,checkpoint));
    }
    public static Frame replay(int[] source,int width,int height,List<Action> actions,Runnable checkpoint) {
        if (actions==null || actions.size()>PhotoEditHistory.MAX_STEPS) throw new IllegalArgumentException();
        Frame frame=start(source,width,height);
        int segments=0;
        for (Action action:actions) {
            if (action.stroke!=null) segments+=action.stroke.size();
            if (segments>2048) throw new IllegalArgumentException();
            frame=apply(frame,action,checkpoint);
        }
        return frame;
    }
    private static int[] crop(int[] pixels,int width,int[] box,Runnable checkpoint) {
        int[] out=new int[box[2]*box[3]];
        for (int y=0;y<box[3];y++) {
            checkpoint.run();System.arraycopy(pixels,(y+box[1])*width+box[0],out,y*box[2],box[2]);
        }
        return out;
    }
    private static int[] protectedResult(Frame frame,int[] pixels,Runnable checkpoint) {
        for (int i=0;i<pixels.length;i++) {
            if ((i & 4095)==0) checkpoint.run();
            if (frame.protection[i]!=0) pixels[i]=frame.pixels[i];
        }
        return pixels;
    }
    private static byte[] cropMask(byte[] source,int width,int[] box,Runnable checkpoint) {
        byte[] out=new byte[box[2]*box[3]];
        for (int y=0;y<box[3];y++) { checkpoint.run();System.arraycopy(source,(y+box[1])*width+box[0],out,y*box[2],box[2]); }
        return out;
    }
    private static byte[] rotateMask(byte[] source,int width,int height,int turns,Runnable checkpoint) {
        byte[] out=new byte[source.length];int newWidth=turns%2==0 ? width : height;
        for (int y=0;y<height;y++) {
            checkpoint.run();
            for (int x=0;x<width;x++) {
                int nx=turns==1 ? height-1-y : turns==2 ? width-1-x : y;
                int ny=turns==1 ? x : turns==2 ? height-1-y : width-1-x;
                out[ny*newWidth+nx]=source[y*width+x];
            }
        }
        return out;
    }
    private static int[] rotate(int[] pixels,int width,int height,int turns,Runnable checkpoint) {
        int newWidth=turns%2==0 ? width : height;
        int[] out=new int[pixels.length];
        for (int y=0;y<height;y++) {
            checkpoint.run();
            for (int x=0;x<width;x++) {
                int nx=turns==1 ? height-1-y : turns==2 ? width-1-x : y;
                int ny=turns==1 ? x : turns==2 ? height-1-y : width-1-x;
                out[ny*newWidth+nx]=pixels[y*width+x];
            }
        }
        return out;
    }
}
