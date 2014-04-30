/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;

import com.android.camera.CameraDisabledException;
import com.android.camera.cameradevice.CameraManager;
import com.android.camera.cameradevice.CameraManager.CameraExceptionCallback;
import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;

/**
 * A class which implements {@link com.android.camera.app.CameraProvider} used
 * by {@link com.android.camera.CameraActivity}.
 * TODO: Make this class package private.
 */
public class CameraController implements CameraManager.CameraOpenCallback, CameraProvider {
    private static final Log.Tag TAG = new Log.Tag("CameraController");
    private static final int EMPTY_REQUEST = -1;
    private final Context mContext;
    private CameraManager.CameraOpenCallback mCallbackReceiver;
    private final Handler mCallbackHandler;
    private final CameraManager mCameraManager;
    private final Camera.CameraInfo[] mCameraInfos;
    private final int mNumberOfCameras;
    private final int mFirstBackCameraId;
    private final int mFirstFrontCameraId;

    private CameraManager.CameraProxy mCameraProxy;
    private int mRequestingCameraId = EMPTY_REQUEST;

    /**
     * Constructor.
     *
     * @param context The {@link android.content.Context} used to check if the
     *                camera is disabled.
     * @param handler The {@link android.os.Handler} to post the camera
     *                callbacks to.
     * @param cameraManager Used for camera open/close.
     */
    public CameraController(Context context, CameraManager.CameraOpenCallback callbackReceiver,
            Handler handler, CameraManager cameraManager) {
        mContext = context;
        mCallbackReceiver = callbackReceiver;
        mCallbackHandler = handler;
        mCameraManager = cameraManager;
        mNumberOfCameras = Camera.getNumberOfCameras();
        mCameraInfos = new Camera.CameraInfo[mNumberOfCameras];
        for (int i = 0; i < mNumberOfCameras; i++) {
            mCameraInfos[i] = new Camera.CameraInfo();
            Camera.getCameraInfo(i, mCameraInfos[i]);
        }

        int firstFront = -1;
        int firstBack = -1;
        // Get the first (smallest) back and first front camera id.
        for (int i = mNumberOfCameras - 1; i >= 0; i--) {
            if (mCameraInfos[i].facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                firstBack = i;
            } else {
                if (mCameraInfos[i].facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    firstFront = i;
                }
            }
        }
        mFirstBackCameraId = firstBack;
        mFirstFrontCameraId = firstFront;
    }

    @Override
    public void setCameraDefaultExceptionCallback(CameraExceptionCallback callback,
            Handler handler) {
        mCameraManager.setCameraDefaultExceptionCallback(callback, handler);
    }

    @Override
    public Camera.CameraInfo[] getCameraInfo() {
        return mCameraInfos;
    }

    @Override
    public int getNumberOfCameras() {
        return mNumberOfCameras;
    }

    @Override
    public int getFirstBackCameraId() {
        return mFirstBackCameraId;
    }

    @Override
    public int getFirstFrontCameraId() {
        return mFirstFrontCameraId;
    }

    @Override
    public boolean isFrontFacingCamera(int id) {
        if (id >= mCameraInfos.length || mCameraInfos[id] == null) {
            Log.e(TAG, "Camera info not available:" + id);
            return false;
        }
        return (mCameraInfos[id].facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    @Override
    public boolean isBackFacingCamera(int id) {
        if (id >= mCameraInfos.length || mCameraInfos[id] == null) {
            Log.e(TAG, "Camera info not available:" + id);
            return false;
        }
        return (mCameraInfos[id].facing == Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    @Override
    public void onCameraOpened(CameraManager.CameraProxy camera) {
        Log.v(TAG, "onCameraOpened");
        if (mRequestingCameraId != camera.getCameraId()) {
            // Not requesting any camera or not waiting for this one.
            return;
        }
        mCameraProxy = camera;
        mRequestingCameraId = EMPTY_REQUEST;
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onCameraOpened(camera);
        }
    }

    @Override
    public void onCameraDisabled(int cameraId) {
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onCameraDisabled(cameraId);
        }
    }

    @Override
    public void onDeviceOpenFailure(int cameraId, String info) {
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onDeviceOpenFailure(cameraId, info);
        }
    }

    @Override
    public void onDeviceOpenedAlready(int cameraId, String info) {
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onDeviceOpenedAlready(cameraId, info);
        }
    }

    @Override
    public void onReconnectionFailure(CameraManager mgr, String info) {
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onReconnectionFailure(mgr, info);
        }
    }

    @Override
    public void requestCamera(int id) {
        // Based on
        // (mRequestingCameraId == id, mRequestingCameraId == EMPTY_REQUEST),
        // we have (T, T), (T, F), (F, T), (F, F).
        // (T, T): implies id == EMPTY_REQUEST. We don't allow this to happen
        //         here. Return.
        // (F, F): A previous request hasn't been fulfilled yet. Return.
        // (T, F): Already requested the same camera. No-op. Return.
        // (F, T): Nothing is going on. Continue.
        if (mRequestingCameraId != EMPTY_REQUEST || mRequestingCameraId == id) {
            return;
        }
        mRequestingCameraId = id;
        if (mCameraProxy == null) {
            // No camera yet.
            checkAndOpenCamera(mContext, mCameraManager, id, mCallbackHandler, this);
        } else if (mCameraProxy.getCameraId() != id) {
            // Already has another camera opened.
            mCameraManager.closeCamera(mCameraProxy, false);
            checkAndOpenCamera(mContext, mCameraManager, id, mCallbackHandler, this);
        } else {
            // The same camera, just do a reconnect.
            Log.v(TAG, "reconnecting to use the existing camera");
            mCameraProxy.reconnect(mCallbackHandler, this);
            mCameraProxy = null;
        }
    }

    @Override
    public boolean waitingForCamera() {
        return mRequestingCameraId != EMPTY_REQUEST;
    }

    @Override
    public void releaseCamera(int id) {
        if (mCameraProxy == null) {
            if (mRequestingCameraId == EMPTY_REQUEST) {
                // Camera not requested yet.
                Log.w(TAG, "Trying to release the camera before requesting");
            }
            // Camera requested but not available yet.
            mRequestingCameraId = EMPTY_REQUEST;
            return;
        }
        if (mCameraProxy.getCameraId() != id) {
            throw new IllegalStateException("Trying to release an unopened camera.");
        }
        mRequestingCameraId = EMPTY_REQUEST;
    }

    public void removeCallbackReceiver() {
        mCallbackReceiver = null;
    }

    /**
     * Closes the opened camera device.
     * TODO: Make this method package private.
     */
    public void closeCamera(boolean synced) {
        Log.v(TAG, "closing camera");
        mCameraProxy = null;
        mCameraManager.closeCamera(mCameraProxy, synced);
        mRequestingCameraId = EMPTY_REQUEST;
    }

    private static void checkAndOpenCamera(Context context, CameraManager cameraManager,
            final int cameraId, Handler handler, final CameraManager.CameraOpenCallback cb) {
        try {
            CameraUtil.throwIfCameraDisabled(context);
            cameraManager.openCamera(handler, cameraId, cb);
        } catch (CameraDisabledException ex) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onCameraDisabled(cameraId);
                }
            });
        }
    }

    public void setOneShotPreviewCallback(Handler handler,
            CameraManager.CameraPreviewDataCallback cb) {
        mCameraProxy.setOneShotPreviewCallback(handler, cb);
    }
}
