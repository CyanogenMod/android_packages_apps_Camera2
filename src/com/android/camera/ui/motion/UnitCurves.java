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
 * Predefined material curves and animations.
 */
public class UnitCurves {
    public static final UnitCurve FAST_OUT_SLOW_IN = new UnitBezier(0.4f, 0.0f, 0.2f, 1.0f);
    public static final UnitCurve LINEAR_OUT_SLOW_IN = new UnitBezier(0.0f, 0.0f, 0.2f, 1.0f);
    public static final UnitCurve FAST_OUT_LINEAR_IN = new UnitBezier(0.4f, 0.0f, 1.0f, 1.0f);
    public static final UnitCurve LINEAR = new UnitBezier(0.0f, 0.0f, 1.0f, 1.0f);

    /**
     * Given two curves (from and to) and a time along the from curve, compute
     * the time at t, and find a t along the 'toCurve' that will produce the
     * same output. This is useful when interpolating between two different curves
     * when the animation is not at the beginning or end.
     *
     * @param enterCurve the curve to compute the value from
     * @param exitCurve the curve to find a time t on that matches output of
     *                  enterCurve at T.
     * @param t the time at which to compute the value (0..1)
     * @return the time along the exitCurve.
     */
    public static float mapEnterCurveToExitCurveAtT(UnitCurve enterCurve, UnitCurve exitCurve,
          float t) {
        return exitCurve.tAt(1 - enterCurve.valueAt(t));
    }
}
