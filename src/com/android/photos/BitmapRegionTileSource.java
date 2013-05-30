/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.photos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.photos.views.TiledImageRenderer;

import java.io.IOException;

/**
 * A {@link com.android.photos.views.TiledImageRenderer.TileSource} using
 * {@link BitmapRegionDecoder} to wrap a local file
 */
public class BitmapRegionTileSource implements TiledImageRenderer.TileSource {

    private static final String TAG = "BitmapRegionTileSource";

    private static final boolean REUSE_BITMAP =
            Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN;
    private static final int MAX_PREVIEW_SIZE = 1024;

    BitmapRegionDecoder mDecoder;
    int mWidth;
    int mHeight;
    int mTileSize;
    private BasicTexture mPreview;
    private final int mRotation;

    // For use only by getTile
    private Rect mWantRegion = new Rect();
    private Rect mOverlapRegion = new Rect();
    private BitmapFactory.Options mOptions;
    private Canvas mCanvas;

    public BitmapRegionTileSource(Context context, String path, int previewSize, int rotation) {
        mTileSize = TiledImageRenderer.suggestedTileSize(context);
        mRotation = rotation;
        try {
            mDecoder = BitmapRegionDecoder.newInstance(path, true);
            mWidth = mDecoder.getWidth();
            mHeight = mDecoder.getHeight();
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "ctor failed", e);
        }
        mOptions = new BitmapFactory.Options();
        mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        mOptions.inPreferQualityOverSpeed = true;
        mOptions.inTempStorage = new byte[16 * 1024];
        if (previewSize != 0) {
            previewSize = Math.min(previewSize, MAX_PREVIEW_SIZE);
            // Although this is the same size as the Bitmap that is likely already
            // loaded, the lifecycle is different and interactions are on a different
            // thread. Thus to simplify, this source will decode its own bitmap.
            int sampleSize = (int) Math.ceil(Math.max(
                    mWidth / (float) previewSize, mHeight / (float) previewSize));
            mOptions.inSampleSize = Math.max(sampleSize, 1);
            Bitmap preview = mDecoder.decodeRegion(
                    new Rect(0, 0, mWidth, mHeight), mOptions);
            if (preview.getWidth() <= MAX_PREVIEW_SIZE && preview.getHeight() <= MAX_PREVIEW_SIZE) {
                mPreview = new BitmapTexture(preview);
            } else {
                Log.w(TAG, String.format(
                        "Failed to create preview of apropriate size! "
                        + " in: %dx%d, sample: %d, out: %dx%d",
                        mWidth, mHeight, sampleSize,
                        preview.getWidth(), preview.getHeight()));
            }
        }
    }

    @Override
    public int getTileSize() {
        return mTileSize;
    }

    @Override
    public int getImageWidth() {
        return mWidth;
    }

    @Override
    public int getImageHeight() {
        return mHeight;
    }

    @Override
    public BasicTexture getPreview() {
        return mPreview;
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    @Override
    public Bitmap getTile(int level, int x, int y, Bitmap bitmap) {
        int tileSize = getTileSize();
        if (!REUSE_BITMAP) {
            return getTileWithoutReusingBitmap(level, x, y, tileSize);
        }

        int t = tileSize << level;
        mWantRegion.set(x, y, x + t, y + t);

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        }

        mOptions.inSampleSize = (1 << level);
        mOptions.inBitmap = bitmap;

        try {
            bitmap = mDecoder.decodeRegion(mWantRegion, mOptions);
        } finally {
            if (mOptions.inBitmap != bitmap && mOptions.inBitmap != null) {
                mOptions.inBitmap = null;
            }
        }

        if (bitmap == null) {
            Log.w("BitmapRegionTileSource", "fail in decoding region");
        }
        return bitmap;
    }

    private Bitmap getTileWithoutReusingBitmap(
            int level, int x, int y, int tileSize) {

        int t = tileSize << level;
        mWantRegion.set(x, y, x + t, y + t);

        mOverlapRegion.set(0, 0, mWidth, mHeight);

        mOptions.inSampleSize = (1 << level);
        Bitmap bitmap = mDecoder.decodeRegion(mOverlapRegion, mOptions);

        if (bitmap == null) {
            Log.w(TAG, "fail in decoding region");
        }

        if (mWantRegion.equals(mOverlapRegion)) {
            return bitmap;
        }

        Bitmap result = Bitmap.createBitmap(tileSize, tileSize, Config.ARGB_8888);
        if (mCanvas == null) {
            mCanvas = new Canvas();
        }
        mCanvas.setBitmap(result);
        mCanvas.drawBitmap(bitmap,
                (mOverlapRegion.left - mWantRegion.left) >> level,
                (mOverlapRegion.top - mWantRegion.top) >> level, null);
        mCanvas.setBitmap(null);
        return result;
    }
}
