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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.android.camera2.R;

import java.util.List;

/**
 * PreviewOverlay is a view that sits on top of the preview. It serves to disambiguate
 * touch events, as {@link com.android.camera.app.CameraAppUI} has a touch listener
 * set on it. As a result, touch events that happen on preview will first go through
 * the touch listener in AppUI, which filters out swipes that should be handled on
 * the app level. The rest of the touch events will be handled here in
 * {@link #onTouchEvent(android.view.MotionEvent)}.
 * <p/>
 * For scale gestures, if an {@link OnZoomChangedListener} is set, the listener
 * will receive callbacks as the scaling happens, and a zoom UI will be hosted in
 * this class.
 */
public class PreviewOverlay extends View {

    private static final String TAG = "PreviewOverlay";

    private final ScaleGestureDetector mScaleDetector;
    private final ZoomProcessor mZoomProcessor = new ZoomProcessor();
    private GestureDetector mGestureDetector = null;
    private OnZoomChangedListener mZoomListener = null;

    public interface OnZoomChangedListener {
        /**
         * This gets called when a zoom is detected and started.
         */
        void onZoomStart();

        /**
         * This gets called when zoom gesture has ended.
         */
        void onZoomEnd();

        /**
         * This gets called when scale gesture changes the zoom value.
         *
         * @param index index of the list of supported zoom ratios
         */
        void onZoomValueChanged(int index);  // only for immediate zoom
    }

    public PreviewOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScaleDetector = new ZoomGestureDetector();
    }

    /**
     * This sets up the zoom listener and zoom related parameters.
     *
     * @param zoomMax max zoom index
     * @param zoom current zoom index
     * @param zoomRatios a list of zoom ratios
     * @param zoomChangeListener a listener that receives callbacks when zoom changes
     */
    public void setupZoom(int zoomMax, int zoom, List<Integer> zoomRatios,
                          OnZoomChangedListener zoomChangeListener) {
        mZoomListener = zoomChangeListener;
        mZoomProcessor.setupZoom(zoomMax, zoom, zoomRatios);
    }

    @Override
    public boolean onTouchEvent(MotionEvent m) {
        // Pass the touch events to scale detector and gesture detector
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(m);
        }
        mScaleDetector.onTouchEvent(m);
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mZoomProcessor.layout(left, top, right, bottom);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mZoomProcessor.draw(canvas);
    }

    /**
     * Each module can pass in their own gesture listener through App UI. When a gesture
     * is detected, the {#link GestureDetector.OnGestureListener} will be notified of
     * the gesture.
     *
     * @param gestureListener a listener from a module that defines how to handle gestures
     */
    public void setGestureListener(GestureDetector.OnGestureListener gestureListener) {
        if (gestureListener != null) {
            mGestureDetector = new GestureDetector(getContext(), gestureListener);
        }
    }

    /**
     * During module switch, connections to the previous module should be cleared.
     */
    public void reset() {
        mZoomListener = null;
        mGestureDetector = null;
    }

    /**
     * Custom scale gesture detector that ignores touch events when no
     * {@link OnZoomChangedListener} is set. Otherwise, it calculates the real-time
     * angle between two fingers in a scale gesture.
     */
    private class ZoomGestureDetector extends ScaleGestureDetector {
        private float mDeltaX;
        private float mDeltaY;

        public ZoomGestureDetector() {
            super(getContext(), mZoomProcessor);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (mZoomListener == null) {
                return false;
            } else {
                boolean handled = super.onTouchEvent(ev);
                if (ev.getPointerCount() > 1) {
                    mDeltaX = ev.getX(1) - ev.getX(0);
                    mDeltaY = ev.getY(1) - ev.getY(0);
                }
                return handled;
            }
        }

        /**
         * Calculate the angle between two fingers. Range: [-pi, pi]
         */
        public float getAngle() {
            return (float) Math.atan2(mDeltaY, mDeltaX);
        }
    }

    /**
     * This class processes recognized scale gestures, notifies {@link OnZoomChangedListener}
     * of any change in scale, and draw the zoom UI on screen.
     */
    private class ZoomProcessor implements ScaleGestureDetector.OnScaleGestureListener {
        private static final String TAG = "CAM_Zoom";

        private int mMaxZoom;
        private int mMinZoom;
        private Paint mPaint;
        private Paint mTextPaint;
        private int mCircleSize;
        private int mCenterX;
        private int mCenterY;
        private float mMaxCircle;
        private float mMinCircle;
        private int mInnerStroke;
        private int mOuterStroke;
        private int mZoomSig;
        private int mZoomFraction;
        private Rect mTextBounds;
        private boolean mVisible = false;
        private List<Integer> mZoomRatios;

        public ZoomProcessor() {
            Resources res = getResources();
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.WHITE);
            mPaint.setStyle(Paint.Style.STROKE);
            mTextPaint = new Paint(mPaint);
            mTextPaint.setStyle(Paint.Style.FILL);
            mTextPaint.setTextSize(res.getDimensionPixelSize(R.dimen.zoom_font_size));
            mTextPaint.setTextAlign(Paint.Align.LEFT);
            mTextPaint.setAlpha(192);
            mInnerStroke = res.getDimensionPixelSize(R.dimen.focus_inner_stroke);
            mOuterStroke = res.getDimensionPixelSize(R.dimen.focus_outer_stroke);
            mMinCircle = res.getDimensionPixelSize(R.dimen.zoom_ring_min);
            mTextBounds = new Rect();
        }

        public void setZoomMax(int zoomMaxIndex) {
            mMaxZoom = zoomMaxIndex;
            mMinZoom = 0;
        }

        public void setZoom(int index) {
            mCircleSize = (int) (mMinCircle + index * (mMaxCircle - mMinCircle)
                    / (mMaxZoom - mMinZoom));
            setZoomValue(mZoomRatios.get(index));
        }

        public void setZoomValue(int value) {
            value = value / 10;
            mZoomSig = value / 10;
            mZoomFraction = value % 10;
        }

        public void layout(int l, int t, int r, int b) {
            mCenterX = (r - l) / 2;
            mCenterY = (b - t) / 2;
            int width = r - l;
            int height = b - t;
            mMaxCircle = Math.min(width, height);
            mMaxCircle = (mMaxCircle - mMinCircle) / 2;
        }

        public void draw(Canvas canvas) {
            if (!mVisible) {
                return;
            }
            mPaint.setStrokeWidth(mInnerStroke);
            canvas.drawCircle(mCenterX, mCenterY, mMinCircle, mPaint);
            canvas.drawCircle(mCenterX, mCenterY, mMaxCircle, mPaint);
            canvas.drawLine(mCenterX - mMinCircle, mCenterY,
                    mCenterX - mMaxCircle - 4, mCenterY, mPaint);
            mPaint.setStrokeWidth(mOuterStroke);
            canvas.drawCircle((float) mCenterX, (float) mCenterY,
                    (float) mCircleSize, mPaint);
            String txt = mZoomSig+"."+mZoomFraction+"x";
            mTextPaint.getTextBounds(txt, 0, txt.length(), mTextBounds);
            canvas.drawText(txt, mCenterX - mTextBounds.centerX(), mCenterY - mTextBounds.centerY(),
                    mTextPaint);
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float sf = detector.getScaleFactor();
            float circle = (int) (mCircleSize * sf * sf);
            circle = Math.max(mMinCircle, circle);
            circle = Math.min(mMaxCircle, circle);
            if (mZoomListener != null && (int) circle != mCircleSize) {
                mCircleSize = (int) circle;
                int zoom = mMinZoom + (int) ((mCircleSize - mMinCircle) * (mMaxZoom - mMinZoom)
                        / (mMaxCircle - mMinCircle));
                mZoomListener.onZoomValueChanged(zoom);
                setZoomValue(mZoomRatios.get(zoom));
            }
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mZoomListener == null) {
                return false;
            }
            mVisible = true;
            if (mZoomListener != null) {
                mZoomListener.onZoomStart();
            }
            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mVisible = false;
            if (mZoomListener != null) {
                mZoomListener.onZoomEnd();
            }
            invalidate();
        }

        private void setupZoom(int zoomMax, int zoom, List<Integer> zoomRatios) {
            mZoomRatios = zoomRatios;
            setZoomMax(zoomMax);
            setZoom(zoom);
        }
    };

}
