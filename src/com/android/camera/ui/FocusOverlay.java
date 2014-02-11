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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.FocusOverlayManager;
import com.android.camera2.R;

/**
 * Displays a focus indicator.
 */
public class FocusOverlay extends View implements FocusOverlayManager.FocusUI {

    private final static int FOCUS_DURATION_MS = 500;
    private final static int FOCUS_INDICATOR_ROTATION_DEGREES = 50;

    private final Drawable mFocusIndicator;
    private final Drawable mFocusOuterRing;
    private final Rect mBounds = new Rect();
    private final ValueAnimator mFocusAnimation = new ValueAnimator();

    private int mPositionX;
    private int mPositionY;
    private int mAngle;
    private final int mFocusIndicatorSize;
    private boolean mShowIndicator;
    private final int mFocusOuterRingSize;

    public FocusOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFocusIndicator = getResources().getDrawable(R.drawable.focus);
        mFocusIndicatorSize = getResources().getDimensionPixelSize(R.dimen.focus_inner_ring_size);
        mFocusOuterRing = getResources().getDrawable(R.drawable.focus_outer_ring);
        mFocusOuterRingSize = getResources().getDimensionPixelSize(R.dimen.focus_outer_ring_size);
    }

    @Override
    public boolean hasFaces() {
        // TODO: Add face detection support.
        return false;
    }

    @Override
    public void clearFocus() {
        mShowIndicator = false;
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPositionX = x;
        mPositionY = y;
        mBounds.set(x - mFocusIndicatorSize / 2, y - mFocusIndicatorSize / 2,
                x + mFocusIndicatorSize / 2, y + mFocusIndicatorSize / 2);
        mFocusIndicator.setBounds(mBounds);
        mFocusOuterRing.setBounds(x - mFocusOuterRingSize / 2, y - mFocusOuterRingSize / 2,
                x + mFocusOuterRingSize / 2, y + mFocusOuterRingSize / 2);
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
        invalidate();
    }

    @Override
    public void onFocusFailed() {
        mFocusAnimation.cancel();
        mShowIndicator = false;
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
    }
}
