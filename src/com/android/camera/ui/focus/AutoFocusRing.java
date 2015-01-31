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

import android.graphics.Canvas;
import android.graphics.Paint;

import com.android.camera.debug.Log.Tag;
import com.android.camera.ui.motion.InterpolateUtils;
import com.android.camera.ui.motion.Invalidator;

/**
 * Passive focus ring animation renderer.
 */
class AutoFocusRing extends FocusRingRenderer {
    private static final Tag TAG = new Tag("AutoFocusRing");

    /**
     * The auto focus ring encapsulates the animation logic for visualizing
     * a focus event when triggered by the camera subsystem.
     *
     * @param invalidator the object to invalidate while running.
     * @param ringPaint the paint to draw the ring with.
     * @param enterDurationMillis the fade in time in milliseconds.
     * @param exitDurationMillis the fade out time in milliseconds.
     */
    public AutoFocusRing(Invalidator invalidator, Paint ringPaint, float enterDurationMillis,
          float exitDurationMillis) {
        super(invalidator, ringPaint, enterDurationMillis, exitDurationMillis);
    }

    @Override
    public void draw(long t, long dt, Canvas canvas) {
        float ringRadius = mRingRadius.update(dt);
        processStates(t);

        if (!isActive()) {
            return;
        }

        mInvalidator.invalidate();
        int ringAlpha = 255;

        if (mFocusState == FocusState.STATE_ENTER) {
            float rFade = InterpolateUtils.unitRatio(t, mEnterStartMillis, mEnterDurationMillis);
            ringAlpha = (int) InterpolateUtils
                  .lerp(0, 255, mEnterOpacityCurve.valueAt(rFade));
        } else if (mFocusState == FocusState.STATE_FADE_OUT) {
            float rFade = InterpolateUtils.unitRatio(t, mExitStartMillis, mExitDurationMillis);
            ringAlpha = (int) InterpolateUtils
                  .lerp(255, 0, mExitOpacityCurve.valueAt(rFade));
        } else if (mFocusState == FocusState.STATE_HARD_STOP) {
            float rFade = InterpolateUtils
                  .unitRatio(t, mHardExitStartMillis, mHardExitDurationMillis);
            ringAlpha = (int) InterpolateUtils
                  .lerp(255, 0, mExitOpacityCurve.valueAt(rFade));
        } else if (mFocusState == FocusState.STATE_INACTIVE) {
            ringAlpha = 0;
        }

        mRingPaint.setAlpha(ringAlpha);
        canvas.drawCircle(getCenterX(), getCenterY(), ringRadius, mRingPaint);
    }

    private void processStates(long t) {
        if (mFocusState == FocusState.STATE_INACTIVE) {
            return;
        }

        if (mFocusState == FocusState.STATE_ENTER && t > mEnterStartMillis + mEnterDurationMillis) {
            mFocusState = FocusState.STATE_ACTIVE;
        }

        if (mFocusState == FocusState.STATE_ACTIVE && !mRingRadius.isActive()) {
            mFocusState = FocusState.STATE_FADE_OUT;
            mExitStartMillis = t;
        }

        if (mFocusState == FocusState.STATE_FADE_OUT && t > mExitStartMillis + mExitDurationMillis) {
            mFocusState = FocusState.STATE_INACTIVE;
        }

        if (mFocusState == FocusState.STATE_HARD_STOP
              && t > mHardExitStartMillis + mHardExitDurationMillis) {
            mFocusState = FocusState.STATE_INACTIVE;
        }
    }
}
