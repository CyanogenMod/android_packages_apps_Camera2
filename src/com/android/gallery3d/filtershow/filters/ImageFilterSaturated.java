
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterSaturated extends ImageFilter {

    public String name() {
        return "Saturated";
    }

    public ImageFilter copy() {
        return new ImageFilterSaturated();
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float saturation);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = 100;
        float value = 2 * p / 100.0f;
        nativeApplyFilter(bitmap, w, h, value);
    }
}
