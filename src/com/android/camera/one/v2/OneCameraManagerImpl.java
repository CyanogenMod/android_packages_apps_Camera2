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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCameraManager;
import com.android.camera.util.Size;

/**
 * The {@link OneCameraManager} implementation on top of Camera2 API.
 */
public class OneCameraManagerImpl extends OneCameraManager {
    private static final Tag TAG = new Tag("OneCameraMgrImpl2");
    private final CameraManager mCameraManager;
    private final int mMaxMemoryMB;

    /**
     * Instantiates a new {@link OneCameraManager} for Camera2 API.
     *
     * @param cameraManager the underlying Camera2 camera manager.
     * @param maxMemoryMB maximum amount of memory opened cameras should consume
     *            during capture and processing, in megabytes.
     */
    public OneCameraManagerImpl(CameraManager cameraManager, int maxMemoryMB) {
        mCameraManager = cameraManager;
        mMaxMemoryMB = maxMemoryMB;
    }

    @Override
    public void open(Facing facing, final Size pictureSize, final OpenCallback openCallback) {
        try {
            final String cameraId = getCameraId(facing);
            Log.i(TAG, "Opening Camera ID " + cameraId);
            mCameraManager.openCamera(cameraId, new CameraDevice.StateListener() {

                @Override
                public void onDisconnected(CameraDevice device) {
                    // TODO, Re-route through the camera instance?
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    openCallback.onFailure();
                }

                @Override
                public void onOpened(CameraDevice device) {
                    try {
                        CameraCharacteristics characteristics = mCameraManager
                                .getCameraCharacteristics(device.getId());
                        // TODO: Set boolean based on whether HDR+ is enabled.
                        OneCamera oneCamera = OneCameraCreator.create(true, device,
                                characteristics, pictureSize, mMaxMemoryMB);
                        openCallback.onCameraOpened(oneCamera);
                    } catch (CameraAccessException e) {
                        Log.d(TAG, "Could not get camera characteristics");
                        openCallback.onFailure();
                    }
                }
            }, null);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not open camera. " + ex.getMessage());
            openCallback.onFailure();
        } catch (UnsupportedOperationException ex) {
            Log.e(TAG, "Could not open camera. " + ex.getMessage());
            openCallback.onFailure();
        }
    }

    @Override
    public boolean hasCameraFacing(Facing facing) {
        return getFirstCameraFacing(facing == Facing.FRONT ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK) != null;
    }

    /** Returns the ID of the first camera facing the given direction. */
    private String getCameraId(Facing facing) {
        if (facing == Facing.FRONT) {
            return getFirstFrontCameraId();
        } else {
            return getFirstBackCameraId();
        }
    }

    /** Returns the ID of the first back-facing camera. */
    private String getFirstBackCameraId() {
        Log.d(TAG, "Getting First BACK Camera");
        String cameraId = getFirstCameraFacing(CameraCharacteristics.LENS_FACING_BACK);
        if (cameraId == null) {
            throw new RuntimeException("No back-facing camera found.");
        }
        return cameraId;
    }

    /** Returns the ID of the first front-facing camera. */
    private String getFirstFrontCameraId() {
        Log.d(TAG, "Getting First FRONT Camera");
        String cameraId = getFirstCameraFacing(CameraCharacteristics.LENS_FACING_FRONT);
        if (cameraId == null) {
            throw new RuntimeException("No front-facing camera found.");
        }
        return cameraId;
    }

    /** Returns the ID of the first camera facing the given direction. */
    private String getFirstCameraFacing(int facing) {
        try {
            String[] cameraIds = mCameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = mCameraManager
                        .getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == facing) {
                    return cameraId;
                }
            }
            return null;
        } catch (CameraAccessException ex) {
            throw new RuntimeException("Unable to get camera ID", ex);
        }
    }
}
