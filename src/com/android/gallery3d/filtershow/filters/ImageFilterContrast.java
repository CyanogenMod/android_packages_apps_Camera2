
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterContrast extends ImageFilter {

    public ImageFilterContrast() {
        mName = "Contrast";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float strength);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p;
        nativeApplyFilter(bitmap, w, h, value);
        return bitmap;
    }
}
