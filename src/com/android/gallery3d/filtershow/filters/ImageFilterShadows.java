
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.ui.ControlPoint;
import com.android.gallery3d.filtershow.ui.Spline;

public class ImageFilterShadows extends ImageFilter {
    private final float SHADOW = .1f;
    private final float MID = .5f;
    private final float HIGHLIGHT = .9f;

    private final float []baseX = {0f,SHADOW,MID,HIGHLIGHT,1f};
    private final float []baseY = {0f,SHADOW,MID,HIGHLIGHT,1f};

    public ImageFilterShadows() {
        mName = "Shadows";

    }
    short [] calcMap(){
        Spline sp = new Spline();
        for (int i = 0; i < baseX.length; i++) {
            sp.addPoint(baseX[i], baseY[i]);
        }
        int max = 4080;
        int w = 40800;
        float []px = new float[w+1];
        float []py = new float[w+1];
        short []vlut = new short[4080+1];
        for (int i = 0; i < px.length; i++) {
            float t = i/(float)(w);

            ControlPoint p = sp.getPoint(t);
            px[i] = p.x;
            py[i] = p.y;
        }
        for (int i = 0; i < py.length; i++) {
            short x = (short)Math.min(4080,Math.max(0,((int)(px[i]*max))));
            short y = (short)Math.min(4082,Math.max(0,((int)(py[i]*max))));
            vlut[x] = y;
        }
        return vlut;
    }
    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterShadows filter = (ImageFilterShadows) super.clone();
        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, short []valMap);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        baseY[1] = (float)(SHADOW*Math.pow(4, mParameter/100.));

        nativeApplyFilter(bitmap, w, h, calcMap());
        return bitmap;
    }
}
