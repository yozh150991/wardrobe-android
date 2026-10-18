package com.familywardrobe.app;

/** Geometry used for both drawing and inverse pointer coordinates. No image pixels are altered here. */
public final class PhotoViewportRules {
    private PhotoViewportRules() {}
    public static float[] viewport(int imageWidth, int imageHeight, float viewWidth, float viewHeight,
            float zoom, float panX, float panY) {
        if (imageWidth < 1 || imageHeight < 1 || !Float.isFinite(viewWidth) || !Float.isFinite(viewHeight) ||
                viewWidth <= 0 || viewHeight <= 0 || !Float.isFinite(zoom) || zoom < 1 || zoom > 8 ||
                !Float.isFinite(panX) || !Float.isFinite(panY)) throw new IllegalArgumentException();
        float fit = Math.min(viewWidth/imageWidth, viewHeight/imageHeight);
        float width = imageWidth*fit*zoom, height = imageHeight*fit*zoom;
        float dx = clamp(panX, -Math.max(0,(width-viewWidth)/2), Math.max(0,(width-viewWidth)/2));
        float dy = clamp(panY, -Math.max(0,(height-viewHeight)/2), Math.max(0,(height-viewHeight)/2));
        return new float[]{(viewWidth-width)/2+dx,(viewHeight-height)/2+dy,width,height,dx,dy};
    }
    public static float[] point(float screenX, float screenY, float[] frame, boolean clampToImage) {
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY) || frame.length < 4 || frame[2] <= 0 || frame[3] <= 0) throw new IllegalArgumentException();
        float x=(screenX-frame[0])/frame[2], y=(screenY-frame[1])/frame[3];
        if (!clampToImage && (x<0 || x>1 || y<0 || y>1)) return null;
        return new float[]{clamp(x,0,1),clamp(y,0,1)};
    }
    public static float[] resize(float left, float top, float right, float bottom, int corner, float x, float y) {
        PhotoGeometry.crop(1000,1000,left,top,right,bottom);
        if (corner<0 || corner>3 || !Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException();
        if (corner==0 || corner==2) left=clamp(x,0,right-.05f); else right=clamp(x,left+.05f,1);
        if (corner==0 || corner==1) top=clamp(y,0,bottom-.05f); else bottom=clamp(y,top+.05f,1);
        return new float[]{left,top,right,bottom};
    }
    private static float clamp(float v,float low,float high) { return Math.max(low,Math.min(high,v)); }
}
