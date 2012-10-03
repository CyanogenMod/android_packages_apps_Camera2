package com.android.gallery3d.filtershow.tools;

import android.graphics.Bitmap;

import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ProcessedBitmap {
    private Bitmap mBitmap;
    private ImagePreset mPreset;
    public ProcessedBitmap(Bitmap bitmap, ImagePreset preset) {
        mBitmap = bitmap;
        mPreset = preset;
    }
    public Bitmap apply() {
        mPreset.apply(mBitmap);
        return mBitmap;
    }
}