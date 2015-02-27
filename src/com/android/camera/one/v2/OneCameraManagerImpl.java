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

import android.annotation.TargetApi;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;

import com.android.camera.CameraActivity;
import com.android.camera.SoundPlayer;
import com.android.camera.app.AppController;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Size;

import com.google.common.base.Optional;

/**
 * The {@link OneCameraManager} implementation on top of Camera2 API.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class OneCameraManagerImpl extends OneCameraManager {
    private static final Tag TAG = new Tag("OneCameraMgrImpl2");

    private final AppController mAppController;
    private final OneCameraFeatureConfig mFeatureConfig;
    private final CameraManager mCameraManager;
    private final int mMaxMemoryMB;
    private final int mMaxImages;
    private final DisplayMetrics mDisplayMetrics;
    private final SoundPlayer mSoundPlayer;

    public static Optional<OneCameraManager> create(CameraActivity activity,
            DisplayMetrics displayMetrics, OneCameraFeatureConfig featureConfig) {
        if (!ApiHelper.HAS_CAMERA_2_API) {
            return Optional.absent();
        }
        CameraManager cameraManager;
        try {
            cameraManager = AndroidServices.instance().provideCameraManager();
        } catch (IllegalStateException ex) {
            Log.e(TAG, "camera2.CameraManager is not available.");
            return Optional.absent();
        }
        final int maxMemoryMB = activity.getServices().getMemoryManager()
                .getMaxAllowedNativeMemoryAllocation();
        final SoundPlayer soundPlayer = activity.getSoundPlayer();
        final int maxImages = GservicesHelper.
                getMaxAllowedImageReaderCount(activity.getContentResolver());
        OneCameraManager oneCameraManager = new OneCameraManagerImpl(
                activity, featureConfig, cameraManager, maxMemoryMB, maxImages, displayMetrics,
                soundPlayer);
        return Optional.of(oneCameraManager);
    }

    /**
     * Instantiates a new {@link OneCameraManager} for Camera2 API.
     *
     * @param cameraManager the underlying Camera2 camera manager.
     * @param maxMemoryMB maximum amount of memory opened cameras should consume
     *            during capture and processing, in megabytes.
     */
    public OneCameraManagerImpl(AppController appController, OneCameraFeatureConfig featureConfig,
            CameraManager cameraManager, int maxMemoryMB, int maxImages,
            DisplayMetrics displayMetrics, SoundPlayer soundPlayer) {
        mAppController = appController;
        mFeatureConfig = featureConfig;
        mCameraManager = cameraManager;
        mMaxMemoryMB = maxMemoryMB;
        mMaxImages = maxImages;
        mDisplayMetrics = displayMetrics;
        mSoundPlayer = soundPlayer;
    }

    @Override
    public void open(Facing facing, final boolean useHdr, final Size pictureSize,
            final OpenCallback openCallback,
            Handler handler, final MainThread mainThread,
            final ImageRotationCalculator imageRotationCalculator,
            final BurstFacade burstController) {
        try {
            final String cameraId = getCameraId(facing);
            Log.i(TAG, "Opening Camera ID " + cameraId);
            mCameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                // We may get multiple calls to StateCallback, but only the
                // first callback indicates the status of the camera-opening
                // operation. For example, we may receive onOpened() and later
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
                            // TODO: Set boolean based on whether HDR+ is
                            // enabled.
                            OneCamera oneCamera = OneCameraCreator.create(mAppController, useHdr,
                                    mFeatureConfig, device, characteristics, pictureSize,
                                    mMaxMemoryMB, mMaxImages, mDisplayMetrics, mSoundPlayer,
                                    mainThread, imageRotationCalculator, burstController);
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

    @Override
    public OneCameraCharacteristics getCameraCharacteristics(Facing facing)
            throws OneCameraAccessException {
        String cameraId = getCameraId(facing);
        try {
            CameraCharacteristics characteristics = mCameraManager
                    .getCameraCharacteristics(cameraId);
            return new OneCameraCharacteristicsImpl(characteristics);
        } catch (CameraAccessException ex) {
            throw new OneCameraAccessException("Unable to get camera characteristics", ex);
        }
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
