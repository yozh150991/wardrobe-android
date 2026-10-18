package com.familywardrobe.app;

import java.util.Arrays;

public final class PhotoGeometryCases {
    private static void box(int[] expected, int[] actual) {
        if (!Arrays.equals(expected, actual)) throw new AssertionError(Arrays.toString(actual));
    }
    public static void fullImage() { box(new int[]{0,0,1200,800}, PhotoGeometry.crop(1200,800,0,0,1,1)); }
    public static void centralCrop() { box(new int[]{300,200,600,400}, PhotoGeometry.crop(1200,800,.25f,.25f,.75f,.75f)); }
    public static void portraitAfterRotation() { box(new int[]{0,300,800,600}, PhotoGeometry.crop(800,1200,0,.25f,1,.75f)); }
    public static void smallSelectionAtEdge() { box(new int[]{6,4,1,1}, PhotoGeometry.crop(7,5,.99f,.99f,1,1)); }
    public static void rotationWraps() {
        int[] inputs = {-5,-1,0,1,4,5}; int[] expected = {3,3,0,1,0,1};
        for (int i=0;i<inputs.length;i++) if (PhotoGeometry.turns(inputs[i]) != expected[i]) throw new AssertionError();
    }
    public static void invalidBoundsRejected() {
        float[][] bounds = {{0,0,0,1},{0,0,1,0},{.9f,0,.2f,1},{-.1f,0,1,1},{0,0,1.1f,1},{0,Float.NaN,1,1},{0,0,1,Float.POSITIVE_INFINITY}};
        for (float[] b:bounds) {
            boolean failed=false;
            try { PhotoGeometry.crop(10,20,b[0],b[1],b[2],b[3]); } catch(IllegalArgumentException e) { failed=true; }
            if (!failed) throw new AssertionError("Invalid crop accepted");
        }
    }
    public static void everyValidCropFitsImage() {
        for (int w:new int[]{1,7,100,1800}) for(int h:new int[]{1,9,800}) {
            for(int start=0;start<10;start++) for(int end=start+1;end<=10;end++) {
                int[] b=PhotoGeometry.crop(w,h,start/10f,start/10f,end/10f,end/10f);
                if(b[0]<0||b[1]<0||b[2]<1||b[3]<1||b[0]+b[2]>w||b[1]+b[3]>h) throw new AssertionError();
            }
        }
    }
    public static void main(String[] args) {
        fullImage(); centralCrop(); portraitAfterRotation(); smallSelectionAtEdge(); rotationWraps(); invalidBoundsRejected(); everyValidCropFitsImage();
        System.out.println("PASS: 7 crop/rotation geometry scenarios; production PhotoGeometry.java.");
    }
}
