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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.Drawable;

import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/**
 * This class implements a circular drawable that starts with a zero radius
 * and can be triggered to animate expand to a given radius.
 * <p>
 * There are two colors associated with this drawable:
 * <p>
 * A background color, which is loaded from a resource
 * R.color.mode_icon_hover_highlight.
 * <p>
 * And, a main color, which is attached to the main circle that is expanded last
 * and is drawn on top of the other colors.
 * <p>
 * The driving purpose for this class is to implement a Material-like look and
 * feel for mode switcher touch events.
 */
public class TouchCircleDrawable extends Drawable {
    private static final int CIRCLE_ANIM_DURATION_MS = 250;

    private Paint mColorPaint = new Paint();
    private Paint mBackgroundPaint = new Paint();
    private int mColor;
    private int mColorAlpha = 0xff;
    private int mColorRadius;
    private int mBackgroundRadius;
    private Drawable mIconDrawable;
    private int mIconDrawableSize;
    private boolean mDrawBackground;

    private Animator.AnimatorListener mAnimatorListener;
    private ValueAnimator.AnimatorUpdateListener mUpdateListener;

    private static final int INVALID = -1;
    private int mW = INVALID;
    private int mH = INVALID;
    private Point mCenter;

    /**
     * Constructor
     *
     * @param resources Resources, needed to poke around for the background
     * color value.
     * @param color The main this circle drawable expands to.
     * @param baseColor The color of the initial expanded circle
     * (draws behind the main color).
     */
    public TouchCircleDrawable(Resources resources, int color, int baseColor) {
        super();

        mColorPaint.setAntiAlias(true);
        mBackgroundPaint.setAntiAlias(true);
        mBackgroundPaint.setColor(resources.getColor(R.color.mode_icon_hover_highlight));

        setColor(color);
    }

    /**
     * Constructor
     *
     * @param resources Resources, needed to poke around for the background color value.
     */
    public TouchCircleDrawable(Resources resources) {
        this(resources, 0xffffff, 0xffffff);
    }

    /**
     * Set the size of this drawable.
     *
     * @param w Width to set.
     * @param h Height to set.
     */
    public void setSize(int w, int h) {
        mW = w;
        mH = h;
    }

    /**
     * Set the center of the circle for this drawable.
     *
     * @param p The center point.
     */
    public void setCenter(Point p) {
        mCenter = p;
        updateIconBounds();
    }

    /**
     * @return The center of this drawable.
     */
    public Point getCenter() {
        return mCenter;
    }

    @Override
    public void draw(Canvas canvas) {
        int w = mW;
        int h = mH;

        if (w == INVALID || h == INVALID || mCenter == null) {
            return;
        }

        if (mDrawBackground) {
            canvas.drawCircle(mCenter.x, mCenter.y, mBackgroundRadius, mBackgroundPaint);
        }
        canvas.drawCircle(mCenter.x, mCenter.y, mColorRadius, mColorPaint);
        if (mIconDrawable != null) {
            mIconDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mColorAlpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mColorPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * Set the main color.
     *
     * @param color The main color.
     */
    public void setColor(int color) {
        mColor = color;
        mColorPaint.setColor(mColor);
        mColorPaint.setAlpha(mColorAlpha);
    }

    public void setIconDrawable(Drawable d, int size) {
        mIconDrawable = d;
        mIconDrawableSize = size;
        updateIconBounds();
    }

    private void updateIconBounds() {
        if (mCenter != null) {
            mIconDrawable.setBounds(
                mCenter.x - mIconDrawableSize/2, mCenter.y - mIconDrawableSize/2,
                mCenter.x + mIconDrawableSize/2, mCenter.y + mIconDrawableSize/2);
        }
    }

    /**
     * Start the expand animation.
     */
    public void animate() {
        mBackgroundRadius = Math.min(mW/2, mH/2);

        final ValueAnimator colorAnimator =
                ValueAnimator.ofInt(0, Math.min(mW/2, mH/2));
        colorAnimator.setDuration(CIRCLE_ANIM_DURATION_MS);
        colorAnimator.setInterpolator(Gusterpolator.INSTANCE);
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mColorRadius = (Integer) animation.getAnimatedValue();
                invalidateSelf();
                if (mUpdateListener != null) {
                    mUpdateListener.onAnimationUpdate(animation);
                }
            }
        });

        colorAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDrawBackground = true;

                if (mAnimatorListener != null) {
                    mAnimatorListener.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mDrawBackground = false;

                if (mAnimatorListener != null) {
                    mAnimatorListener.onAnimationEnd(animation);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mDrawBackground = false;

                if (mAnimatorListener != null) {
                    mAnimatorListener.onAnimationCancel(animation);
                }
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                if (mAnimatorListener != null) {
                    mAnimatorListener.onAnimationRepeat(animation);
                }
            }
        });

        colorAnimator.start();
    }

    /**
     *  Reset this drawable to its initial, preanimated state.
     */
    public void reset() {
        mColorRadius = 0;
    }

    /**
     * Set an {@link android.animation.Animator.AnimatorListener} to be
     * attached to the animation.
     *
     * @param listener The listener.
     */
    public void setAnimatorListener(Animator.AnimatorListener listener) {
        mAnimatorListener = listener;
    }

    /**
     * Set an {@link android.animation.ValueAnimator} to be
     * attached to the animation.
     *
     * @param listener The listener.
     */
    public void setUpdateListener(ValueAnimator.AnimatorUpdateListener listener) {
        mUpdateListener = listener;
    }
}
