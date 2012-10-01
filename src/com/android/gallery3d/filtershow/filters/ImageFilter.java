
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

public class ImageFilter implements Cloneable {

    protected int mParameter = 0;
    protected String mName = "Original";

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilter filter = (ImageFilter) super.clone();
        filter.setName(getName());
        filter.setParameter(getParameter());
        return filter;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void apply(Bitmap bitmap) {
        // do nothing here, subclasses will implement filtering here
    }

    public int getParameter() {
        return mParameter;
    }

    public void setParameter(int value) {
        mParameter = value;
    }

    public boolean same(ImageFilter filter) {
        if (!filter.getName().equalsIgnoreCase(getName())) {
            return false;
        }
        return true;
    }

    native protected void nativeApplyGradientFilter(Bitmap bitmap, int w, int h,
            int[] redGradient, int[] greenGradient, int[] blueGradient);

}
