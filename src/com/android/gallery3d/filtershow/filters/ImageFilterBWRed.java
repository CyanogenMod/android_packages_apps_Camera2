
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBWRed extends ImageFilter {

    public ImageFilterBWRed() {
        mName = "Black & White (Red)";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        nativeApplyFilter(bitmap, w, h);
    }

}
