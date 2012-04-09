// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.gallery3d.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

import com.android.gallery3d.ui.Log;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.io.FileDescriptor;
import java.util.ArrayList;

public class BitmapPool {
    private static final String TAG = "BitmapPool";

    private static final int POOL_SIZE = 16;
    private final ArrayList<Bitmap> mPool = new ArrayList<Bitmap>(POOL_SIZE);

    private final int mWidth;
    private final int mHeight;

    public BitmapPool(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public synchronized Bitmap getBitmap() {
        int size = mPool.size();
        return size > 0 ? mPool.remove(size - 1) : null;
    }

    public void recycle(Bitmap bitmap) {
        if (bitmap == null) return;
        if ((bitmap.getWidth() != mWidth) || (bitmap.getHeight() != mHeight)) {
            bitmap.recycle();
            return;
        }
        synchronized (this) {
            if (mPool.size() < POOL_SIZE) mPool.add(bitmap);
        }
    }

    public synchronized void clear() {
        mPool.clear();
    }

    public Bitmap decode(JobContext jc,
            byte[] data, int offset, int length, BitmapFactory.Options options) {
        if (options == null) options = new BitmapFactory.Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1) ? getBitmap() : null;
        try {
            Bitmap bitmap = DecodeUtils.decode(jc, data, offset, length, options);
            if (options.inBitmap != null && options.inBitmap != bitmap) {
                recycle(options.inBitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) throw e;

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            recycle(options.inBitmap);
            options.inBitmap = null;
            return DecodeUtils.decode(jc, data, offset, length, options);
        }
    }

    // This is the same as the method above except the source data comes
    // from a file descriptor instead of a byte array.
    public Bitmap decode(JobContext jc,
            FileDescriptor fileDescriptor, Options options) {
        if (options == null) options = new BitmapFactory.Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1) ? getBitmap() : null;
        try {
            Bitmap bitmap = DecodeUtils.decode(jc, fileDescriptor, options);
            if (options.inBitmap != null&& options.inBitmap != bitmap) {
                recycle(options.inBitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) throw e;

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            recycle(options.inBitmap);
            options.inBitmap = null;
            return DecodeUtils.decode(jc, fileDescriptor, options);
        }
    }
}
