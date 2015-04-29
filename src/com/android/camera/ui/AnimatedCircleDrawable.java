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

package com.android.camera.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import com.android.camera.util.Gusterpolator;

public class AnimatedCircleDrawable extends Drawable {
    private static final int CIRCLE_ANIM_DURATION_MS = 300;
    private static int DRAWABLE_MAX_LEVEL = 10000;

    private int mCanvasWidth;
    private int mCanvasHeight;

    private int mAlpha = 0xff;
    private int mColor;
    private Paint mPaint;
    private int mRadius;
    private int mSmallRadiusTarget;

    public AnimatedCircleDrawable(int smallRadiusTarget) {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mSmallRadiusTarget = smallRadiusTarget;
    }

    public void setColor(int color) {
        mColor = color;
        updatePaintColor();
    }

    private void updatePaintColor() {
        int paintColor = (mAlpha << 24) | (mColor & 0x00ffffff);
        mPaint.setColor(paintColor);
        invalidateSelf();
    }

    // abstract overrides
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        updatePaintColor();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        //TODO support this?
    }
    // end abstract overrides

    @Override
    public boolean onLevelChange(int level) {
        if (level != getLevel()) {
            invalidateSelf();
            return true;
        }
        return false;
    }

    public void animateToSmallRadius() {
        int smallLevel = map(mSmallRadiusTarget,
                0, diagonalLength(mCanvasWidth, mCanvasHeight)/2,
                0, DRAWABLE_MAX_LEVEL);
        final ValueAnimator animator =
            ValueAnimator.ofInt(getLevel(), smallLevel);
        animator.setDuration(CIRCLE_ANIM_DURATION_MS);
        animator.setInterpolator(Gusterpolator.INSTANCE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setLevel((Integer) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    public void animateToFullSize() {
        final ValueAnimator animator =
            ValueAnimator.ofInt(getLevel(), DRAWABLE_MAX_LEVEL);
        animator.setDuration(CIRCLE_ANIM_DURATION_MS);
        animator.setInterpolator(Gusterpolator.INSTANCE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setLevel((Integer) animation.getAnimatedValue());
            }
        });
        animator.start();
    }

    @Override
    public void draw(Canvas canvas) {
        mCanvasWidth = canvas.getWidth();
        mCanvasHeight = canvas.getHeight();

        mRadius = map(getLevel(), 0, DRAWABLE_MAX_LEVEL,
                0, diagonalLength(canvas.getWidth(), canvas.getHeight())/2);
        canvas.drawCircle(canvas.getWidth()/2.0f, canvas.getHeight()/2.0f,
                mRadius, mPaint);
    }

    /**
     * Maps a given value x from one input range [in_min, in_max] to
     * another output range [out_min, out-max].
     * @param x Value to be mapped.
     * @param in_min Input range minimum.
     * @param in_max Input range maximum.
     * @param out_min Output range minimum.
     * @param out_max Output range maximum.
     * @return The mapped value.
     */
    private static int map(int x, int in_min, int in_max, int out_min, int out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    private static int diagonalLength(int w, int h) {
        return (int) Math.sqrt((w*w) + (h*h));
    }
}