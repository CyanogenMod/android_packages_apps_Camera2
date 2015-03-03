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
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import android.widget.Button;
import com.android.camera.debug.Log;
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
public class PreviewOverlay extends View
    implements PreviewStatusListener.PreviewAreaChangedListener {

    public static final float ZOOM_MIN_RATIO = 1.0f;
    private static final int NUM_ZOOM_LEVELS = 7;
    private static final float MIN_ZOOM = 1f;

    private static final Log.Tag TAG = new Log.Tag("PreviewOverlay");

    /** Minimum time between calls to zoom listener. */
    private static final long ZOOM_MINIMUM_WAIT_MILLIS = 33;

    /** Next time zoom change should be sent to listener. */
    private long mDelayZoomCallUntilMillis = 0;
    private final ZoomGestureDetector mScaleDetector;
    private final ZoomProcessor mZoomProcessor = new ZoomProcessor();
    private GestureDetector mGestureDetector = null;
    private View.OnTouchListener mTouchListener = null;
    private OnZoomChangedListener mZoomListener = null;
    private OnPreviewTouchedListener mOnPreviewTouchedListener;

    /** Maximum zoom; intialize to 1.0 (disabled) */
    private float mMaxZoom = MIN_ZOOM;
    /**
     * Current zoom value in accessibility mode, ranging from MIN_ZOOM to
     * mMaxZoom.
     */
    private float mCurrA11yZoom = MIN_ZOOM;
    /**
     * Current zoom level ranging between 1 and NUM_ZOOM_LEVELS. Each level is
     * associated with a discrete zoom value.
     */
    private int mCurrA11yZoomLevel = 1;

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
         * @param ratio zoom ratio, [1.0f,maximum]
         */
        void onZoomValueChanged(float ratio);  // only for immediate zoom
    }

    public interface OnPreviewTouchedListener {
        /**
         * This gets called on any preview touch event.
         */
        public void onPreviewTouched(MotionEvent ev);
    }

    public PreviewOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScaleDetector = new ZoomGestureDetector();
    }

    /**
     * This sets up the zoom listener and zoom related parameters when
     * the range of zoom ratios is continuous.
     *
     * @param zoomMaxRatio max zoom ratio, [1.0f,+Inf)
     * @param zoom current zoom ratio, [1.0f,zoomMaxRatio]
     * @param zoomChangeListener a listener that receives callbacks when zoom changes
     */
    public void setupZoom(float zoomMaxRatio, float zoom,
                          OnZoomChangedListener zoomChangeListener) {
        mZoomListener = zoomChangeListener;
        mZoomProcessor.setupZoom(zoomMaxRatio, zoom);
    }

    /**
     * uZooms camera in when in accessibility mode.
     *
     * @param view is the current view
     * @param maxZoom is the maximum zoom value on the given device
     * @return float representing the current zoom value
     */
    public float zoomIn(View view, float maxZoom) {
        mCurrA11yZoomLevel++;
        mMaxZoom = maxZoom;
        mCurrA11yZoom = getZoomAtLevel(mCurrA11yZoomLevel);
        mZoomListener.onZoomValueChanged(mCurrA11yZoom);
        view.announceForAccessibility(String.format(
                view.getResources().
                        getString(R.string.accessibility_zoom_announcement), mCurrA11yZoom));
        return mCurrA11yZoom;
    }

    /**
     * Zooms camera out when in accessibility mode.
     *
     * @param view is the current view
     * @param maxZoom is the maximum zoom value on the given device
     * @return float representing the current zoom value
     */
    public float zoomOut(View view, float maxZoom) {
        mCurrA11yZoomLevel--;
        mMaxZoom = maxZoom;
        mCurrA11yZoom = getZoomAtLevel(mCurrA11yZoomLevel);
        mZoomListener.onZoomValueChanged(mCurrA11yZoom);
        view.announceForAccessibility(String.format(
                view.getResources().
                        getString(R.string.accessibility_zoom_announcement), mCurrA11yZoom));
        return mCurrA11yZoom;
    }

    /**
     * Method used in accessibility mode. Ensures that there are evenly spaced
     * zoom values ranging from MIN_ZOOM to NUM_ZOOM_LEVELS
     *
     * @param level is the zoom level being computed in the range
     * @return the zoom value at the given level
     */
    private float getZoomAtLevel(int level) {
        return (MIN_ZOOM + ((level - 1) * ((mMaxZoom - MIN_ZOOM) / (NUM_ZOOM_LEVELS - 1))));
    }

    @Override
    public boolean onTouchEvent(MotionEvent m) {
        // Pass the touch events to scale detector and gesture detector
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(m);
        }
        if (mTouchListener != null) {
            mTouchListener.onTouch(this, m);
        }
        mScaleDetector.onTouchEvent(m);
        if (mOnPreviewTouchedListener != null) {
            mOnPreviewTouchedListener.onPreviewTouched(m);
        }
        return true;
    }

    /**
     * Set an {@link OnPreviewTouchedListener} to be executed on any preview
     * touch event.
     */
    public void setOnPreviewTouchedListener(OnPreviewTouchedListener listener) {
        mOnPreviewTouchedListener = listener;
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        mZoomProcessor.layout((int) previewArea.left, (int) previewArea.top,
                (int) previewArea.right, (int) previewArea.bottom);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mZoomProcessor.draw(canvas);
    }

    /**
     * Each module can pass in their own gesture listener through App UI. When a gesture
     * is detected, the {@link GestureDetector.OnGestureListener} will be notified of
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
     * Set a touch listener on the preview overlay.  When a module doesn't support a
     * {@link GestureDetector.OnGestureListener}, this can be used instead.
     */
    public void setTouchListener(View.OnTouchListener touchListener) {
        mTouchListener = touchListener;
    }

    /**
     * During module switch, connections to the previous module should be cleared.
     */
    public void reset() {
        mZoomListener = null;
        mGestureDetector = null;
        mTouchListener = null;
        mCurrA11yZoomLevel = 1;
        mCurrA11yZoom = MIN_ZOOM;
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
        private final Log.Tag TAG = new Log.Tag("ZoomProcessor");

        // Diameter of Zoom UI as fraction of maximum possible without clipping.
        private static final float ZOOM_UI_SIZE = 0.8f;
        // Diameter of Zoom UI donut hole as fraction of Zoom UI diameter.
        private static final float ZOOM_UI_DONUT = 0.25f;

        private final float mMinRatio = 1.0f;
        private float mMaxRatio;
        // Continuous Zoom level [0,1].
        private float mCurrentRatio;
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

        // Set maximum zoom ratio from Module.
        public void setZoomMax(float zoomMaxRatio) {
            mMaxRatio = zoomMaxRatio;
        }

        // Set current zoom ratio from Module.
        public void setZoom(float ratio) {
            mCurrentRatio = ratio;
        }

        public void layout(int l, int t, int r, int b) {
            mCenterX = (r + l) / 2;
            mCenterY = (b + t) / 2;
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
            float fillRatio = (mCurrentRatio - mMinRatio) / (mMaxRatio - mMinRatio);
            float zoomRadius = mInnerRadius + fillRatio * (mOuterRadius - mInnerRadius);
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
            mCurrentRatio = (0.33f + mCurrentRatio) * sf * sf - 0.33f;
            if (mCurrentRatio < mMinRatio) {
                mCurrentRatio = mMinRatio;
            }
            if (mCurrentRatio > mMaxRatio) {
                mCurrentRatio = mMaxRatio;
            }

            // Only call the listener with a certain frequency. This is
            // necessary because these listeners will make repeated
            // applySettings() calls into the portability layer, and doing this
            // too often can back up its handler and result in visible lag in
            // updating the zoom level and other controls.
            long now = SystemClock.uptimeMillis();
            if (now > mDelayZoomCallUntilMillis) {
                if (mZoomListener != null) {
                    mZoomListener.onZoomValueChanged(mCurrentRatio);
                }
                mDelayZoomCallUntilMillis = now + ZOOM_MINIMUM_WAIT_MILLIS;
            }
            mFingerAngle = mScaleDetector.getAngle();
            invalidate();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mZoomProcessor.showZoomUI();
            if (mZoomListener == null) {
                return false;
            }
            if (mZoomListener != null) {
                mZoomListener.onZoomStart();
            }
            mFingerAngle = mScaleDetector.getAngle();
            invalidate();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mZoomProcessor.hideZoomUI();
            if (mZoomListener != null) {
                mZoomListener.onZoomEnd();
            }
            invalidate();
        }

        public boolean isVisible() {
            return mVisible;
        }

        public void showZoomUI() {
            if (mZoomListener == null) {
                return;
            }
            mVisible = true;
            mFingerAngle = mScaleDetector.getAngle();
            invalidate();
        }

        public void hideZoomUI() {
            if (mZoomListener == null) {
                return;
            }
            mVisible = false;
            invalidate();
        }

        private void setupZoom(float zoomMax, float zoom) {
            setZoomMax(zoomMax);
            setZoom(zoom);
        }
    };

}
