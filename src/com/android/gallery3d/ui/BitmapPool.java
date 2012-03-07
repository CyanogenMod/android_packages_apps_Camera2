// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

import com.android.gallery3d.data.DecodeUtils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.io.FileDescriptor;
import java.util.ArrayList;

public class BitmapPool {
    private static final String TAG = "BitmapPool";

    public static final int TYPE_MICRO_THUMB = 0;
    private static final int TYPE_COUNT = 1;
    private static final int POOL_SIZE = 16;
    private static final int EXPECTED_WIDTH[] = {MediaItem.MICROTHUMBNAIL_TARGET_SIZE};
    private static final int EXPECTED_HEIGHT[] = {MediaItem.MICROTHUMBNAIL_TARGET_SIZE};

    @SuppressWarnings("unchecked")
    private static final ArrayList<Bitmap> sPools[] = new ArrayList[TYPE_COUNT];
    static {
        for (int i = 0; i < TYPE_COUNT; ++i) {
            sPools[i] = new ArrayList<Bitmap>();
        }
    }

    private BitmapPool() {
    }

    public static Bitmap getBitmap(int type) {
        ArrayList<Bitmap> list = sPools[type];
        synchronized (list) {
            int size = list.size();
            return size > 0 ? list.remove(size - 1) : null;
        }
    }

    public static void recycle(int type, Bitmap bitmap) {
        if (bitmap == null) return;
        if ((bitmap.getWidth() != EXPECTED_WIDTH[type])
                || (bitmap.getHeight() != EXPECTED_HEIGHT[type])) {
            bitmap.recycle();
            return;
        }
        ArrayList<Bitmap> list = sPools[type];
        synchronized (list) {
            if (list.size() < POOL_SIZE) list.add(bitmap);
        }
    }

    public static void clear() {
        for (int i = 0; i < TYPE_COUNT; ++i) {
            ArrayList<Bitmap> list = sPools[i];
            synchronized (list) {
                list.clear();
            }
        }
    }

    public static Bitmap decode(JobContext jc, int type,
            byte[] data, int offset, int length, BitmapFactory.Options options) {
        if (options == null) options = new BitmapFactory.Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1) ? getBitmap(type) : null;
        try {
            Bitmap bitmap = DecodeUtils.decode(jc, data, offset, length, options);
            if (options.inBitmap != null && options.inBitmap != bitmap) {
                recycle(type, bitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) throw e;

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            recycle(type, options.inBitmap);
            options.inBitmap = null;
            return DecodeUtils.decode(jc, data, offset, length, options);
        }
    }

    // This is the same as the method above except the source data comes
    // from a file descriptor instead of a byte array.
    public static Bitmap decode(int type,
            JobContext jc, FileDescriptor fileDescriptor, Options options) {
        if (options == null) options = new BitmapFactory.Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1) ? getBitmap(type) : null;
        try {
            Bitmap bitmap = DecodeUtils.decode(jc, fileDescriptor, options);
            if (options.inBitmap != null&& options.inBitmap != bitmap) {
                recycle(type, bitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) throw e;

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            recycle(type, options.inBitmap);
            options.inBitmap = null;
            return DecodeUtils.decode(jc, fileDescriptor, options);
        }
    }
}
