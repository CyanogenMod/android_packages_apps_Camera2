
package com.android.gallery3d.filtershow.filters;

import com.android.gallery3d.R;

public class ImageFilterSharpen extends ImageFilterRS {

    private static final String LOGTAG = "ImageFilterSharpen";
    private ScriptC_convolve3x3 mScript;

    public ImageFilterSharpen() {
        mName = "Sharpen";
    }

    @Override
    public void createFilter(android.content.res.Resources res, float scaleFactor,
            boolean highQuality) {
        int w = mInPixelsAllocation.getType().getX();
        int h = mInPixelsAllocation.getType().getY();

        float p1 = mParameter * scaleFactor;
        float value = p1 / 100.0f;
        float f[] = new float[9];
        float p = value;
        f[0] = -p;
        f[1] = -p;
        f[2] = -p;
        f[3] = -p;
        f[4] = 8 * p + 1;
        f[5] = -p;
        f[6] = -p;
        f[7] = -p;
        f[8] = -p;
        if (mScript == null) {
            mScript = new ScriptC_convolve3x3(getRenderScriptContext(), res, R.raw.convolve3x3);
        }
        mScript.set_gCoeffs(f);
        mScript.set_gWidth(w);
        mScript.set_gHeight(h);
    }

    @Override
    public void runFilter() {
        mScript.set_gIn(mInPixelsAllocation);
        mScript.bind_gPixels(mInPixelsAllocation);
        mScript.forEach_root(mInPixelsAllocation, mOutPixelsAllocation);
    }

}
