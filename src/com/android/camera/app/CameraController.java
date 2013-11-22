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
import android.util.Log;

import com.android.camera.CameraDisabledException;
import com.android.camera.util.CameraUtil;

/**
 * A class which implements {@link com.android.camera.app.CameraProvider} used
 * by {@link com.android.camera.CameraActivity}.
 * TODO: Make this class package private.
 */
public class CameraController implements CameraManager.CameraOpenCallback, CameraProvider {
    private final String TAG = "CameraController";
    private Context mContext;
    private CameraManager.CameraOpenCallback mCallbackReceiver;
    private Handler mCallbackHandler;
    private final CameraManager mCameraManager;
    private final Camera.CameraInfo[] mCameraInfos;
    private final int mNumberOfCameras;
    private final int mFirstBackCameraId;
    private final int mFirstFrontCameraId;

    private CameraManager.CameraProxy mCameraProxy;
    private int mRequestingCameraId = -1;

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
    public void onCameraOpened(CameraManager.CameraProxy camera) {
        mRequestingCameraId = -1;
        mCameraProxy = camera;
        mCallbackReceiver.onCameraOpened(camera);
    }

    @Override
    public void onCameraDisabled(int cameraId) {
        mCallbackReceiver.onCameraDisabled(cameraId);
    }

    @Override
    public void onDeviceOpenFailure(int cameraId) {
        mCallbackReceiver.onDeviceOpenFailure(cameraId);
    }

    @Override
    public void onReconnectionFailure(CameraManager mgr) {
        mCallbackReceiver.onReconnectionFailure(mgr);
    }

    @Override
    public void requestCamera(int id) {
        // Double open is avoided.
        if (mRequestingCameraId == id) {
            return;
        }
        mRequestingCameraId = id;
        if (mCameraProxy == null) {
            // No camera yet.
            checkAndOpenCamera(mContext, mCameraManager, id, mCallbackHandler, this);
        } else if (mCameraProxy.getCameraId() != id) {
            // Already has another camera opened.
            mCameraProxy.release(false);
            mCameraProxy = null;
            checkAndOpenCamera(mContext, mCameraManager, id, mCallbackHandler, this);
        } else {
            // The same camera, just do a reconnect.
            mCameraProxy.reconnect(mCallbackHandler, this);
            mCameraProxy = null;
        }
    }

    @Override
    public void releaseCamera(int id) {
        if (mCameraProxy.getCameraId() != id) {
            throw new IllegalStateException("Trying to release an unopened camera.");
        }
        if (mRequestingCameraId != -1) {
            mRequestingCameraId = -1;
        }
    }

    /**
     * Closes the opened camera device.
     * TODO: Make this method package private.
     */
    public void closeCamera() {
        Log.v(TAG, "closing camera");
        if (mCameraProxy == null) {
            return;
        }
        mCameraProxy.release(true);
        mCameraProxy = null;
        mRequestingCameraId = -1;
    }

    private static void checkAndOpenCamera(Context context, CameraManager cameraManager,
            final int cameraId, Handler handler, final CameraManager.CameraOpenCallback cb) {
        try {
            CameraUtil.throwIfCameraDisabled(context);
            cameraManager.cameraOpen(handler, cameraId, cb);
        } catch (CameraDisabledException ex) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onCameraDisabled(cameraId);
                }
            });
        }
    }
}
