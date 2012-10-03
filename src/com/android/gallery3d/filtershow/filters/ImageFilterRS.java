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

    public void createFilter(android.content.res.Resources res) {
    }

    public void runFilter() {
    }

    public void update(Bitmap bitmap) {
        mOutPixelsAllocation.copyTo(bitmap);
    }

    public void apply(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        try {
            prepare(bitmap);
            createFilter(mResources);
            runFilter();
            update(bitmap);
        } catch (android.renderscript.RSIllegalArgumentException e) {
            Log.e(LOGTAG, "Illegal argument? " + e);
        } catch (android.renderscript.RSRuntimeException e) {
            Log.e(LOGTAG, "RS runtime exception ? " + e);
        }
    }

    public static RenderScript getRenderScriptContext() {
        return mRS;
    }

    public static void setRenderScriptContext(Activity context) {
        mRS = RenderScript.create(context);
        mResources = context.getResources();
    }

}
