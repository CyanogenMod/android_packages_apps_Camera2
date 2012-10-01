
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBWBlue extends ImageFilter {

    public ImageFilterBWBlue() {
        mName = "Black & White (Blue)";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        nativeApplyFilter(bitmap, w, h);
    }

}
