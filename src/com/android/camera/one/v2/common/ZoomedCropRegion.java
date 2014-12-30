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

package com.android.camera.one.v2.common;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;

import com.google.common.base.Supplier;

/**
 * Computes the current crop region based on the current zoom.
 */
public class ZoomedCropRegion implements Supplier<Rect> {
    private final Rect mSensorArrayArea;
    private final Supplier<Float> mZoom;

    public ZoomedCropRegion(Rect sensorArrayArea, Supplier<Float> zoom) {
        mSensorArrayArea = sensorArrayArea;
        mZoom = zoom;
    }

    @Override
    public Rect get() {
        float zoom = mZoom.get();
        Rect sensor = mSensorArrayArea;
        int xCenter = sensor.width() / 2;
        int yCenter = sensor.height() / 2;
        int xDelta = (int) (0.5f * sensor.width() / zoom);
        int yDelta = (int) (0.5f * sensor.height() / zoom);
        return new Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
    }
}
