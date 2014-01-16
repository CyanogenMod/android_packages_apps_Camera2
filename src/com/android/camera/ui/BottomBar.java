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
import android.graphics.RectF;
import android.util.AttributeSet;
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

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;

/**
 * BottomBar swaps its width and height on rotation. In addition, it also changes
 * gravity and layout orientation based on the new orientation. Specifically, in
 * landscape it aligns to the right side of its parent and lays out its children
 * vertically, whereas in portrait, it stays at the bottom of the parent and has
 * a horizontal layout orientation.
*/
public class BottomBar extends FrameLayout
    implements PreviewStatusListener.PreviewAreaSizeChangedListener,
               PreviewOverlay.OnPreviewTouchedListener {

    private static final String TAG = "BottomBar";

    private static final int BOTTOMBAR_OPTIONS_TIMEOUT_MS = 2000;

    private static final float CIRCLE_RADIUS = 64.0f;
    private static final int CIRCLE_ANIM_DURATION_MS = 300;

    private int mWidth;
    private int mHeight;
    private float mOffsetShorterEdge;
    private float mOffsetLongerEdge;

    private final int mOptimalHeight;
    private boolean mOverLayBottomBar;

    private ToggleImageButton mOptionsToggle;

    private TopRightMostOverlay mOptionsOverlay;
    private TopRightWeightedLayout mOptionsLayout;
    private FrameLayout mCaptureLayout;
    private TopRightWeightedLayout mIntentLayout;
    private boolean mIsCaptureIntent = false;

    /**
     * A generic Runnable for setting the options toggle
     * to the capture layout state and performing the state
     * transition.
     */
    private final Runnable mCloseOptionsRunnable =
        new Runnable() {
            @Override
            public void run() {
                if (mOptionsToggle != null) {
                    mOptionsToggle.setState(0, true);
                }
            }
        };

    private ShutterButton mShutterButton;

    private int mBackgroundColor;
    private int mBackgroundPressedColor;
    private int mBackgroundAlpha = 0xff;

    private final Paint mCirclePaint = new Paint();
    private final Path mCirclePath = new Path();
    private boolean mDrawCircle;
    private final Path mRectPath = new Path();

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOptimalHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);
    }

    private void setPaintColor(int alpha, int color, boolean isCaptureChange) {
        int computedColor = (alpha << 24) | (color & 0x00ffffff);
        mCirclePaint.setColor(computedColor);
        if (!isCaptureChange) {
            mOptionsToggle.setBackgroundColor(computedColor);
        }
        invalidate();
    }

    private void setPaintColor(int alpha, int color) {
        setPaintColor(alpha, color, false);
    }

    @Override
    public void onFinishInflate() {
        mOptionsOverlay
            = (TopRightMostOverlay) findViewById(R.id.bottombar_options_overlay);
        mOptionsLayout
            = (TopRightWeightedLayout) findViewById(R.id.bottombar_options);
        mCaptureLayout
            = (FrameLayout) findViewById(R.id.bottombar_capture);
        mIntentLayout
            = (TopRightWeightedLayout) findViewById(R.id.bottombar_intent);

        mShutterButton
            = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getActionMasked()) {
                    setPaintColor(mBackgroundAlpha, mBackgroundPressedColor, true);
                    invalidate();
                } else if (MotionEvent.ACTION_UP == event.getActionMasked()) {
                    setPaintColor(mBackgroundAlpha, mBackgroundColor, true);
                    invalidate();
                }

                return false;
            }
        });

        mOptionsOverlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    // close options immediately.
                    closeModeOptionsDelayed(BOTTOMBAR_OPTIONS_TIMEOUT_MS);
                }
                // Let touch event reach mode options or shutter.
                return false;
            }
        });
    }

    @Override
    public void onPreviewTouched(MotionEvent ev) {
        // close options immediately.
        closeModeOptionsDelayed(0);
    }

    /**
     * Schedule (or re-schedule) the options menu to be closed
     * after a number of milliseconds.  If the options menu
     * is already closed, nothing is scheduled.
     */
    private void closeModeOptionsDelayed(int milliseconds) {
        // Check that the bottom bar options are visible.
        if (mOptionsLayout.getVisibility() != View.VISIBLE) {
            return;
        }

        // Remove queued callbacks.
        removeCallbacks(mCloseOptionsRunnable);

        // Close the bottom bar options view in n milliseconds.
        postDelayed(mCloseOptionsRunnable, milliseconds);
    }

    /**
     * Initializes the bottom bar toggle for switching between
     * capture and the bottom bar options.
     */
    public void setupToggle(boolean isCaptureIntent) {
        mIsCaptureIntent = isCaptureIntent;

        // Of type ToggleImageButton because ToggleButton
        // has a non-removable spacing for text on the right-hand side.
        mOptionsToggle = (ToggleImageButton) findViewById(R.id.bottombar_options_toggle);
        mOptionsToggle.setState(0, false);
        mOptionsToggle.setOnStateChangeListener(new ToggleImageButton.OnStateChangeListener() {
            @Override
            public void stateChanged(View view, boolean toOptions) {
                if (toOptions) {
                    if (mIsCaptureIntent) {
                        hideIntentLayout();
                    }
                    transitionToOptions();
                } else {
                    if (mIsCaptureIntent) {
                        transitionToIntentLayout();
                    } else {
                        transitionToCapture();
                    }
                }
            }
        });
        mOptionsOverlay.setReferenceViewParent(mOptionsLayout);
    }

    /**
     * Hide the intent layout.  This is necessary for switching between
     * the intent capture layout and the bottom bar options.
     */
    private void hideIntentLayout() {
        mIntentLayout.setVisibility(View.INVISIBLE);
    }

    /**
     * Perform a transition from the bottom bar options layout to the
     * bottom bar capture layout.
     */
    private void transitionToCapture() {
        mOptionsLayout.setVisibility(View.INVISIBLE);
        mCaptureLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Perform a transition from the bottom bar capture layout to the
     * bottom bar options layout.
     */
    private void transitionToOptions() {
        mCaptureLayout.setVisibility(View.INVISIBLE);
        mOptionsLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Perform a transition to the global intent layout.  The current
     * layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentLayout() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        mOptionsLayout.setVisibility(View.INVISIBLE);
        mOptionsOverlay.setVisibility(View.VISIBLE);
        mIntentLayout.setVisibility(View.VISIBLE);

        View button;
        button = mIntentLayout.findViewById(R.id.done_button);
        button.setVisibility(View.INVISIBLE);
        button = mIntentLayout.findViewById(R.id.retake_button);
        button.setVisibility(View.INVISIBLE);
    }

    /**
     * Perform a transition to the global intent review layout.
     * The current layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentReviewLayout() {
        mCaptureLayout.setVisibility(View.INVISIBLE);
        mOptionsLayout.setVisibility(View.INVISIBLE);
        mOptionsOverlay.setVisibility(View.INVISIBLE);

        View button;
        button = mIntentLayout.findViewById(R.id.done_button);
        button.setVisibility(View.VISIBLE);
        button = mIntentLayout.findViewById(R.id.retake_button);
        button.setVisibility(View.VISIBLE);
        mIntentLayout.setVisibility(View.VISIBLE);
    }

    private void setButtonImageLevels(int level) {
        ((MultiToggleImageButton) findViewById(R.id.flash_toggle_button)).setImageLevel(level);
        ((MultiToggleImageButton) findViewById(R.id.camera_toggle_button)).setImageLevel(level);
        ((MultiToggleImageButton) findViewById(R.id.hdr_plus_toggle_button)).setImageLevel(level);
        ((MultiToggleImageButton) findViewById(R.id.refocus_toggle_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.cancel_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.done_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.retake_button)).setImageLevel(level);
        mOptionsToggle.setImageLevel(level);
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
            if (previewAspectRatio >= screenAspectRatio) {
                mOverLayBottomBar = true;
                setBackgroundAlpha(128);
                setButtonImageLevels(1);
            } else {
                mOverLayBottomBar = false;
                setBackgroundAlpha(255);
                setButtonImageLevels(0);
            }
        }

        // Calculates the width and height needed for the bar.
        int barWidth, barHeight;
        if (mWidth > mHeight) {
            // TODO: The bottom bar should not need to care about the
            // the type of its parent.  Handle this in the parent layout.
            ((LinearLayout.LayoutParams) getLayoutParams()).gravity = Gravity.RIGHT;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mOptimalHeight;
                barHeight = mHeight;
            } else {
                barWidth = (int) (mWidth - mOffsetLongerEdge);
                barHeight = mHeight;
            }
        } else {
            ((LinearLayout.LayoutParams) getLayoutParams()).gravity = Gravity.BOTTOM;
            if ((mOffsetLongerEdge == 0 && mOffsetShorterEdge == 0) || mOverLayBottomBar) {
                barWidth = mWidth;
                barHeight = mOptimalHeight;
            } else {
                barWidth = mWidth;
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

            int shortEdge = mOptionsToggle.getWidth();
            if (mOptionsToggle.getHeight() < shortEdge) {
                shortEdge = mOptionsToggle.getHeight();
            }
            mRectPath.reset();
            if (width > height) {
                mRectPath.addRect(
                    0.0f,
                    0.0f,
                    (float) width - shortEdge,
                    (float) height,
                    Path.Direction.CW);
            } else {
                mRectPath.addRect(
                    0.0f,
                    (float) shortEdge,
                    (float) width,
                    (float) height,
                    Path.Direction.CW);
            }
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
        if (mDrawCircle) {
            canvas.drawPath(mCirclePath, mCirclePaint);
        } else {
            canvas.drawPath(mRectPath, mCirclePaint);
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
     * Set the shutter button's icon resource
     */
    public void setShutterButtonIcon(int resId) {
        mShutterButton.setImageResource(resId);
    }

    /**
     * Animates bar to a single stop button
     */
    public void animateToCircle(int resId) {
        final ValueAnimator radiusAnimator = ValueAnimator.ofFloat(
                                                 (float) diagonalLength()/2,
                                                 CIRCLE_RADIUS);
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

        View optionsOverlay = findViewById(R.id.bottombar_options_overlay);
        optionsOverlay.setVisibility(View.INVISIBLE);

        mDrawCircle = true;
        transitionDrawable.startTransition(CIRCLE_ANIM_DURATION_MS);
        radiusAnimator.start();
    }

    /**
     * Animates bar to full width / length with video capture icon
     */
    public void animateToFullSize(int resId) {
        final ValueAnimator radiusAnimator = ValueAnimator.ofFloat(
                                                 CIRCLE_RADIUS,
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
                View optionsOverlay = findViewById(R.id.bottombar_options_overlay);
                optionsOverlay.setVisibility(View.VISIBLE);
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
