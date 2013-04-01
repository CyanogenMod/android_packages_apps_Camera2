/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import static com.android.camera.Util.Assert;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.android.gallery3d.common.ApiHelper;

import java.io.IOException;
import java.util.concurrent.SynchronousQueue;

public class CameraManager {
    private static final String TAG = "CameraManager";
    private static CameraManager sCameraManager = new CameraManager();

    private Parameters mParameters;
    private boolean mParametersIsDirty;
    private SynchronousQueue<Parameters> mParametersQueue =
            new SynchronousQueue<Parameters>();
    private SynchronousQueue<IOExceptionHolder> mReconnectExceptionQueue =
            new SynchronousQueue<IOExceptionHolder>();

    private static final int RELEASE = 1;
    private static final int RECONNECT = 2;
    private static final int UNLOCK = 3;
    private static final int LOCK = 4;
    private static final int SET_PREVIEW_TEXTURE_ASYNC = 5;
    private static final int START_PREVIEW_ASYNC = 6;
    private static final int STOP_PREVIEW = 7;
    private static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 8;
    private static final int ADD_CALLBACK_BUFFER = 9;
    private static final int AUTO_FOCUS = 10;
    private static final int CANCEL_AUTO_FOCUS = 11;
    private static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 12;
    private static final int SET_DISPLAY_ORIENTATION = 13;
    private static final int SET_ZOOM_CHANGE_LISTENER = 14;
    private static final int SET_FACE_DETECTION_LISTENER = 15;
    private static final int START_FACE_DETECTION = 16;
    private static final int STOP_FACE_DETECTION = 17;
    private static final int SET_ERROR_CALLBACK = 18;
    private static final int SET_PARAMETERS = 19;
    private static final int GET_PARAMETERS = 20;
    private static final int SET_PARAMETERS_ASYNC = 21;
    private static final int SET_PREVIEW_DISPLAY_ASYNC = 22;
    private static final int SET_PREVIEW_CALLBACK = 23;
    private static final int ENABLE_SHUTTER_SOUND = 24;

    private Handler mCameraHandler;
    private CameraProxy mCameraProxy;
    private android.hardware.Camera mCamera;

    // This holder is used when we need to pass the exception
    // back to the calling thread. SynchornousQueue doesn't
    // allow we to pass a null object thus a holder is needed.
    private class IOExceptionHolder {
        public IOException ex;
    }

    public static CameraManager instance() {
        return sCameraManager;
    }

    private CameraManager() {
        HandlerThread ht = new HandlerThread("Camera Handler Thread");
        ht.start();
        mCameraHandler = new CameraHandler(ht.getLooper());
    }

    private class CameraHandler extends Handler {
        CameraHandler(Looper looper) {
            super(looper);
        }

        @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
        private void startFaceDetection() {
            mCamera.startFaceDetection();
        }

        @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
        private void stopFaceDetection() {
            mCamera.stopFaceDetection();
        }

        @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
        private void setFaceDetectionListener(FaceDetectionListener listener) {
            mCamera.setFaceDetectionListener(listener);
        }

        @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
        private void setPreviewTexture(Object surfaceTexture) {
            try {
                mCamera.setPreviewTexture((SurfaceTexture) surfaceTexture);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN_MR1)
        private void enableShutterSound(boolean enable) {
            mCamera.enableShutterSound(enable);
        }

        /*
         * This method does not deal with the build version check.  Everyone should
         * check first before sending message to this handler.
         */
        @Override
        public void handleMessage(final Message msg) {
            try {
                switch (msg.what) {
                    case RELEASE:
                        mCamera.release();
                        mCamera = null;
                        mCameraProxy = null;
                        return;

                    case RECONNECT:
                        IOExceptionHolder holder = new IOExceptionHolder();
                        holder.ex = null;
                        try {
                            mCamera.reconnect();
                        } catch (IOException ex) {
                            holder.ex = ex;
                        }
                        try {
                            mReconnectExceptionQueue.put(holder);
                        } catch (InterruptedException ex) {
                        }
                        return;

                    case UNLOCK:
                        mCamera.unlock();
                        return;

                    case LOCK:
                        mCamera.lock();
                        return;

                    case SET_PREVIEW_TEXTURE_ASYNC:
                        setPreviewTexture(msg.obj);
                        return;

                    case SET_PREVIEW_DISPLAY_ASYNC:
                        try {
                            mCamera.setPreviewDisplay((SurfaceHolder) msg.obj);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return;

                    case START_PREVIEW_ASYNC:
                        mCamera.startPreview();
                        return;

                    case STOP_PREVIEW:
                        mCamera.stopPreview();
                        return;

                    case SET_PREVIEW_CALLBACK_WITH_BUFFER:
                        mCamera.setPreviewCallbackWithBuffer(
                            (PreviewCallback) msg.obj);
                        return;

                    case ADD_CALLBACK_BUFFER:
                        mCamera.addCallbackBuffer((byte[]) msg.obj);
                        return;

                    case AUTO_FOCUS:
                        mCamera.autoFocus((AutoFocusCallback) msg.obj);
                        return;

                    case CANCEL_AUTO_FOCUS:
                        mCamera.cancelAutoFocus();
                        return;

                    case SET_AUTO_FOCUS_MOVE_CALLBACK:
                        setAutoFocusMoveCallback(mCamera, msg.obj);
                        return;

                    case SET_DISPLAY_ORIENTATION:
                        mCamera.setDisplayOrientation(msg.arg1);
                        return;

                    case SET_ZOOM_CHANGE_LISTENER:
                        mCamera.setZoomChangeListener(
                            (OnZoomChangeListener) msg.obj);
                        return;

                    case SET_FACE_DETECTION_LISTENER:
                        setFaceDetectionListener((FaceDetectionListener) msg.obj);
                        return;

                    case START_FACE_DETECTION:
                        startFaceDetection();
                        return;

                    case STOP_FACE_DETECTION:
                        stopFaceDetection();
                        return;

                    case SET_ERROR_CALLBACK:
                        mCamera.setErrorCallback((ErrorCallback) msg.obj);
                        return;

                    case SET_PARAMETERS:
                        mCamera.setParameters((Parameters) msg.obj);
                        return;

                    case GET_PARAMETERS:
                        try {
                            mParametersQueue.put(mCamera.getParameters());
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return;

                    case SET_PARAMETERS_ASYNC:
                        mCamera.setParameters((Parameters) msg.obj);
                        return;

                    case SET_PREVIEW_CALLBACK:
                        mCamera.setPreviewCallback((PreviewCallback) msg.obj);
                        return;

                    case ENABLE_SHUTTER_SOUND:
                        enableShutterSound((msg.arg1 == 1) ? true : false);
                        return;

                    default:
                        throw new RuntimeException("Invalid CameraProxy message=" + msg.what);
                }
            } catch (RuntimeException e) {
                if (msg.what != RELEASE && mCamera != null) {
                    try {
                        mCamera.release();
                    } catch (Exception ex) {
                        Log.e(TAG, "Fail to release the camera.");
                    }
                    mCamera = null;
                    mCameraProxy = null;
                }
                throw e;
            }
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
    private void setAutoFocusMoveCallback(android.hardware.Camera camera,
            Object cb) {
        camera.setAutoFocusMoveCallback((AutoFocusMoveCallback) cb);
    }

    // Open camera synchronously. This method is invoked in the context of a
    // background thread.
    CameraProxy cameraOpen(int cameraId) {
        // Cannot open camera in mCameraHandler, otherwise all camera events
        // will be routed to mCameraHandler looper, which in turn will call
        // event handler like Camera.onFaceDetection, which in turn will modify
        // UI and cause exception like this:
        // CalledFromWrongThreadException: Only the original thread that created
        // a view hierarchy can touch its views.
        mCamera = android.hardware.Camera.open(cameraId);
        if (mCamera != null) {
            mCameraProxy = new CameraProxy();
            mParametersIsDirty = true;
            return mCameraProxy;
        } else {
            return null;
        }
    }

    public class CameraProxy {
        private ConditionVariable mWaitDoneLock = new ConditionVariable();
        private Runnable mUnlockRunnable = new Runnable() {
            @Override
            public void run() {
                mWaitDoneLock.open();
            }
        };


        private CameraProxy() {
            Assert(mCamera != null);
        }

        public android.hardware.Camera getCamera() {
            return mCamera;
        }

        public void release() {
            // release() must be synchronous so we know exactly when the camera
            // is released and can continue on.
            mCameraHandler.sendEmptyMessage(RELEASE);
            waitDone();
        }

        public void reconnect() throws IOException {
            mCameraHandler.sendEmptyMessage(RECONNECT);
            IOExceptionHolder holder = null;
            try {
                holder = mReconnectExceptionQueue.take();
            } catch (InterruptedException ex) {
            }
            if (holder != null && holder.ex != null) {
                throw holder.ex;
            }
        }

        public void unlock() {
            mCameraHandler.sendEmptyMessage(UNLOCK);
        }

        public void lock() {
            mCameraHandler.sendEmptyMessage(LOCK);
        }

        @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
        public void setPreviewTextureAsync(final SurfaceTexture surfaceTexture) {
            mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC, surfaceTexture).sendToTarget();
        }

        public void setPreviewDisplayAsync(final SurfaceHolder surfaceHolder) {
            mCameraHandler.obtainMessage(SET_PREVIEW_DISPLAY_ASYNC, surfaceHolder).sendToTarget();
        }

        public void startPreviewAsync() {
            mCameraHandler.sendEmptyMessage(START_PREVIEW_ASYNC);
        }

        public void stopPreview() {
            mCameraHandler.sendEmptyMessage(STOP_PREVIEW);
        }

        public void setPreviewCallback(final PreviewCallback cb) {
            mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK, cb).sendToTarget();
        }

        public void setPreviewCallbackWithBuffer(final PreviewCallback cb) {
            mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK_WITH_BUFFER, cb).sendToTarget();
        }

        public void addCallbackBuffer(byte[] callbackBuffer) {
            mCameraHandler.obtainMessage(ADD_CALLBACK_BUFFER, callbackBuffer).sendToTarget();
        }

        public void autoFocus(AutoFocusCallback cb) {
            mCameraHandler.obtainMessage(AUTO_FOCUS, cb).sendToTarget();
        }

        public void cancelAutoFocus() {
            mCameraHandler.removeMessages(AUTO_FOCUS);
            mCameraHandler.sendEmptyMessage(CANCEL_AUTO_FOCUS);
        }

        @TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
        public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
            mCameraHandler.obtainMessage(SET_AUTO_FOCUS_MOVE_CALLBACK, cb).sendToTarget();
        }

        public void takePicture(final ShutterCallback shutter, final PictureCallback raw,
                final PictureCallback postview, final PictureCallback jpeg) {
            // Too many parameters, so use post for simplicity
            mCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCamera.takePicture(shutter, raw, postview, jpeg);
                }
            });
        }

        public void takePicture2(final ShutterCallback shutter, final PictureCallback raw,
                final PictureCallback postview, final PictureCallback jpeg,
                final int cameraState, final int focusState) {
            // Too many parameters, so use post for simplicity
            mCameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mCamera.takePicture(shutter, raw, postview, jpeg);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "take picture failed; cameraState:" + cameraState
                            + ", focusState:" + focusState);
                        throw e;
                    }
                }
            });
        }

        public void setDisplayOrientation(int degrees) {
            mCameraHandler.obtainMessage(SET_DISPLAY_ORIENTATION, degrees, 0)
                    .sendToTarget();
        }

        public void setZoomChangeListener(OnZoomChangeListener listener) {
            mCameraHandler.obtainMessage(SET_ZOOM_CHANGE_LISTENER, listener).sendToTarget();
        }

        @TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
        public void setFaceDetectionListener(FaceDetectionListener listener) {
            mCameraHandler.obtainMessage(SET_FACE_DETECTION_LISTENER, listener).sendToTarget();
        }

        public void startFaceDetection() {
            mCameraHandler.sendEmptyMessage(START_FACE_DETECTION);
        }

        public void stopFaceDetection() {
            mCameraHandler.sendEmptyMessage(STOP_FACE_DETECTION);
        }

        public void setErrorCallback(ErrorCallback cb) {
            mCameraHandler.obtainMessage(SET_ERROR_CALLBACK, cb).sendToTarget();
        }

        public void setParameters(Parameters params) {
            // TODO: check if this synchronous version is necessary
            mParametersIsDirty = true;
            mCameraHandler.obtainMessage(SET_PARAMETERS, params).sendToTarget();
        }

        public void setParametersAsync(Parameters params) {
            mParametersIsDirty = true;
            mCameraHandler.removeMessages(SET_PARAMETERS_ASYNC);
            mCameraHandler.obtainMessage(SET_PARAMETERS_ASYNC, params).sendToTarget();
        }

        public Parameters getParameters() {
            if (mParametersIsDirty || mParameters == null) {
                mCameraHandler.sendEmptyMessage(GET_PARAMETERS);
                try {
                    mParameters = mParametersQueue.take();
                    mParametersIsDirty = false;
                } catch (InterruptedException ex) {
                }
            }
            return mParameters;
        }

        public void enableShutterSound(boolean enable) {
            mCameraHandler.obtainMessage(
                    ENABLE_SHUTTER_SOUND, (enable ? 1 : 0), 0).sendToTarget();
        }

        public void waitDone() {
            mWaitDoneLock.close();
            mCameraHandler.post(mUnlockRunnable);
            mWaitDoneLock.block();
        }
    }
}
