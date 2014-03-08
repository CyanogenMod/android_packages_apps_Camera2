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
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.camera.ShutterButton;
import com.android.camera.util.Gusterpolator;
import com.android.camera2.R;

/**
 * BottomBar swaps its width and height on rotation. In addition, it also changes
 * gravity and layout orientation based on the new orientation. Specifically, in
 * landscape it aligns to the right side of its parent and lays out its children
 * vertically, whereas in portrait, it stays at the bottom of the parent and has
 * a horizontal layout orientation.
*/
public class BottomBar extends FrameLayout
    implements PreviewStatusListener.PreviewAreaChangedListener {

    public interface AdjustPreviewAreaListener {
        /**
         * Called when the preview should be centered in the reference area.
         *
         * @param rect The reference area.
         */
        public void fitAndCenterPreviewAreaInRect(RectF rect);

        /**
         * Called when the preview should be aligned to the bottom of the
         * reference area.
         *
         * @param rect The reference area.
         */
        public void fitAndAlignBottomInRect(RectF rect);

        /**
         * Called when the preview should be aligned to the right of the
         * reference area.
         *
         * @param rect The reference area.
         */
        public void fitAndAlignRightInRect(RectF rect);
    }

    private static final String TAG = "BottomBar";

    private static final int CIRCLE_ANIM_DURATION_MS = 300;

    private static final int MODE_CAPTURE = 0;
    private static final int MODE_INTENT = 1;
    private static final int MODE_INTENT_REVIEW = 2;
    private int mMode;

    private float mPreviewShortEdge;
    private float mPreviewLongEdge;

    private final int mMinimumHeight;
    private final int mMaximumHeight;
    private final int mOptimalHeight;
    private final int mBackgroundAlphaOverlay;
    private final int mBackgroundAlphaDefault;
    private boolean mOverLayBottomBar;
    // To avoid multiple object allocations in onLayout().
    private final RectF mAlignArea = new RectF();

    private FrameLayout mCaptureLayout;
    private TopRightWeightedLayout mIntentReviewLayout;

    private ShutterButton mShutterButton;

    private int mBackgroundColor;
    private int mBackgroundPressedColor;
    private int mBackgroundAlpha = 0xff;

    private final Paint mCirclePaint = new Paint();
    private final Path mCirclePath = new Path();
    private boolean mDrawCircle;
    private final float mCircleRadius;
    private final Path mRectPath = new Path();

    private final RectF mRect = new RectF();

    private AdjustPreviewAreaListener mAdjustPreviewAreaListener;

    public void setAdjustPreviewAreaListener(AdjustPreviewAreaListener listener) {
        mAdjustPreviewAreaListener = listener;
        notifyAreaAdjust();
    }

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMinimumHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_min);
        mMaximumHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_max);
        mOptimalHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);
        mCircleRadius = getResources()
            .getDimensionPixelSize(R.dimen.video_capture_circle_diameter) / 2;
        mCirclePaint.setAntiAlias(true);
        mBackgroundAlphaOverlay = getResources().getInteger(R.integer.bottom_bar_background_alpha_overlay);
        mBackgroundAlphaDefault = getResources().getInteger(R.integer
                .bottom_bar_background_alpha);
    }

    private void setPaintColor(int alpha, int color, boolean isCaptureChange) {
        int computedColor = (alpha << 24) | (color & 0x00ffffff);
        mCirclePaint.setColor(computedColor);
        invalidate();
    }

    private void setPaintColor(int alpha, int color) {
        setPaintColor(alpha, color, false);
    }

    private void setCaptureButtonUp() {
        setPaintColor(mBackgroundAlpha, mBackgroundColor, true);
        invalidate();
    }

    private void setCaptureButtonDown() {
        setPaintColor(mBackgroundAlpha, mBackgroundPressedColor, true);
        invalidate();
    }

    @Override
    public void onFinishInflate() {
        mCaptureLayout
            = (FrameLayout) findViewById(R.id.bottombar_capture);
        mIntentReviewLayout
            = (TopRightWeightedLayout) findViewById(R.id.bottombar_intent_review);

        mShutterButton
            = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getActionMasked()) {
                    setCaptureButtonDown();
                } else if (MotionEvent.ACTION_UP == event.getActionMasked() ||
                        MotionEvent.ACTION_CANCEL == event.getActionMasked()) {
                    setCaptureButtonUp();
                } else if (MotionEvent.ACTION_MOVE == event.getActionMasked()) {
                    if (!mRect.contains(event.getX(), event.getY())) {
                        setCaptureButtonUp();
                    }
                }
                return false;
            }
        });
    }

    /**
     * Hide the intent layout.  This is necessary for switching between
     * the intent capture layout and the bottom bar options.
     */
    private void hideIntentReviewLayout() {
        mIntentReviewLayout.setVisibility(View.INVISIBLE);
    }

    /**
     * Perform a transition from the bottom bar options layout to the
     * bottom bar capture layout.
     */
    public void transitionToCapture() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        if (mMode == MODE_INTENT || mMode == MODE_INTENT_REVIEW) {
            mIntentReviewLayout.setVisibility(View.INVISIBLE);
        }

        mMode = MODE_CAPTURE;
    }

    /**
     * Perform a transition to the global intent layout.  The current
     * layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentCaptureLayout() {
        mIntentReviewLayout.setVisibility(View.INVISIBLE);
        mCaptureLayout.setVisibility(View.VISIBLE);

        mMode = MODE_INTENT;
    }

    /**
     * Perform a transition to the global intent review layout.
     * The current layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentReviewLayout() {
        mCaptureLayout.setVisibility(View.INVISIBLE);
        mIntentReviewLayout.setVisibility(View.VISIBLE);

        mMode = MODE_INTENT_REVIEW;
    }

    private void setButtonImageLevels(int level) {
        ((ImageButton) findViewById(R.id.cancel_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.done_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.retake_button)).setImageLevel(level);
    }

    private void setOverlayBottomBar(boolean overlay) {
        mOverLayBottomBar = overlay;
        if (overlay) {
            setBackgroundAlpha(mBackgroundAlphaOverlay);
            setButtonImageLevels(1);
        } else {
            setBackgroundAlpha(mBackgroundAlphaDefault);
            setButtonImageLevels(0);
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int measureWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int measureHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (measureWidth == 0 || measureHeight == 0) {
            return;
        }

        if (mPreviewShortEdge != 0 && mPreviewLongEdge != 0) {
            float previewAspectRatio =
                    mPreviewLongEdge / mPreviewShortEdge;
            if (previewAspectRatio < 1.0) {
                previewAspectRatio = 1.0f / previewAspectRatio;
            }
            float screenAspectRatio = (float) measureWidth / (float) measureHeight;
            if (screenAspectRatio < 1.0) {
                screenAspectRatio = 1.0f / screenAspectRatio;
            }
            // TODO: background alphas should be set by xml references to colors.
            if (previewAspectRatio >= screenAspectRatio) {
                setOverlayBottomBar(true);
            } else {
                setOverlayBottomBar(false);
            }
        }

        // Calculates the width and height needed for the bar.
        int barWidth, barHeight;
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) getLayoutParams();
        if (measureWidth > measureHeight) {
            // Landscape.
            // TODO: The bottom bar should not need to care about the
            // the type of its parent.  Handle this in the parent layout.
            layoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            barHeight = (int) mPreviewShortEdge;
            if ((mPreviewLongEdge == 0 && mPreviewShortEdge == 0) || mOverLayBottomBar) {
                barWidth = mOptimalHeight;
            } else {
                float previewAspectRatio = mPreviewLongEdge / mPreviewShortEdge;
                barWidth = (int) (measureWidth - mPreviewLongEdge);
                if (barWidth < mMinimumHeight) {
                    barWidth = mOptimalHeight;
                    setOverlayBottomBar(previewAspectRatio > 14f / 9f);
                } else if (barWidth > mMaximumHeight) {
                    barWidth = mMaximumHeight;
                    setOverlayBottomBar(false);
                }
            }
        } else {
            // Portrait
            layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            barWidth = (int) mPreviewShortEdge;
            if ((mPreviewLongEdge == 0 && mPreviewShortEdge == 0) || mOverLayBottomBar) {
                barHeight = mOptimalHeight;
            } else {
                float previewAspectRatio = mPreviewLongEdge / mPreviewShortEdge;
                barHeight = (int) (measureHeight - mPreviewLongEdge);
                if (barHeight < mMinimumHeight) {
                    barHeight = mOptimalHeight;
                    setOverlayBottomBar(previewAspectRatio > 14f / 9f);
                } else if (barHeight > mMaximumHeight) {
                    barHeight = mMaximumHeight;
                    setOverlayBottomBar(false);
                }
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        notifyAreaAdjust();

        final int width = getWidth();
        final int height = getHeight();

        if (changed) {
            mCirclePath.reset();
            mCirclePath.addCircle(
                width/2,
                height/2,
                (int)(diagonalLength(width, height)/2),
                Path.Direction.CW);

            mRect.set(
                0.0f,
                0.0f,
                width,
                height);
            mRectPath.reset();
            mRectPath.addRect(mRect, Path.Direction.CW);
        }
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        setOffset(previewArea.width(), previewArea.height());
    }

    private void setOffset(float scaledTextureWidth, float scaledTextureHeight) {
        float offsetLongerEdge, offsetShorterEdge;
        if (scaledTextureHeight > scaledTextureWidth) {
            offsetLongerEdge = scaledTextureHeight;
            offsetShorterEdge = scaledTextureWidth;
        } else {
            offsetLongerEdge = scaledTextureWidth;
            offsetShorterEdge = scaledTextureHeight;
        }
        if (mPreviewLongEdge != offsetLongerEdge || mPreviewShortEdge != offsetShorterEdge) {
            mPreviewLongEdge = offsetLongerEdge;
            mPreviewShortEdge = offsetShorterEdge;
            requestLayout();
        }
    }

    // prevent touches on bottom bar (not its children)
    // from triggering a touch event on preview area
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        switch (mMode) {
            case MODE_CAPTURE:
                if (mDrawCircle) {
                    canvas.drawPath(mCirclePath, mCirclePaint);
                } else {
                    canvas.drawPath(mRectPath, mCirclePaint);
                }
                break;
            case MODE_INTENT:
                canvas.drawPaint(mCirclePaint); // TODO make this case handle capture button
                                                // highlighting correctly
                break;
            case MODE_INTENT_REVIEW:
                canvas.drawPaint(mCirclePaint);
        }

        super.onDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
    }

    public void setBackgroundPressedColor(int color) {
        mBackgroundPressedColor = color;
    }

    public void setBackgroundAlpha(int alpha) {
        mBackgroundAlpha = alpha;
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
    }

    private double diagonalLength(double w, double h) {
        return Math.sqrt((w*w) + (h*h));
    }
    private double diagonalLength() {
        return diagonalLength(getWidth(), getHeight());
    }

    private TransitionDrawable crossfadeDrawable(Drawable from, Drawable to) {
        Drawable [] arrayDrawable = new Drawable[2];
        arrayDrawable[0] = from;
        arrayDrawable[1] = to;
        TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
        transitionDrawable.setCrossFadeEnabled(true);
        return transitionDrawable;
    }

    /**
     * Sets the shutter button's icon resource. By default, all drawables instances
     * loaded from the same resource share a common state; if you modify the state
     * of one instance, all the other instances will receive the same modification.
     * In order to modify properties of this icon drawable without affecting other
     * drawables, here we use a mutable drawable which is guaranteed to not share
     * states with other drawables.
     */
    public void setShutterButtonIcon(int resId) {
        Drawable iconDrawable = getResources().getDrawable(resId);
        if (iconDrawable != null) {
            iconDrawable = iconDrawable.mutate();
        }
        mShutterButton.setImageDrawable(iconDrawable);
    }

    /**
     * Animates bar to a single stop button
     */
    public void animateToCircle(int resId) {
        final ValueAnimator radiusAnimator = ValueAnimator.ofFloat(
                                                 (float) diagonalLength()/2,
                                                 mCircleRadius);
        radiusAnimator.setDuration(CIRCLE_ANIM_DURATION_MS);
        radiusAnimator.setInterpolator(Gusterpolator.INSTANCE);

        radiusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCirclePath.reset();
                mCirclePath.addCircle(
                    getWidth()/2,
                    getHeight()/2,
                    (Float) animation.getAnimatedValue(),
                    Path.Direction.CW);

                invalidate();
            }
        });

        TransitionDrawable transitionDrawable = crossfadeDrawable(
                mShutterButton.getDrawable(),
                getResources().getDrawable(resId));
        mShutterButton.setImageDrawable(transitionDrawable);

        mDrawCircle = true;
        transitionDrawable.startTransition(CIRCLE_ANIM_DURATION_MS);
        radiusAnimator.start();
    }

    /**
     * Animates bar to full width / length with video capture icon
     */
    public void animateToFullSize(int resId) {
        final ValueAnimator radiusAnimator = ValueAnimator.ofFloat(
                                                 mCircleRadius,
                                                 (float) diagonalLength()/2);
        radiusAnimator.setDuration(CIRCLE_ANIM_DURATION_MS);
        radiusAnimator.setInterpolator(Gusterpolator.INSTANCE);
        radiusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCirclePath.reset();
                mCirclePath.addCircle(
                    getWidth()/2,
                    getHeight()/2,
                    (Float) animation.getAnimatedValue(),
                    Path.Direction.CW);

                invalidate();
            }
        });
        radiusAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDrawCircle = false;
            }
        });

        TransitionDrawable transitionDrawable = crossfadeDrawable(
                mShutterButton.getDrawable(),
                getResources().getDrawable(resId));
        mShutterButton.setImageDrawable(transitionDrawable);

        transitionDrawable.startTransition(CIRCLE_ANIM_DURATION_MS);
        radiusAnimator.start();
    }

    private void notifyAreaAdjust() {
        final int width = getWidth();
        final int height = getHeight();

        if (width == 0 || height == 0 || mAdjustPreviewAreaListener == null) {
            return;
        }
        if (width > height) {
            // Portrait
            if (!mOverLayBottomBar) {
                mAlignArea.set(getLeft(), 0, getRight(), getTop());
            } else {
                mAlignArea.set(getLeft(), 0, getRight(), getBottom());
            }
            mAdjustPreviewAreaListener.fitAndAlignBottomInRect(mAlignArea);
        } else {
            // Landscape
            if (!mOverLayBottomBar) {
                mAlignArea.set(0, getTop(), getLeft(), getBottom());
            } else {
                mAlignArea.set(0, getTop(), getRight(), getBottom());
            }
            mAdjustPreviewAreaListener.fitAndAlignRightInRect(mAlignArea);
        }
    }
}
