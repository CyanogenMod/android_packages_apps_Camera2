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

package com.android.camera.one.v2.autofocus;

import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.one.Settings3A;
import com.google.common.base.Preconditions;

final class PointMeteringParameters implements MeteringParameters {
    private final PointF mAFPoint;
    private final PointF mAEPoint;
    private final int mSensorOrientation;
    private final Settings3A mSettings3A;

    private PointMeteringParameters(PointF afPoint, PointF aePoint, int sensorOrientation,
            Settings3A settings3A) {
        mAFPoint = afPoint;
        mAEPoint = aePoint;
        mSensorOrientation = sensorOrientation;
        mSettings3A = settings3A;
    }

    /**
     * Constructs new MeteringParameters.
     *
     * @param afPoint The center of the desired AF metering region, in
     *            normalized portrait coordinates such that (0, 0) is top left
     *            and (1, 1) is bottom right.
     * @param aePoint The center of the desired AE metering region, in
     *            normalized portrait coordinates such that (0, 0) is top left
     *            and (1, 1) is bottom right.
     * @param sensorOrientation sensor orientation as defined by
     *            CameraCharacteristics
     *            .get(CameraCharacteristics.SENSOR_ORIENTATION).
     * @param settings3A 3A settings.
     */
    public static PointMeteringParameters createForNormalizedCoordinates(
            PointF afPoint,
            PointF aePoint,
            int sensorOrientation,
            Settings3A settings3A) {
        Preconditions.checkArgument(sensorOrientation % 90 == 0, "sensorOrientation must be a " +
                "multiple of 90");
        Preconditions.checkArgument(sensorOrientation >= 0, "sensorOrientation must not be " +
                "negative");
        sensorOrientation %= 360;

        return new PointMeteringParameters(afPoint, aePoint, sensorOrientation,
                settings3A);
    }

    /**
     * @param cropRegion The current crop region, see
     *            {@link CaptureRequest#SCALER_CROP_REGION}.
     */
    @Override
    public MeteringRectangle[] getAERegions(Rect cropRegion) {
        return new MeteringRectangle[] {
                regionForNormalizedCoord(mAEPoint, cropRegion)
        };
    }

    /**
     * @param cropRegion The current crop region, see
     *            {@link CaptureRequest#SCALER_CROP_REGION}.
     */
    @Override
    public MeteringRectangle[] getAFRegions(Rect cropRegion) {
        return new MeteringRectangle[] {
                regionForNormalizedCoord(mAFPoint, cropRegion)
        };
    }

    private MeteringRectangle regionForNormalizedCoord(PointF point,
            Rect cropRegion) {
        // Compute half side length in pixels.
        int minCropEdge = Math.min(cropRegion.width(), cropRegion.height());
        int halfSideLength = (int) (0.5f * mSettings3A.getMeteringRegionFraction() * minCropEdge);

        // Compute the output MeteringRectangle in sensor space.
        // point is normalized to the screen.
        // Crop region itself is specified in sensor coordinates (see
        // CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE).

        // Normalized coordinates, now rotated into sensor space.
        PointF nsc = transformPortraitCoordinatesToSensorCoordinates(point);

        int xCenterSensor = (int) (cropRegion.left + nsc.x * cropRegion.width());
        int yCenterSensor = (int) (cropRegion.top + nsc.y * cropRegion.height());

        Rect meteringRegion = new Rect(xCenterSensor - halfSideLength,
                yCenterSensor - halfSideLength,
                xCenterSensor + halfSideLength,
                yCenterSensor + halfSideLength);

        // Clamp meteringRegion to cropRegion.
        meteringRegion.left = clamp(meteringRegion.left, cropRegion.left,
                cropRegion.right);
        meteringRegion.top = clamp(meteringRegion.top, cropRegion.top, cropRegion.bottom);
        meteringRegion.right = clamp(meteringRegion.right, cropRegion.left,
                cropRegion.right);
        meteringRegion.bottom = clamp(meteringRegion.bottom, cropRegion.top,
                cropRegion.bottom);

        return new MeteringRectangle(meteringRegion, mSettings3A.getMeteringWeight());
    }

    /**
     * Given (nx, ny) \in [0, 1]^2, in the display's portrait coordinate system,
     * returns normalized sensor coordinates \in [0, 1]^2 depending on how the
     * sensor's orientation \in {0, 90, 180, 270}.
     */
    private PointF transformPortraitCoordinatesToSensorCoordinates(
            PointF point) {
        switch (mSensorOrientation) {
            case 0:
                return point;
            case 90:
                return new PointF(point.y, 1.0f - point.x);
            case 180:
                return new PointF(1.0f - point.x, 1.0f - point.y);
            case 270:
                return new PointF(1.0f - point.y, point.x);
            default:
                // Impossible exception.
                throw new IllegalArgumentException("Unsupported Sensor Orientation");
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
