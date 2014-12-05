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

/**
 * This models a value after the behavior of a spring. The value tracks the current value, a target
 * value, and the current velocity and applies both a directional force and a damping force to the
 * value on each update call.
 */
public class DampedSpring {
    public static final float DEFAULT_TIME_TO_90_PERCENT_MILLIS = 200.0f;
    public static final float DEFAULT_SPRING_STIFFNESS = 3.75f;
    public static final float EPSILON = 0.01f;

    private final float mSpringStiffness;
    private final float mTimeTo90PercentMs;

    private float mTarget = 0f;
    private float mVelocity = 0f;
    private float mValue = 0f;

    public DampedSpring() {
        this(DEFAULT_TIME_TO_90_PERCENT_MILLIS, DEFAULT_SPRING_STIFFNESS);
    }

    public DampedSpring(float timeTo90PercentMs) {
        this(timeTo90PercentMs, DEFAULT_SPRING_STIFFNESS);
    }

    public DampedSpring(float timeTo90PercentMs, float springStiffness) {
        // TODO: Assert timeTo90PercentMs >= 1ms, it might behave badly at low values.
        // TODO: Assert springStiffness > 2.0f

        mTimeTo90PercentMs = timeTo90PercentMs;
        mSpringStiffness = springStiffness;

        if (springStiffness > timeTo90PercentMs) {
            throw new IllegalArgumentException("Creating a spring value with "
                  + "excessive stiffness will oscillate endlessly.");
        }
    }

    /**
     * @return the current value.
     */
    public float getValue() {
        return mValue;
    }

    /**
     * @param value the value to set this instance's current state too.
     */
    public void setValue(float value) {
        mValue = value;
    }

    /**
     * @return the current target value.
     */
    public float getTarget() {
        return mTarget;
    }

    /**
     * Set a target value. The current value will maintain any existing velocity values and will
     * move towards the new target value. To forcibly stopAt the value use the stopAt() method.
     *
     * @param value the new value to move the current value towards.
     */
    public void setTarget(float value) {
        mTarget = value;
    }

    /**
     * Update the current value, moving it towards the actual value over the given
     * time delta (in milliseconds) since the last update. This works off of the
     * principle of a critically damped spring such that any given current value
     * will move elastically towards the target value. The current value maintains
     * and applies velocity, acceleration, and a damping force to give a continuous,
     * smooth transition towards the target value.
     *
     * @param dtMs the time since the last update, or zero.
     * @return the current value after the update occurs.
     */
    public float update(float dtMs) {
        float dt = dtMs / mTimeTo90PercentMs;
        float dts = dt * mSpringStiffness;

        // If the dts > 1, and the velocity is zero, the force will exceed the
        // distance to the target value and it will overshoot the value, causing
        // weird behavior and unintended oscillation. since a critically damped
        // spring should never overshoot the value, simply the current value to the
        // target value.
        if (dts > 1.0f || dts < 0.0f) {
            stop();
            return mValue;
        }

        float delta = (mTarget - mValue);
        float force = delta - 2.0f * mVelocity;

        mVelocity += force * dts;
        mValue += mVelocity * dts;

        // If we get close enough to the actual value, simply set the current value
        // to the current target value and stop.
        if (!isActive()) {
            stop();
        }

        return mValue;
    }

    /**
     * @return true if this instance has velocity or it is not at the target value.
     */
    public boolean isActive() {
        boolean hasVelocity = Math.abs(mVelocity) >= EPSILON;
        boolean atTarget = Math.abs(mTarget - mValue) < EPSILON;
        return hasVelocity || !atTarget;
    }

    /**
     * Stop the spring motion wherever it is currently at. Sets target to the
     * current value and sets the velocity to zero.
     */
    public void stop() {
        mTarget = mValue;
        mVelocity = 0.0f;
    }
}
