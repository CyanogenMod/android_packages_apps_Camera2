
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilter {

    protected int mParameter = 0;

    public String name() {
        return "Original";
    }

    // TODO: maybe use clone instead?
    public ImageFilter copy() {
        ImageFilter filter = new ImageFilter();
        filter.setParameter(mParameter);
        return filter;
    }

    public void apply(Bitmap bitmap) {
        // do nothing here, subclasses will implement filtering here
    }

    public void setParameter(int value) {
        mParameter = value;
    }

    public boolean same(ImageFilter filter) {
        if (!filter.name().equalsIgnoreCase(name())) {
            return false;
        }
        return true;
    }

    native protected void nativeApplyGradientFilter(Bitmap bitmap, int w, int h,
            int[] redGradient, int[] greenGradient, int[] blueGradient);

}
