package com.android.gallery3d.filtershow.filters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.util.Log;

public class ImageFilterRS extends ImageFilter {
    private final String LOGTAG = "ImageFilterRS";

    private static RenderScript mRS = null;
    protected static Allocation mInPixelsAllocation;
    protected static Allocation mOutPixelsAllocation;
    private static android.content.res.Resources mResources = null;

    public void prepare(Bitmap bitmap) {
        mInPixelsAllocation = Allocation.createFromBitmap(mRS, bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
        mOutPixelsAllocation = Allocation.createTyped(mRS, mInPixelsAllocation.getType());
    }

    public void createFilter(android.content.res.Resources res, float scaleFactor,
            boolean highQuality) {
    }

    public void runFilter() {
    }

    public void update(Bitmap bitmap) {
        mOutPixelsAllocation.copyTo(bitmap);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (bitmap == null) {
            return bitmap;
        }
        try {
            prepare(bitmap);
            createFilter(mResources, scaleFactor, highQuality);
            runFilter();
            update(bitmap);
        } catch (android.renderscript.RSIllegalArgumentException e) {
            Log.e(LOGTAG, "Illegal argument? " + e);
        } catch (android.renderscript.RSRuntimeException e) {
            Log.e(LOGTAG, "RS runtime exception ? " + e);
        }
        return bitmap;
    }

    public static RenderScript getRenderScriptContext() {
        return mRS;
    }

    public static void setRenderScriptContext(Activity context) {
        mRS = RenderScript.create(context);
        mResources = context.getResources();
    }

}
