package com.familywardrobe.app;

import java.util.BitSet;

/** Four-connected, fixed-seed colour selection. It never crosses transparent pixels. */
public final class PhotoRegionRules {
    private PhotoRegionRules() {}

    public static final class Selection {
        private final BitSet selected;
        private final int width, height, count;
        private Selection(BitSet selected, int width, int height, int count) {
            this.selected=selected; this.width=width; this.height=height; this.count=count;
        }
        public int getCount() { return count; }
        public boolean contains(int x, int y) {
            return x>=0 && y>=0 && x<width && y<height && selected.get(y*width+x);
        }
        public boolean leavesVisiblePixels(int[] source, Runnable checkpoint) {
            validateImage(source,width,height);
            for (int i=0;i<source.length;i++) {
                if ((i & 4095)==0) checkpoint.run();
                if ((source[i]>>>24)!=0 && !selected.get(i)) return true;
            }
            return false;
        }
        /** ALPHA_8 bitmaps can pad each row; keep the mask aligned with source pixels. */
        public byte[] alphaMask(int stride, Runnable checkpoint) {
            if (stride<width || (long)stride*height>32_000_000) throw new IllegalArgumentException();
            byte[] alpha=new byte[stride*height];
            int visited=0;
            for (int i=selected.nextSetBit(0); i>=0; i=selected.nextSetBit(i+1)) {
                if ((visited++ & 4095)==0) checkpoint.run();
                alpha[(i/width)*stride+i%width]=(byte)255;
            }
            checkpoint.run();
            return alpha;
        }
        public int[] erase(int[] source, Runnable checkpoint) {
            validateImage(source,width,height);
            checkpoint.run();
            int[] result=source.clone();
            int visited=0;
            for (int i=selected.nextSetBit(0); i>=0; i=selected.nextSetBit(i+1)) {
                if ((visited++ & 4095)==0) checkpoint.run();
                result[i]&=0x00ffffff;
            }
            checkpoint.run();
            return result;
        }
    }

    public static void validatePoint(float x, float y, float tolerance) {
        for (float v:new float[]{x,y,tolerance})
            if (!Float.isFinite(v) || v<0 || v>1) throw new IllegalArgumentException();
    }
    private static void validateImage(int[] pixels, int width, int height) {
        if (pixels==null || width<1 || height<1 || (long)width*height>16_000_000 ||
            pixels.length!=(long)width*height) throw new IllegalArgumentException();
    }
    public static Selection select(int[] pixels, int width, int height, float x, float y,
                                   float tolerance, Runnable checkpoint) {
        return select(pixels,width,height,x,y,tolerance,null,checkpoint);
    }
    public static Selection select(int[] pixels, int width, int height, float x, float y,
                                   float tolerance, byte[] protection, Runnable checkpoint) {
        validateImage(pixels,width,height); validatePoint(x,y,tolerance); checkpoint.run();
        if (protection!=null && protection.length!=pixels.length) throw new IllegalArgumentException();
        int seed=Math.round(y*(height-1))*width+Math.round(x*(width-1));
        BitSet selected=new BitSet(pixels.length);
        int colour=pixels[seed];
        if ((colour>>>24)==0 || (protection!=null && protection[seed]!=0)) return new Selection(selected,width,height,0);
        double threshold=7.0*255*255*tolerance*tolerance;
        IntQueue queue=new IntQueue(Math.min(1024,pixels.length));
        selected.set(seed); queue.add(seed);
        int count=1, visited=0;
        while (!queue.isEmpty()) {
            if ((visited++ & 1023)==0) checkpoint.run();
            int i=queue.remove(), column=i%width;
            if (column>0 && add(i-1,pixels,colour,threshold,selected,queue,protection)) count++;
            if (column+1<width && add(i+1,pixels,colour,threshold,selected,queue,protection)) count++;
            if (i>=width && add(i-width,pixels,colour,threshold,selected,queue,protection)) count++;
            if (i+width<pixels.length && add(i+width,pixels,colour,threshold,selected,queue,protection)) count++;
        }
        checkpoint.run();
        return new Selection(selected,width,height,count);
    }
    private static boolean add(int i, int[] pixels, int colour, double threshold,
                               BitSet selected, IntQueue queue, byte[] protection) {
        if (selected.get(i) || (pixels[i]>>>24)==0 || (protection!=null && protection[i]!=0)) return false;
        int pixel=pixels[i];
        int red=((pixel>>>16)&255)-((colour>>>16)&255);
        int green=((pixel>>>8)&255)-((colour>>>8)&255);
        int blue=(pixel&255)-(colour&255);
        // Every candidate is compared with the seed, so a smooth gradient cannot drift indefinitely.
        if (2*red*red+4*green*green+blue*blue>threshold) return false;
        selected.set(i); queue.add(i); return true;
    }
    private static final class IntQueue {
        private int[] values;
        private int head, size;
        IntQueue(int capacity) { values=new int[capacity]; }
        boolean isEmpty() { return size==0; }
        void add(int value) {
            if (size==values.length) {
                int[] expanded=new int[values.length*2];
                for (int i=0;i<size;i++) expanded[i]=values[(head+i)%values.length];
                values=expanded; head=0;
            }
            values[(head+size)%values.length]=value; size++;
        }
        int remove() { int value=values[head]; head=(head+1)%values.length; size--; return value; }
    }
}
