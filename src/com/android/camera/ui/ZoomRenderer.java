/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.view.ScaleGestureDetector;

import com.android.camera2.R;

// TODO: remove this; functionality has been moved to PreviewOverlay.
@Deprecated
public class ZoomRenderer extends OverlayRenderer
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "ZoomRenderer";

    final private int mMinIndex = 0;
    private int mMaxIndex;
    // Discrete Zoom level [mMinIndex,mMaxIndex].
    private int mCurrentIndex;
    // Continuous Zoom level [0,1].
    private float mCurrentFraction;
    private double mFingerRadians;
    private OnZoomChangedListener mListener;
    private final ScaleGestureDetector mDetector;
    private final Paint mPaint;
    private int mCenterX;
    private int mCenterY;
    private float mOuterRadius;
    private float mInnerRadius;
    private final int mZoomStroke;

    public interface OnZoomChangedListener {
        void onZoomStart();
        void onZoomEnd();
        void onZoomValueChanged(int index);
    }

    public ZoomRenderer(Context ctx) {
        Resources res = ctx.getResources();
        mZoomStroke = res.getDimensionPixelSize(R.dimen.zoom_stroke);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mZoomStroke);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetector = new ScaleGestureDetector(ctx, this);
        setVisible(false);
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

    // Set Zoom Value to display from Module.
    public void setZoomValue(int centiValue) {
        // Do nothing.
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
        // UI will extend from 20% to 80% of maximum inset circle.
        float insetCircleRadius = Math.min(getWidth(), getHeight());
        mInnerRadius = insetCircleRadius * 0.12f;
        mOuterRadius = insetCircleRadius * 0.38f;
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    @Override
    public void onDraw(Canvas canvas) {
        // Draw background.
        mPaint.setAlpha(70);
        canvas.drawLine(mCenterX + mInnerRadius * (float) Math.cos(mFingerRadians),
                mCenterY - mInnerRadius * (float) Math.sin(mFingerRadians),
                mCenterX + mOuterRadius * (float) Math.cos(mFingerRadians),
                mCenterY - mOuterRadius * (float) Math.sin(mFingerRadians), mPaint);
        canvas.drawLine(mCenterX - mInnerRadius * (float) Math.cos(mFingerRadians),
                mCenterY + mInnerRadius * (float) Math.sin(mFingerRadians),
                mCenterX - mOuterRadius * (float) Math.cos(mFingerRadians),
                mCenterY + mOuterRadius * (float) Math.sin(mFingerRadians), mPaint);
        // Draw Zoom progress.
        mPaint.setAlpha(255);
        float zoomRadius = mInnerRadius + mCurrentFraction * (mOuterRadius - mInnerRadius);
        canvas.drawLine(mCenterX + mInnerRadius * (float) Math.cos(mFingerRadians),
                mCenterY - mInnerRadius * (float) Math.sin(mFingerRadians),
                mCenterX + zoomRadius * (float) Math.cos(mFingerRadians),
                mCenterY - zoomRadius * (float) Math.sin(mFingerRadians), mPaint);
        canvas.drawLine(mCenterX - mInnerRadius * (float) Math.cos(mFingerRadians),
                mCenterY + mInnerRadius * (float) Math.sin(mFingerRadians),
                mCenterX - zoomRadius * (float) Math.cos(mFingerRadians),
                mCenterY + zoomRadius * (float) Math.sin(mFingerRadians), mPaint);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        final float sf = detector.getScaleFactor();
        mCurrentFraction = (0.33f + mCurrentFraction) * sf * sf - 0.33f;
        if (mCurrentFraction < 0.0f) mCurrentFraction = 0.0f;
        if (mCurrentFraction > 1.0f) mCurrentFraction = 1.0f;
        int newIndex = mMinIndex + (int) (mCurrentFraction * (mMaxIndex - mMinIndex));
        if (mListener != null && newIndex != mCurrentIndex) {
            mListener.onZoomValueChanged(newIndex);
            mCurrentIndex = newIndex;
        }
        // mFingerRadians is currently constrained to [0,Pi/2].
        // TODO: Get actual touch coordinates to enable full [0,Pi] range.
        mFingerRadians = Math.atan2(detector.getCurrentSpanY(),detector.getCurrentSpanX());
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        setVisible(true);
        if (mListener != null) {
            mListener.onZoomStart();
        }
        update();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        setVisible(false);
        if (mListener != null) {
            mListener.onZoomEnd();
        }
    }

}
