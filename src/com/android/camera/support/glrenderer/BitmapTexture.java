package com.android.camera.support.glrenderer;

import android.graphics.Bitmap;

import junit.framework.Assert;

// BitmapTexture is a texture whose content is specified by a fixed Bitmap.
//
// The texture does not own the Bitmap. The user should make sure the Bitmap
// is valid during the texture's lifetime. When the texture is recycled, it
// does not free the Bitmap.
public class BitmapTexture extends UploadedTexture {
    protected Bitmap mContentBitmap;

    public BitmapTexture(Bitmap bitmap) {
        this(bitmap, false);
    }

    public BitmapTexture(Bitmap bitmap, boolean hasBorder) {
        super(hasBorder);
        Assert.assertTrue(bitmap != null && !bitmap.isRecycled());
        mContentBitmap = bitmap;
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        // Do nothing.
    }

    @Override
    protected Bitmap onGetBitmap() {
        return mContentBitmap;
    }

    public Bitmap getBitmap() {
        return mContentBitmap;
    }
}
