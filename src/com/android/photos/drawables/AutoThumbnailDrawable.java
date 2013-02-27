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

package com.android.photos.drawables;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoThumbnailDrawable extends Drawable {

    private static final String TAG = "AutoMipMapDrawable";

    private static ExecutorService sThreadPool = Executors.newSingleThreadExecutor();
    private static byte[] sTempStorage = new byte[64 * 1024];

    private Bitmap mBitmap;
    private Paint mPaint = new Paint();
    private String mDataUri;
    private boolean mIsQueued;
    private int mImageWidth, mImageHeight;
    private BitmapFactory.Options mOptions = new BitmapFactory.Options();
    private Rect mBounds = new Rect();
    private Matrix mDrawMatrix = new Matrix();
    private int mSampleSize = 1;

    public AutoThumbnailDrawable() {
        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
        mDrawMatrix.reset();
        mOptions.inTempStorage = sTempStorage;
    }

    public void setImage(String dataUri, int width, int height) {
        if (TextUtils.equals(mDataUri, dataUri)) return;
        synchronized (this) {
            mImageWidth = width;
            mImageHeight = height;
            mDataUri = dataUri;
            mBitmap = null;
            refreshSampleSizeLocked();
        }
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        synchronized (this) {
            mBounds.set(bounds);
            if (mBounds.isEmpty()) {
                mBitmap = null;
            } else {
                refreshSampleSizeLocked();
                updateDrawMatrixLocked();
            }
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mBitmap != null) {
            canvas.save();
            canvas.clipRect(mBounds);
            canvas.concat(mDrawMatrix);
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            canvas.restore();
        } else {
            // TODO: Draw placeholder...?
        }
    }

    private void updateDrawMatrixLocked() {
        if (mBitmap == null || mBounds.isEmpty()) {
            mDrawMatrix.reset();
            return;
        }

        float scale;
        float dx = 0, dy = 0;

        int dwidth = mBitmap.getWidth();
        int dheight = mBitmap.getHeight();
        int vwidth = mBounds.width();
        int vheight = mBounds.height();

        // Calculates a matrix similar to ScaleType.CENTER_CROP
        if (dwidth * vheight > vwidth * dheight) {
            scale = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scale) * 0.5f;
        } else {
            scale = (float) vwidth / (float) dwidth;
            dy = (vheight - dheight * scale) * 0.5f;
        }
        if (scale < .8f) {
            Log.w(TAG, "sample size was too small! Overdrawing! " + scale + ", " + mSampleSize);
        } else if (scale > 1.5f) {
            Log.w(TAG, "Potential quality loss! " + scale + ", " + mSampleSize);
        }

        mDrawMatrix.setScale(scale, scale);
        mDrawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
    }

    private int calculateSampleSizeLocked(int dwidth, int dheight) {
        float scale;

        int vwidth = mBounds.width();
        int vheight = mBounds.height();

        // Inverse of updateDrawMatrixLocked
        if (dwidth * vheight > vwidth * dheight) {
            scale = (float) dheight / (float) vheight;
        } else {
            scale = (float) dwidth / (float) vwidth;
        }
        return (int) (scale + .5f);
    }

    private void refreshSampleSizeLocked() {
        if (mBounds.isEmpty()) return;

        int sampleSize = calculateSampleSizeLocked(mImageWidth, mImageHeight);
        if (sampleSize != mSampleSize || mBitmap == null) {
            mSampleSize = sampleSize;
            loadBitmapLocked();
        }
    }

    private void loadBitmapLocked() {
        if (!mIsQueued && !mBounds.isEmpty()) {
            unscheduleSelf(mUpdateBitmap);
            sThreadPool.execute(mLoadBitmap);
            mIsQueued = true;
        }
    }

    public float getAspectRatio() {
        return (float) mImageWidth / (float) mImageHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }

    @Override
    public int getOpacity() {
        Bitmap bm = mBitmap;
        return (bm == null || bm.hasAlpha() || mPaint.getAlpha() < 255) ?
                PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public void setAlpha(int alpha) {
        int oldAlpha = mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        invalidateSelf();
    }

    private final Runnable mLoadBitmap = new Runnable() {
        @Override
        public void run() {
            // TODO: Use bitmap pool
            String data;
            int sampleSize;
            synchronized (this) {
                data = mDataUri;
                sampleSize = calculateSampleSizeLocked(mImageWidth, mImageHeight);
                mSampleSize = sampleSize;
                mIsQueued = false;
            }
            FileInputStream fis = null;
            try {
                ExifInterface exif = new ExifInterface(data);
                if (exif.hasThumbnail()) {
                    byte[] thumbnail = exif.getThumbnail();
                    mOptions.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(thumbnail, 0,
                            thumbnail.length, mOptions);
                    int exifThumbSampleSize = calculateSampleSizeLocked(
                            mOptions.outWidth, mOptions.outHeight);
                    mOptions.inJustDecodeBounds = false;
                    mOptions.inSampleSize = exifThumbSampleSize;
                    mBitmap = BitmapFactory.decodeByteArray(thumbnail, 0,
                            thumbnail.length, mOptions);
                    if (mBitmap != null) {
                        synchronized (this) {
                            if (TextUtils.equals(data, mDataUri)) {
                                scheduleSelf(mUpdateBitmap, 0);
                            }
                        }
                        return;
                    }
                }
                fis = new FileInputStream(data);
                FileDescriptor fd = fis.getFD();
                mOptions.inSampleSize = sampleSize;
                mBitmap = BitmapFactory.decodeFileDescriptor(fd, null, mOptions);
            } catch (Exception e) {
                Log.d("AsyncBitmap", "Failed to fetch bitmap", e);
                return;
            } finally {
                try {
                    fis.close();
                } catch (Exception e) {}
            }
            synchronized (this) {
                if (TextUtils.equals(data, mDataUri)) {
                    scheduleSelf(mUpdateBitmap, 0);
                }
            }
        }
    };

    private final Runnable mUpdateBitmap = new Runnable() {

        @Override
        public void run() {
            synchronized (AutoThumbnailDrawable.this) {
                updateDrawMatrixLocked();
                invalidateSelf();
            }
        }
    };

}
