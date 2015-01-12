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
import android.os.Handler;

import com.android.camera.CameraDisabledException;
import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GservicesHelper;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgent.CameraExceptionCallback;
import com.android.ex.camera2.portability.CameraDeviceInfo;

/**
 * A class which implements {@link com.android.camera.app.CameraProvider} used
 * by {@link com.android.camera.CameraActivity}.
 * TODO: Make this class package private.
 */
public class CameraController implements CameraAgent.CameraOpenCallback, CameraProvider {
    private static final Log.Tag TAG = new Log.Tag("CameraController");
    public static final int EMPTY_REQUEST = -1;
    private final Context mContext;
    public CameraAgent.CameraOpenCallback mCallbackReceiver;
    public Handler mCallbackHandler;
    private CameraAgent mCameraAgent;
    private CameraAgent mCameraAgentNg;

    /** The one for the API that is currently in use (deprecated one by default). */
    private CameraDeviceInfo mInfo;

    public CameraAgent.CameraProxy mCameraProxy;
    public int mRequestingCameraId = EMPTY_REQUEST;

    /**
     * Determines which of mCameraAgent and mCameraAgentNg is currently in use.
     * <p>It's only possible to enable this if the new API is actually
     * supported.</p>
     */
    private boolean mUsingNewApi = false;

    /**
     * Constructor.
     *
     * @param context The {@link android.content.Context} used to check if the
     *                camera is disabled.
     * @param handler The {@link android.os.Handler} to post the camera
     *                callbacks to.
     * @param cameraManager Used for camera open/close.
     * @param cameraManagerNg Used for camera open/close with the new API. If
     *                        {@code null} or the same object as
     *                        {@code cameraManager}, the new API will not be
     *                        exposed and requests for it will get the old one.
     */
    public CameraController(Context context, CameraAgent.CameraOpenCallback callbackReceiver,
            Handler handler, CameraAgent cameraManager, CameraAgent cameraManagerNg) {
        mContext = context;
        mCallbackReceiver = callbackReceiver;
        mCallbackHandler = handler;
        // If the new implementation is the same as the old, the
        // CameraAgentFactory decided this device doesn't support the new API.
        setCameraAgent(cameraManager, cameraManagerNg);
        mInfo = mCameraAgent.getCameraDeviceInfo();
        if (mInfo == null && mCallbackReceiver != null) {
            mCallbackReceiver.onDeviceOpenFailure(-1, "GETTING_CAMERA_INFO");
        }
    }

    public void setCameraAgent(CameraAgent cameraManager, CameraAgent cameraManagerNg) {
        mCameraAgent = cameraManager;
        mCameraAgentNg = cameraManagerNg != cameraManager ? cameraManagerNg : null;
    }

    @Override
    public void setCameraDefaultExceptionCallback(CameraExceptionCallback callback,
            Handler handler) {
        mCameraAgent.setCameraDefaultExceptionCallback(callback, handler);
        if (mCameraAgentNg != null) {
            mCameraAgentNg.setCameraDefaultExceptionCallback(callback, handler);
        }
    }

    @Override
    public CameraDeviceInfo.Characteristics getCharacteristics(int cameraId) {
        if (mInfo == null) {
            return null;
        }
        return mInfo.getCharacteristics(cameraId);
    }

    @Override
    public int getCurrentCameraId() {
        if (mCameraProxy != null) {
            return mCameraProxy.getCameraId();
        } else {
            Log.v(TAG, "getCurrentCameraId without an open camera... returning requested id");
            return mRequestingCameraId;
        }
    }

    @Override
    public int getNumberOfCameras() {
        if (mInfo == null) {
            return 0;
        }
        return mInfo.getNumberOfCameras();
    }

    @Override
    public int getFirstBackCameraId() {
        if (mInfo == null) {
            return -1;
        }
        return mInfo.getFirstBackCameraId();
    }

    @Override
    public int getFirstFrontCameraId() {
        if (mInfo == null) {
            return -1;
        }
        return mInfo.getFirstFrontCameraId();
    }

    @Override
    public boolean isFrontFacingCamera(int id) {
        if (mInfo == null) {
            return false;
        }
        if (id >= mInfo.getNumberOfCameras() || mInfo.getCharacteristics(id) == null) {
            Log.e(TAG, "Camera info not available:" + id);
            return false;
        }
        return mInfo.getCharacteristics(id).isFacingFront();
    }

    @Override
    public boolean isBackFacingCamera(int id) {
        if (mInfo == null) {
            return false;
        }
        if (id >= mInfo.getNumberOfCameras() || mInfo.getCharacteristics(id) == null) {
            Log.e(TAG, "Camera info not available:" + id);
            return false;
        }
        return mInfo.getCharacteristics(id).isFacingBack();
    }

    @Override
    public void onCameraOpened(CameraAgent.CameraProxy camera) {
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
    public void onReconnectionFailure(CameraAgent mgr, String info) {
        if (mCallbackReceiver != null) {
            mCallbackReceiver.onReconnectionFailure(mgr, info);
        }
    }

    @Override
    public void requestCamera(int id) {
        requestCamera(id, false);
    }

    @Override
    public void requestCamera(int id, boolean useNewApi) {
        Log.v(TAG, "requestCamera");
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
        if (mInfo == null) {
            return;
        }
        mRequestingCameraId = id;

        // Only actually use the new API if it's supported on this device.
        useNewApi = mCameraAgentNg != null && useNewApi;
        CameraAgent cameraManager = useNewApi ? mCameraAgentNg : mCameraAgent;

        if (mCameraProxy == null) {
            // No camera yet.
            checkAndOpenCamera(mContext, cameraManager, id, mCallbackHandler, this);
        } else if (mCameraProxy.getCameraId() != id || mUsingNewApi != useNewApi) {
            boolean syncClose = GservicesHelper.useCamera2ApiThroughPortabilityLayer(mContext);
            Log.v(TAG, "different camera already opened, closing then reopening");
            // Already has camera opened, and is switching cameras and/or APIs.
            if (mUsingNewApi) {
                mCameraAgentNg.closeCamera(mCameraProxy, true);
            } else {
                // if using API2 ensure API1 usage is also synced
                mCameraAgent.closeCamera(mCameraProxy, syncClose);
            }
            checkAndOpenCamera(mContext, cameraManager, id, mCallbackHandler, this);
        } else {
            // The same camera, just do a reconnect.
            Log.v(TAG, "reconnecting to use the existing camera");
            mCameraProxy.reconnect(mCallbackHandler, this);
            mCameraProxy = null;
        }

        mUsingNewApi = useNewApi;
        mInfo = cameraManager.getCameraDeviceInfo();
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
        if (mCameraProxy == null) {
            Log.v(TAG, "No camera open, not closing");
            return;
        }
        Log.v(TAG, "Closing camera");
        mCameraProxy = null;
        if (mUsingNewApi) {
            mCameraAgentNg.closeCamera(mCameraProxy, synced);
        } else {
            mCameraAgent.closeCamera(mCameraProxy, synced);
        }
        mRequestingCameraId = EMPTY_REQUEST;
        mUsingNewApi = false;
    }

    private static void checkAndOpenCamera(Context context, CameraAgent cameraManager,
            final int cameraId, Handler handler, final CameraAgent.CameraOpenCallback cb) {
        Log.v(TAG, "checkAndOpenCamera");
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
            CameraAgent.CameraPreviewDataCallback cb) {
        mCameraProxy.setOneShotPreviewCallback(handler, cb);
    }
}
