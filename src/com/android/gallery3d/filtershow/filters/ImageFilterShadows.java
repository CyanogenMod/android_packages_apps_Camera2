
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterShadows extends ImageFilter {

    public ImageFilterShadows() {
        mName = "Shadows";

    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterShadows filter = (ImageFilterShadows) super.clone();
        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float  factor);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;

        nativeApplyFilter(bitmap, w, h, p);
        return bitmap;
    }
}
