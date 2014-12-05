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
 * This represents is a precomputed cubic bezier curve starting at (0,0) and
 * going to (1,1) with two configurable control points. Once the instance is
 * created, the control points cannot be modified.
 *
 * Generally, this will be used for computing timing curves for with control
 * points where an x value will be provide from 0.0 - 1.0, and the y value will
 * be solved for where y is used as the timing value in some linear
 * interpolation of a value.
 */
public class UnitBezier implements UnitCurve {

    private static final float EPSILON = 1e-6f;

    private final DerivableFloatFn mXFn;
    private final DerivableFloatFn mYFn;

    /**
     * Build and pre-compute a unit bezier. This assumes a starting point of
     * (0, 0) and end point of (1.0, 1.0).
     *
     * @param c0x control point x value for p0
     * @param c0y control point y value for p0
     * @param c1x control point x value for p1
     * @param c1y control point y value for p1
     */
    public UnitBezier(float c0x, float c0y, float c1x, float c1y) {
        mXFn = new CubicBezierFn(c0x, c1x);
        mYFn = new CubicBezierFn(c0y, c1y);
    }

    /**
     * Given a unit bezier curve find the height of the curve at t (which is
     * internally represented as the xAxis).
     *
     * @param t the x position between 0 and 1 to solve for y.
     * @return the closest approximate height of the curve at x.
     */
    @Override
    public float valueAt(float t) {
        return mYFn.value(solve(t, mXFn));
    }

    /**
     * Given a unit bezier curve find a value along the x axis such that
     * valueAt(result) produces the input value.
     *
     * @param value the y position between 0 and 1 to solve for x
     * @return the closest approximate input that will produce value when provided
     * to the valueAt function.
     */
    @Override
    public float tAt(float value) {
        return mXFn.value(solve(value, mYFn));
    }

    private float solve(float target, DerivableFloatFn fn) {
        // For a linear fn, t = value. This makes value a good starting guess.
        float input = target;

        // Newton's method (Faster than bisection)
        for (int i = 0; i < 8; i++) {
            float value = fn.value(input) - target;
            if (Math.abs(value) < EPSILON) {
                return input;
            }
            float derivative = fn.derivative(input);
            if (Math.abs(derivative) < EPSILON) {
                break;
            }
            input = input - value / derivative;
        }

        // Fallback on bi-section
        float min = 0.0f;
        float max = 1.0f;
        input = target;

        if (input < min) {
            return min;
        }
        if (input > max) {
            return max;
        }

        while (min < max) {
            float value = fn.value(input);
            if (Math.abs(value - target) < EPSILON) {
                return input;
            }

            if (target > value) {
                min = input;
            } else {
                max = input;
            }

            input = (max - min) * .5f + min;
        }

        // Give up, return the closest match we got too.
        return input;
    }

    private interface DerivableFloatFn {
        float value(float x);
        float derivative(float x);
    }

    /**
     * Precomputed constants for a given set of control points along a given
     * cubic bezier axis.
     */
    private static class CubicBezierFn implements DerivableFloatFn {
        private final float c;
        private final float a;
        private final float b;

        /**
         * Build and pre-compute a single axis for a unit bezier. This assumes p0
         * is 0 and p1 is 1.
         *
         * @param c0 start control point.
         * @param c1 end control point.
         */
        public CubicBezierFn(float c0, float c1) {
            c = 3.0f * c0;
            b = 3.0f * (c1 - c0) - c;
            a = 1.0f - c - b;
        }

        public float value(float x) {
            return ((a * x + b) * x + c) * x;
        }
        public float derivative(float x) {
            return (3.0f * a * x + 2.0f * b) * x + c;
        }
    }
}
