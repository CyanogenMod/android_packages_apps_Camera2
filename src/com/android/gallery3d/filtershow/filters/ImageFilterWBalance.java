
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageFilterWBalance extends ImageFilter {
    private static final String TAG = "ImageFilterWBalance";

    public ImageFilterWBalance() {
        setFilterType(TYPE_WBALANCE);
        mName = "WBalance";
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, int locX, int locY);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        nativeApplyFilter(bitmap, w, h, -1,-1);
        return bitmap;
    }
}
