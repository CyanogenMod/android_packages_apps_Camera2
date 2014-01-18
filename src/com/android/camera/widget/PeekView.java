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
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

/**
 * An ImageView which has the built-in peek animation support.
 */
public class PeekView extends ImageView {

    private static final float ROTATE_ANGLE = -15f;
    private static final long PEEK_IN_DURATION_MS = 300;
    private static final long PEEK_STAY_DURATION_MS = 200;
    private static final long PEEK_OUT_DURATION_MS = 300;

    private AnimatorSet mPeekAnimator;
    private float mPeekRotateAngle;
    private Point mRotationPivot;
    private float mRotateScale;
    private boolean mAnimationCanceled;

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
        ValueAnimator.AnimatorUpdateListener updateListener =
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        mPeekRotateAngle = mRotateScale * (Float) valueAnimator.getAnimatedValue();
                        drawPeekAnimation();
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
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setTranslationX(getMeasuredWidth());
    }

    /**
     * Starts the peek animation.
     *
     * @param bitmap The bitmap for the animation.
     * @param strong {@code true} if the animation is the strong version which
     *               shows more portion of the bitmap.
     */
    public void startPeekAnimation(final Bitmap bitmap, boolean strong) {
        mRotateScale = (strong ? 1.0f : 0.5f);
        setImageDrawable(new BitmapDrawable(getResources(), bitmap));
        mRotationPivot.set(0, getHeight());
        mPeekAnimator.start();
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

    private void drawPeekAnimation() {
        if (mPeekAnimator.isRunning()) {
            setTranslationX(getMeasuredWidth());
            setRotation(mPeekRotateAngle);
            setPivotX(mRotationPivot.x);
            setPivotY(mRotationPivot.y);
        }
    }

    private void clear() {
        setVisibility(INVISIBLE);
        setImageDrawable(null);
        setRotation(0);
    }
}
