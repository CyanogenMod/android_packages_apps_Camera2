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

package com.android.camera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.gallery3d.R;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.RawTexture;

/**
 * Class to handle the capture animation.
 */
public class CaptureAnimManager {
    @SuppressWarnings("unused")
    private static final String TAG = "CAM_Capture";
    // times mark endpoint of animation phase
    private static final int TIME_FLASH = 200;
    private static final int TIME_HOLD = 400;
    private static final int TIME_SLIDE = 800;
    private static final int TIME_HOLD2 = 3300;
    private static final int TIME_SLIDE2 = 4100;

    private static final int ANIM_BOTH = 0;
    private static final int ANIM_FLASH = 1;
    private static final int ANIM_SLIDE = 2;
    private static final int ANIM_HOLD2 = 3;
    private static final int ANIM_SLIDE2 = 4;

    private final Interpolator mSlideInterpolator = new DecelerateInterpolator();

    private volatile int mAnimOrientation;  // Could be 0, 90, 180 or 270 degrees.
    private long mAnimStartTime;  // milliseconds.
    private float mX;  // The center of the whole view including preview and review.
    private float mY;
    private int mDrawWidth;
    private int mDrawHeight;
    private int mAnimType;

    private int mHoldX;
    private int mHoldY;
    private int mHoldW;
    private int mHoldH;

    private int mOffset;

    private int mMarginRight;
    private int mMarginTop;
    private int mSize;
    private Resources mResources;
    private NinePatchTexture mBorder;
    private int mShadowSize;

    public static int getAnimationDuration() {
        return TIME_SLIDE2;
    }

    /* preview: camera preview view.
     * review: view of picture just taken.
     */
    public CaptureAnimManager(Context ctx) {
        mBorder = new NinePatchTexture(ctx, R.drawable.capture_thumbnail_shadow);
        mResources = ctx.getResources();
    }

    public void setOrientation(int displayRotation) {
        mAnimOrientation = (360 - displayRotation) % 360;
    }

    public void animateSlide() {
        if (mAnimType != ANIM_FLASH) {
            return;
        }
        mAnimType = ANIM_SLIDE;
        mAnimStartTime = SystemClock.uptimeMillis();
    }

    public void animateFlash() {
        mAnimType = ANIM_FLASH;
    }

    public void animateFlashAndSlide() {
        mAnimType = ANIM_BOTH;
    }

    public void startAnimation() {
        mAnimStartTime = SystemClock.uptimeMillis();
    }

    private void setAnimationGeometry(int x, int y, int w, int h) {
        mMarginRight = mResources.getDimensionPixelSize(R.dimen.capture_margin_right);
        mMarginTop = mResources.getDimensionPixelSize(R.dimen.capture_margin_top);
        mSize = mResources.getDimensionPixelSize(R.dimen.capture_size);
        mShadowSize = mResources.getDimensionPixelSize(R.dimen.capture_border);
        mOffset = mMarginRight + mSize;
        // Set the views to the initial positions.
        mDrawWidth = w;
        mDrawHeight = h;
        mX = x;
        mY = y;
        mHoldW = mSize;
        mHoldH = mSize;
        switch (mAnimOrientation) {
            case 0:  // Preview is on the left.
                mHoldX = x + w - mMarginRight - mSize;
                mHoldY = y + mMarginTop;
                break;
            case 90:  // Preview is below.
                mHoldX = x + mMarginTop;
                mHoldY = y + mMarginRight;
                break;
            case 180:  // Preview on the right.
                mHoldX = x + mMarginRight;
                mHoldY = y + h - mMarginTop - mSize;
                break;
            case 270:  // Preview is above.
                mHoldX = x + w - mMarginTop - mSize;
                mHoldY = y + h - mMarginRight - mSize;
                break;
        }
    }

    // Returns true if the animation has been drawn.
    public boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review, int lx, int ly, int lw, int lh) {
        setAnimationGeometry(lx, ly, lw, lh);
        long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
        // Check if the animation is over
        if (mAnimType == ANIM_SLIDE && timeDiff > TIME_SLIDE2 - TIME_HOLD) return false;
        if (mAnimType == ANIM_BOTH && timeDiff > TIME_SLIDE2) return false;

        // determine phase and time in phase
        int animStep = mAnimType;
        if (mAnimType == ANIM_SLIDE) {
            timeDiff += TIME_HOLD;
        }
        if (mAnimType == ANIM_SLIDE || mAnimType == ANIM_BOTH) {
            if (timeDiff < TIME_HOLD) {
                animStep = ANIM_FLASH;
            } else if (timeDiff < TIME_SLIDE) {
                animStep = ANIM_SLIDE;
                timeDiff -= TIME_HOLD;
            } else if (timeDiff < TIME_HOLD2) {
                animStep = ANIM_HOLD2;
                timeDiff -= TIME_SLIDE;
            } else {
                // SLIDE2
                animStep = ANIM_SLIDE2;
                timeDiff -= TIME_HOLD2;
            }
        }

        if (animStep == ANIM_FLASH) {
            review.draw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            if (timeDiff < TIME_FLASH) {
                float f = 0.3f - 0.3f * timeDiff / TIME_FLASH;
                int color = Color.argb((int) (255 * f), 255, 255, 255);
                canvas.fillRect(mX, mY, mDrawWidth, mDrawHeight, color);
            }
        } else if (animStep == ANIM_SLIDE) {
            float fraction = mSlideInterpolator.getInterpolation((float) (timeDiff) / (TIME_SLIDE - TIME_HOLD));
            float x = mX;
            float y = mY;
            float w = 0;
            float h = 0;
            x = interpolate(mX, mHoldX, fraction);
            y = interpolate(mY, mHoldY, fraction);
            w = interpolate(mDrawWidth, mHoldW, fraction);
            h = interpolate(mDrawHeight, mHoldH, fraction);
            preview.directDraw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            review.draw(canvas, (int) x, (int) y, (int) w, (int) h);
        } else if (animStep == ANIM_HOLD2) {
            preview.directDraw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            review.draw(canvas, mHoldX, mHoldY, mHoldW, mHoldH);
            mBorder.draw(canvas, (int) mHoldX - mShadowSize, (int) mHoldY - mShadowSize,
                    (int) mHoldW + 2 * mShadowSize, (int) mHoldH + 2 * mShadowSize);
        } else if (animStep == ANIM_SLIDE2) {
            float fraction = (float)(timeDiff) / (TIME_SLIDE2 - TIME_HOLD2);
            float x = mHoldX;
            float y = mHoldY;
            float d = mOffset * fraction;
            switch (mAnimOrientation) {
            case 0:
                x = mHoldX + d;
                break;
            case 180:
                x = mHoldX - d;
                break;
            case 90:
                y = mHoldY - d;
                break;
            case 270:
                y = mHoldY + d;
                break;
            }
            preview.directDraw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            mBorder.draw(canvas, (int) x - mShadowSize, (int) y - mShadowSize,
                    (int) mHoldW + 2 * mShadowSize, (int) mHoldH + 2 * mShadowSize);
            review.draw(canvas, (int) x, (int) y, mHoldW, mHoldH);
        }
        return true;
    }

    private static float interpolate(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }

}
