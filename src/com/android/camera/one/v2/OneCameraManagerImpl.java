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

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.DisplayMetrics;

import com.android.camera.SoundPlayer;
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

    private final Context mContext;
    private final CameraManager mCameraManager;
    private final int mMaxMemoryMB;
    private final DisplayMetrics mDisplayMetrics;
    private final SoundPlayer mSoundPlayer;

    /**
     * Instantiates a new {@link OneCameraManager} for Camera2 API.
     *
     * @param cameraManager the underlying Camera2 camera manager.
     * @param maxMemoryMB maximum amount of memory opened cameras should consume
     *            during capture and processing, in megabytes.
     */
    public OneCameraManagerImpl(Context context, CameraManager cameraManager, int maxMemoryMB,
            DisplayMetrics displayMetrics, SoundPlayer soundPlayer) {
        mContext = context;
        mCameraManager = cameraManager;
        mMaxMemoryMB = maxMemoryMB;
        mDisplayMetrics = displayMetrics;
        mSoundPlayer = soundPlayer;
    }

    @Override
    public void open(Facing facing, final boolean useHdr, final Size pictureSize,
            final OpenCallback openCallback, Handler handler) {
        try {
            final String cameraId = getCameraId(facing);
            Log.i(TAG, "Opening Camera ID " + cameraId);
            mCameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                // We may get multiple calls to StateCallback, but only the
                // first callback indicates the status of the camera-opening
                // operation.  For example, we may receive onOpened() and later
                // onClosed(), but only the first should be relayed to
                // openCallback.
                private boolean isFirstCallback = true;

                @Override
                public void onDisconnected(CameraDevice device) {
                    if (isFirstCallback) {
                        isFirstCallback = false;
                        // If the camera is disconnected before it is opened
                        // then we must call close.
                        device.close();
                        openCallback.onCameraClosed();
                    }
                }

                @Override
                public void onClosed(CameraDevice device) {
                    if (isFirstCallback) {
                        isFirstCallback = false;
                        openCallback.onCameraClosed();
                    }
                }

                @Override
                public void onError(CameraDevice device, int error) {
                    if (isFirstCallback) {
                        isFirstCallback = false;
                        device.close();
                        openCallback.onFailure();
                    }
                }

                @Override
                public void onOpened(CameraDevice device) {
                    if (isFirstCallback) {
                        isFirstCallback = false;
                        try {
                            CameraCharacteristics characteristics = mCameraManager
                                    .getCameraCharacteristics(device.getId());
                            // TODO: Set boolean based on whether HDR+ is enabled.
                            OneCamera oneCamera = OneCameraCreator.create(mContext, useHdr, device,
                                    characteristics, pictureSize, mMaxMemoryMB, mDisplayMetrics,
                                    mSoundPlayer);
                            openCallback.onCameraOpened(oneCamera);
                        } catch (CameraAccessException e) {
                            Log.d(TAG, "Could not get camera characteristics");
                            openCallback.onFailure();
                        }
                    }
                }
            }, handler);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not open camera. " + ex.getMessage());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    openCallback.onFailure();
                }
            });
        } catch (UnsupportedOperationException ex) {
            Log.e(TAG, "Could not open camera. " + ex.getMessage());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    openCallback.onFailure();
                }
            });
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
    public String getFirstBackCameraId() {
        Log.d(TAG, "Getting First BACK Camera");
        String cameraId = getFirstCameraFacing(CameraCharacteristics.LENS_FACING_BACK);
        if (cameraId == null) {
            throw new RuntimeException("No back-facing camera found.");
        }
        return cameraId;
    }

    /** Returns the ID of the first front-facing camera. */
    public String getFirstFrontCameraId() {
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
