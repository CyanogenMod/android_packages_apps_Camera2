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

package com.android.camera.ui;

/**
 * Touch coordinate.
 */
public class TouchCoordinate {
    private float x;
    private float y;
    private float maxX;
    private float maxY;

    /**
     * Constructor.
     * @param x X value for the touch, with 0 typically the lowest value.
     * @param y Y value for the touch, with 0 typically the lowest value.
     * @param maxX Highest X value possible for any touch.
     * @param maxY Highest Y value possible for any touch.
     */
    public TouchCoordinate(float x, float y, float maxX, float maxY) {
        this.x = x;
        this.y = y;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getMaxX() {
        return this.maxX;
    }

    public float getMaxY() {
        return this.maxY;
    }
}
