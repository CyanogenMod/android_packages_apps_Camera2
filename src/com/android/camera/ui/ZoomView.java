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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ZoomView extends ImageView {

    private static final String TAG = "ZoomView";

    private int mViewportWidth = 0;
    private int mViewportHeight = 0;

    private RectF mInitialRect;
    private int mFullResImageWidth;
    private int mFullResImageHeight;

    private BitmapRegionDecoder mRegionDecoder;
    private DecodePartialBitmap mPartialDecodingTask;

    private Uri mUri;

    private class DecodePartialBitmap extends AsyncTask<RectF, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(RectF... params) {
            RectF endRect = params[0];
            // Find intersection with the screen
            RectF visibleRect = new RectF(endRect);
            visibleRect.intersect(0, 0, mViewportWidth, mViewportHeight);

            Matrix m2 = new Matrix();
            m2.setRectToRect(endRect, new RectF(0, 0, mFullResImageWidth, mFullResImageHeight),
                    Matrix.ScaleToFit.CENTER);
            RectF visibleInImage = new RectF();
            m2.mapRect(visibleInImage, visibleRect);

            // Decode region
            Rect v = new Rect();
            visibleInImage.round(v);
            if (isCancelled()) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = getSampleFactor(v.width(), v.height());
            Bitmap b = mRegionDecoder.decodeRegion(v, options);
            return b;
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) {
                return;
            }
            setImageBitmap(b);
            showPartiallyDecodedImage(true);
            mPartialDecodingTask = null;
        }
    }

    public ZoomView(Context context) {
        super(context);
        setScaleType(ScaleType.CENTER_INSIDE);
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int w = right - left;
                int h = bottom - top;
                if (mViewportHeight != h || mViewportWidth != w) {
                    mViewportWidth = w;
                    mViewportHeight = h;
                }
            }
        });
    }

    public void loadBitmap(Uri uri, RectF imageRect) {
        mUri = uri;
        mFullResImageHeight = 0;
        mFullResImageWidth = 0;
        InputStream is = getInputStream();
        try {
            mRegionDecoder = BitmapRegionDecoder.newInstance(is, false);
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Fail to instantiate region decoder");
        }
        decodeImageSize();
        startPartialDecodingTask(imageRect);
    }

    private void showPartiallyDecodedImage(boolean show) {
        if (show) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
        mPartialDecodingTask = null;
    }

    public boolean onTouchEvent(MotionEvent e) {
        setVisibility(GONE);
        return false;
    }

    public void cancelPartialDecodingTask() {
        if (mPartialDecodingTask != null && !mPartialDecodingTask.isCancelled()) {
            mPartialDecodingTask.cancel(true);
            setVisibility(GONE);
        }
        mPartialDecodingTask = null;
    }

    /**
     * snap back to the screen bounds from current position
     */
    private void snapBack() {
    }

    /**
     * snap back to the screen bounds from given position
     * @param rect
     * @return resulting rect after snapping back
     */
    private RectF snapBack(RectF rect) {
        RectF newRect = new RectF(rect);
        if (rect.width() < mViewportWidth && rect.height() < mViewportHeight) {
            newRect = mInitialRect;
            return newRect;
        }

        float dx = 0, dy = 0;

        if (newRect.width() < mViewportWidth) {
            // Center it
            dx = mViewportWidth / 2 - (newRect.left + newRect.right) / 2;
        } else {
            if (newRect.left > 0) {
                dx = -newRect.left;
            } else if (newRect.right < mViewportWidth) {
                dx = mViewportWidth - newRect.right;
            }
        }

        if (newRect.height() < mViewportHeight) {
            dy = mViewportHeight / 2 - (newRect.top + newRect.bottom) / 2;
        } else {
            if (newRect.top > 0) {
                dy = -newRect.top;
            } else if (newRect.bottom < mViewportHeight) {
                dy = mViewportHeight - newRect.bottom;
            }
        }

        if (dx != 0 || dy != 0) {
            newRect.offset(dx, dy);
        }
        return newRect;
    }

    /**
     * If the given rect is smaller than viewport on x or y axis, center rect within
     * viewport on the corresponding axis. Otherwise, make sure viewport is within
     * the bounds of the rect.
     */
    public static Rect adjustToFitInBounds(Rect rect, int viewportWidth, int viewportHeight) {
        int dx = 0, dy = 0;
        Rect newRect = new Rect(rect);
        if (newRect.width() < viewportWidth) {
            dx = viewportWidth / 2 - (newRect.left + newRect.right) / 2;
        } else {
            if (newRect.left > 0) {
                dx = -newRect.left;
            } else if (newRect.right < viewportWidth) {
                dx = viewportWidth - newRect.right;
            }
        }

        if (newRect.height() < viewportHeight) {
            dy = viewportHeight / 2 - (newRect.top + newRect.bottom) / 2;
        } else {
            if (newRect.top > 0) {
                dy = -newRect.top;
            } else if (newRect.bottom < viewportHeight) {
                dy = viewportHeight - newRect.bottom;
            }
        }

        if (dx != 0 || dy != 0) {
            newRect.offset(dx, dy);
        }
        return newRect;
    }

    private void zoomAt(float x, float y) {
    /*  TODO: double tap to zoom
        Matrix startMatrix = mFullImage.getImageMatrix();
        Matrix endMatrix = new Matrix();
        RectF currentImageRect = new RectF();
        startMatrix.mapRect(currentImageRect, mBitmapRect);

        if (currentImageRect.width() < mFullResImageWidth - TOLERANCE) {
            // Zoom in
            float scale = ((float) mFullResImageWidth) / currentImageRect.width();
            endMatrix.set(startMatrix);
            endMatrix.postScale(scale, scale, x, y);
            RectF endRect = new RectF();
            endMatrix.mapRect(endRect, mBitmapRect);
            RectF snapBackRect = snapBack(endRect);
            endMatrix.setRectToRect(mBitmapRect, snapBackRect, Matrix.ScaleToFit.CENTER);
            // Start animation
            startAnimation(startMatrix, endMatrix);
            startPartialDecodingTask(snapBackRect);
        } else {
            // Zoom out
            endMatrix.setRectToRect(mBitmapRect, mInitialRect, Matrix.ScaleToFit.CENTER);
            // Start animation
            startAnimation(startMatrix, endMatrix);
        } */

    }

    private void startPartialDecodingTask(RectF endRect) {
        // Cancel on-going partial decoding tasks
        cancelPartialDecodingTask();
        mPartialDecodingTask = new DecodePartialBitmap();
        mPartialDecodingTask.execute(endRect);
    }

    private void decodeImageSize() {
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;
        InputStream is = getInputStream();
        BitmapFactory.decodeStream(is, null, option);
        try {
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close input stream");
        }
        mFullResImageWidth = option.outWidth;
        mFullResImageHeight = option.outHeight;
    }

    // TODO: Cache the inputstream
    private InputStream getInputStream() {
        InputStream is = null;
        try {
            is = getContext().getContentResolver().openInputStream(mUri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found at: " + mUri);
        }
        return is;
    }

    /**
     * Find closest sample factor that is power of 2, based on the given width and height
     *
     * @param width width of the partial region to decode
     * @param height height of the partial region to decode
     * @return sample factor
     */
    private int getSampleFactor(int width, int height) {

        float fitWidthScale = ((float) mViewportWidth) / ((float) width);
        float fitHeightScale = ((float) mViewportHeight) / ((float) height);

        float scale = Math.min(fitHeightScale, fitWidthScale);

        // Find the closest sample factor that is power of 2
        int sampleFactor = (int) (1f / scale);
        if (sampleFactor <=1) {
            return 1;
        }
        for (int i = 0; i < 32; i++) {
            if ((1 << (i + 1)) > sampleFactor) {
                sampleFactor = (1 << i);
                break;
            }
        }
        return sampleFactor;
    }
}
