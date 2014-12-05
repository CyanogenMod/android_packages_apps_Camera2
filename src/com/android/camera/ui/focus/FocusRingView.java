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

package com.android.camera.ui.focus;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.debug.Log.Tag;
import com.android.camera.ui.motion.AnimationClock.SystemTimeClock;
import com.android.camera.ui.motion.DynamicAnimator;
import com.android.camera.ui.motion.InterpolateUtils;
import com.android.camera.ui.motion.Invalidator;
import com.android.camera2.R;

/**
 * Custom view for running the focus ring animations.
 */
public class FocusRingView extends View implements Invalidator, FocusRing {
    private static final Tag TAG = new Tag("FocusRingView");

    // A Diopter of 0.0f ish is infinity.
    // A Diopter of about 15f or so is focused "as close as possible"
    // Diopter max is computed from device testing, TODO: Replace with LENS_FOCUS_RANGE
    // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html
    // TODO: Refactor diopter to radius computation outside this class.
    private static final float DIOPTER_MIN = 0.0f;
    private static final float DIOPTER_MAX = 15.0f;
    private static final float DIOPTER_MEDIAN = (DIOPTER_MAX - DIOPTER_MIN) / 2.0f;

    private static final float FADE_IN_DURATION_MILLIS = 1000f;
    private static final float FADE_OUT_DURATION_MILLIS = 250f;

    private final AutoFocusRing autoFocusRing;
    private final ManualFocusRing manualFocusRing;
    private final DynamicAnimator animator;

    private final int mFocusCircleMinSize;
    private final int mFocusCircleMaxSize;

    private FocusRingRenderer currentFocusAnimation;
    private boolean isFirstDraw = true;
    private float mLastDiopter = DIOPTER_MEDIAN;

    public FocusRingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = getResources();
        Paint mPaint = makePaint(res, R.color.focus_color);

        mFocusCircleMinSize = res.getDimensionPixelSize(R.dimen.focus_circle_min_size);
        mFocusCircleMaxSize = res.getDimensionPixelSize(R.dimen.focus_circle_max_size);

        animator = new DynamicAnimator(this, new SystemTimeClock());

        autoFocusRing = new AutoFocusRing(animator, mPaint,
              FADE_IN_DURATION_MILLIS,
              FADE_OUT_DURATION_MILLIS);
        manualFocusRing = new ManualFocusRing(animator, mPaint,
              FADE_OUT_DURATION_MILLIS);

        animator.animations.add(autoFocusRing);
        animator.animations.add(manualFocusRing);
    }

    @Override
    public boolean isPassiveFocusRunning() {
        return autoFocusRing.isActive();
    }

    @Override
    public boolean isActiveFocusRunning() {
        return manualFocusRing.isActive();
    }

    @Override
    public void startPassiveFocus() {
        animator.invalidate();
        long tMs = animator.getTimeMillis();

        if (manualFocusRing.isActive() && !manualFocusRing.isExiting()) {
            manualFocusRing.stop(tMs);
        }

        float lastRadius = radiusForDiopter(mLastDiopter);
        autoFocusRing.start(tMs, lastRadius, lastRadius);
        currentFocusAnimation = autoFocusRing;
    }

    @Override
    public void startActiveFocus() {
        animator.invalidate();
        long tMs = animator.getTimeMillis();

        if (autoFocusRing.isActive() && !autoFocusRing.isExiting()) {
            autoFocusRing.stop(tMs);
        }

        manualFocusRing.start(tMs, 0.0f, radiusForDiopter(mLastDiopter));
        currentFocusAnimation = manualFocusRing;
    }

    @Override
    public void stopFocusAnimations() {
        long tMs = animator.getTimeMillis();
        if (manualFocusRing.isActive() && !manualFocusRing.isExiting()
              && !manualFocusRing.isEntering()) {
            manualFocusRing.exit(tMs);
        }

        if (autoFocusRing.isActive() && !autoFocusRing.isExiting()) {
            autoFocusRing.exit(tMs);
        }
    }

    @Override
    public void setFocusLocation(float viewX, float viewY) {
        autoFocusRing.setCenterX((int) viewX);
        autoFocusRing.setCenterY((int) viewY);
        manualFocusRing.setCenterX((int) viewX);
        manualFocusRing.setCenterY((int) viewY);
    }

    @Override
    public void setFocusDiopter(float diopter) {
        long tMs = animator.getTimeMillis();
        // Some devices return zero for invalid or "unknown" diopter values.
        if (currentFocusAnimation != null && diopter > 0.1f) {
            currentFocusAnimation.setRadius(tMs, radiusForDiopter(diopter));
            mLastDiopter = diopter;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isFirstDraw) {
            isFirstDraw = false;
            centerAutofocusRing();
        }

        animator.draw(canvas);
    }

    private void centerAutofocusRing() {
        float screenW = this.getWidth();
        float screenH = this.getHeight();

        autoFocusRing.setCenterX((int) (screenW / 2f));
        autoFocusRing.setCenterY((int) (screenH / 2f));
    }

    private float radiusForDiopter(float diopter) {
        return InterpolateUtils.scale(diopter, DIOPTER_MIN, DIOPTER_MAX, mFocusCircleMinSize,
              mFocusCircleMaxSize);
    }

    private Paint makePaint(Resources res, int color) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(res.getColor(color));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(res.getDimension(R.dimen.focus_circle_stroke));
        return paint;
    }
}
