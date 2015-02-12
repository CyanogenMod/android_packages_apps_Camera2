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

import android.graphics.Paint;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.ui.motion.DampedSpring;
import com.android.camera.ui.motion.DynamicAnimation;
import com.android.camera.ui.motion.Invalidator;
import com.android.camera.ui.motion.UnitCurve;
import com.android.camera.ui.motion.UnitCurves;

/**
 * Base class for defining the focus ring states, enter and exit durations, and
 * positioning logic.
 */
abstract class FocusRingRenderer implements DynamicAnimation {
    private static final Tag TAG = new Tag("FocusRingRenderer");

    /**
     * Primary focus states that a focus ring renderer can go through.
     */
    protected static enum FocusState {
        STATE_INACTIVE,
        STATE_ENTER,
        STATE_ACTIVE,
        STATE_FADE_OUT,
        STATE_HARD_STOP,
    }

    protected final Invalidator mInvalidator;
    protected final Paint mRingPaint;
    protected final DampedSpring mRingRadius;
    protected final UnitCurve mEnterOpacityCurve;
    protected final UnitCurve mExitOpacityCurve;
    protected final UnitCurve mHardExitOpacityCurve;
    protected final float mEnterDurationMillis;
    protected final float mExitDurationMillis;
    protected final float mHardExitDurationMillis = 64;

    private int mCenterX;
    private int mCenterY;
    protected long mEnterStartMillis = 0;
    protected long mExitStartMillis = 0;
    protected long mHardExitStartMillis = 0;

    protected FocusState mFocusState = FocusState.STATE_INACTIVE;

    /**
     * A dynamic, configurable, self contained ring render that will inform
     * via invalidation if it should continue to be receive updates
     * and re-draws.
     *
     * @param invalidator the object to inform if it requires more draw calls.
     * @param ringPaint the paint to use to draw the ring.
     * @param enterDurationMillis the fade in duration in milliseconds
     * @param exitDurationMillis the fade out duration in milliseconds.
     */
    FocusRingRenderer(Invalidator invalidator, Paint ringPaint, float enterDurationMillis,
          float exitDurationMillis) {
        mInvalidator = invalidator;
        mRingPaint = ringPaint;
        mEnterDurationMillis = enterDurationMillis;
        mExitDurationMillis = exitDurationMillis;

        mEnterOpacityCurve = UnitCurves.FAST_OUT_SLOW_IN;
        mExitOpacityCurve = UnitCurves.FAST_OUT_LINEAR_IN;
        mHardExitOpacityCurve = UnitCurves.FAST_OUT_LINEAR_IN;

        mRingRadius = new DampedSpring();
    }

    /**
     * Set the centerX position for this focus ring renderer.
     *
     * @param value the x position
     */
    public void setCenterX(int value) {
        mCenterX = value;
    }

    protected int getCenterX() {
        return mCenterX;
    }

    /**
     * Set the centerY position for this focus ring renderer.
     *
     * @param value the y position
     */
    public void setCenterY(int value) {
        mCenterY = value;
    }

    protected int getCenterY() {
        return mCenterY;
    }

    /**
     * Set the physical radius of this ring.
     *
     * @param value the radius of the ring.
     */
    public void setRadius(long tMs, float value) {
        if (mFocusState == FocusState.STATE_FADE_OUT
              && Math.abs(mRingRadius.getTarget() - value) > 0.1) {
            Log.v(TAG, "FOCUS STATE ENTER VIA setRadius(" + tMs + ", " + value + ")");
            mFocusState = FocusState.STATE_ENTER;
            mEnterStartMillis = computeEnterStartTimeMillis(tMs, mEnterDurationMillis);
        }

        mRingRadius.setTarget(value);
    }

    /**
     * returns true if the renderer is not in an inactive state.
     */
    @Override
    public boolean isActive() {
        return mFocusState != FocusState.STATE_INACTIVE;
    }

    /**
     * returns true if the renderer is in an exit state.
     */
    public boolean isExiting() {
        return mFocusState == FocusState.STATE_FADE_OUT
              || mFocusState == FocusState.STATE_HARD_STOP;
    }

    /**
     * returns true if the renderer is in an enter state.
     */
    public boolean isEntering() {
        return mFocusState == FocusState.STATE_ENTER;
    }

    /**
     * Initialize and start the animation with the given start and
     * target radius.
     */
    public void start(long startMs, float initialRadius, float targetRadius) {
        if (mFocusState != FocusState.STATE_INACTIVE) {
            Log.w(TAG, "start() called while the ring was still focusing!");
        }
        mRingRadius.stop();
        mRingRadius.setValue(initialRadius);
        mRingRadius.setTarget(targetRadius);
        mEnterStartMillis = startMs;

        mFocusState = FocusState.STATE_ENTER;
        mInvalidator.invalidate();
    }

    /**
     * Put the animation in the exit state regardless of the current
     * dynamic transition. If the animation is currently in an enter state
     * this will compute an exit start time such that the exit time lines
     * up with the enter time at the current transition value.
     *
     * @param t the current animation time.
     */
    public void exit(long t) {
        if (mRingRadius.isActive()) {
            mRingRadius.stop();
        }

        mFocusState = FocusState.STATE_FADE_OUT;
        mExitStartMillis = computeExitStartTimeMs(t, mExitDurationMillis);
    }

    /**
     * Put the animation in the hard stop state regardless of the current
     * dynamic transition. If the animation is currently in an enter state
     * this will compute an exit start time such that the exit time lines
     * up with the enter time at the current transition value.
     *
     * @param tMillis the current animation time in milliseconds.
     */
    public void stop(long tMillis) {
        if (mRingRadius.isActive()) {
            mRingRadius.stop();
        }

        mFocusState = FocusState.STATE_HARD_STOP;
        mHardExitStartMillis = computeExitStartTimeMs(tMillis, mHardExitDurationMillis);
    }

    private long computeExitStartTimeMs(long tMillis, float exitDuration) {
        if (mEnterStartMillis + mEnterDurationMillis <= tMillis) {
            return tMillis;
        }

        // Compute the current progress on the enter animation.
        float enterT = (tMillis - mEnterStartMillis) / mEnterDurationMillis;

        // Find a time on the exit curve such that it will produce the same value.
        float exitT = UnitCurves.mapEnterCurveToExitCurveAtT(mEnterOpacityCurve, mExitOpacityCurve,
              enterT);

        // Compute the a start time before tMs such that the ratio of time completed
        // equals the computed exit curve animation position.
        return tMillis - (long) (exitT * exitDuration);
    }

    private long computeEnterStartTimeMillis(long tMillis, float enterDuration) {
        if (mExitStartMillis + mExitDurationMillis <= tMillis) {
            return tMillis;
        }

        // Compute the current progress on the enter animation.
        float exitT = (tMillis - mExitStartMillis) / mExitDurationMillis;

        // Find a time on the exit curve such that it will produce the same value.
        float enterT = UnitCurves.mapEnterCurveToExitCurveAtT(mExitOpacityCurve, mEnterOpacityCurve,
              exitT);

        // Compute the a start time before tMs such that the ratio of time completed
        // equals the computed exit curve animation position.
        return tMillis - (long) (enterT * enterDuration);
    }
}
