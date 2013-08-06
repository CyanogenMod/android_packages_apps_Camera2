package com.android.camera.support.glrenderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import junit.framework.Assert;

// ResourceTexture is a texture whose Bitmap is decoded from a resource.
// By default ResourceTexture is not opaque.
public class ResourceTexture extends UploadedTexture {

    protected final Context mContext;
    protected final int mResId;

    public ResourceTexture(Context context, int resId) {
        Assert.assertNotNull(context);
        mContext = context;
        mResId = resId;
        setOpaque(false);
    }

    @Override
    protected Bitmap onGetBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        if (!inFinalizer()) {
            bitmap.recycle();
        }
    }
}
