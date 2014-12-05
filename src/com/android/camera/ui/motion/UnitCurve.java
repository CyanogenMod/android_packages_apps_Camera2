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
 * Simple functions that produce values along a curve for any given input and can compute input
 * times for a given output value.
 */
public interface UnitCurve {

    /**
     * Produce a unit value of this curve at time t. The function should always return a valid
     * return value for any valid t input.
     *
     * @param t ratio of time passed from (0..1)
     * @return the unit value at t.
     */
    float valueAt(float t);

    /**
     * If possible, find a value for t such that valueAt(t) == value or best guess.
     *
     * @param value to match to the output of valueAt(t)
     * @return t where valueAt(t) == value or throw.
     */
    float tAt(float value);
}
