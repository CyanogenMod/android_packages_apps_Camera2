
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBrightness extends ImageFilter {

    public String name() {
        return "Brightness";
    }

    public ImageFilter copy() {
        return new ImageFilterBrightness();
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float bright);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = mParameter;
        float value = p;
        nativeApplyFilter(bitmap, w, h, value);
    }
}
