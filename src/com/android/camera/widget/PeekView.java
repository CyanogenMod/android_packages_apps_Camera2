/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.widget;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

/**
 * An ImageView which has the built-in peek animation support.
 */
public class PeekView extends ImageView {

    private static final float ROTATE_ANGLE = -7f;
    private static final long PEEK_IN_DURATION_MS = 200;
    private static final long PEEK_STAY_DURATION_MS = 100;
    private static final long PEEK_OUT_DURATION_MS = 200;
    private static final float FILMSTRIP_SCALE = 0.7f;

    private AnimatorSet mPeekAnimator;
    private float mPeekRotateAngle;
    private Point mRotationPivot;
    private float mRotateScale;
    private boolean mAnimationCanceled;
    private Drawable mImageDrawable;
    private Rect mDrawableBound;

    public PeekView(Context context) {
        super(context);
        init();
    }

    public PeekView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PeekView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mRotationPivot = new Point();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (mImageDrawable == null) {
            return;
        }
        c.save();
        c.rotate(mPeekRotateAngle, mRotationPivot.x, mRotationPivot.y);
        mImageDrawable.setBounds(mDrawableBound);
        mImageDrawable.draw(c);
        c.restore();
    }

    /**
     * Starts the peek animation.
     *
     * @param bitmap The bitmap for the animation.
     * @param strong {@code true} if the animation is the strong version which
     *               shows more portion of the bitmap.
     * @param accessibilityString An accessibility String to be announced
                     during the peek animation.
     */
    public void startPeekAnimation(final Bitmap bitmap, boolean strong,
            String accessibilityString) {
        ValueAnimator.AnimatorUpdateListener updateListener =
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        mPeekRotateAngle = mRotateScale * (Float) valueAnimator.getAnimatedValue();
                        invalidate();
                    }
                };
        ValueAnimator peekAnimateIn = ValueAnimator.ofFloat(0f, ROTATE_ANGLE);
        ValueAnimator peekAnimateStay = ValueAnimator.ofFloat(ROTATE_ANGLE, ROTATE_ANGLE);
        ValueAnimator peekAnimateOut = ValueAnimator.ofFloat(ROTATE_ANGLE, 0f);
        peekAnimateIn.addUpdateListener(updateListener);
        peekAnimateOut.addUpdateListener(updateListener);
        peekAnimateIn.setDuration(PEEK_IN_DURATION_MS);
        peekAnimateStay.setDuration(PEEK_STAY_DURATION_MS);
        peekAnimateOut.setDuration(PEEK_OUT_DURATION_MS);
        peekAnimateIn.setInterpolator(new DecelerateInterpolator());
        peekAnimateOut.setInterpolator(new AccelerateInterpolator());
        mPeekAnimator = new AnimatorSet();
        mPeekAnimator.playSequentially(peekAnimateIn, peekAnimateStay, peekAnimateOut);
        mPeekAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                setVisibility(VISIBLE);
                mAnimationCanceled = false;
                invalidate();
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (!mAnimationCanceled) {
                    clear();
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                mAnimationCanceled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        mRotateScale = (strong ? 1.0f : 0.5f);
        mImageDrawable = new BitmapDrawable(getResources(), bitmap);
        Point drawDim = CameraUtil.resizeToFill(mImageDrawable.getIntrinsicWidth(),
                mImageDrawable.getIntrinsicHeight(), 0, (int) (getWidth() * FILMSTRIP_SCALE),
                (int) (getHeight() * FILMSTRIP_SCALE));
        int x = getMeasuredWidth();
        int y = (getMeasuredHeight() - drawDim.y) / 2;
        mDrawableBound = new Rect(x, y, x + drawDim.x, y + drawDim.y);
        mRotationPivot.set(x, (int) (y + drawDim.y * 1.1));
        mPeekAnimator.start();

        announceForAccessibility(accessibilityString);
    }

    /**
     * @return whether the animation is running.
     */
    public boolean isPeekAnimationRunning() {
        return mPeekAnimator.isRunning();
    }

    /**
     * Stops the animation. See {@link android.animation.Animator#end()}.
     */
    public void stopPeekAnimation() {
        if (isPeekAnimationRunning()) {
            mPeekAnimator.end();
        } else {
            clear();
        }
    }

    /**
     * Cancels the animation. See {@link android.animation.Animator#cancel()}.
     */
    public void cancelPeekAnimation() {
        if (isPeekAnimationRunning()) {
            mPeekAnimator.cancel();
        } else {
            clear();
        }
    }

    private void clear() {
        setVisibility(INVISIBLE);
        setImageDrawable(null);
    }
}
