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

import android.content.Context;
import android.os.Handler;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.SafeCloseable;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgent.CameraOpenCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;
import com.android.ex.camera2.portability.CameraAgentFactory;
import com.android.ex.camera2.portability.CameraAgentFactory.CameraApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Set of device actions for opening and closing a single portability
 * layer camera device.
 */
public class PortabilityCameraActions implements SingleDeviceActions<CameraProxy> {
    private static final Tag TAG = new Tag("Camera2PReq");

    private final CameraDeviceKey<Integer> mId;
    private final HandlerFactory mHandlerFactory;
    private final ExecutorService mBackgroundRunner;
    private final Context mContext;
    private final CameraApi mApiVersion;
    private final Logger mLogger;

    public PortabilityCameraActions(
          CameraDeviceKey<Integer> id,
          Context context,
          CameraApi apiVersion,
          ExecutorService backgroundRunner,
          HandlerFactory handlerFactory,
          Logger.Factory logFactory) {
        mId = id;
        mContext = context;
        mApiVersion = apiVersion;
        mBackgroundRunner = backgroundRunner;
        mHandlerFactory = handlerFactory;
        mLogger = logFactory.create(TAG);

        mLogger.d("Created Camera2Request");
    }

    @Override
    public void executeOpen(SingleDeviceOpenListener<CameraProxy> openListener,
          Lifetime deviceLifetime) {
        mLogger.d("executeOpen()");
        CameraAgent agent = CameraAgentFactory.getAndroidCameraAgent(mContext, mApiVersion);
        deviceLifetime.add(new CameraAgentRecycler(mApiVersion, mLogger));

        mBackgroundRunner.execute(new OpenCameraRunnable(agent, mId.getCameraId(),
              mHandlerFactory.create(deviceLifetime, "Camera2 Lifetime"),
              openListener, mLogger));
    }

    @Override
    public void executeClose(SingleDeviceCloseListener closeListener, CameraProxy device) {
        mLogger.d("executeClose()");
        mBackgroundRunner.execute(new CloseCameraRunnable(device, device.getAgent(),
              closeListener, mLogger));
    }

    /**
     * Recycles camera agents and ensures that recycle is only called
     * once per instance.
     */
    private static class CameraAgentRecycler implements SafeCloseable {
        private final CameraApi mCameraApi;
        private final Logger mLogger;
        private final AtomicBoolean mIsClosed;

        public CameraAgentRecycler(CameraApi cameraApi, Logger logger) {
            mCameraApi = cameraApi;
            mLogger = logger;
            mIsClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            if (!mIsClosed.getAndSet(true)) {
                mLogger.d("Recycling CameraAgentFactory for CameraApi: " + mCameraApi);
                CameraAgentFactory.recycle(mCameraApi);
            }
        }
    }

    /**
     * Internal runnable that executes a CameraManager openCamera call.
     */
    private static class OpenCameraRunnable implements Runnable {
        private final SingleDeviceOpenListener<CameraProxy> mOpenListener;
        private final int mCameraId;
        private final Handler mHandler;
        private final CameraAgent mCameraAgent;
        private final Logger mLogger;

        public OpenCameraRunnable(CameraAgent cameraAgent, int cameraId,
              Handler handler, SingleDeviceOpenListener<CameraProxy> openListener,
              Logger logger) {
            mCameraAgent = cameraAgent;
            mCameraId = cameraId;
            mHandler = handler;
            mOpenListener = openListener;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.d("mCameraAgent.openCamera()");
                mCameraAgent.openCamera(mHandler, mCameraId,
                      new OpenCameraStateCallback(mOpenListener, mLogger));
            } catch (SecurityException e) {
                mOpenListener.onDeviceOpenException(e);
            }
        }
    }

    /**
     * Internal runnable that executes a close on a cameraDevice.
     */
    private static class CloseCameraRunnable implements Runnable {
        private final SingleDeviceCloseListener mCloseListener;
        private final CameraProxy mCameraDevice;
        private final CameraAgent mCameraAgent;
        private final Logger mLogger;

        public CloseCameraRunnable(CameraProxy cameraDevice, CameraAgent cameraAgent,
              SingleDeviceCloseListener closeListener, Logger logger) {
            mCameraDevice = cameraDevice;
            mCameraAgent = cameraAgent;
            mCloseListener = closeListener;
            mLogger = logger;
        }

        @Override
        public void run() {
            try {
                mLogger.d("mCameraAgent.closeCamera");
                mCameraAgent.closeCamera(mCameraDevice, true /* synchronous */);
                mCloseListener.onDeviceClosed();
            } catch (Exception e) {
                mCloseListener.onDeviceClosingException(e);
            }
        }
    }

    /**
     * Internal callback that provides a camera device to a future.
     */
    private static class OpenCameraStateCallback implements CameraOpenCallback {
        private final SingleDeviceOpenListener<CameraProxy> mOpenListener;
        private final Logger mLogger;
        private boolean mHasBeenCalled = false;

        public OpenCameraStateCallback(SingleDeviceOpenListener<CameraProxy> openListener,
              Logger logger) {
            mOpenListener = openListener;
            mLogger = logger;
        }

        private boolean called() {
            boolean result = mHasBeenCalled;
            if (!mHasBeenCalled) {
                mHasBeenCalled = true;
            } else {
                mLogger.d("Callback was re-executed.");
            }

            return result;
        }

        @Override
        public void onCameraOpened(CameraProxy camera) {
            if (!called()) {
                mLogger.d("onOpened(cameraDevice: " + camera.getCameraId() + ")");
                mOpenListener.onDeviceOpened(camera);
            }
        }

        @Override
        public void onCameraDisabled(int cameraId) {
            if (!called()) {
                mLogger.d("onCameraDisabled(cameraId: " + cameraId + ")");
                mOpenListener.onDeviceOpenException(new CameraOpenException(-1));
            }
        }

        @Override
        public void onDeviceOpenFailure(int cameraId, String info) {
            if (!called()) {
                mLogger.d("onDeviceOpenFailure(cameraId: " + cameraId
                      + ", info: " + info + ")");
                mOpenListener.onDeviceOpenException(new CameraOpenException(-1));
            }
        }

        @Override
        public void onDeviceOpenedAlready(int cameraId, String info) {
            if (!called()) {
                mLogger.d("onDeviceOpenedAlready(cameraId: " + cameraId
                      + ", info: " + info + ")");
                mOpenListener.onDeviceOpenException(new CameraOpenException(-1));
            }
        }

        @Override
        public void onReconnectionFailure(CameraAgent mgr, String info) {
            if (!called()) {
                mLogger.d("onReconnectionFailure: " + info);
                mOpenListener.onDeviceOpenException(new CameraOpenException(-1));
            }
        }
    }
}
