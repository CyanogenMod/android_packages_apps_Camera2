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

    private Paint mDebugPaint;
    private Paint mDebugAEPaint;
    private int mDebugStartColor;
    private int mDebugPassiveColor;
    private int mDebugSuccessColor;
    private int mDebugFailColor;
    private Rect mFocusDebugAFRect;
    private Rect mFocusDebugAERect;
    private boolean mIsPassiveScan;

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
            mDebugPassiveColor = res.getColor(R.color.focus_debug_light);
            mDebugSuccessColor = res.getColor(R.color.focus_debug_success);
            mDebugFailColor = res.getColor(R.color.focus_debug_fail);
            mDebugPaint = new Paint();
            mDebugPaint.setColor(res.getColor(R.color.focus_debug));
            mDebugPaint.setAntiAlias(true);
            mDebugPaint.setStyle(Paint.Style.STROKE);
            mDebugPaint.setStrokeWidth(res.getDimension(R.dimen.focus_debug_stroke));
            mDebugAEPaint = new Paint(mDebugPaint);
            mDebugAEPaint.setColor(res.getColor(R.color.focus_debug));
            mFocusDebugAFRect = new Rect();
            mFocusDebugAERect = new Rect();
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
            mFocusDebugAFRect.set(x - aFsize / 2, y - aFsize / 2, x + aFsize / 2, y + aFsize / 2);
            // If AE region is different size than AF region and active scan.
            if (aFsize != aEsize && !isPassiveScan) {
                mFocusDebugAERect.set(x - aEsize / 2, y - aEsize / 2, x + aEsize / 2,
                        y + aEsize / 2);
            } else {
                mFocusDebugAERect.set(0, 0, 0, 0);
            }
            mDebugPaint.setColor(isPassiveScan ? mDebugPassiveColor : mDebugStartColor);
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
    }

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
    }

    @Override
    public void onFocusSucceeded() {
        mFocusAnimation.cancel();
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI && !mIsPassiveScan) {
            mDebugPaint.setColor(mDebugSuccessColor);
        }
        invalidate();
    }

    @Override
    public void onFocusFailed() {
        mFocusAnimation.cancel();
        mShowIndicator = false;
        if (CAPTURE_DEBUG_UI && !mIsPassiveScan) {
            mDebugPaint.setColor(mDebugFailColor);
        }
        invalidate();
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
        if (CAPTURE_DEBUG_UI && mFocusDebugAFRect != null) {
            canvas.drawRect(mFocusDebugAFRect, mDebugPaint);
            float delta = 0.1f * mFocusDebugAERect.width();
            float left = mFocusDebugAERect.left;
            float top = mFocusDebugAERect.top;
            float right = mFocusDebugAERect.right;
            float bot = mFocusDebugAERect.bottom;

            canvas.drawLines(new float[]{left, top + delta, left, top, left, top, left + delta, top}, mDebugAEPaint);
            canvas.drawLines(new float[]{right, top + delta, right, top, right, top, right - delta, top}, mDebugAEPaint);
            canvas.drawLines(new float[]{left, bot - delta, left, bot, left, bot, left + delta, bot}, mDebugAEPaint);
            canvas.drawLines(new float[]{right, bot - delta, right, bot, right, bot, right - delta, bot}, mDebugAEPaint);
        }
    }
}
