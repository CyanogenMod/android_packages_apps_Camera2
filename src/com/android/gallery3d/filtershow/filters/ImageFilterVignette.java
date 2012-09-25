
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterVignette extends ImageFilter {

    public String name() {
        return "Vignette";
    }

    public ImageFilter copy() {
        return new ImageFilterVignette();
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float strength);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p / 100.0f;
        nativeApplyFilter(bitmap, w, h, value);
    }
}
