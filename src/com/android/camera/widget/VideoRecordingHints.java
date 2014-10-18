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
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.OrientationEventListener;
import android.view.View;

import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.lang.ref.WeakReference;

/**
 * This class is designed to show the video recording hint when device is held in
 * portrait before video recording. The rotation device indicator will start rotating
 * after a time-out and will fade out if the device is rotated to landscape. A tap
 * on screen will dismiss the indicator.
 */
public class VideoRecordingHints extends View {

    private static final int PORTRAIT_ROTATE_DELAY_MS = 1000;
    private static final int ROTATION_DURATION_MS = 1000;
    private static final int FADE_OUT_DURATION_MS = 600;
    private static final float ROTATION_DEGREES = 180f;
    private static final float INITIAL_ROTATION = 0f;
    private static final int UNSET = -1;

    private final int mRotateArrowsHalfSize;
    private final int mPhoneGraphicHalfWidth;
    private final Drawable mRotateArrows;
    private final Drawable mPhoneGraphic;
    private final int mPhoneGraphicHalfHeight;
    private final boolean mIsDefaultToPortrait;
    private float mRotation = INITIAL_ROTATION;
    private final ValueAnimator mRotationAnimation;
    private final ObjectAnimator mAlphaAnimator;
    private boolean mIsInLandscape = false;
    private int mCenterX = UNSET;
    private int mCenterY = UNSET;
    private int mLastOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private static class RotationAnimatorListener implements Animator.AnimatorListener {
        private final WeakReference<VideoRecordingHints> mHints;
        private boolean mCanceled = false;

        public RotationAnimatorListener(VideoRecordingHints hint) {
            mHints = new WeakReference<VideoRecordingHints>(hint);
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCanceled = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            VideoRecordingHints hint = mHints.get();
            if (hint == null) {
                return;
            }

            hint.mRotation = ((int) hint.mRotation) % 360;
            // If animation is canceled, do not restart it.
            if (mCanceled) {
                return;
            }
            hint.post(new Runnable() {
                @Override
                public void run() {
                    VideoRecordingHints hint = mHints.get();
                    if (hint != null) {
                        hint.continueRotationAnimation();
                    }
                }
            });
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceled = true;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            // Do nothing.
        }
    }

    private static class AlphaAnimatorListener implements Animator.AnimatorListener {
        private final WeakReference<VideoRecordingHints> mHints;
        AlphaAnimatorListener(VideoRecordingHints hint) {
            mHints = new WeakReference<VideoRecordingHints>(hint);
        }

        @Override
        public void onAnimationStart(Animator animation) {
            // Do nothing.
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            VideoRecordingHints hint = mHints.get();
            if (hint == null) {
                return;
            }

            hint.invalidate();
            hint.setAlpha(1f);
            hint.mRotation = 0;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            // Do nothing.
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            // Do nothing.
        }
    }

    public VideoRecordingHints(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRotateArrows = getResources().getDrawable(R.drawable.rotate_arrows);
        mPhoneGraphic = getResources().getDrawable(R.drawable.ic_phone_graphic);
        mRotateArrowsHalfSize = getResources().getDimensionPixelSize(
                R.dimen.video_hint_arrow_size) / 2;
        mPhoneGraphicHalfWidth = getResources()
                .getDimensionPixelSize(R.dimen.video_hint_phone_graphic_width) / 2;
        mPhoneGraphicHalfHeight = getResources()
                .getDimensionPixelSize(R.dimen.video_hint_phone_graphic_height) / 2;

        mRotationAnimation = ValueAnimator.ofFloat(mRotation, mRotation + ROTATION_DEGREES);
        mRotationAnimation.setDuration(ROTATION_DURATION_MS);
        mRotationAnimation.setStartDelay(PORTRAIT_ROTATE_DELAY_MS);
        mRotationAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mRotation = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });

        mRotationAnimation.addListener(new RotationAnimatorListener(this));

        mAlphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        mAlphaAnimator.setDuration(FADE_OUT_DURATION_MS);
        mAlphaAnimator.addListener(new AlphaAnimatorListener(this));
        mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(context);
    }

    /**
     * Restart the rotation animation using the current rotation as the starting
     * rotation, and then rotate a pre-defined amount. If the rotation animation
     * is currently running, do nothing.
     */
    private void continueRotationAnimation() {
        if (mRotationAnimation.isRunning()) {
            return;
        }
        mRotationAnimation.setFloatValues(mRotation, mRotation + ROTATION_DEGREES);
        mRotationAnimation.start();
    }

    @Override
    public void onVisibilityChanged(View v, int visibility) {
        super.onVisibilityChanged(v, visibility);
        if (getVisibility() == VISIBLE && !isInLandscape()) {
            continueRotationAnimation();
        } else if (getVisibility() != VISIBLE) {
            mRotationAnimation.cancel();
            mRotation = 0;
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Center drawables in the layout
        mCenterX = (right - left) / 2;
        mCenterY = (bottom - top) / 2;
        mRotateArrows.setBounds(mCenterX - mRotateArrowsHalfSize, mCenterY - mRotateArrowsHalfSize,
                mCenterX + mRotateArrowsHalfSize, mCenterY + mRotateArrowsHalfSize);
        mPhoneGraphic.setBounds(mCenterX - mPhoneGraphicHalfWidth, mCenterY - mPhoneGraphicHalfHeight,
                mCenterX + mPhoneGraphicHalfWidth, mCenterY + mPhoneGraphicHalfHeight);
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        // Don't draw anything after the fade-out animation in landscape.
        if (mIsInLandscape && !mAlphaAnimator.isRunning()) {
            return;
        }
        canvas.save();
        canvas.rotate(-mRotation, mCenterX, mCenterY);
        mRotateArrows.draw(canvas);
        canvas.restore();
        if (mIsInLandscape) {
            canvas.save();
            canvas.rotate(90, mCenterX, mCenterY);
            mPhoneGraphic.draw(canvas);
            canvas.restore();
        } else {
            mPhoneGraphic.draw(canvas);
        }
    }

    /**
     * Handles orientation change by starting/stopping the video hint based on the
     * new orientation.
     */
    public void onOrientationChanged(int orientation) {
        if (mLastOrientation == orientation) {
            return;
        }
        mLastOrientation = orientation;
        if (mLastOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }

        mIsInLandscape = isInLandscape();
        if (getVisibility() == VISIBLE) {
            if (mIsInLandscape) {
                // Landscape.
                mRotationAnimation.cancel();
                // Start fading out.
                if (mAlphaAnimator.isRunning()) {
                    return;
                }
                mAlphaAnimator.start();
            } else {
                // Portrait.
                continueRotationAnimation();
            }
        }
    }

    /**
     * Returns whether the device is in landscape based on the natural orientation
     * and rotation from natural orientation.
     */
    private boolean isInLandscape() {
        return (mLastOrientation % 180 == 90 && mIsDefaultToPortrait)
                || (mLastOrientation % 180 == 0 && !mIsDefaultToPortrait);
    }
}
