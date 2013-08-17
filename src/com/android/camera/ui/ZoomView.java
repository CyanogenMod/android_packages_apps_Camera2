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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ZoomView extends FrameLayout {

    private static final String TAG = "ZoomView";

    // Gesture statuses
    private static final int IDLE = 0;
    private static final int SCALE = 1;
    private static final int SCROLL = 2;

    // When the image is zoomed in to within TOLERANCE pixels of its original size,
    // we consider it all the way zoomed in
    private static final int TOLERANCE = 10;

    private static final int ANIMATION_DURATION_MS = 200;

    private int mViewportWidth = 0;
    private int mViewportHeight = 0;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGesturesDetector;
    private int mGestureState = IDLE;

    private ImageView mPartialImage;
    private ImageView mFullImage;

    private RectF mInitialRect;
    private int mFullResImageWidth;
    private int mFullResImageHeight;

    private BitmapRegionDecoder mRegionDecoder;
    private DecodePartialBitmap mPartialDecodingTask;
    private LoadBitmapTask mFullImageDecodingTask;
    private RectF mBitmapRect;

    private float mLastScalePivotX;
    private float mLastScalePivotY;

    private ObjectAnimator mAnimator;
    private Uri mUri;

    private TypeEvaluator<Matrix> mEvaluator = new TypeEvaluator<Matrix>() {
        @Override
        public Matrix evaluate(float fraction, Matrix startValue, Matrix endValue) {

            RectF startRect = new RectF();
            startValue.mapRect(startRect, mBitmapRect);
            RectF endRect = new RectF();
            endValue.mapRect(endRect, mBitmapRect);

            float top = startRect.top + (endRect.top - startRect.top) * fraction;
            float left = startRect.left + (endRect.left - startRect.left) * fraction;
            float right = startRect.right + (endRect.right - startRect.right) * fraction;
            float bottom = startRect.bottom + (endRect.bottom - startRect.bottom) * fraction;
            RectF currentRect = new RectF(left, top, right, bottom);

            Matrix m = new Matrix();
            m.setRectToRect(mBitmapRect, currentRect, Matrix.ScaleToFit.CENTER);
            return m;
        }
    };

    private GestureDetector.SimpleOnGestureListener mGestureListener
            = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            zoomAt(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            if (mGestureState == SCALE) {
                return false;
            }
            mGestureState = SCROLL;

            // Translate image matrix
            Matrix m = new Matrix(mFullImage.getImageMatrix());
            m.postTranslate(-distanceX, -distanceY);
            mFullImage.setImageMatrix(m);
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            showPartiallyDecodedImage(true);
            return true;
        }
    };

    private ScaleGestureDetector.OnScaleGestureListener mScaleListener
            = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            mLastScalePivotX = detector.getFocusX();
            mLastScalePivotY = detector.getFocusY();

            // Scale image matrix
            Matrix m = new Matrix(mFullImage.getImageMatrix());
            m.postScale(scaleFactor, scaleFactor, mLastScalePivotX, mLastScalePivotY);
            mFullImage.setImageMatrix(m);
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mGestureState = SCALE;
            cancelPartialDecodingTask();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mGestureState = IDLE;
            snapBack();
        }
    };

    private void cancelPartialDecodingTask() {
        if (mPartialDecodingTask != null) {
            mPartialDecodingTask.cancel(true);
        }
    }

    private class LoadBitmapTask extends AsyncTask<Object, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Object... params) {
            // Params[0]: BitmapFactory.options
            if (params.length < 1) {
                return null;
            }
            InputStream is = getInputStream();
            if (isCancelled()) {
                return null;
            }
            BitmapFactory.Options options = (BitmapFactory.Options) params[0];
            return BitmapFactory.decodeStream(is, null, options);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap");
                return;
            }
            initFullImageView(bitmap);
            mFullImageDecodingTask = null;
        }
    }

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
            mPartialImage.setImageBitmap(b);
            showPartiallyDecodedImage(true);
            mPartialDecodingTask = null;
        }
    }

    public ZoomView(Context context, Uri uri) {
        super(context);
        mUri = uri;
        mPartialImage = new ImageView(context);
        mFullImage = new ImageView(context);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        addView(mPartialImage, lp);
        addView(mFullImage, lp);
        mFullImage.setScaleType(ImageView.ScaleType.MATRIX);
        InputStream is = getInputStream();
        try {
            mRegionDecoder = BitmapRegionDecoder.newInstance(is, false);
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Fail to instantiate region decoder");
        }

        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int w = right - left;
                int h = bottom - top;
                if (mViewportHeight != h || mViewportWidth != w) {
                    mViewportWidth = w;
                    mViewportHeight = h;
                    loadBitmap();
                }
            }
        });
        mGesturesDetector = new GestureDetector(getContext(), mGestureListener);
        mScaleDetector = new ScaleGestureDetector(getContext(), mScaleListener);
    }

    private void initFullImageView(Bitmap bitmap) {
        mFullImage.setImageBitmap(bitmap);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        mBitmapRect = new RectF(0, 0, w, h);
        Matrix initialMatrix = new Matrix();
        initialMatrix.setRectToRect(mBitmapRect, mInitialRect, Matrix.ScaleToFit.CENTER);
        mFullImage.setImageMatrix(initialMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // TODO: The touch event handling could use some refinement
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            endAnimation();
            cancelPartialDecodingTask();
            // Show down-sampled full image when there is touch interaction
            showPartiallyDecodedImage(false);
            mGestureState = IDLE;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_UP
            && mGestureState == SCROLL) {
            snapBack();
            mGestureState = IDLE;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            mGestureState = SCALE;
        }
        boolean ret = mGesturesDetector.onTouchEvent(ev);
        return mScaleDetector.onTouchEvent(ev) || ret;
    }

    private void showPartiallyDecodedImage(boolean show) {
        if (show) {
            mPartialImage.setVisibility(View.VISIBLE);
            mFullImage.setVisibility(View.GONE);
        } else {
            mFullImage.setVisibility(View.VISIBLE);
            mPartialImage.setVisibility(View.GONE);
        }
    }

    /**
     * Snap back to the screen bounds from current position
     */
    private void snapBack() {
        RectF endRect = new RectF();
        mFullImage.getImageMatrix().mapRect(endRect, mBitmapRect);
        snapBack(endRect);
    }

    /**
     * Snap back to the screen bounds from given position
     */
    private void snapBack(RectF endRect) {

        if (endRect.width() < mViewportWidth && endRect.height() < mViewportHeight) {
            snapToInitialRect(true);
            return;
        }

        float dx = 0, dy = 0;
        Matrix startMatrix = mFullImage.getImageMatrix();
        Matrix endMatrix = new Matrix(startMatrix);
        boolean needsSnapping = false;

        if (endRect.width() > mFullResImageWidth) {
            needsSnapping = true;
            float x = mScaleDetector.getFocusX();
            float y = mScaleDetector.getFocusY();
            float scale = mFullResImageWidth / endRect.width();
            endMatrix.postScale(scale, scale, x, y);
            endMatrix.mapRect(endRect, mBitmapRect);
        }

        if (endRect.width() < mViewportWidth) {
            // Center it
            dx = mViewportWidth / 2 - (endRect.left + endRect.right) / 2;
        } else {
            if (endRect.left > 0) {
                dx = -endRect.left;
            } else if (endRect.right < mViewportWidth) {
                dx = mViewportWidth - endRect.right;
            }
        }

        if (endRect.height() < mViewportHeight) {
            dy = mViewportHeight / 2 - (endRect.top + endRect.bottom) / 2;
        } else {
            if (endRect.top > 0) {
                dy = -endRect.top;
            } else if (endRect.bottom < mViewportHeight) {
                dy = mViewportHeight - endRect.bottom;
            }
        }

        if (dx != 0 || dy != 0 || needsSnapping) {
            endRect.offset(dx, dy);
            endMatrix.postTranslate(dx, dy);
            startAnimation(startMatrix, endMatrix);
        }

        startPartialDecodingTask(endRect);
    }

    private void snapToInitialRect(boolean withAnimation) {
        // Restore to initial rect
        Matrix endMatrix = new Matrix();
        endMatrix.setRectToRect(mBitmapRect, mInitialRect, Matrix.ScaleToFit.CENTER);
        if (withAnimation) {
            startAnimation(mFullImage.getImageMatrix(), endMatrix);
        } else {
            mFullImage.setImageMatrix(endMatrix);
        }
    }

    private void zoomAt(float x, float y) {
        Matrix startMatrix = mFullImage.getImageMatrix();
        Matrix endMatrix = new Matrix();
        RectF currentImageRect = new RectF();
        startMatrix.mapRect(currentImageRect, mBitmapRect);

        if (currentImageRect.width() < mFullResImageWidth - TOLERANCE) {
            // Zoom in
            float scale = ((float) mFullResImageWidth) / currentImageRect.width();
            endMatrix.set(startMatrix);
            endMatrix.postScale(scale, scale, x, y);
            // Start animation
            startAnimation(startMatrix, endMatrix);

            RectF endRect = new RectF();
            endMatrix.mapRect(endRect, mBitmapRect);
            startPartialDecodingTask(endRect);
        } else {
            // Zoom out
            endMatrix.setRectToRect(mBitmapRect, mInitialRect, Matrix.ScaleToFit.CENTER);
            // Start animation
            startAnimation(startMatrix, endMatrix);
        }

    }

    private void startAnimation(Matrix startMatrix, final Matrix endMatrix) {
        endAnimation();
        showPartiallyDecodedImage(false);
        mAnimator = ObjectAnimator.ofObject(mFullImage, "imageMatrix", mEvaluator,
                startMatrix, endMatrix)
                .setDuration(ANIMATION_DURATION_MS);
        mAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Set end value
                mFullImage.setImageMatrix(endMatrix);
                mAnimator.removeAllListeners();
                mAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mAnimator.start();
    }

    private void endAnimation() {
        if (mAnimator == null) {
            return;
        }
        if (mAnimator.isRunning()) {
            mAnimator.end();
        }
    }

    private void startPartialDecodingTask(RectF endRect) {
        // Cancel on-going partial decoding tasks
        cancelPartialDecodingTask();
        mPartialDecodingTask = new DecodePartialBitmap();
        mPartialDecodingTask.execute(endRect);
    }

    private void loadBitmap() {
        if (mFullResImageHeight == 0 || mFullResImageWidth == 0) {
            decodeImageSize();
        }

        if (mViewportHeight != 0 && mViewportWidth != 0) {
            // Calculate where the bitmap rect should be positioned based on viewport size
            calculateInitialRect();
            if (mBitmapRect != null && mBitmapRect.width() > mInitialRect.width()
                    && mBitmapRect.height() > mInitialRect.height()) {
                // No need to reload bitmap
                snapToInitialRect(false);
            } else {
                BitmapFactory.Options option = new BitmapFactory.Options();
                // Down-sample the bitmap whenever possible to be efficient
                int sampleFactor = getSampleFactor(mFullResImageWidth, mFullResImageHeight);
                option.inSampleSize = sampleFactor;
                if (mFullImageDecodingTask != null) {
                    mFullImageDecodingTask.cancel(true);
                }
                mFullImageDecodingTask = new LoadBitmapTask();
                mFullImageDecodingTask.execute(option);
            }
        }
    }

    private void calculateInitialRect() {
        float fitWidthScale = ((float) mViewportWidth) / ((float) mFullResImageWidth);
        float fitHeightScale = ((float) mViewportHeight) / ((float) mFullResImageHeight);
        float scale = Math.min(fitHeightScale, fitWidthScale);

        int centerX = mViewportWidth / 2;
        int centerY = mViewportHeight / 2;
        int width = (int) (scale * mFullResImageWidth);
        int height = (int) (scale * mFullResImageHeight);

        mInitialRect = new RectF(centerX - width / 2, centerY - height / 2,
                centerX + width / 2, centerY + height / 2);
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
