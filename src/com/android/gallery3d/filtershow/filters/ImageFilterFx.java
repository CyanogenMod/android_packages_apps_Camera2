
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.gallery3d.R;

import java.util.Arrays;

public class ImageFilterFx extends ImageFilter {
    private static final String TAG = "ImageFilterFx";
    Bitmap fxBitmap;
    public static final String ORIG = "Original";
    public ImageFilterFx(Bitmap fxBitmap,String name) {
        setFilterType(TYPE_FX);
        mName = name;
        this.fxBitmap = fxBitmap;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterFx filter = (ImageFilterFx) super.clone();
        filter.fxBitmap = this.fxBitmap;
        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h,Bitmap  fxBitmap, int fxw, int fxh);

    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (fxBitmap==null)
            return bitmap;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int fxw = fxBitmap.getWidth();
        int fxh = fxBitmap.getHeight();

        nativeApplyFilter(bitmap, w, h,   fxBitmap,  fxw,  fxh);
        return bitmap;
    }
}
