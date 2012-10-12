
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterVignette extends ImageFilter {

    public ImageFilterVignette() {
        setFilterType(TYPE_VIGNETTE);
        mName = "Vignette";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float strength);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p / 100.0f;
        nativeApplyFilter(bitmap, w, h, value);

        return bitmap;
    }
}
