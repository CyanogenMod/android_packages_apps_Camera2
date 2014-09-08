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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.FocusOverlayManager;
import com.android.camera.debug.DebugPropertyHelper;
import com.android.camera.debug.Log;
import com.android.camera2.R;

/**
 * Displays a focus indicator.
 */
public class FocusOverlay extends View implements FocusOverlayManager.FocusUI {
    private static final Log.Tag TAG = new Log.Tag("FocusOverlay");

    /** System Properties switch to enable debugging focus UI. */
    private static final boolean CAPTURE_DEBUG_UI = DebugPropertyHelper.showCaptureDebugUI();

    private final static int FOCUS_DURATION_MS = 500;
    private final static int FOCUS_INDICATOR_ROTATION_DEGREES = 50;

    private final Drawable mFocusIndicator;
    private Drawable mFocusOuterRing;
    private final Rect mBounds = new Rect();
    private final ValueAnimator mFocusAnimation = new ValueAnimator();

    private Paint mDebugSolidPaint;
    private Paint mDebugCornersPaint;
    private Paint mDebugTextPaint;
    private int mDebugStartColor;
    private int mDebugSuccessColor;
    private int mDebugFailColor;
    private Rect mFocusDebugSolidRect;
    private Rect mFocusDebugCornersRect;
    private boolean mIsPassiveScan;
    private String mDebugMessage;

    private int mPositionX;
    private int mPositionY;
    private int mAngle;
    private final int mFocusIndicatorSize;
    private boolean mShowIndicator;
    private final int mFocusOuterRingSize;

    public FocusOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFocusIndicator = getResources().getDrawable(R.drawable.focus_ring_touch_inner);
        mFocusIndicatorSize = getResources().getDimensionPixelSize(R.dimen.focus_inner_ring_size);
        mFocusOuterRing = getResources().getDrawable(R.drawable.focus_ring_touch_outer);
        mFocusOuterRingSize = getResources().getDimensionPixelSize(R.dimen.focus_outer_ring_size);

        if (CAPTURE_DEBUG_UI) {
            Resources res = getResources();
            mDebugStartColor = res.getColor(R.color.focus_debug);
            mDebugSuccessColor = res.getColor(R.color.focus_debug_success);
            mDebugFailColor = res.getColor(R.color.focus_debug_fail);
            mDebugTextPaint= new Paint();
            mDebugTextPaint.setColor(res.getColor(R.color.focus_debug_text));
            mDebugTextPaint.setStyle(Paint.Style.FILL);
            mDebugSolidPaint = new Paint();
            mDebugSolidPaint.setColor(res.getColor(R.color.focus_debug));
            mDebugSolidPaint.setAntiAlias(true);
            mDebugSolidPaint.setStyle(Paint.Style.STROKE);
            mDebugSolidPaint.setStrokeWidth(res.getDimension(R.dimen.focus_debug_stroke));
            mDebugCornersPaint = new Paint(mDebugSolidPaint);
            mDebugCornersPaint.setColor(res.getColor(R.color.focus_debug));
            mFocusDebugSolidRect = new Rect();
            mFocusDebugCornersRect = new Rect();
        }
    }

    @Override
    public boolean hasFaces() {
        // TODO: Add face detection support.
        return false;
    }

    @Override
    public void clearFocus() {
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI) {
            setVisibility(INVISIBLE);
        }
    }

    @Override
    public void setFocusPosition(int x, int y, boolean isPassiveScan) {
        setFocusPosition(x, y, isPassiveScan, 0, 0);
    }

    @Override
    public void setFocusPosition(int x, int y, boolean isPassiveScan, int aFsize, int aEsize) {
        mIsPassiveScan = isPassiveScan;
        mPositionX = x;
        mPositionY = y;
        mBounds.set(x - mFocusIndicatorSize / 2, y - mFocusIndicatorSize / 2,
                x + mFocusIndicatorSize / 2, y + mFocusIndicatorSize / 2);
        mFocusIndicator.setBounds(mBounds);
        mFocusOuterRing.setBounds(x - mFocusOuterRingSize / 2, y - mFocusOuterRingSize / 2,
                x + mFocusOuterRingSize / 2, y + mFocusOuterRingSize / 2);

        if (CAPTURE_DEBUG_UI) {
            mFocusOuterRing.setBounds(0, 0, 0, 0);
            if (isPassiveScan) {
                // Use AE rect only.
                mFocusDebugSolidRect.setEmpty();
                int avg = (aFsize + aEsize) / 2;
                mFocusDebugCornersRect.set(x - avg / 2, y - avg / 2, x + avg / 2, y + avg / 2);
            } else {
                mFocusDebugSolidRect.set(x - aFsize / 2, y - aFsize / 2, x + aFsize / 2,
                        y + aFsize / 2);
                // If AE region is different size than AF region and active scan.
                if (aFsize != aEsize) {
                    mFocusDebugCornersRect.set(x - aEsize / 2, y - aEsize / 2, x + aEsize / 2,
                            y + aEsize / 2);
                } else {
                    mFocusDebugCornersRect.setEmpty();
                }
            }
            mDebugSolidPaint.setColor(mDebugStartColor);
            mDebugCornersPaint.setColor(mDebugStartColor);
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        invalidate();
    }

    /**
     * This is called in:
     * <ul>
     * <li>API1 non-CAF after autoFocus().</li>
     * <li>API1 CAF mode for onAutoFocusMoving(true).</li>
     * <li>API2 for transition to ACTIVE_SCANNING or PASSIVE_SCANNING.</li>
     * <ul>
     * TODO after PhotoModule/GcamModule deprecation: Do not use this for CAF.
     */
    @Override
    public void onFocusStarted() {
        mShowIndicator = true;
        mFocusAnimation.setIntValues(0, FOCUS_INDICATOR_ROTATION_DEGREES);
        mFocusAnimation.setDuration(FOCUS_DURATION_MS);
        mFocusAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAngle = (Integer) animation.getAnimatedValue();
                invalidate();
            }
        });
        mFocusAnimation.start();
        if (CAPTURE_DEBUG_UI) {
            mDebugMessage = null;
        }
    }

    /**
     * This is called in:
     * <ul>
     * <li>API1 non-CAF for onAutoFocus(true).</li>
     * <li>API2 non-CAF for transition to FOCUSED_LOCKED.</li>
     * <li>API1 CAF mode for onAutoFocusMoving(false).</li>
     * <ul>
     * TODO after PhotoModule/GcamModule deprecation: Do not use this for CAF.
     */
    @Override
    public void onFocusSucceeded() {
        mFocusAnimation.cancel();
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI && !mIsPassiveScan) {
            mDebugSolidPaint.setColor(mDebugSuccessColor);
        }
        invalidate();
    }

    /**
     * This is called in:
     * <ul>
     * <li>API1 non-CAF for onAutoFocus(false).</li>
     * <li>API2 non-CAF for transition to NOT_FOCUSED_LOCKED.</li>
     * <ul>
     */
    @Override
    public void onFocusFailed() {
        mFocusAnimation.cancel();
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI && !mIsPassiveScan) {
            mDebugSolidPaint.setColor(mDebugFailColor);
        }
        invalidate();
    }

    /**
     * This is called in:
     * API2 for CAF state changes to PASSIVE_FOCUSED or PASSIVE_UNFOCUSED.
     */
    @Override
    public void setPassiveFocusSuccess(boolean success) {
        mFocusAnimation.cancel();
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI) {
            mDebugCornersPaint.setColor(success ? mDebugSuccessColor : mDebugFailColor);
        }
        invalidate();
    }

    @Override
    public void showDebugMessage(String message) {
        if (CAPTURE_DEBUG_UI) {
            mDebugMessage = message;
        }
    }

    @Override
    public void pauseFaceDetection() {
        // TODO: Add face detection support.
    }

    @Override
    public void resumeFaceDetection() {
        // TODO: Add face detection support.
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mShowIndicator) {
            mFocusOuterRing.draw(canvas);
            canvas.save();
            canvas.rotate(mAngle, mPositionX, mPositionY);
            mFocusIndicator.draw(canvas);
            canvas.restore();
        }
        if (CAPTURE_DEBUG_UI) {
            canvas.drawRect(mFocusDebugSolidRect, mDebugSolidPaint);
            float delta = 0.1f * mFocusDebugCornersRect.width();
            float left = mFocusDebugCornersRect.left;
            float top = mFocusDebugCornersRect.top;
            float right = mFocusDebugCornersRect.right;
            float bot = mFocusDebugCornersRect.bottom;

            canvas.drawLines(new float[]{left, top + delta, left, top, left, top, left + delta, top}, mDebugCornersPaint);
            canvas.drawLines(new float[]{right, top + delta, right, top, right, top, right - delta, top}, mDebugCornersPaint);
            canvas.drawLines(new float[]{left, bot - delta, left, bot, left, bot, left + delta, bot}, mDebugCornersPaint);
            canvas.drawLines(new float[]{right, bot - delta, right, bot, right, bot, right - delta, bot}, mDebugCornersPaint);

            if (mDebugMessage != null) {
                mDebugTextPaint.setTextSize(40);
                canvas.drawText(mDebugMessage, left - 4, bot + 44, mDebugTextPaint);
            }
        }
    }
}
