/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.BitmapPool;

public class TileImageViewAdapter implements TileImageView.Model {
    private static final String TAG = "TileImageViewAdapter";
    protected ScreenNail mScreenNail;
    protected boolean mOwnScreenNail;
    protected BitmapRegionDecoder mRegionDecoder;
    protected int mImageWidth;
    protected int mImageHeight;
    protected int mLevelCount;

    public TileImageViewAdapter() {
    }

    public TileImageViewAdapter(
            Bitmap bitmap, BitmapRegionDecoder regionDecoder) {
        Utils.checkNotNull(bitmap);
        updateScreenNail(new BitmapScreenNail(bitmap), true);
        mRegionDecoder = regionDecoder;
        mImageWidth = regionDecoder.getWidth();
        mImageHeight = regionDecoder.getHeight();
        mLevelCount = calculateLevelCount();
    }

    public synchronized void clear() {
        updateScreenNail(null, false);
        mImageWidth = 0;
        mImageHeight = 0;
        mLevelCount = 0;
        mRegionDecoder = null;
    }

    public synchronized void setScreenNail(Bitmap bitmap, int width, int height) {
        Utils.checkNotNull(bitmap);
        updateScreenNail(new BitmapScreenNail(bitmap), true);
        mImageWidth = width;
        mImageHeight = height;
        mRegionDecoder = null;
        mLevelCount = 0;
    }

    public synchronized void setScreenNail(
            ScreenNail screenNail, int width, int height) {
        Utils.checkNotNull(screenNail);
        updateScreenNail(screenNail, false);
        mImageWidth = width;
        mImageHeight = height;
        mRegionDecoder = null;
        mLevelCount = 0;
    }

    private void updateScreenNail(ScreenNail screenNail, boolean own) {
        if (mScreenNail != null && mOwnScreenNail) {
            mScreenNail.recycle();
        }
        mScreenNail = screenNail;
        mOwnScreenNail = own;
    }

    public synchronized void setRegionDecoder(BitmapRegionDecoder decoder) {
        mRegionDecoder = Utils.checkNotNull(decoder);
        mImageWidth = decoder.getWidth();
        mImageHeight = decoder.getHeight();
        mLevelCount = calculateLevelCount();
    }

    private int calculateLevelCount() {
        return Math.max(0, Utils.ceilLog2(
                (float) mImageWidth / mScreenNail.getWidth()));
    }

    // Gets a sub image on a rectangle of the current photo. For example,
    // getTile(1, 50, 50, 100, 3, pool) means to get the region located
    // at (50, 50) with sample level 1 (ie, down sampled by 2^1) and the
    // target tile size (after sampling) 100 with border 3.
    //
    // From this spec, we can infer the actual tile size to be
    // 100 + 3x2 = 106, and the size of the region to be extracted from the
    // photo to be 200 with border 6.
    //
    // As a result, we should decode region (50-6, 50-6, 250+6, 250+6) or
    // (44, 44, 256, 256) from the original photo and down sample it to 106.
    @Override
    public Bitmap getTile(int level, int x, int y, int tileSize,
            int borderSize, BitmapPool pool) {
        int b = borderSize << level;
        int t = tileSize << level;

        Rect wantRegion = new Rect(x - b, y - b, x + t + b, y + t + b);

        boolean needClear;
        BitmapRegionDecoder regionDecoder = null;

        synchronized (this) {
            regionDecoder = mRegionDecoder;
            if (regionDecoder == null) return null;

            // We need to clear a reused bitmap, if wantRegion is not fully
            // within the image.
            needClear = !new Rect(0, 0, mImageWidth, mImageHeight)
                    .contains(wantRegion);
        }

        Bitmap bitmap = pool == null ? null : pool.getBitmap();
        if (bitmap != null) {
            if (needClear) bitmap.eraseColor(0);
        } else {
            int s = tileSize + 2 * borderSize;
            bitmap = Bitmap.createBitmap(s, s, Config.ARGB_8888);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Config.ARGB_8888;
        options.inPreferQualityOverSpeed = true;
        options.inSampleSize =  (1 << level);
        options.inBitmap = bitmap;

        try {
            // In CropImage, we may call the decodeRegion() concurrently.
            synchronized (regionDecoder) {
                bitmap = regionDecoder.decodeRegion(wantRegion, options);
            }
        } finally {
            if (options.inBitmap != bitmap && options.inBitmap != null) {
                if (pool != null) pool.recycle(options.inBitmap);
                options.inBitmap = null;
            }
        }

        if (bitmap == null) {
            Log.w(TAG, "fail in decoding region");
        }
        return bitmap;
    }

    @Override
    public ScreenNail getScreenNail() {
        return mScreenNail;
    }

    @Override
    public int getImageHeight() {
        return mImageHeight;
    }

    @Override
    public int getImageWidth() {
        return mImageWidth;
    }

    @Override
    public int getLevelCount() {
        return mLevelCount;
    }
}
