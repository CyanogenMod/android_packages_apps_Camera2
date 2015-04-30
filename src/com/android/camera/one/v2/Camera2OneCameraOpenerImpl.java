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
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.device.ActiveCameraDeviceTracker;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraOpener;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.google.common.base.Optional;

/**
 * The {@link com.android.camera.one.OneCameraOpener} implementation on top of Camera2 API.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2OneCameraOpenerImpl implements OneCameraOpener {
    private static final Tag TAG = new Tag("OneCamera1Opnr");

    private final Context mContext;
    private final OneCameraFeatureConfig mFeatureConfig;
    private final ActiveCameraDeviceTracker mActiveCameraDeviceTracker;
    private final CameraManager mCameraManager;
    private final DisplayMetrics mDisplayMetrics;

    public static Optional<OneCameraOpener> create(
            OneCameraFeatureConfig featureConfig,
            Context context,
            ActiveCameraDeviceTracker activeCameraDeviceTracker,
            DisplayMetrics displayMetrics) {
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
        OneCameraOpener oneCameraOpener = new Camera2OneCameraOpenerImpl(
                featureConfig,
                context,
                cameraManager,
                activeCameraDeviceTracker,
                displayMetrics);
        return Optional.of(oneCameraOpener);
    }

    /**
     * Instantiates a new {@link com.android.camera.one.OneCameraOpener} for Camera2 API.
     *
     * @param cameraManager the underlying Camera2 camera manager.
     */
    public Camera2OneCameraOpenerImpl(OneCameraFeatureConfig featureConfig,
            Context context,
            CameraManager cameraManager,
            ActiveCameraDeviceTracker activeCameraDeviceTracker,
            DisplayMetrics displayMetrics) {
        mFeatureConfig = featureConfig;
        mContext = context;
        mCameraManager = cameraManager;
        mActiveCameraDeviceTracker = activeCameraDeviceTracker;
        mDisplayMetrics = displayMetrics;
    }

    @Override
    public void open(
            final CameraId cameraKey,
            final OneCameraCaptureSetting captureSetting,
            final Handler handler,
            final MainThread mainThread,
            final ImageRotationCalculator imageRotationCalculator,
            final BurstFacade burstController,
            final SoundPlayer soundPlayer,
            final OpenCallback openCallback,
            final FatalErrorHandler fatalErrorHandler) {
        try {
            Log.i(TAG, "Opening Camera: " + cameraKey);

            mActiveCameraDeviceTracker.onCameraOpening(cameraKey);

            mCameraManager.openCamera(cameraKey.getValue(), new CameraDevice.StateCallback() {
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
                    } else {
                        // Ensures we handle the case where an error occurs
                        // after the camera has been opened.
                        fatalErrorHandler.onGenericCameraAccessFailure();
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
                            OneCamera oneCamera = OneCameraCreator.create(
                                    device,
                                    characteristics,
                                    mFeatureConfig,
                                    captureSetting,
                                    mDisplayMetrics,
                                    mContext,
                                    mainThread,
                                    imageRotationCalculator,
                                    burstController,
                                    soundPlayer, fatalErrorHandler);

                            if (oneCamera != null) {
                                openCallback.onCameraOpened(oneCamera);
                            } else {
                                Log.d(TAG, "Could not construct a OneCamera object!");
                                openCallback.onFailure();
                            }
                        } catch (CameraAccessException e) {
                            Log.d(TAG, "Could not get camera characteristics", e);
                            openCallback.onFailure();
                        } catch (OneCameraAccessException e) {
                            Log.d(TAG, "Could not create OneCamera", e);
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
        } catch (SecurityException ex) {
            fatalErrorHandler.onCameraDisabledFailure();
        }
    }
}
