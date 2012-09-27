
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterHue extends ImageFilter {
    private ColorSpaceMatrix cmatrix = new ColorSpaceMatrix();

    public String name() {
        return "Hue";
    }

    public ImageFilter copy() {
        return new ImageFilterHue();
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float []matrix);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p;
        cmatrix.identity();
        cmatrix.setHue(value);

        nativeApplyFilter(bitmap, w, h, cmatrix.getMatrix());
    }
}
