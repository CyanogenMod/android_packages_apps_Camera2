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

import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.Pollable;
import com.android.camera.one.v2.AutoFocusHelper;

/**
 * Enables polling for the current AF metering rectangles based on the current
 * metering parameters and crop region.
 */
public class PollableAFRegion implements Pollable<MeteringRectangle[]> {
    private final Pollable<MeteringParameters> mMeteringParameters;
    private final Pollable<Rect> mCropRegion;
    private final OrientationManager.DeviceOrientation mSensorOrientation;

    public PollableAFRegion(Pollable<MeteringParameters> meteringParameters,
                            Pollable<Rect> cropRegion,
                            OrientationManager.DeviceOrientation sensorOrientation) {
        mMeteringParameters = meteringParameters;
        mCropRegion = cropRegion;
        mSensorOrientation = sensorOrientation;
    }

    @Override
    public MeteringRectangle[] get(MeteringRectangle[] defaultValue) {
        try {
            return get();
        } catch (NoValueSetException e) {
            return defaultValue;
        }
    }

    @Override
    public MeteringRectangle[] get() throws NoValueSetException {
        MeteringParameters parameters = mMeteringParameters.get();
        if (parameters.getMode() == MeteringParameters.Mode.POINT) {
            Rect cropRegion = mCropRegion.get();
            PointF point = parameters.getAEPoint();
            return AutoFocusHelper.afRegionsForNormalizedCoord(point.x, point.y, cropRegion,
                    mSensorOrientation.getDegrees());
        } else {
            return AutoFocusHelper.getZeroWeightRegion();
        }
    }
}
