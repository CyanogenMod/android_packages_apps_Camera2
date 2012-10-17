
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterHue extends ImageFilter {
    private ColorSpaceMatrix cmatrix = null;

    public ImageFilterHue() {
        mName = "Hue";
        cmatrix = new ColorSpaceMatrix();
        mMaxParameter = 180;
        mMinParameter = -180;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterHue filter = (ImageFilterHue) super.clone();
        filter.cmatrix = new ColorSpaceMatrix(cmatrix);
        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float []matrix);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p;
        cmatrix.identity();
        cmatrix.setHue(value);

        nativeApplyFilter(bitmap, w, h, cmatrix.getMatrix());

        return bitmap;
    }
}
