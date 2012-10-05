package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ZoomCache {

    private ImagePreset mImagePreset = null;
    private Bitmap mBitmap = null;
    private Rect mBounds = null;

    // TODO: move the processing to a background thread...
    public Bitmap getImage(ImagePreset preset, Rect bounds) {
        if (mBounds != bounds) {
            return null;
        }
        if (mImagePreset == null) {
            return null;
        }
        if (!mImagePreset.same(preset)) {
            return null;
        }
        return mBitmap;
    }

    public void setImage(ImagePreset preset, Rect bounds, Bitmap bitmap) {
        mBitmap = bitmap;
        mBounds = bounds;
        mImagePreset = preset;
    }

    public void reset(ImagePreset imagePreset) {
        if (imagePreset == mImagePreset) {
            mBitmap = null;
        }
    }
}
