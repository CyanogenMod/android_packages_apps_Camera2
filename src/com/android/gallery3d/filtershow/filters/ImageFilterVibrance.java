
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterVibrance extends ImageFilter {

    public ImageFilterVibrance() {
        mName = "Vibrance";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float bright);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = mParameter;
        float value = p;
        nativeApplyFilter(bitmap, w, h, value);

        return bitmap;
    }
}
