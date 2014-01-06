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

    public static final int ZOOM_MIN_FACTOR = 100;

    private final ZoomGestureDetector mScaleDetector;
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

    /**
     * This sets up the zoom listener and zoom related parameters when
     * the range of zoom ratios is continuous.
     *
     * @param zoomMax max zoom ratio
     * @param zoom current zoom index
     * @param zoomChangeListener a listener that receives callbacks when zoom changes
     */
    public void setupZoom(float zoomMaxRatio, int zoom, OnZoomChangedListener zoomChangeListener) {
        mZoomListener = zoomChangeListener;
        int zoomMax = ((int) zoomMaxRatio * 100) - ZOOM_MIN_FACTOR;
        mZoomProcessor.setupZoom(zoomMax, zoom, null);
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
            return (float) Math.atan2(-mDeltaY, mDeltaX);
        }
    }

    /**
     * This class processes recognized scale gestures, notifies {@link OnZoomChangedListener}
     * of any change in scale, and draw the zoom UI on screen.
     */
    private class ZoomProcessor implements ScaleGestureDetector.OnScaleGestureListener {
        private static final String TAG = "ZoomProcessor";

        // Diameter of Zoom UI as fraction of maximum possible without clipping.
        private static final float ZOOM_UI_SIZE = 0.8f;
        // Diameter of Zoom UI donut hole as fraction of Zoom UI diameter.
        private static final float ZOOM_UI_DONUT = 0.25f;

        final private int mMinIndex = 0;
        private int mMaxIndex;
        // Discrete Zoom level [mMinIndex,mMaxIndex].
        private int mCurrentIndex;
        // Continuous Zoom level [0,1].
        private float mCurrentFraction;
        private double mFingerAngle;  // in radians.
        private final Paint mPaint;
        private int mCenterX;
        private int mCenterY;
        private float mOuterRadius;
        private float mInnerRadius;
        private final int mZoomStroke;
        private boolean mVisible = false;
        private List<Integer> mZoomRatios;

        public ZoomProcessor() {
            Resources res = getResources();
            mZoomStroke = res.getDimensionPixelSize(R.dimen.zoom_stroke);
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.WHITE);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mZoomStroke);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        // Set maximum Zoom Index from Module.
        public void setZoomMax(int zoomMaxIndex) {
            mMaxIndex = zoomMaxIndex;
        }

        // Set current Zoom Index from Module.
        public void setZoom(int index) {
            mCurrentIndex = index;
            mCurrentFraction = (float) index / (mMaxIndex - mMinIndex);
        }

        public void setZoomValue(int value) {
            // Do nothing because we are not display text value in current UI.
        }

        public void layout(int l, int t, int r, int b) {
            // TODO: Needs to be centered in preview TextureView
            mCenterX = (r - l) / 2;
            mCenterY = (b - t) / 2;
            // UI will extend from 20% to 80% of maximum inset circle.
            float insetCircleDiameter = Math.min(getWidth(), getHeight());
            mOuterRadius = insetCircleDiameter * 0.5f * ZOOM_UI_SIZE;
            mInnerRadius = mOuterRadius * ZOOM_UI_DONUT;
        }

        public void draw(Canvas canvas) {
            if (!mVisible) {
                return;
            }
            // Draw background.
            mPaint.setAlpha(70);
            canvas.drawLine(mCenterX + mInnerRadius * (float) Math.cos(mFingerAngle),
                    mCenterY - mInnerRadius * (float) Math.sin(mFingerAngle),
                    mCenterX + mOuterRadius * (float) Math.cos(mFingerAngle),
                    mCenterY - mOuterRadius * (float) Math.sin(mFingerAngle), mPaint);
            canvas.drawLine(mCenterX - mInnerRadius * (float) Math.cos(mFingerAngle),
                    mCenterY + mInnerRadius * (float) Math.sin(mFingerAngle),
                    mCenterX - mOuterRadius * (float) Math.cos(mFingerAngle),
                    mCenterY + mOuterRadius * (float) Math.sin(mFingerAngle), mPaint);
            // Draw Zoom progress.
            mPaint.setAlpha(255);
            float zoomRadius = mInnerRadius + mCurrentFraction * (mOuterRadius - mInnerRadius);
            canvas.drawLine(mCenterX + mInnerRadius * (float) Math.cos(mFingerAngle),
                    mCenterY - mInnerRadius * (float) Math.sin(mFingerAngle),
                    mCenterX + zoomRadius * (float) Math.cos(mFingerAngle),
                    mCenterY - zoomRadius * (float) Math.sin(mFingerAngle), mPaint);
            canvas.drawLine(mCenterX - mInnerRadius * (float) Math.cos(mFingerAngle),
                    mCenterY + mInnerRadius * (float) Math.sin(mFingerAngle),
                    mCenterX - zoomRadius * (float) Math.cos(mFingerAngle),
                    mCenterY + zoomRadius * (float) Math.sin(mFingerAngle), mPaint);
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float sf = detector.getScaleFactor();
            mCurrentFraction = (0.33f + mCurrentFraction) * sf * sf - 0.33f;
            if (mCurrentFraction < 0.0f) mCurrentFraction = 0.0f;
            if (mCurrentFraction > 1.0f) mCurrentFraction = 1.0f;
            int newIndex = mMinIndex + (int) (mCurrentFraction * (mMaxIndex - mMinIndex));
            if (mZoomListener != null && newIndex != mCurrentIndex) {
                mZoomListener.onZoomValueChanged(newIndex);
                mCurrentIndex = newIndex;
            }
            mFingerAngle = mScaleDetector.getAngle();
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
            mFingerAngle = mScaleDetector.getAngle();
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
