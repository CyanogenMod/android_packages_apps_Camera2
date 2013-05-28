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
import android.graphics.BitmapFactory.Options;
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
    private static final int HEADER_MAX_SIZE = 128 * 1024;

    private static final Pool<BitmapFactory.Options> sOptions =
            new SynchronizedPool<BitmapFactory.Options>(POOL_SIZE);

    private interface Decoder<T> {
        Bitmap decode(T input, BitmapFactory.Options options);

        boolean decodeBounds(T input, BitmapFactory.Options options);
    }

    private static abstract class OnlyDecode<T> implements Decoder<T> {
        @Override
        public boolean decodeBounds(T input, BitmapFactory.Options options) {
            decode(input, options);
            return true;
        }
    }

    private static final Decoder<InputStream> sStreamDecoder = new Decoder<InputStream>() {
        @Override
        public Bitmap decode(InputStream is, Options options) {
            return BitmapFactory.decodeStream(is, null, options);
        }

        @Override
        public boolean decodeBounds(InputStream is, Options options) {
            is.mark(HEADER_MAX_SIZE);
            BitmapFactory.decodeStream(is, null, options);
            try {
                is.reset();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Could not decode stream to bitmap", e);
                return false;
            }
        }
    };

    private static final Decoder<String> sFileDecoder = new OnlyDecode<String>() {
        @Override
        public Bitmap decode(String filePath, Options options) {
            return BitmapFactory.decodeFile(filePath, options);
        }
    };

    private static final Decoder<byte[]> sByteArrayDecoder = new OnlyDecode<byte[]>() {
        @Override
        public Bitmap decode(byte[] data, Options options) {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        }
    };

    private static <T> Bitmap delegateDecode(Decoder<T> decoder, T input) {
        BitmapFactory.Options options = getOptions();
        GalleryBitmapPool pool = GalleryBitmapPool.getInstance();
        try {
            options.inJustDecodeBounds = true;
            if (!decoder.decodeBounds(input, options)) {
                return null;
            }
            options.inJustDecodeBounds = false;
            Bitmap reuseBitmap = pool.get(options.outWidth, options.outHeight);
            options.inBitmap = reuseBitmap;
            Bitmap decodedBitmap = decoder.decode(input, options);
            if (reuseBitmap != null && decodedBitmap != reuseBitmap) {
                pool.put(reuseBitmap);
            }
            return decodedBitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) {
                throw e;
            }
            pool.put(options.inBitmap);
            options.inBitmap = null;
            return decoder.decode(input, options);
        } finally {
            options.inBitmap = null;
            options.inJustDecodeBounds = false;
            sOptions.release(options);
        }
    }

    public static Bitmap decode(InputStream in) {
        try {
            if (!in.markSupported()) {
                in = new BufferedInputStream(in);
            }
            return delegateDecode(sStreamDecoder, in);
        } finally {
            Utils.closeSilently(in);
        }
    }

    public static Bitmap decode(File file) {
        return decodeFile(file.getPath());
    }

    public static Bitmap decodeFile(String path) {
        return delegateDecode(sFileDecoder, path);
    }

    public static Bitmap decodeByteArray(byte[] data) {
        return delegateDecode(sByteArrayDecoder, data);
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
}
