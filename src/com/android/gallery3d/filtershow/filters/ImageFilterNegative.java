package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

import com.android.gallery3d.R;

public class ImageFilterNegative extends ImageFilter {

    public ImageFilterNegative() {
        mName = "Negative";
    }

    @Override
    public int getButtonId() {
        return R.id.negativeButton;
    }

    @Override
    public int getTextId() {
        return R.string.negative;
    }

    @Override
    public boolean isNil() {
        return false;
    }

    @Override
    public boolean showEditingControls() {
        return false;
    }

    @Override
    public boolean showParameterValue() {
        return false;
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
