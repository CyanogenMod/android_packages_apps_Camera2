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
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.app.CameraAppUI;
import com.android.camera.debug.Log;
import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/**
 * This view is designed to handle all the animations during camera mode transition.
 * It should only be visible during mode switch.
 */
public class ModeTransitionView extends View {
    private static final Log.Tag TAG = new Log.Tag("ModeTransView");

    private static final int PEEP_HOLE_ANIMATION_DURATION_MS = 300;
    private static final int ICON_FADE_OUT_DURATION_MS = 850;
    private static final int FADE_OUT_DURATION_MS = 250;

    private static final int IDLE = 0;
    private static final int PULL_UP_SHADE = 1;
    private static final int PULL_DOWN_SHADE = 2;
    private static final int PEEP_HOLE_ANIMATION = 3;
    private static final int FADE_OUT = 4;
    private static final int SHOW_STATIC_IMAGE = 5;

    private static final float SCROLL_DISTANCE_MULTIPLY_FACTOR = 2f;
    private static final int ALPHA_FULLY_TRANSPARENT = 0;
    private static final int ALPHA_FULLY_OPAQUE = 255;
    private static final int ALPHA_HALF_TRANSPARENT = 127;

    private final GestureDetector mGestureDetector;
    private final Paint mMaskPaint = new Paint();
    private final Rect mIconRect = new Rect();
    /** An empty drawable to fall back to when mIconDrawable set to null. */
    private final Drawable mDefaultDrawable = new ColorDrawable();

    private Drawable mIconDrawable;
    private int mBackgroundColor;
    private int mWidth = 0;
    private int mHeight = 0;
    private int mPeepHoleCenterX = 0;
    private int mPeepHoleCenterY = 0;
    private float mRadius = 0f;
    private int mIconSize;
    private AnimatorSet mPeepHoleAnimator;
    private int mAnimationType = PEEP_HOLE_ANIMATION;
    private float mScrollDistance = 0;
    private final Path mShadePath = new Path();
    private final Paint mShadePaint = new Paint();
    private CameraAppUI.AnimationFinishedListener mAnimationFinishedListener;
    private float mScrollTrend;
    private Bitmap mBackgroundBitmap;

    public ModeTransitionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaskPaint.setAlpha(0);
        mMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mBackgroundColor = getResources().getColor(R.color.video_mode_color);
        mGestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent ev) {
                        setScrollDistance(0f);
                        mScrollTrend = 0f;
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float distanceX, float distanceY) {
                        setScrollDistance(getScrollDistance()
                                + SCROLL_DISTANCE_MULTIPLY_FACTOR * distanceY);
                        mScrollTrend = 0.3f * mScrollTrend + 0.7f * distanceY;
                        return false;
                    }
                });
        mIconSize = getResources().getDimensionPixelSize(R.dimen.mode_transition_view_icon_size);
        setIconDrawable(mDefaultDrawable);
    }

    /**
     * Updates the size and shape of the shade
     */
    private void updateShade() {
        if (mAnimationType == PULL_UP_SHADE || mAnimationType == PULL_DOWN_SHADE) {
            mShadePath.reset();
            float shadeHeight;
            if (mAnimationType == PULL_UP_SHADE) {
                // Scroll distance > 0.
                mShadePath.addRect(0, mHeight - getScrollDistance(), mWidth, mHeight,
                        Path.Direction.CW);
                shadeHeight = getScrollDistance();
            } else {
                // Scroll distance < 0.
                mShadePath.addRect(0, 0, mWidth, - getScrollDistance(), Path.Direction.CW);
                shadeHeight = getScrollDistance() * (-1);
            }

            if (mIconDrawable != null) {
                if (shadeHeight < mHeight / 2 || mHeight == 0) {
                    mIconDrawable.setAlpha(ALPHA_FULLY_TRANSPARENT);
                } else {
                    int alpha  = ((int) shadeHeight - mHeight / 2)  * ALPHA_FULLY_OPAQUE
                            / (mHeight / 2);
                    mIconDrawable.setAlpha(alpha);
                }
            }
            invalidate();
        }
    }

    /**
     * Sets the scroll distance. Note this function gets called in every
     * frame during animation. It should be very light weight.
     *
     * @param scrollDistance the scaled distance that user has scrolled
     */
    public void setScrollDistance(float scrollDistance) {
        // First make sure scroll distance is clamped to the valid range.
        if (mAnimationType == PULL_UP_SHADE) {
            scrollDistance = Math.min(scrollDistance, mHeight);
            scrollDistance = Math.max(scrollDistance, 0);
        } else if (mAnimationType == PULL_DOWN_SHADE) {
            scrollDistance = Math.min(scrollDistance, 0);
            scrollDistance = Math.max(scrollDistance, -mHeight);
        }
        mScrollDistance = scrollDistance;
        updateShade();
    }

    public float getScrollDistance() {
        return mScrollDistance;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mAnimationType == PEEP_HOLE_ANIMATION) {
            canvas.drawColor(mBackgroundColor);
            if (mPeepHoleAnimator != null) {
                // Draw a transparent circle using clear mode
                canvas.drawCircle(mPeepHoleCenterX, mPeepHoleCenterY, mRadius, mMaskPaint);
            }
        } else if (mAnimationType == PULL_UP_SHADE || mAnimationType == PULL_DOWN_SHADE) {
            canvas.drawPath(mShadePath, mShadePaint);
        } else if (mAnimationType == IDLE || mAnimationType == FADE_OUT) {
            canvas.drawColor(mBackgroundColor);
        } else if (mAnimationType == SHOW_STATIC_IMAGE) {
            // TODO: These different animation types need to be refactored into
            // different animation effects.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            super.onDraw(canvas);
            return;
        }
        super.onDraw(canvas);
        mIconDrawable.draw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mWidth = right - left;
        mHeight = bottom - top;
        // Center the icon in the view.
        mIconRect.set(mWidth / 2 - mIconSize / 2, mHeight / 2 - mIconSize / 2,
                mWidth / 2 + mIconSize / 2, mHeight / 2 + mIconSize / 2);
        mIconDrawable.setBounds(mIconRect);
    }

    /**
     * This is an overloaded function. When no position is provided for the animation,
     * the peep hole will start at the default position (i.e. center of the view).
     */
    public void startPeepHoleAnimation() {
        float x = mWidth / 2;
        float y = mHeight / 2;
        startPeepHoleAnimation(x, y);
    }

    /**
     * Starts the peep hole animation where the circle is centered at position (x, y).
     */
    private void startPeepHoleAnimation(float x, float y) {
        if (mPeepHoleAnimator != null && mPeepHoleAnimator.isRunning()) {
            return;
        }
        mAnimationType = PEEP_HOLE_ANIMATION;
        mPeepHoleCenterX = (int) x;
        mPeepHoleCenterY = (int) y;

        int horizontalDistanceToFarEdge = Math.max(mPeepHoleCenterX, mWidth - mPeepHoleCenterX);
        int verticalDistanceToFarEdge = Math.max(mPeepHoleCenterY, mHeight - mPeepHoleCenterY);
        int endRadius = (int) (Math.sqrt(horizontalDistanceToFarEdge * horizontalDistanceToFarEdge
                + verticalDistanceToFarEdge * verticalDistanceToFarEdge));

        final ValueAnimator radiusAnimator = ValueAnimator.ofFloat(0, endRadius);
        radiusAnimator.setDuration(PEEP_HOLE_ANIMATION_DURATION_MS);

        final ValueAnimator  iconScaleAnimator = ValueAnimator.ofFloat(1f, 0.5f);
        iconScaleAnimator.setDuration(ICON_FADE_OUT_DURATION_MS);

        final ValueAnimator  iconAlphaAnimator = ValueAnimator.ofInt(ALPHA_HALF_TRANSPARENT,
                ALPHA_FULLY_TRANSPARENT);
        iconAlphaAnimator.setDuration(ICON_FADE_OUT_DURATION_MS);

        mPeepHoleAnimator = new AnimatorSet();
        mPeepHoleAnimator.playTogether(radiusAnimator, iconAlphaAnimator, iconScaleAnimator);
        mPeepHoleAnimator.setInterpolator(Gusterpolator.INSTANCE);

        iconAlphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // Modify mask by enlarging the hole
                mRadius = (Float) radiusAnimator.getAnimatedValue();

                mIconDrawable.setAlpha((Integer) iconAlphaAnimator.getAnimatedValue());
                float scale = (Float) iconScaleAnimator.getAnimatedValue();
                int size = (int) (scale * (float) mIconSize);

                mIconDrawable.setBounds(mPeepHoleCenterX - size / 2,
                        mPeepHoleCenterY - size / 2,
                        mPeepHoleCenterX + size / 2,
                        mPeepHoleCenterY + size / 2);

                invalidate();
            }
        });

        mPeepHoleAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Sets a HW layer on the view for the animation.
                setLayerType(LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Sets the layer type back to NONE as a workaround for b/12594617.
                setLayerType(LAYER_TYPE_NONE, null);
                mPeepHoleAnimator = null;
                mRadius = 0;
                mIconDrawable.setAlpha(ALPHA_FULLY_OPAQUE);
                mIconDrawable.setBounds(mIconRect);
                setVisibility(GONE);
                mAnimationType = IDLE;
                if (mAnimationFinishedListener != null) {
                    mAnimationFinishedListener.onAnimationFinished(true);
                    mAnimationFinishedListener = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mPeepHoleAnimator.start();

    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean touchHandled = mGestureDetector.onTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
            // TODO: Take into account fling
            snap();
        }
        return touchHandled;
    }

    /**
     * Snaps the shade to position at the end of a gesture.
     */
    private void snap() {
        if (mScrollTrend >= 0 && mAnimationType == PULL_UP_SHADE) {
            // Snap to full screen.
            snapShadeTo(mHeight, ALPHA_FULLY_OPAQUE);
        } else if (mScrollTrend <= 0 && mAnimationType == PULL_DOWN_SHADE) {
            // Snap to full screen.
            snapShadeTo(-mHeight, ALPHA_FULLY_OPAQUE);
        } else if (mScrollTrend < 0 && mAnimationType == PULL_UP_SHADE) {
            // Snap back.
            snapShadeTo(0, ALPHA_FULLY_TRANSPARENT, false);
        } else if (mScrollTrend > 0 && mAnimationType == PULL_DOWN_SHADE) {
            // Snap back.
            snapShadeTo(0, ALPHA_FULLY_TRANSPARENT, false);
        }
    }

    private void snapShadeTo(int scrollDistance, int alpha) {
        snapShadeTo(scrollDistance, alpha, true);
    }

    /**
     * Snaps the shade to a given scroll distance and sets the icon alpha. If the shade
     * is to snap back out, then hide the view after the animation.
     *
     * @param scrollDistance scaled user scroll distance
     * @param alpha ending alpha of the icon drawable
     * @param snapToFullScreen whether this snap animation snaps the shade to full screen
     */
    private void snapShadeTo(final int scrollDistance, final int alpha,
                             final boolean snapToFullScreen) {
        if (mAnimationType == PULL_UP_SHADE || mAnimationType == PULL_DOWN_SHADE) {
            ObjectAnimator scrollAnimator = ObjectAnimator.ofFloat(this, "scrollDistance",
                    scrollDistance);
            scrollAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setScrollDistance(scrollDistance);
                    mIconDrawable.setAlpha(alpha);
                    mAnimationType = IDLE;
                    if (!snapToFullScreen) {
                        setVisibility(GONE);
                    }
                    if (mAnimationFinishedListener != null) {
                        mAnimationFinishedListener.onAnimationFinished(snapToFullScreen);
                        mAnimationFinishedListener = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            scrollAnimator.setInterpolator(Gusterpolator.INSTANCE);
            scrollAnimator.start();
        }
    }


    /**
     * Set the states for the animation that pulls up a shade with given shade color.
     *
     * @param shadeColorId color id of the shade that will be pulled up
     * @param iconId id of the icon that will appear on top the shade
     * @param listener a listener that will get notified when the animation
     *        is finished. Could be <code>null</code>.
     */
    public void prepareToPullUpShade(int shadeColorId, int iconId,
                                     CameraAppUI.AnimationFinishedListener listener) {
        prepareShadeAnimation(PULL_UP_SHADE, shadeColorId, iconId, listener);
    }

    /**
     * Set the states for the animation that pulls down a shade with given shade color.
     *
     * @param shadeColorId color id of the shade that will be pulled down
     * @param modeIconResourceId id of the icon that will appear on top the shade
     * @param listener a listener that will get notified when the animation
     *        is finished. Could be <code>null</code>.
     */
    public void prepareToPullDownShade(int shadeColorId, int modeIconResourceId,
                                       CameraAppUI.AnimationFinishedListener listener) {;
        prepareShadeAnimation(PULL_DOWN_SHADE, shadeColorId, modeIconResourceId, listener);
    }

    /**
     * Set the states for the animation that involves a shade.
     *
     * @param animationType type of animation that will happen to the shade
     * @param shadeColorId color id of the shade that will be animated
     * @param iconResId id of the icon that will appear on top the shade
     * @param listener a listener that will get notified when the animation
     *        is finished. Could be <code>null</code>.
     */
    private void prepareShadeAnimation(int animationType, int shadeColorId, int iconResId,
                                       CameraAppUI.AnimationFinishedListener listener) {
        mAnimationFinishedListener = listener;
        if (mPeepHoleAnimator != null && mPeepHoleAnimator.isRunning()) {
            mPeepHoleAnimator.end();
        }
        mAnimationType = animationType;
        resetShade(shadeColorId, iconResId);
    }

    /**
     * Reset the shade with the given shade color and icon drawable.
     *
     * @param shadeColorId id of the shade color
     * @param modeIconResourceId resource id of the icon drawable
     */
    private void resetShade(int shadeColorId, int modeIconResourceId) {
        // Sets color for the shade.
        int shadeColor = getResources().getColor(shadeColorId);
        mBackgroundColor = shadeColor;
        mShadePaint.setColor(shadeColor);
        // Reset scroll distance.
        setScrollDistance(0f);
        // Sets new drawable.
        updateIconDrawableByResourceId(modeIconResourceId);
        mIconDrawable.setAlpha(0);
        setVisibility(VISIBLE);
    }

    /**
     * By default, all drawables instances loaded from the same resource share a
     * common state; if you modify the state of one instance, all the other
     * instances will receive the same modification. So here we need to make sure
     * we mutate the drawable loaded from resource.
     *
     * @param modeIconResourceId resource id of the icon drawable
     */
    private void updateIconDrawableByResourceId(int modeIconResourceId) {
        Drawable iconDrawable = getResources().getDrawable(modeIconResourceId);
        if (iconDrawable == null) {
            // Resource id not found
            Log.e(TAG, "Invalid resource id for icon drawable. Setting icon drawable to null.");
            setIconDrawable(null);
            return;
        }
        // Mutate the drawable loaded from resource so modifying its states does
        // not affect other drawable instances loaded from the same resource.
        setIconDrawable(iconDrawable.mutate());
    }

    /**
     * In order to make sure icon drawable is never set to null. Fall back to an
     * empty drawable when icon needs to get reset.
     *
     * @param iconDrawable new drawable for icon. A value of <code>null</code> sets
     *        the icon drawable to the default drawable.
     */
    private void setIconDrawable(Drawable iconDrawable) {
        if (iconDrawable == null) {
            mIconDrawable = mDefaultDrawable;
        } else {
            mIconDrawable = iconDrawable;
        }
    }

    /**
     * Initialize the mode cover with a mode theme color and a mode icon.
     *
     * @param colorId resource id of the mode theme color
     * @param modeIconResourceId resource id of the icon drawable
     */
    public void setupModeCover(int colorId, int modeIconResourceId) {
        mBackgroundBitmap = null;
        // Stop ongoing animation.
        if (mPeepHoleAnimator != null && mPeepHoleAnimator.isRunning()) {
            mPeepHoleAnimator.cancel();
        }
        mAnimationType = IDLE;
        mBackgroundColor = getResources().getColor(colorId);
        // Sets new drawable.
        updateIconDrawableByResourceId(modeIconResourceId);
        mIconDrawable.setAlpha(ALPHA_FULLY_OPAQUE);
        setVisibility(VISIBLE);
    }

    /**
     * Hides the cover view and notifies the
     * {@link com.android.camera.app.CameraAppUI.AnimationFinishedListener} of whether
     * the hide animation is successfully finished.
     *
     * @param animationFinishedListener a listener that will get notified when the
     *        animation is finished. Could be <code>null</code>.
     */
    public void hideModeCover(
            final CameraAppUI.AnimationFinishedListener animationFinishedListener) {
        if (mAnimationType != IDLE) {
            // Nothing to hide.
            if (animationFinishedListener != null) {
                // Animation not successful.
                animationFinishedListener.onAnimationFinished(false);
            }
        } else {
            // Start fade out animation.
            mAnimationType = FADE_OUT;
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
            alphaAnimator.setDuration(FADE_OUT_DURATION_MS);
            // Linear interpolation.
            alphaAnimator.setInterpolator(null);
            alphaAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(GONE);
                    setAlpha(1f);
                    if (animationFinishedListener != null) {
                        animationFinishedListener.onAnimationFinished(true);
                        mAnimationType = IDLE;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            alphaAnimator.start();
        }
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        int alphaScaled = (int) (255f * getAlpha());
        mBackgroundColor = (mBackgroundColor & 0xFFFFFF) | (alphaScaled << 24);
        mIconDrawable.setAlpha(alphaScaled);
    }

    /**
     * Setup the mode cover with a screenshot.
     */
    public void setupModeCover(Bitmap screenShot) {
        mBackgroundBitmap = screenShot;
        setVisibility(VISIBLE);
        mAnimationType = SHOW_STATIC_IMAGE;
    }

    /**
     * Hide the mode cover without animation.
     */
    // TODO: Refactor this and define how cover should be hidden during cover setup
    public void hideImageCover() {
        mBackgroundBitmap = null;
        setVisibility(GONE);
        mAnimationType = IDLE;
    }
}

