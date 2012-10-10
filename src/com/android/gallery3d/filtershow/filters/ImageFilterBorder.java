
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class ImageFilterBorder extends ImageFilter {
    Drawable mNinePatch = null;

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterBorder filter = (ImageFilterBorder) super.clone();
        filter.setDrawable(mNinePatch);
        return filter;
    }

    public ImageFilterBorder(Drawable ninePatch) {
        mName = "Border";
        mNinePatch = ninePatch;
    }

    @Override
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

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (mNinePatch == null) {
            return bitmap;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        float scale = scaleFactor * 2.0f;
        Rect bounds = new Rect(0, 0, (int) (w / scale), (int) (h / scale));
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        mNinePatch.setBounds(bounds);
        mNinePatch.draw(canvas);
        return bitmap;
    }
}
