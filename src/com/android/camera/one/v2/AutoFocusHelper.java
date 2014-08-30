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

package com.android.camera.one.v2;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;

import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.Settings3A;

/**
 * Helper class to implement auto focus and 3A in camera2-based
 * {@link com.android.camera.one.OneCamera} implementations.
 */
public class AutoFocusHelper {
    private static final Log.Tag TAG = new Log.Tag("OneCameraAFHelp");

    private static final int METERING_WEIGHT = Settings3A.getCamera2MeteringWeight();

    /**
     * Convert reported camera2 AF state to OneCamera AutoFocusState.
     */
    public static OneCamera.AutoFocusState stateFromCamera2State(int state) {
        switch (state) {
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                return OneCamera.AutoFocusState.ACTIVE_SCAN;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                return OneCamera.AutoFocusState.PASSIVE_SCAN;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                return OneCamera.AutoFocusState.PASSIVE_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                return OneCamera.AutoFocusState.ACTIVE_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                return OneCamera.AutoFocusState.PASSIVE_UNFOCUSED;
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                return OneCamera.AutoFocusState.ACTIVE_UNFOCUSED;
            default:
                return OneCamera.AutoFocusState.INACTIVE;
        }
    }

    /**
     * Complain if CONTROL_AF_STATE is not present in result.
     * Could indicate bug in API implementation.
     */
    public static boolean checkControlAfState(CaptureResult result) {
        boolean missing = result.get(CaptureResult.CONTROL_AF_STATE) == null;
        if (missing) {
            // throw new IllegalStateException("CaptureResult missing CONTROL_AF_STATE.");
            Log.e(TAG, "\n!!!! TotalCaptureResult missing CONTROL_AF_STATE. !!!!\n ");
        }
        return !missing;
    }

    /**
     * Complain if LENS_STATE is not present in result.
     * Could indicate bug in API implementation.
     */
    public static boolean checkLensState(CaptureResult result) {
        boolean missing = result.get(CaptureResult.LENS_STATE) == null;
        if (missing) {
            // throw new IllegalStateException("CaptureResult missing LENS_STATE.");
            Log.e(TAG, "\n!!!! TotalCaptureResult missing LENS_STATE. !!!!\n ");
        }
        return !missing;
    }


    public static void logExtraFocusInfo(CaptureResult result) {
        if(!checkControlAfState(result) || !checkLensState(result)) {
            return;
        }

        Object tag = result.getRequest().getTag();

        Log.v(TAG, String.format("af_state:%-17s  lens_foc_dist:%.3f  lens_state:%-10s  %s",
                controlAFStateToString(result.get(CaptureResult.CONTROL_AF_STATE)),
                result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                lensStateToString(result.get(CaptureResult.LENS_STATE)),
                (tag == null) ? "" : "[" + tag +"]"
        ));
    }

    /** Compute 3A regions for a sensor-referenced touch coordinate. */
    private static MeteringRectangle[] regionsForSensorCoord(int xc, int yc, float width,
                                                             Rect cropRegion) {
        float minCropEdge = (float) Math.min(cropRegion.width(), cropRegion.height());
        int delta = (int) (0.5 * width * minCropEdge);
        Rect region = new Rect(xc - delta, yc - delta, xc + delta, yc + delta);
        // Make sure region is inside the sensor area.
        if (!region.intersect(cropRegion)) {
            region = cropRegion;
        }
        return new MeteringRectangle[]{new MeteringRectangle(region, METERING_WEIGHT)};
    }

    /**
     * Return AF region(s) for a sensor-referenced touch coordinate.
     *
     * @return AF region(s).
     */
    public static MeteringRectangle[] afRegionsForSensorCoord(int xc, int yc, Rect cropRegion) {
        return regionsForSensorCoord(xc, yc, Settings3A.getAutoFocusRegionWidth(), cropRegion);
    }

    /**
     * Return AE region(s) for a sensor-referenced touch coordinate.
     *
     * @return AE region(s).
     */
    public static MeteringRectangle[] aeRegionsForSensorCoord(int xc, int yc, Rect cropRegion) {
        return regionsForSensorCoord(xc, yc, Settings3A.getMeteringRegionWidth(), cropRegion);
    }

    /**
     * Calculates sensor crop region for a zoom level (zoom >= 1.0).
     *
     * @return Crop region.
     */
    public static Rect cropRegionForZoom(CameraCharacteristics characteristics, float zoom) {
        Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int xCenter = sensor.width() / 2;
        int yCenter = sensor.height() / 2;
        int xDelta = (int) (0.5f * sensor.width() / zoom);
        int yDelta = (int) (0.5f * sensor.height() / zoom);
        return new Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
    }

    /**
     * Utility function: converts CaptureResult.CONTROL_AF_STATE to String.
     */
    private static String controlAFStateToString(int controlAFState) {
        switch (controlAFState) {
            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                return "inactive";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                return "passive_scan";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                return "passive_focused";
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                return "active_scan";
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                return "focus_locked";
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                return "not_focus_locked";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                return "passive_unfocused";
            default:
                return "unknown";
        }
    }

    /**
     * Utility function: converts CaptureResult.LENS_STATE to String.
     */
    private static String lensStateToString(int lensState) {
        switch (lensState) {
            case CaptureResult.LENS_STATE_MOVING:
                return "moving";
            case CaptureResult.LENS_STATE_STATIONARY:
                return "stationary";
            default:
                return "unknown";
        }
    }

}
