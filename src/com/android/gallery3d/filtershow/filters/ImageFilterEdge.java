package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

import com.android.gallery3d.R;

public class ImageFilterEdge extends ImageFilter {

    public ImageFilterEdge() {
        mName = "Edge";
        mPreviewParameter = 0;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float p);

    @Override
    public int getButtonId() {
        return R.id.edgeButton;
    }

    @Override
    public int getTextId() {
        return R.string.edge;
    }

    @Override
    public boolean isNil() {
        return false;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter + 101;
        p = (float) p / 100;
        nativeApplyFilter(bitmap, w, h, p);
        return bitmap;
    }
}
