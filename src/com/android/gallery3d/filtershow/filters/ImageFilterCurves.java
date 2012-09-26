
package com.android.gallery3d.filtershow.filters;

import com.android.gallery3d.filtershow.ui.Spline;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageFilterCurves extends ImageFilter {

    private static final String LOGTAG = "ImageFilterCurves";

    private float[] mCurve = new float[256];

    private boolean mUseRed = true;
    private boolean mUseGreen = true;
    private boolean mUseBlue = true;
    private String mName = "Curves";
    private Spline mSpline = null;

    public String name() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
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

    public ImageFilter copy() {
        ImageFilterCurves curves = new ImageFilterCurves();
        curves.setCurve(mCurve);
        curves.setName(mName);
        curves.setSpline(new Spline(mSpline));
        return curves;
    }

    public void populateArray(int[] array) {
        for (int i = 0; i < 256; i++) {
            array[i] = (int) (mCurve[i]);
        }
    }

    public void apply(Bitmap bitmap) {

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
    }

    public void setSpline(Spline spline) {
        mSpline = spline;
    }

    public Spline getSpline() {
        return mSpline;
    }
}
