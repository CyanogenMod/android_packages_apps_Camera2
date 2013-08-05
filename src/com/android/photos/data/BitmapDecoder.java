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
package com.android.photos.data;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Pools.Pool;
import android.util.Pools.SynchronizedPool;

import com.android.gallery3d.common.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * BitmapDecoder keeps a pool of temporary storage to reuse for decoding
 * bitmaps. It also simplifies the multi-stage decoding required to efficiently
 * use GalleryBitmapPool. The static methods decode and decodeFile can be used
 * to decode a bitmap from GalleryBitmapPool. The bitmap may be returned
 * directly to GalleryBitmapPool or use the put method here when the bitmap is
 * ready to be recycled.
 */
public class BitmapDecoder {
    private static final String TAG = BitmapDecoder.class.getSimpleName();
    private static final int POOL_SIZE = 4;
    private static final int TEMP_STORAGE_SIZE_BYTES = 16 * 1024;
    private static final int HEADER_MAX_SIZE = 16 * 1024;

    private static final Pool<BitmapFactory.Options> sOptions =
            new SynchronizedPool<BitmapFactory.Options>(POOL_SIZE);

    public static Bitmap decode(InputStream in) {
        BitmapFactory.Options opts = getOptions();
        try {
            if (!in.markSupported()) {
                in = new BufferedInputStream(in);
            }
            opts.inJustDecodeBounds = true;
            in.mark(HEADER_MAX_SIZE);
            BitmapFactory.decodeStream(in, null, opts);
            in.reset();
            opts.inJustDecodeBounds = false;
            GalleryBitmapPool pool = GalleryBitmapPool.getInstance();
            Bitmap reuseBitmap = pool.get(opts.outWidth, opts.outHeight);
            opts.inBitmap = reuseBitmap;
            Bitmap decodedBitmap = BitmapFactory.decodeStream(in, null, opts);
            if (reuseBitmap != null && decodedBitmap != reuseBitmap) {
                pool.put(reuseBitmap);
            }
            return decodedBitmap;
        } catch (IOException e) {
            Log.e(TAG, "Could not decode stream to bitmap", e);
            return null;
        } finally {
            Utils.closeSilently(in);
            release(opts);
        }
    }

    public static Bitmap decode(File in) {
        return decodeFile(in.toString());
    }

    public static Bitmap decodeFile(String in) {
        BitmapFactory.Options opts = getOptions();
        try {
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(in, opts);
            opts.inJustDecodeBounds = false;
            GalleryBitmapPool pool = GalleryBitmapPool.getInstance();
            Bitmap reuseBitmap = pool.get(opts.outWidth, opts.outHeight);
            opts.inBitmap = reuseBitmap;
            Bitmap decodedBitmap = BitmapFactory.decodeFile(in, opts);
            if (reuseBitmap != null && decodedBitmap != reuseBitmap) {
                pool.put(reuseBitmap);
            }
            return decodedBitmap;
        } finally {
            release(opts);
        }
    }

    public static void put(Bitmap bitmap) {
        GalleryBitmapPool.getInstance().put(bitmap);
    }

    private static BitmapFactory.Options getOptions() {
        BitmapFactory.Options opts = sOptions.acquire();
        if (opts == null) {
            opts = new BitmapFactory.Options();
            opts.inMutable = true;
            opts.inPreferredConfig = Config.ARGB_8888;
            opts.inSampleSize = 1;
            opts.inTempStorage = new byte[TEMP_STORAGE_SIZE_BYTES];
        }

        return opts;
    }

    private static void release(BitmapFactory.Options opts) {
        opts.inBitmap = null;
        opts.inJustDecodeBounds = false;
        sOptions.release(opts);
    }
}
