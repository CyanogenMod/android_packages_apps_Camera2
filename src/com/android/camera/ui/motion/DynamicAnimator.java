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

package com.android.camera.ui.motion;

import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Designed to handle the lifecycle of a view that needs a continuous update /
 * redraw cycle that does not have a defined start / end time.
 *
 * Fixed length animations should NOT use this class.
 */
public class DynamicAnimator implements Invalidator {

    public final List<DynamicAnimation> animations = new ArrayList<>();

    private final Invalidator mInvalidator;
    private final AnimationClock mClock;

    private boolean mUpdateRequested = false;
    private boolean mIsDrawing = false;
    private long mLastDrawTimeMillis = 0;
    private long mDrawTimeMillis = 0;

    public DynamicAnimator(Invalidator invalidator, AnimationClock clock) {
        mInvalidator = invalidator;
        mClock = clock;
    }

    public void draw(Canvas canvas) {
        mIsDrawing = true;
        mUpdateRequested = false;

        mDrawTimeMillis = mClock.getTimeMillis();

        if (mLastDrawTimeMillis <= 0) {
            mLastDrawTimeMillis = mDrawTimeMillis; // On the initial draw, dt is zero.
        }

        long dt = mDrawTimeMillis - mLastDrawTimeMillis;
        mLastDrawTimeMillis = mDrawTimeMillis;

        // Run the animation
        for (DynamicAnimation renderer : animations) {
            if (renderer.isActive()) {
                renderer.draw(mDrawTimeMillis, dt, canvas);
            }
        }

        // If either the update or the draw methods requested new frames, then
        // invalidate the view which should give us another frame to work with.
        // Otherwise, stopAt the last update time.
        if (mUpdateRequested) {
            mInvalidator.invalidate();
        } else {
            mLastDrawTimeMillis = -1;
        }

        mIsDrawing = false;
    }

    /**
     * If a scheduleNewFrame request comes in outside of the animation loop,
     * and we didn't schedule a frame after the previous loop (or it's the
     * first time we've used this instance), invalidate the view and set the
     * last update time to the current time. Theoretically, a few milliseconds
     * have elapsed before the view gets updated.
     */
    @Override
    public void invalidate() {
        if (!mIsDrawing && !mUpdateRequested) {
            mInvalidator.invalidate();
            mLastDrawTimeMillis = mClock.getTimeMillis();
        }

        mUpdateRequested = true;
    }

    /**
     * This will return the "best guess" for the most current animation frame
     * time.  If the loop is currently drawing, then it will return the time the
     * draw began, and if an update is currently requested it will return the
     * time that the update was requested at, and if neither of these are true
     * it will return the current system clock time.
     *
     * This method will not trigger a new update.
     */
    public long getTimeMillis() {
        if (mIsDrawing) {
            return mDrawTimeMillis;
        }

        if (mUpdateRequested) {
            return mLastDrawTimeMillis;
        }

        return mClock.getTimeMillis();
    }
}
