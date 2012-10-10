
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBWRed extends ImageFilter {

    public ImageFilterBWRed() {
        mName = "B&W - Red";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        nativeApplyFilter(bitmap, w, h);
        return bitmap;
    }

}
