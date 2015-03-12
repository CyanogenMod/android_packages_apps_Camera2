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

import android.annotation.TargetApi;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build.VERSION_CODES;
import android.os.Handler;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;

import java.util.concurrent.Executor;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Set of device actions for opening and closing a single Camera2 device.
 */
@TargetApi(VERSION_CODES.LOLLIPOP)
@ParametersAreNonnullByDefault
public class Camera2Actions implements SingleDeviceActions<CameraDevice> {
    private static final Tag TAG = new Tag("Camera2Act");

    private final CameraDeviceKey mId;
    private final CameraManager mCameraManager;
    private final HandlerFactory mHandlerFactory;
    private final Executor mBackgroundExecutor;
    private final Logger mLogger;

    public Camera2Actions(CameraDeviceKey id,
          CameraManager cameraManager,
          Executor backgroundExecutor,
          HandlerFactory handlerFactory,
          Logger.Factory logFactory) {
        mId = id;
        mCameraManager = cameraManager;
        mBackgroundExecutor = backgroundExecutor;
        mHandlerFactory = handlerFactory;
        mLogger = logFactory.create(TAG);
        mLogger.d("Created Camera2Request");
    }

    @Override
    public void executeOpen(SingleDeviceOpenListener<CameraDevice> openListener,
          Lifetime deviceLifetime) throws UnsupportedOperationException {
        mLogger.i("executeOpen(id: " + mId.getCameraId() + ")");
        mBackgroundExecutor.execute(new OpenCameraRunnable(mCameraManager,
              mId.getCameraId().getValue(),
              // TODO THIS IS BAD. If there are multiple requests to open,
              // we don't want to add the handler to the lifetime until after
              // the camera device is opened or the camera could be opened with
              // an invalid thread.
              mHandlerFactory.create(deviceLifetime, "Camera2 Lifetime"),
              openListener, mLogger));
    }

    @Override
    public void executeClose(SingleDeviceCloseListener closeListener, CameraDevice device)
          throws UnsupportedOperationException {
        mLogger.i("executeClose(" + device.getId() + ")");
        mBackgroundExecutor.execute(new CloseCameraRunnable(device, closeListener, mLogger));
    }

    /**
     * Internal runnable that executes a CameraManager openCamera call.
     */
    private static class OpenCameraRunnable implements Runnable {
        private final SingleDeviceOpenListener<CameraDevice> mOpenListener;
        private final String mCameraId;
        private final Handler mHandler;
        private final CameraManager mCameraManager;
        private final Logger mLogger;

        public OpenCameraRunnable(CameraManager cameraManager, String cameraId,
              Handler handler, SingleDeviceOpenListener<CameraDevice> openListener,
              Logger logger) {
            mCameraManager = cameraManager;
            mCameraId = cameraId;
            mHandler = handler;
            mOpenListener = openListener;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.i("mCameraManager.openCamera(id: " + mCameraId + ")");
                mCameraManager.openCamera(mCameraId, new OpenCameraStateCallback(mOpenListener,
                            mLogger), mHandler);
            } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
                mLogger.e("There was a problem opening camera " + mCameraId, e);
                mOpenListener.onDeviceOpenException(e);
            }
        }
    }

    /**
     * Internal runnable that executes a close on a cameraDevice.
     */
    private static class CloseCameraRunnable implements Runnable {
        private final SingleDeviceCloseListener mCloseListener;
        private final CameraDevice mCameraDevice;
        private final Logger mLogger;

        public CloseCameraRunnable(CameraDevice cameraDevice,
              SingleDeviceCloseListener closeListener,
              Logger logger) {
            mCameraDevice = cameraDevice;
            mCloseListener = closeListener;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.i("mCameraDevice.close(id: " + mCameraDevice.getId() + ")");
                mCameraDevice.close();
                mCloseListener.onDeviceClosed();
            } catch (Exception e) {
                mLogger.e("Closing the camera produced an exception!", e);
                mCloseListener.onDeviceClosingException(e);
            }
        }
    }

    /**
     * Internal callback that provides a camera device to a future.
     */
    private static class OpenCameraStateCallback extends CameraDevice.StateCallback {
        private final SingleDeviceOpenListener<CameraDevice> mOpenListener;
        private final Logger mLogger;
        private boolean mHasBeenCalled = false;

        public OpenCameraStateCallback(SingleDeviceOpenListener<CameraDevice> openListener,
              Logger logger) {
            mOpenListener = openListener;
            mLogger = logger;
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            if (!called()) {
                mLogger.i("onOpened(id: " + cameraDevice.getId() + ")");
                mOpenListener.onDeviceOpened(cameraDevice);
            }
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            if (!called()) {
                mLogger.w("onClosed(id: " + cameraDevice.getId() + ")");
                mOpenListener.onDeviceOpenException(cameraDevice);
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            if (!called()) {
                mLogger.w("onDisconnected(id: " + cameraDevice.getId() + ")");
                mOpenListener.onDeviceOpenException(cameraDevice);
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int errorId) {
            if (!called()) {
                mLogger.e("onError(id: " + cameraDevice.getId()
                      + ", errorId: " + errorId + ")");
                mOpenListener.onDeviceOpenException(new CameraOpenException(errorId));
            }
        }

        private boolean called() {
            boolean result = mHasBeenCalled;
            if (!mHasBeenCalled) {
                mHasBeenCalled = true;
            } else {
                mLogger.v("Callback was re-executed.");
            }

            return result;
        }
    }
}
