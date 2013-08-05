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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.util.Log;
import com.android.photos.views.TiledImageRenderer;

import java.io.IOException;

public class BitmapRegionTileSource implements TiledImageRenderer.TileSource {

    BitmapRegionDecoder mDecoder;


    public BitmapRegionTileSource(String path) {
        try {
            mDecoder = BitmapRegionDecoder.newInstance(path, true);
        } catch (IOException e) {
            Log.w("BitmapRegionTileSource", "ctor failed", e);
        }
    }

    @Override
    public int getTileSize() {
        return 256;
    }

    @Override
    public int getImageWidth() {
        return mDecoder.getWidth();
    }

    @Override
    public int getImageHeight() {
        return mDecoder.getHeight();
    }

    @Override
    public Bitmap getTile(int level, int x, int y, Bitmap bitmap) {
        int tileSize = getTileSize();
        int t = tileSize << level;

        Rect wantRegion = new Rect(x, y, x + t, y + t);

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inPreferQualityOverSpeed = true;
        options.inSampleSize =  (1 << level);
        options.inBitmap = bitmap;

        try {
            // In CropImage, we may call the decodeRegion() concurrently.
            bitmap = mDecoder.decodeRegion(wantRegion, options);
        } finally {
            if (options.inBitmap != bitmap && options.inBitmap != null) {
                options.inBitmap = null;
            }
        }

        if (bitmap == null) {
            Log.w("BitmapRegionTileSource", "fail in decoding region");
        }
        return bitmap;
    }
}
