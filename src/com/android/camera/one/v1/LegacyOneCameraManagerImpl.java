/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.one.v1;

import android.hardware.Camera;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.google.common.base.Optional;

import javax.annotation.Nonnull;

/**
 * The {@link com.android.camera.one.OneCameraManager} implementation on top of the Camera API 1.
 */
public class LegacyOneCameraManagerImpl implements OneCameraManager {
    private static final Tag TAG = new Tag("LegacyHM");
    private static final int NO_DEVICE = -1;
    private static final long CAMERA_ACCESS_TIMEOUT_MILLIS = 750;

    // Lazy singleton
    private static Optional<LegacyOneCameraManagerImpl> INSTANCE;

    private final CameraId mFirstBackCameraId;
    private final CameraId mFirstFrontCameraId;
    private final Camera.CameraInfo[] mCameraInfos;

    private OneCameraCharacteristics mBackCameraCharacteristics;
    private OneCameraCharacteristics mFrontCameraCharacteristics;

    public static Optional<LegacyOneCameraManagerImpl> instance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }

        int numberOfCameras;
        Camera.CameraInfo[] cameraInfos;
        try {
            numberOfCameras = Camera.getNumberOfCameras();
            cameraInfos = new Camera.CameraInfo[numberOfCameras];
            for (int i = 0; i < numberOfCameras; i++) {
                cameraInfos[i] = new Camera.CameraInfo();
                Camera.getCameraInfo(i, cameraInfos[i]);
            }
        } catch (RuntimeException ex) {
            Log.e(TAG, "Exception while creating CameraDeviceInfo", ex);
            return Optional.absent();
        }

        int firstFront = NO_DEVICE;
        int firstBack = NO_DEVICE;
        // Get the first (smallest) back and first front camera id.
        for (int i = numberOfCameras - 1; i >= 0; i--) {
            if (cameraInfos[i].facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                firstBack = i;
            } else {
                if (cameraInfos[i].facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    firstFront = i;
                }
            }
        }

        CameraId frontCameraId = firstFront >= 0 ? CameraId.fromLegacyId(firstFront) : null;
        CameraId backCameraId = firstBack >= 0 ? CameraId.fromLegacyId(firstBack) : null;

        LegacyOneCameraManagerImpl cameraManager =
              new LegacyOneCameraManagerImpl(backCameraId, frontCameraId, cameraInfos);
        INSTANCE = Optional.of(cameraManager);
        return INSTANCE;
    }

    /**
     * Instantiates a new {@link com.android.camera.one.OneCameraManager} for Camera1 API.
     */
    public LegacyOneCameraManagerImpl(
          CameraId firstBackCameraId,
          CameraId firstFrontCameraId,
          Camera.CameraInfo[] info) {
        mFirstBackCameraId = firstBackCameraId;
        mFirstFrontCameraId = firstFrontCameraId;

        mCameraInfos = info;
    }

    @Override
    public boolean hasCamera() {
        return false;
    }

    @Override
    public boolean hasCameraFacing(@Nonnull Facing facing) {
        return findFirstCameraFacing(facing) != null;
    }

    @Override
    public CameraId findFirstCamera() {
        return mFirstBackCameraId;
    }

    @Override
    public CameraId findFirstCameraFacing(@Nonnull Facing facing) {
        if (facing == Facing.BACK && mFirstBackCameraId != null) {
            return mFirstBackCameraId;
        } else if (facing == Facing.FRONT && mFirstFrontCameraId != null) {
            return mFirstFrontCameraId;
        }
        return null;
    }

    @Override
    public OneCameraCharacteristics getOneCameraCharacteristics(@Nonnull CameraId cameraId)
          throws OneCameraAccessException {
        // Returns the cached object if there exists one.
        if (cameraId.equals(mFirstBackCameraId)) {
            if (mBackCameraCharacteristics == null) {
                Log.w(TAG, "WARNING: Computing potentially long running device access!"
                      + cameraId);
                mBackCameraCharacteristics = computeCameraCharacteristics(cameraId);
            }

            Log.w(TAG, "Returning camera characteristics for back camera."
                  + cameraId);

            return mBackCameraCharacteristics;
        } else if (cameraId.equals(mFirstFrontCameraId)) {
            if (mFrontCameraCharacteristics == null) {
                Log.w(TAG, "WARNING: Computing potentially long running device access!"
                      + cameraId);
                mFrontCameraCharacteristics = computeCameraCharacteristics(cameraId);
            }

            Log.w(TAG, "Returning camera characteristics for front camera."
                  + cameraId);
            return mFrontCameraCharacteristics;
        }

        Log.e(TAG, "BackCamera: " + mFirstBackCameraId + ", " + " ==? " + (mFirstBackCameraId
              == cameraId));
        Log.e(TAG, "FrontCamera: " + mFirstFrontCameraId);
        Log.e(TAG, "No matching camera id for: " + cameraId);
        return null;
    }

    public OneCameraCharacteristics computeCameraCharacteristics(CameraId key)
          throws OneCameraAccessException  {
        OneCameraCharacteristics characteristics;
        Camera camera = null;
        try {
            camera = Camera.open(key.getLegacyValue());
            Camera.Parameters cameraParameters = camera.getParameters();
            if (cameraParameters == null) {
                Log.e(TAG, "Camera object returned null parameters!");
                throw new OneCameraAccessException("API1 Camera.getParameters() returned null");
            }
            characteristics = new OneCameraCharacteristicsImpl(
                  mCameraInfos[key.getLegacyValue()], cameraParameters);
        } finally {
            if (camera != null) {
                camera.release();
            }
        }

        return characteristics;
    }
}