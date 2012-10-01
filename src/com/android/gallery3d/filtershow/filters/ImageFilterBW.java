
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilterBW extends ImageFilter {

    public ImageFilterBW() {
        mName = "Black & White";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h);

    public void apply(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        nativeApplyFilter(bitmap, w, h);
    }

}
