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

import android.hardware.camera2.CaptureResult;

import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;

/**
 * Helper class to implement autofocus and 3A in camera2-based
 * {@link com.android.camera.one.OneCamera} implementations.
 */
public class AutoFocusHelper {

    private static final Log.Tag TAG = new Log.Tag("OneCameraAFHelp");

    /**
     * Convert reported camera2 AF state to OneCamera AutoFocusState.
     */
    public static OneCamera.AutoFocusState stateFromCamera2State(int state) {
        switch (state) {
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                return OneCamera.AutoFocusState.SCANNING;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                return OneCamera.AutoFocusState.STOPPED_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                return OneCamera.AutoFocusState.STOPPED_UNFOCUSED;
            default:
                return OneCamera.AutoFocusState.INACTIVE;
        }
    }

    /**
     * Convert reported camera2 AF state to OneCamera AutoFocusMode.
     */
    public static OneCamera.AutoFocusMode modeFromCamera2Mode(int mode) {
        if (mode == CaptureResult.CONTROL_AF_MODE_AUTO) {
            return OneCamera.AutoFocusMode.AUTO;
        } else {
            // CONTROL_AF_MODE_CONTINUOUS_PICTURE is the other mode used.
            return OneCamera.AutoFocusMode.CONTINUOUS_PICTURE;
        }
    }

    public static void logExtraFocusInfo(CaptureResult result) {
        // Nexus 5 has a bug where CONTROL_AF_STATE is missing sometimes.
        if (result.get(CaptureResult.CONTROL_AF_STATE) == null) {
            // throw new
            // IllegalStateException("CaptureResult missing CONTROL_AF_STATE.");
            Log.e(TAG, "\n!!!! TotalCaptureResult missing CONTROL_AF_STATE. !!!!\n ");
            return;
        }
        if (result.get(CaptureResult.LENS_STATE) == null) {
            // throw new
            // IllegalStateException("CaptureResult missing LENS_STATE.");
            Log.e(TAG, "\n!!!! TotalCaptureResult missing LENS_STATE. !!!!\n ");
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
