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
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.android.camera.ShutterButton;
import com.android.camera.MultiToggleImageButton;
import com.android.camera.ToggleImageButton;
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
    implements PreviewStatusListener.PreviewAreaSizeChangedListener {

    private static final String TAG = "BottomBar";

    private static final int CIRCLE_ANIM_DURATION_MS = 300;

    private static final int MODE_CAPTURE = 0;
    private static final int MODE_INTENT = 1;
    private static final int MODE_INTENT_REVIEW = 2;
    private int mMode;

    private int mWidth;
    private int mHeight;
    private float mOffsetShorterEdge;
    private float mOffsetLongerEdge;

    private final int mOptimalHeight;
    private boolean mOverLayBottomBar;

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

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOptimalHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);
        mCircleRadius = getResources()
            .getDimensionPixelSize(R.dimen.video_capture_circle_diameter) / 2;
        mCirclePaint.setAntiAlias(true);
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
                } else if (MotionEvent.ACTION_UP == event.getActionMasked()
                           || MotionEvent.ACTION_CANCEL == event.getActionMasked()) {
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

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (mWidth == 0 || mHeight == 0) {
            return;
        }

        if (mOffsetShorterEdge != 0 && mOffsetLongerEdge != 0) {
            float previewAspectRatio =
                    mOffsetLongerEdge / mOffsetShorterEdge;
            if (previewAspectRatio < 1.0) {
                previewAspectRatio = 1.0f/previewAspectRatio;
            }
            float screenAspectRatio = (float) mWidth / (float) mHeight;
            if (screenAspectRatio < 1.0) {
                screenAspectRatio = 1.0f/screenAspectRatio;
            }
            // TODO: background alphas should be set by xml references to colors.
            if (previewAspectRatio >= screenAspectRatio) {
                mOverLayBottomBar = true;
                setBackgroundAlpha(153);
                setButtonImageLevels(1);
            } else {
                mOverLayBottomBar = false;
                setBackgroundAlpha(255);
                setButtonImageLevels(0);
            }
        }

        // Calculates the width and height needed for the bar.
        int barWidth, barHeight;
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) getLayoutParams();
        if (mWidth > mHeight) {
            // TODO: The bottom bar should not need to care about the
            // the type of its parent.  Handle this in the parent layout.
            layoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            barHeight = (int) mOffsetShorterEdge;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mOptimalHeight;
            } else {
                barWidth = (int) (mWidth - mOffsetLongerEdge);
            }
        } else {
            layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            barWidth = (int) mOffsetShorterEdge;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barHeight = mOptimalHeight;
            } else {
                barHeight = (int) (mHeight - mOffsetLongerEdge);
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;

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
                (float) width,
                (float) height);
            mRectPath.reset();
            mRectPath.addRect(mRect, Path.Direction.CW);
        }
    }

    private void adjustBottomBar(float scaledTextureWidth,
                                 float scaledTextureHeight) {
        setOffset(scaledTextureWidth, scaledTextureHeight);
    }

    @Override
    public void onPreviewAreaSizeChanged(RectF previewArea) {
        adjustBottomBar(previewArea.width(), previewArea.height());
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
        if (mOffsetLongerEdge != offsetLongerEdge || mOffsetShorterEdge != offsetShorterEdge) {
            mOffsetLongerEdge = offsetLongerEdge;
            mOffsetShorterEdge = offsetShorterEdge;
            requestLayout();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
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
}
