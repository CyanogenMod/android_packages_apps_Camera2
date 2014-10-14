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

package com.android.camera.one;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.android.camera.CameraActivity;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;

/**
 * The camera manager is responsible for instantiating {@link OneCamera}
 * instances.
 */
public abstract class OneCameraManager {
    private static Tag TAG = new Tag("OneCameraManager");

    /**
     * Attempts to open the camera facing the given direction with the given
     * capture size.
     *
     * Exactly one call will always be made to a single method in the provided
     * {@link OpenCallback}.
     *
     * @param facing which camera to open. The first camera found in the given
     *            direction will be opened.
     * @param enableHdr if an HDR feature exists, open a camera that supports it
     * @param captureSize the capture size. This must be one of the supported
     *            sizes.
     * @param callback this listener is called when the camera was opened or
     *            when it failed to open.
     * @param handler the handler on which callback methods are invoked.
     */
    public abstract void open(Facing facing, boolean enableHdr, Size captureSize,
            OpenCallback callback, Handler handler);

    /**
     * Returns whether the device has a camera facing the given direction.
     */
    public abstract boolean hasCameraFacing(Facing facing);

    /**
     * Creates a camera manager that is based on Camera2 API, if available, or
     * otherwise uses the portability layer API.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera.
     */
    public static OneCameraManager get(CameraActivity activity) throws OneCameraException {
        return create(activity);
    }

    /**
     * Creates a new camera manager that is based on Camera2 API, if available.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera.
     */
    private static OneCameraManager create(CameraActivity activity) throws OneCameraException {
        DisplayMetrics displayMetrics = getDisplayMetrics(activity);
        CameraManager cameraManager = null;

        try {
            cameraManager = ApiHelper.HAS_CAMERA_2_API ? (CameraManager) activity
                    .getSystemService(Context.CAMERA_SERVICE) : null;
        } catch (IllegalStateException ex) {
            cameraManager = null;
            Log.e(TAG, "Could not get camera service v2", ex);
        }
        int maxMemoryMB = activity.getServices().getMemoryManager()
                .getMaxAllowedNativeMemoryAllocation();
        return new com.android.camera.one.v2.OneCameraManagerImpl(
                activity.getApplicationContext(), cameraManager, maxMemoryMB,
                displayMetrics, activity.getSoundPlayer());
    }

    private static DisplayMetrics getDisplayMetrics(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            displayMetrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return displayMetrics;
    }
}
