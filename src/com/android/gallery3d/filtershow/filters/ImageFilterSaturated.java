
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterSaturated extends ImageFilter {

    public ImageFilterSaturated() {
        mName = "Saturated";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float saturation);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = mParameter;
        float value = 1 +  p / 100.0f;
        nativeApplyFilter(bitmap, w, h, value);
        return bitmap;
    }
}
