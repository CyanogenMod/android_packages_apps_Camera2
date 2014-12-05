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
 * Various static helper functions for interpolating between values.
 */
public class InterpolateUtils {

    private InterpolateUtils() {
    }

    /**
     * Linear interpolation from v0 to v1 as t goes from 0...1
     *
     * @param v0 the value at t=0
     * @param v1 the value at t=1
     * @param t value in the range of 0 to 1.
     * @return the value between v0 and v1 as a ratio between 0 and 1 defined by t.
     */
    public static float lerp(float v0, float v1, float t) {
        return v0 + t * (v1 - v0);
    }

    /**
     * Project a value that is within the in(Min/Max) number space into the to(Min/Max) number
     * space.
     *
     * @param v value to scale into the 'to' number space.
     * @param vMin min value of the values number space.
     * @param vMax max value of the values number space.
     * @param pMin min value of the projection number space.
     * @param pMax max value of the projection number space.
     * @return the ratio of the value in the source number space as a value in the to(Min/Max)
     * number space.
     */
    public static float scale(float v, float vMin, float vMax, float pMin, float pMax) {
        return (pMax - pMin) * (v - vMin) / (vMax - vMin) + pMin;
    }

    /**
     * Value between 0 and 1 as a ratio between tBegin over tDuration
     * with no upper bound.
     */
    public static float unitRatio(long t, long tBegin, float tDuration) {
        if (t <= tBegin) {
            return 0.0f;
        }

        return (t - tBegin) / tDuration;
    }
}
