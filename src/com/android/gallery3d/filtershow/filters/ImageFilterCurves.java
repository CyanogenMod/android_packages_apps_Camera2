
package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.ui.Spline;

public class ImageFilterCurves extends ImageFilter {

    private static final String LOGTAG = "ImageFilterCurves";

    private final float[] mCurve = new float[256];

    private boolean mUseRed = true;
    private boolean mUseGreen = true;
    private boolean mUseBlue = true;
    private Spline mSpline = null;

    public ImageFilterCurves() {
        mName = "Curves";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterCurves filter = (ImageFilterCurves) super.clone();
        filter.setCurve(mCurve);
        filter.setSpline(new Spline(mSpline));
        return filter;
    }

    public void setUseRed(boolean value) {
        mUseRed = value;
    }

    public void setUseGreen(boolean value) {
        mUseGreen = value;
    }

    public void setUseBlue(boolean value) {
        mUseBlue = value;
    }

    public void setCurve(float[] curve) {
        for (int i = 0; i < curve.length; i++) {
            if (i < 256) {
                mCurve[i] = curve[i];
            }
        }
    }

    @Override
    public boolean same(ImageFilter filter) {
        boolean isCurveFilter = super.same(filter);
        if (!isCurveFilter) {
            return false;
        }
        ImageFilterCurves curve = (ImageFilterCurves) filter;
        for (int i = 0; i < 256; i++) {
            if (curve.mCurve[i] != mCurve[i]) {
                return false;
            }
        }
        return true;
    }

    public void populateArray(int[] array) {
        for (int i = 0; i < 256; i++) {
            array[i] = (int) (mCurve[i]);
        }
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int[] redGradient = null;
        if (mUseRed) {
            redGradient = new int[256];
            populateArray(redGradient);
        }
        int[] greenGradient = null;
        if (mUseGreen) {
            greenGradient = new int[256];
            populateArray(greenGradient);
        }
        int[] blueGradient = null;
        if (mUseBlue) {
            blueGradient = new int[256];
            populateArray(blueGradient);
        }

        nativeApplyGradientFilter(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                redGradient, greenGradient, blueGradient);
        return bitmap;
    }

    public void setSpline(Spline spline) {
        mSpline = spline;
    }

    public Spline getSpline() {
        return mSpline;
    }
}
