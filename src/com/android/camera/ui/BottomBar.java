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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.android.camera.CaptureLayoutHelper;
import com.android.camera.ShutterButton;
import com.android.camera.debug.Log;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

/**
 * BottomBar swaps its width and height on rotation. In addition, it also
 * changes gravity and layout orientation based on the new orientation.
 * Specifically, in landscape it aligns to the right side of its parent and lays
 * out its children vertically, whereas in portrait, it stays at the bottom of
 * the parent and has a horizontal layout orientation.
 */
public class BottomBar extends FrameLayout {

    private static final Log.Tag TAG = new Log.Tag("BottomBar");

    private static final int CIRCLE_ANIM_DURATION_MS = 300;
    private static final int DRAWABLE_MAX_LEVEL = 10000;
    private static final int MODE_CAPTURE = 0;
    private static final int MODE_INTENT = 1;
    private static final int MODE_INTENT_REVIEW = 2;
    private static final int MODE_CANCEL = 3;

    private int mMode;

    private final int mBackgroundAlphaOverlay;
    private final int mBackgroundAlphaDefault;
    private boolean mOverLayBottomBar;

    private FrameLayout mCaptureLayout;
    private FrameLayout mCancelLayout;
    private TopRightWeightedLayout mIntentReviewLayout;

    private ShutterButton mShutterButton;
    private ImageButton mCancelButton;

    private int mBackgroundColor;
    private int mBackgroundPressedColor;
    private int mBackgroundAlpha = 0xff;

    private boolean mDrawCircle;
    private final float mCircleRadius;
    private CaptureLayoutHelper mCaptureLayoutHelper = null;

    private final Drawable.ConstantState[] mShutterButtonBackgroundConstantStates;
    // a reference to the shutter background's first contained drawable
    // if it's an animated circle drawable (for video mode)
    private AnimatedCircleDrawable mAnimatedCircleDrawable;
    // a reference to the shutter background's first contained drawable
    // if it's a color drawable (for all other modes)
    private ColorDrawable mColorDrawable;

    private RectF mRect = new RectF();

    public BottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCircleRadius = getResources()
                .getDimensionPixelSize(R.dimen.video_capture_circle_diameter) / 2;
        mBackgroundAlphaOverlay = getResources()
                .getInteger(R.integer.bottom_bar_background_alpha_overlay);
        mBackgroundAlphaDefault = getResources()
                .getInteger(R.integer.bottom_bar_background_alpha);

        // preload all the drawable BGs
        TypedArray ar = context.getResources()
                .obtainTypedArray(R.array.shutter_button_backgrounds);
        int len = ar.length();
        mShutterButtonBackgroundConstantStates = new Drawable.ConstantState[len];
        for (int i = 0; i < len; i++) {
            int drawableId = ar.getResourceId(i, -1);
            mShutterButtonBackgroundConstantStates[i] =
                    context.getResources().getDrawable(drawableId).getConstantState();
        }
        ar.recycle();
    }

    private void setPaintColor(int alpha, int color) {
        if (mAnimatedCircleDrawable != null) {
            mAnimatedCircleDrawable.setColor(color);
            mAnimatedCircleDrawable.setAlpha(alpha);
        } else if (mColorDrawable != null) {
            mColorDrawable.setColor(color);
            mColorDrawable.setAlpha(alpha);
        }

        if (mIntentReviewLayout != null) {
            ColorDrawable intentBackground = (ColorDrawable) mIntentReviewLayout
                    .getBackground();
            intentBackground.setColor(color);
            intentBackground.setAlpha(alpha);
        }
    }

    private void refreshPaintColor() {
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
    }

    private void setCancelBackgroundColor(int alpha, int color) {
        LayerDrawable layerDrawable = (LayerDrawable) mCancelButton.getBackground();
        Drawable d = layerDrawable.getDrawable(0);
        if (d instanceof AnimatedCircleDrawable) {
            AnimatedCircleDrawable animatedCircleDrawable = (AnimatedCircleDrawable) d;
            animatedCircleDrawable.setColor(color);
            animatedCircleDrawable.setAlpha(alpha);
        } else if (d instanceof ColorDrawable) {
            ColorDrawable colorDrawable = (ColorDrawable) d;
            if (!ApiHelper.isLOrHigher()) {
                colorDrawable.setColor(color);
            }
            colorDrawable.setAlpha(alpha);
        }
    }

    private void setCaptureButtonUp() {
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
    }

    private void setCaptureButtonDown() {
        if (!ApiHelper.isLOrHigher()) {
            setPaintColor(mBackgroundAlpha, mBackgroundPressedColor);
        }
    }

    private void setCancelButtonUp() {
        setCancelBackgroundColor(mBackgroundAlpha, mBackgroundColor);
    }

    private void setCancelButtonDown() {
        setCancelBackgroundColor(mBackgroundAlpha, mBackgroundPressedColor);
    }

    @Override
    public void onFinishInflate() {
        mCaptureLayout =
                (FrameLayout) findViewById(R.id.bottombar_capture);
        mCancelLayout =
                (FrameLayout) findViewById(R.id.bottombar_cancel);
        mCancelLayout.setVisibility(View.GONE);

        mIntentReviewLayout =
                (TopRightWeightedLayout) findViewById(R.id.bottombar_intent_review);

        mShutterButton =
                (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getActionMasked()) {
                    setCaptureButtonDown();
                } else if (MotionEvent.ACTION_UP == event.getActionMasked() ||
                        MotionEvent.ACTION_CANCEL == event.getActionMasked()) {
                    setCaptureButtonUp();
                } else if (MotionEvent.ACTION_MOVE == event.getActionMasked()) {
                    mRect.set(0, 0, getWidth(), getHeight());
                    if (!mRect.contains(event.getX(), event.getY())) {
                        setCaptureButtonUp();
                    }
                }
                return false;
            }
        });

        mCancelButton =
                (ImageButton) findViewById(R.id.shutter_cancel_button);
        mCancelButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getActionMasked()) {
                    setCancelButtonDown();
                } else if (MotionEvent.ACTION_UP == event.getActionMasked() ||
                        MotionEvent.ACTION_CANCEL == event.getActionMasked()) {
                    setCancelButtonUp();
                } else if (MotionEvent.ACTION_MOVE == event.getActionMasked()) {
                    mRect.set(0, 0, getWidth(), getHeight());
                    if (!mRect.contains(event.getX(), event.getY())) {
                        setCancelButtonUp();
                    }
                }
                return false;
            }
        });

        extendTouchAreaToMatchParent(R.id.done_button);
    }

    private void extendTouchAreaToMatchParent(int id) {
        final View button = findViewById(id);
        final View parent = (View) button.getParent();

        parent.post(new Runnable() {
            @Override
            public void run() {
                Rect parentRect = new Rect();
                parent.getHitRect(parentRect);
                Rect buttonRect = new Rect();
                button.getHitRect(buttonRect);

                int widthDiff = parentRect.width() - buttonRect.width();
                int heightDiff = parentRect.height() - buttonRect.height();

                buttonRect.left -= widthDiff/2;
                buttonRect.right += widthDiff/2;
                buttonRect.top -= heightDiff/2;
                buttonRect.bottom += heightDiff/2;

                parent.setTouchDelegate(new TouchDelegate(buttonRect, button));
            }
        });
    }

    /**
     * Perform a transition from the bottom bar options layout to the bottom bar
     * capture layout.
     */
    public void transitionToCapture() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        mCancelLayout.setVisibility(View.GONE);
        mIntentReviewLayout.setVisibility(View.GONE);

        mMode = MODE_CAPTURE;
    }

    /**
     * Perform a transition from the bottom bar options layout to the bottom bar
     * capture layout.
     */
    public void transitionToCancel() {
        mCaptureLayout.setVisibility(View.GONE);
        mIntentReviewLayout.setVisibility(View.GONE);
        mCancelLayout.setVisibility(View.VISIBLE);

        mMode = MODE_CANCEL;
    }

    /**
     * Perform a transition to the global intent layout. The current layout
     * state of the bottom bar is irrelevant.
     */
    public void transitionToIntentCaptureLayout() {
        mIntentReviewLayout.setVisibility(View.GONE);
        mCaptureLayout.setVisibility(View.VISIBLE);
        mCancelLayout.setVisibility(View.GONE);

        mMode = MODE_INTENT;
    }

    /**
     * Perform a transition to the global intent review layout. The current
     * layout state of the bottom bar is irrelevant.
     */
    public void transitionToIntentReviewLayout() {
        mCaptureLayout.setVisibility(View.GONE);
        mIntentReviewLayout.setVisibility(View.VISIBLE);
        mCancelLayout.setVisibility(View.GONE);

        mMode = MODE_INTENT_REVIEW;
    }

    /**
     * @return whether UI is in intent review mode
     */
    public boolean isInIntentReview() {
        return mMode == MODE_INTENT_REVIEW;
    }

    private void setButtonImageLevels(int level) {
        ((ImageButton) findViewById(R.id.cancel_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.done_button)).setImageLevel(level);
        ((ImageButton) findViewById(R.id.retake_button)).setImageLevel(level);
    }

    /**
     * Configure the bottom bar to either overlay a live preview, or render off
     * the preview. If overlaying the preview, ensure contained drawables have
     * reduced opacity and that the bottom bar itself has no background to allow
     * the preview to render through. If not overlaying the preview, set
     * contained drawables to opaque and ensure that the bottom bar itself has
     * a view background, so that varying alpha (i.e. mode list transitions) are
     * based upon that background instead of an underlying preview.
     *
     * @param overlay if true, treat bottom bar as overlaying the preview
     */
    private void setOverlayBottomBar(boolean overlay) {
        mOverLayBottomBar = overlay;
        if (overlay) {
            setBackgroundAlpha(mBackgroundAlphaOverlay);
            setButtonImageLevels(1);
            // clear background on the containing bottom bar, rather than the
            // contained drawables
            super.setBackground(null);
        } else {
            setBackgroundAlpha(mBackgroundAlphaDefault);
            setButtonImageLevels(0);
            // setBackgroundColor is overridden and delegates to contained
            // drawables, call super to set the containing background color in
            // this mode.
            super.setBackgroundColor(mBackgroundColor);
        }
    }

    /**
     * Sets a capture layout helper to query layout rect from.
     */
    public void setCaptureLayoutHelper(CaptureLayoutHelper helper) {
        mCaptureLayoutHelper = helper;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int measureWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int measureHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (measureWidth == 0 || measureHeight == 0) {
            return;
        }

        if (mCaptureLayoutHelper == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            Log.e(TAG, "Capture layout helper needs to be set first.");
        } else {
            RectF bottomBarRect = mCaptureLayoutHelper.getBottomBarRect();
            super.onMeasure(MeasureSpec.makeMeasureSpec(
                    (int) bottomBarRect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) bottomBarRect.height(), MeasureSpec.EXACTLY)
                    );
            boolean shouldOverlayBottomBar = mCaptureLayoutHelper.shouldOverlayBottomBar();
            setOverlayBottomBar(shouldOverlayBottomBar);
        }
    }

    // prevent touches on bottom bar (not its children)
    // from triggering a touch event on preview area
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
        setCancelBackgroundColor(mBackgroundAlpha, mBackgroundColor);
    }

    private void setBackgroundPressedColor(int color) {
        if (ApiHelper.isLOrHigher()) {
            // not supported (setting a color on a RippleDrawable is hard =[ )
        } else {
            mBackgroundPressedColor = color;
        }
    }

    private LayerDrawable applyCircleDrawableToShutterBackground(LayerDrawable shutterBackground) {
        // the background for video has a circle_item drawable placeholder
        // that gets replaced by an AnimatedCircleDrawable for the cool
        // shrink-down-to-a-circle effect
        // all other modes need not do this replace
        Drawable d = shutterBackground.findDrawableByLayerId(R.id.circle_item);
        if (d != null) {
            Drawable animatedCircleDrawable =
                    new AnimatedCircleDrawable((int) mCircleRadius);
            shutterBackground
                    .setDrawableByLayerId(R.id.circle_item, animatedCircleDrawable);
            animatedCircleDrawable.setLevel(DRAWABLE_MAX_LEVEL);
        }

        return shutterBackground;
    }

    private LayerDrawable newDrawableFromConstantState(Drawable.ConstantState constantState) {
        return (LayerDrawable) constantState.newDrawable(getContext().getResources());
    }

    private void setupShutterBackgroundForModeIndex(int index) {
        LayerDrawable shutterBackground = applyCircleDrawableToShutterBackground(
                newDrawableFromConstantState(mShutterButtonBackgroundConstantStates[index]));
        mShutterButton.setBackground(shutterBackground);
        mCancelButton.setBackground(applyCircleDrawableToShutterBackground(
                newDrawableFromConstantState(mShutterButtonBackgroundConstantStates[index])));

        Drawable d = shutterBackground.getDrawable(0);
        mAnimatedCircleDrawable = null;
        mColorDrawable = null;
        if (d instanceof AnimatedCircleDrawable) {
            mAnimatedCircleDrawable = (AnimatedCircleDrawable) d;
        } else if (d instanceof ColorDrawable) {
            mColorDrawable = (ColorDrawable) d;
        }

        int colorId = CameraUtil.getCameraThemeColorId(index, getContext());
        int pressedColor = getContext().getResources().getColor(colorId);
        setBackgroundPressedColor(pressedColor);
        refreshPaintColor();
    }

    public void setColorsForModeIndex(int index) {
        setupShutterBackgroundForModeIndex(index);
    }

    public void setBackgroundAlpha(int alpha) {
        mBackgroundAlpha = alpha;
        setPaintColor(mBackgroundAlpha, mBackgroundColor);
        setCancelBackgroundColor(mBackgroundAlpha, mBackgroundColor);
    }

    /**
     * Sets the shutter button enabled if true, disabled if false.
     * <p>
     * Disabled means that the shutter button is not clickable and is greyed
     * out.
     */
    public void setShutterButtonEnabled(final boolean enabled) {
        mShutterButton.post(new Runnable() {
            @Override
            public void run() {
                mShutterButton.setEnabled(enabled);
                setShutterButtonImportantToA11y(enabled);
            }
        });
    }

    /**
     * Sets whether shutter button should be included in a11y announcement and
     * navigation
     */
    public void setShutterButtonImportantToA11y(boolean important) {
        if (important) {
            mShutterButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        } else {
            mShutterButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /**
     * Returns whether the capture button is enabled.
     */
    public boolean isShutterButtonEnabled() {
        return mShutterButton.isEnabled();
    }

    private TransitionDrawable crossfadeDrawable(Drawable from, Drawable to) {
        Drawable[] arrayDrawable = new Drawable[2];
        arrayDrawable[0] = from;
        arrayDrawable[1] = to;
        TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
        transitionDrawable.setCrossFadeEnabled(true);
        return transitionDrawable;
    }

    /**
     * Sets the shutter button's icon resource. By default, all drawables
     * instances loaded from the same resource share a common state; if you
     * modify the state of one instance, all the other instances will receive
     * the same modification. In order to modify properties of this icon
     * drawable without affecting other drawables, here we use a mutable
     * drawable which is guaranteed to not share states with other drawables.
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
    public void animateToVideoStop(int resId) {
        if (mOverLayBottomBar && mAnimatedCircleDrawable != null) {
            mAnimatedCircleDrawable.animateToSmallRadius();
            mDrawCircle = true;
        }

        TransitionDrawable transitionDrawable = crossfadeDrawable(
                mShutterButton.getDrawable(),
                getResources().getDrawable(resId));
        mShutterButton.setImageDrawable(transitionDrawable);
        transitionDrawable.startTransition(CIRCLE_ANIM_DURATION_MS);
    }

    /**
     * Animates bar to full width / length with video capture icon
     */
    public void animateToFullSize(int resId) {
        if (mDrawCircle && mAnimatedCircleDrawable != null) {
            mAnimatedCircleDrawable.animateToFullSize();
            mDrawCircle = false;
        }

        TransitionDrawable transitionDrawable = crossfadeDrawable(
                mShutterButton.getDrawable(),
                getResources().getDrawable(resId));
        mShutterButton.setImageDrawable(transitionDrawable);
        transitionDrawable.startTransition(CIRCLE_ANIM_DURATION_MS);
    }
}
