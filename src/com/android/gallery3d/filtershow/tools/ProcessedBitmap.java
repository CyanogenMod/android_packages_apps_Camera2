package com.android.gallery3d.filtershow.tools;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ProcessedBitmap {
    private Bitmap mBitmap;
    private final ImagePreset mPreset;
    public ProcessedBitmap(Bitmap bitmap, ImagePreset preset) {
        mBitmap = bitmap;
        mPreset = preset;
    }
    public Bitmap apply() {
        mBitmap = mPreset.apply(mBitmap);
        return mBitmap;
    }
}