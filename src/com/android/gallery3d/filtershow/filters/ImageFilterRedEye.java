
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.Arrays;

public class ImageFilterRedEye extends ImageFilter {
    private static final String TAG = "ImageFilterRedEye";


    public ImageFilterRedEye() {
        mName = "Redeye";

    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterRedEye filter = (ImageFilterRedEye) super.clone();

        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, short []matrix);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p;
        int box = Math.min(w, h);
        int sizex = Math.min((int)((p+100)*box/400),w/2);
        int sizey = Math.min((int)((p+100)*box/800),h/2);

        short [] rect = new short[]{
                (short) (w/2-sizex),(short) (w/2-sizey),
                (short) (2*sizex),(short) (2*sizey)};

        nativeApplyFilter(bitmap, w, h, rect);
    }
}
