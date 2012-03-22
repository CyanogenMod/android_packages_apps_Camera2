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
import android.graphics.Canvas;

import com.android.gallery3d.common.BitmapUtils;

import java.util.ArrayList;

public class BitmapTileProvider implements TileImageView.Model {
    private final ScreenNail mScreenNail;
    private final Bitmap[] mMipmaps;
    private final Config mConfig;
    private final int mImageWidth;
    private final int mImageHeight;

    private boolean mRecycled = false;

    public BitmapTileProvider(Bitmap bitmap, int maxBackupSize) {
        mImageWidth = bitmap.getWidth();
        mImageHeight = bitmap.getHeight();
        ArrayList<Bitmap> list = new ArrayList<Bitmap>();
        list.add(bitmap);
        while (bitmap.getWidth() > maxBackupSize
                || bitmap.getHeight() > maxBackupSize) {
            bitmap = BitmapUtils.resizeBitmapByScale(bitmap, 0.5f, false);
            list.add(bitmap);
        }

        mScreenNail = new BitmapScreenNail(list.remove(list.size() - 1), 0);
        mMipmaps = list.toArray(new Bitmap[list.size()]);
        mConfig = Config.ARGB_8888;
    }

    public ScreenNail getScreenNail() {
        return mScreenNail;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getLevelCount() {
        return mMipmaps.length;
    }

    public Bitmap getTile(int level, int x, int y, int tileSize,
            int borderSize) {
        x >>= level;
        y >>= level;
        int size = tileSize + 2 * borderSize;
        Bitmap result = Bitmap.createBitmap(size, size, mConfig);
        Bitmap mipmap = mMipmaps[level];
        Canvas canvas = new Canvas(result);
        int offsetX = -x + borderSize;
        int offsetY = -y + borderSize;
        canvas.drawBitmap(mipmap, offsetX, offsetY, null);

        // If the valid region (covered by mipmap or border) is smaller than the
        // result bitmap, subset it.
        int endX = offsetX + mipmap.getWidth() + borderSize;
        int endY = offsetY + mipmap.getHeight() + borderSize;
        if (endX < size || endY < size) {
            return Bitmap.createBitmap(result, 0, 0, Math.min(size, endX),
                    Math.min(size, endY));
        } else {
            return result;
        }
    }

    public void recycle() {
        if (mRecycled) return;
        mRecycled = true;
        for (Bitmap bitmap : mMipmaps) {
            BitmapUtils.recycleSilently(bitmap);
        }
        if (mScreenNail != null) {
            mScreenNail.pauseDraw();
        }
    }

    public boolean isFailedToLoad() {
        return false;
    }
}
