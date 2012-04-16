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
import android.graphics.Canvas;
import android.graphics.Rect;

import com.android.gallery3d.common.Utils;

public class TileImageViewAdapter implements TileImageView.Model {
    private static final String TAG = "TileImageViewAdapter";
    protected BitmapRegionDecoder mRegionDecoder;
    protected int mImageWidth;
    protected int mImageHeight;
    protected Bitmap mBackupImage;
    protected int mLevelCount;
    protected boolean mFailedToLoad;

    public TileImageViewAdapter() {
    }

    public TileImageViewAdapter(Bitmap backup, BitmapRegionDecoder regionDecoder) {
        mBackupImage = Utils.checkNotNull(backup);
        mRegionDecoder = regionDecoder;
        mImageWidth = regionDecoder.getWidth();
        mImageHeight = regionDecoder.getHeight();
        mLevelCount = calculateLevelCount();
    }

    public synchronized void clear() {
        mBackupImage = null;
        mImageWidth = 0;
        mImageHeight = 0;
        mLevelCount = 0;
        mRegionDecoder = null;
        mFailedToLoad = false;
    }

    public synchronized void setBackupImage(Bitmap backup, int width, int height) {
        mBackupImage = Utils.checkNotNull(backup);
        mImageWidth = width;
        mImageHeight = height;
        mRegionDecoder = null;
        mLevelCount = 0;
        mFailedToLoad = false;
    }

    public synchronized void setRegionDecoder(BitmapRegionDecoder decoder) {
        mRegionDecoder = Utils.checkNotNull(decoder);
        mImageWidth = decoder.getWidth();
        mImageHeight = decoder.getHeight();
        mLevelCount = calculateLevelCount();
        mFailedToLoad = false;
    }

    private int calculateLevelCount() {
        return Math.max(0, Utils.ceilLog2(
                (float) mImageWidth / mBackupImage.getWidth()));
    }

    @Override
    public synchronized Bitmap getTile(int level, int x, int y, int tileSize,
            int borderSize) {
        if (mRegionDecoder == null) return null;

        // wantRegion is the rectangle on the original image we want. askRegion
        // is the rectangle on the original image that we will ask from
        // mRegionDecoder. Both are in the coordinates of the original image,
        // not the coordinates of the scaled-down images.
        Rect wantRegion = new Rect();
        Rect askRegion = new Rect();

        int b = borderSize << level;
        wantRegion.set(x - b, y - b, x + (tileSize << level) + b,
                y + (tileSize << level) + b);

        // askRegion is the intersection of wantRegion and the original image.
        askRegion.set(0, 0, mImageWidth, mImageHeight);
        Utils.assertTrue(askRegion.intersect(wantRegion));

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Config.ARGB_8888;
        options.inPreferQualityOverSpeed = true;
        options.inSampleSize =  (1 << level);

        Bitmap bitmap;

        // In CropImage, we may call the decodeRegion() concurrently.
        synchronized (mRegionDecoder) {
            bitmap = mRegionDecoder.decodeRegion(askRegion, options);
        }

        if (bitmap == null) {
            Log.w(TAG, "fail in decoding region");
            return null;
        }

        if (wantRegion.equals(askRegion)) return bitmap;

        // Now the wantRegion does not match the askRegion. This means we are at
        // a boundary tile, and we need to add paddings. Create a new Bitmap
        // and copy over.
        int size = tileSize + 2 * borderSize;
        Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        int offsetX = (askRegion.left - wantRegion.left) >> level;
        int offsetY = (askRegion.top - wantRegion.top) >> level;
        canvas.drawBitmap(bitmap, offsetX, offsetY, null);

        // If the valid region (covered by bitmap or border) is smaller than the
        // result bitmap, subset it.
        int endX = offsetX + bitmap.getWidth() + borderSize;
        int endY = offsetY + bitmap.getHeight() + borderSize;
        bitmap.recycle();
        if (endX < size || endY < size) {
            return Bitmap.createBitmap(result, 0, 0, Math.min(size, endX),
                    Math.min(size, endY));
        } else {
            return result;
        }
    }

    @Override
    public Bitmap getBackupImage() {
        return mBackupImage;
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

    public void setFailedToLoad() {
        mFailedToLoad = true;
    }

    @Override
    public boolean isFailedToLoad() {
        return mFailedToLoad;
    }
}
