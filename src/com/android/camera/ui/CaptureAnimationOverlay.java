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
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.android.camera.debug.Log;
import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/**
 * This class handles all the animations at capture time. Post capture animations
 * will be handled in a separate place.
 */
public class CaptureAnimationOverlay extends View
    implements PreviewStatusListener.PreviewAreaChangedListener {
    private final static Log.Tag TAG = new Log.Tag("CaptureAnimOverlay");

    private final static int FLASH_ALPHA_BEFORE_SHRINK = 180;
    private final static int FLASH_ALPHA_AFTER_SHRINK = 50;
    private final static int FLASH_COLOR = Color.WHITE;

    private static final float FLASH_MAX_ALPHA = 0.85f;
    private static final long FLASH_FULL_DURATION_MS = 65;
    private static final long FLASH_DECREASE_DURATION_MS = 150;

    private RectF mPreviewArea = new RectF();

    private AnimatorSet mFlashAnimation;
    private final Paint mPaint = new Paint();
    private final Interpolator mFlashAnimInterpolator;
    private final ValueAnimator.AnimatorUpdateListener mFlashAnimUpdateListener;
    private final Animator.AnimatorListener mFlashAnimListener;

    public CaptureAnimationOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setColor(FLASH_COLOR);
        mFlashAnimInterpolator = new LinearInterpolator();
        mFlashAnimUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setAlpha((Float) animation.getAnimatedValue());
                invalidate();
            }
        };
        mFlashAnimListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mFlashAnimation = null;
                setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };
    }

    /**
     * Start flash animation.
     */
    public void startFlashAnimation() {
        if (mFlashAnimation != null && mFlashAnimation.isRunning()) {
            mFlashAnimation.cancel();
        }

        ValueAnimator flashAnim1 = ValueAnimator.ofFloat(FLASH_MAX_ALPHA, FLASH_MAX_ALPHA);
        ValueAnimator flashAnim2 = ValueAnimator.ofFloat(FLASH_MAX_ALPHA, .0f);
        flashAnim1.setDuration(FLASH_FULL_DURATION_MS);
        flashAnim2.setDuration(FLASH_DECREASE_DURATION_MS);
        flashAnim1.addUpdateListener(mFlashAnimUpdateListener);
        flashAnim2.addUpdateListener(mFlashAnimUpdateListener);
        flashAnim1.setInterpolator(mFlashAnimInterpolator);
        flashAnim2.setInterpolator(mFlashAnimInterpolator);


        mFlashAnimation = new AnimatorSet();
        mFlashAnimation.play(flashAnim1).before(flashAnim2);
        mFlashAnimation.addListener(mFlashAnimListener);
        mFlashAnimation.start();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mFlashAnimation != null && mFlashAnimation.isRunning()) {
            canvas.drawRect(mPreviewArea, mPaint);
        }
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        mPreviewArea.set(previewArea);
    }
}
