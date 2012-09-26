
package com.android.gallery3d.filtershow.filters;

import com.android.gallery3d.R;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;

public class ImageFilterBorder extends ImageFilter {
    Drawable mNinePatch = null;

    public ImageFilterBorder(Drawable ninePatch) {
        mNinePatch = ninePatch;
    }

    public String name() {
        return "Border";
    }

    public ImageFilter copy() {
        return new ImageFilterBorder(mNinePatch);
    }

    public boolean same(ImageFilter filter) {
        boolean isBorderFilter = super.same(filter);
        if (!isBorderFilter) {
            return false;
        }
        ImageFilterBorder borderFilter = (ImageFilterBorder) filter;
        if (mNinePatch != borderFilter.mNinePatch) {
            return false;
        }
        return true;
    }

    public void setDrawable(Drawable ninePatch) {
        // TODO: for now we only use nine patch
        mNinePatch = ninePatch;
    }

    public void apply(Bitmap bitmap) {
        if (mNinePatch == null) {
            return;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Rect bounds = new Rect(0, 0, w, h);
        Canvas canvas = new Canvas(bitmap);
        mNinePatch.setBounds(bounds);
        mNinePatch.draw(canvas);
    }
}
