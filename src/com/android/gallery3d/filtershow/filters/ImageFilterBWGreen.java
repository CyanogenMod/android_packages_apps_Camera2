
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBWGreen extends ImageFilter {

    public ImageFilterBWGreen() {
        mName = "B&W - Green";
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
