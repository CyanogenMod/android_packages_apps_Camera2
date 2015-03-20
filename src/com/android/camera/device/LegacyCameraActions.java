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

package com.android.camera.device;

import android.hardware.Camera;
import android.os.Handler;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Set of device actions for opening and closing a single Legacy camera
 * device.
 */
@ParametersAreNonnullByDefault
public class LegacyCameraActions implements SingleDeviceActions<Camera> {
    private static final Tag TAG = new Tag("Camera1Act");

    private final CameraDeviceKey mId;
    private final HandlerFactory mHandlerFactory;
    private final Logger mLogger;

    @Nullable
    private Handler mCameraHandler;

    public LegacyCameraActions(CameraDeviceKey id, HandlerFactory handlerFactory,
          Logger.Factory logFactory) {
        mId = id;
        mHandlerFactory = handlerFactory;
        mLogger = logFactory.create(TAG);
    }

    @Override
    public void executeOpen(SingleDeviceOpenListener<Camera> openListener,
          Lifetime deviceLifetime) throws UnsupportedOperationException {
        mLogger.i("executeOpen(id: " + mId.getCameraId() + ")");

        mCameraHandler = mHandlerFactory.create(deviceLifetime, "LegacyCamera Handler");
        mCameraHandler.post(new OpenCameraRunnable(openListener,
              mId.getCameraId().getLegacyValue(), mLogger));
    }

    @Override
    public void executeClose(SingleDeviceCloseListener closeListener, Camera device)
          throws UnsupportedOperationException {
        mLogger.i("executeClose(" + mId.getCameraId() + ")");

        Runnable closeCamera = new CloseCameraRunnable(closeListener,
              device,
              mId.getCameraId().getLegacyValue(),
              mLogger);

        if (mCameraHandler != null) {
            mCameraHandler.post(closeCamera);
        } else {
            mLogger.e("executeClose() was executed before the handler was created!");
            closeCamera.run();
        }
    }

    /**
     * Internal runnable that calls Camera.open and creates a new
     * camera device.
     */
    private static class OpenCameraRunnable implements Runnable {
        private final SingleDeviceOpenListener<Camera> mResults;
        private final int mCameraId;
        private final Logger mLogger;

        public OpenCameraRunnable(SingleDeviceOpenListener<Camera> results,
              int cameraId,
              Logger logger) {
            mCameraId = cameraId;
            mResults = results;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.i("Camera.open(id: " + mCameraId + ")");
                Camera device = Camera.open(mCameraId);
                mResults.onDeviceOpened(device);
            } catch (RuntimeException e) {
                mLogger.e("Opening the camera produced an exception!", e);
                mResults.onDeviceOpenException(e);
            }
        }
    }

    /**
     * Internal runnable that releases the Camera device.
     */
    private static class CloseCameraRunnable implements Runnable {
        private final SingleDeviceCloseListener mCloseListener;
        private final int mCameraId;
        private final Camera mCameraDevice;
        private final Logger mLogger;

        public CloseCameraRunnable(SingleDeviceCloseListener closeListener,
              Camera cameraDevice,
              int cameraId,
              Logger logger) {
            mCameraDevice = cameraDevice;
            mCloseListener = closeListener;
            mCameraId = cameraId;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.i("Camera.release(id: " + mCameraId + ")");
                mCameraDevice.release();
                mCloseListener.onDeviceClosed();
            } catch (Exception e) {
                mLogger.e("Closing the camera produced an exception!", e);
                mCloseListener.onDeviceClosingException(e);
            }
        }
    }
}
