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

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.android.camera.debug.Log;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A class to implement {@link CameraManager} of the Android camera framework.
 */
class AndroidCameraManagerImpl implements CameraManager {
    private static final Log.Tag TAG = new Log.Tag("AndroidCamMgrImpl");
    private static final long CAMERA_OPERATION_TIMEOUT_MS = 2500;
    private static final long MAX_MESSAGE_QUEUE_LENGTH = 256;

    private Parameters mParameters;
    private boolean mParametersIsDirty;

    /* Messages used in CameraHandler. */
    // Camera initialization/finalization
    private static final int OPEN_CAMERA = 1;
    private static final int RELEASE =     2;
    private static final int RECONNECT =   3;
    private static final int UNLOCK =      4;
    private static final int LOCK =        5;
    // Preview
    private static final int SET_PREVIEW_TEXTURE_ASYNC =        101;
    private static final int START_PREVIEW_ASYNC =              102;
    private static final int STOP_PREVIEW =                     103;
    private static final int SET_PREVIEW_CALLBACK_WITH_BUFFER = 104;
    private static final int ADD_CALLBACK_BUFFER =              105;
    private static final int SET_PREVIEW_DISPLAY_ASYNC =        106;
    private static final int SET_PREVIEW_CALLBACK =             107;
    private static final int SET_ONE_SHOT_PREVIEW_CALLBACK =    108;
    // Parameters
    private static final int SET_PARAMETERS =     201;
    private static final int GET_PARAMETERS =     202;
    private static final int REFRESH_PARAMETERS = 203;
    // Focus, Zoom
    private static final int AUTO_FOCUS =                   301;
    private static final int CANCEL_AUTO_FOCUS =            302;
    private static final int SET_AUTO_FOCUS_MOVE_CALLBACK = 303;
    private static final int SET_ZOOM_CHANGE_LISTENER =     304;
    // Face detection
    private static final int SET_FACE_DETECTION_LISTENER = 461;
    private static final int START_FACE_DETECTION =        462;
    private static final int STOP_FACE_DETECTION =         463;
    private static final int SET_ERROR_CALLBACK =          464;
    // Presentation
    private static final int ENABLE_SHUTTER_SOUND =    501;
    private static final int SET_DISPLAY_ORIENTATION = 502;
    // Capture
    private static final int CAPTURE_PHOTO = 601;

    /** Camera states **/
    // These states are defined bitwise so we can easily to specify a set of
    // states together.
    private static final int CAMERA_UNOPENED = 1;
    private static final int CAMERA_IDLE = 1 << 1;
    private static final int CAMERA_UNLOCKED = 1 << 2;
    private static final int CAMERA_CAPTURING = 1 << 3;
    private static final int CAMERA_FOCUSING = 1 << 4;

    private final CameraHandler mCameraHandler;
    private final HandlerThread mCameraHandlerThread;
    private final CameraStateHolder mCameraState;
    private final DispatchThread mDispatchThread;

    // Used to retain a copy of Parameters for setting parameters.
    private Parameters mParamsToSet;

    private Handler mCameraExceptionCallbackHandler;
    private CameraExceptionCallback mCameraExceptionCallback =
        new CameraExceptionCallback() {
            @Override
            public void onCameraException(RuntimeException e) {
                throw e;
            }
        };

    AndroidCameraManagerImpl() {
        mCameraHandlerThread = new HandlerThread("Camera Handler Thread");
        mCameraHandlerThread.start();
        mCameraHandler = new CameraHandler(mCameraHandlerThread.getLooper());
        mCameraExceptionCallbackHandler = mCameraHandler;
        mCameraState = new CameraStateHolder();
        mDispatchThread = new DispatchThread();
        mDispatchThread.start();
    }

    @Override
    public void setCameraDefaultExceptionCallback(CameraExceptionCallback callback,
            Handler handler) {
        synchronized (mCameraExceptionCallback) {
            mCameraExceptionCallback = callback;
            mCameraExceptionCallbackHandler = handler;
        }
    }

    /**
     * Recycles the resources used by this instance. CameraManager will be in
     * an unusable state after calling this.
     */
    public void recycle() {
        mDispatchThread.end();
    }

    private static class CameraStateHolder {
        private int mState;

        public CameraStateHolder() {
            setState(CAMERA_UNOPENED);
        }

        public CameraStateHolder(int state) {
            setState(state);
        }

        public synchronized void setState(int state) {
            mState = state;
            this.notifyAll();
        }

        public synchronized int getState() {
            return mState;
        }

        private interface ConditionChecker {
            /**
             * @return Whether the condition holds.
             */
            boolean success();
        }

        /**
         * A helper method used by {@link #waitToAvoidStates(int)} and
         * {@link #waitForStates(int)}. This method will wait until the
         * condition is successful.
         *
         * @param stateChecker The state checker to be used.
         * @param timeoutMs The timeout limit in milliseconds.
         * @return {@code false} if the wait is interrupted or timeout limit is
         *         reached.
         */
        private boolean waitForCondition(ConditionChecker stateChecker,
                long timeoutMs) {
            long timeBound = SystemClock.uptimeMillis() + timeoutMs;
            synchronized (this) {
                while (!stateChecker.success()) {
                    try {
                        this.wait(timeoutMs);
                    } catch (InterruptedException ex) {
                        if (SystemClock.uptimeMillis() > timeBound) {
                            // Timeout.
                            Log.w(TAG, "Timeout waiting.");
                        }
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Block the current thread until the state becomes one of the
         * specified.
         *
         * @param states Expected states.
         * @return {@code false} if the wait is interrupted or timeout limit is
         *         reached.
         */
        public boolean waitForStates(final int states) {
            return waitForCondition(new ConditionChecker() {
                @Override
                public boolean success() {
                    return (states | mState) == states;
                }
            }, CAMERA_OPERATION_TIMEOUT_MS);
        }

        /**
         * Block the current thread until the state becomes NOT one of the
         * specified.
         *
         * @param states States to avoid.
         * @return {@code false} if the wait is interrupted or timeout limit is
         *         reached.
         */
        public boolean waitToAvoidStates(final int states) {
            return waitForCondition(new ConditionChecker() {
                @Override
                public boolean success() {
                    return (states & mState) == 0;
                }
            }, CAMERA_OPERATION_TIMEOUT_MS);
        }
    }

    /**
     * The handler on which the actual camera operations happen.
     */
    private class CameraHandler extends Handler {
        private Camera mCamera;
        private class CaptureCallbacks {
            public final ShutterCallback mShutter;
            public final PictureCallback mRaw;
            public final PictureCallback mPostView;
            public final PictureCallback mJpeg;

            CaptureCallbacks(ShutterCallback shutter, PictureCallback raw, PictureCallback postView,
                    PictureCallback jpeg) {
                mShutter = shutter;
                mRaw = raw;
                mPostView = postView;
                mJpeg = jpeg;
            }
        }

        CameraHandler(Looper looper) {
            super(looper);
        }

        private void startFaceDetection() {
            mCamera.startFaceDetection();
        }

        private void stopFaceDetection() {
            mCamera.stopFaceDetection();
        }

        private void setFaceDetectionListener(FaceDetectionListener listener) {
            mCamera.setFaceDetectionListener(listener);
        }

        private void setPreviewTexture(Object surfaceTexture) {
            try {
                mCamera.setPreviewTexture((SurfaceTexture) surfaceTexture);
            } catch (IOException e) {
                Log.e(TAG, "Could not set preview texture", e);
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        private void enableShutterSound(boolean enable) {
            mCamera.enableShutterSound(enable);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private void setAutoFocusMoveCallback(
                android.hardware.Camera camera, Object cb) {
            camera.setAutoFocusMoveCallback((AutoFocusMoveCallback) cb);
        }

        private void capture(final CaptureCallbacks cb) {
            try {
                mCamera.takePicture(cb.mShutter, cb.mRaw, cb.mPostView, cb.mJpeg);
            } catch (RuntimeException e) {
                // TODO: output camera state and focus state for debugging.
                Log.e(TAG, "take picture failed.");
                throw e;
            }
        }

        public void requestTakePicture(
                final ShutterCallback shutter,
                final PictureCallback raw,
                final PictureCallback postView,
                final PictureCallback jpeg) {
            final CaptureCallbacks callbacks = new CaptureCallbacks(shutter, raw, postView, jpeg);
            obtainMessage(CAPTURE_PHOTO, callbacks).sendToTarget();
        }

        /**
         * This method does not deal with the API level check.  Everyone should
         * check first for supported operations before sending message to this handler.
         */
        @Override
        public void handleMessage(final Message msg) {
            try {
                switch (msg.what) {
                    case OPEN_CAMERA: {
                        final CameraOpenCallback openCallback = (CameraOpenCallback) msg.obj;
                        final int cameraId = msg.arg1;
                        if (mCameraState.getState() != CAMERA_UNOPENED) {
                            openCallback.onDeviceOpenedAlready(cameraId);
                            break;
                        }

                        mCamera = android.hardware.Camera.open(cameraId);
                        if (mCamera != null) {
                            mParametersIsDirty = true;

                            // Get a instance of Camera.Parameters for later use.
                            if (mParamsToSet == null) {
                                mParamsToSet = mCamera.getParameters();
                            }

                            mCameraState.setState(CAMERA_IDLE);
                            if (openCallback != null) {
                                openCallback.onCameraOpened(
                                        new AndroidCameraProxyImpl(cameraId, mCamera));
                            }
                        } else {
                            if (openCallback != null) {
                                openCallback.onDeviceOpenFailure(cameraId);
                            }
                        }
                        break;
                    }

                    case RELEASE: {
                        mCamera.release();
                        mCameraState.setState(CAMERA_UNOPENED);
                        mCamera = null;
                        break;
                    }

                    case RECONNECT: {
                        final CameraOpenCallbackForward cbForward =
                                (CameraOpenCallbackForward) msg.obj;
                        final int cameraId = msg.arg1;
                        try {
                            mCamera.reconnect();
                        } catch (IOException ex) {
                            if (cbForward != null) {
                                cbForward.onReconnectionFailure(AndroidCameraManagerImpl.this);
                            }
                            break;
                        }

                        mCameraState.setState(CAMERA_IDLE);
                        if (cbForward != null) {
                            cbForward.onCameraOpened(new AndroidCameraProxyImpl(cameraId, mCamera));
                        }
                        break;
                    }

                    case UNLOCK: {
                        mCamera.unlock();
                        mCameraState.setState(CAMERA_UNLOCKED);
                        break;
                    }

                    case LOCK: {
                        mCamera.lock();
                        mCameraState.setState(CAMERA_IDLE);
                        break;
                    }

                    case SET_PREVIEW_TEXTURE_ASYNC: {
                        setPreviewTexture(msg.obj);
                        break;
                    }

                    case SET_PREVIEW_DISPLAY_ASYNC: {
                        try {
                            mCamera.setPreviewDisplay((SurfaceHolder) msg.obj);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    }

                    case START_PREVIEW_ASYNC: {
                        final CameraStartPreviewCallbackForward cbForward =
                            (CameraStartPreviewCallbackForward) msg.obj;
                        mCamera.startPreview();
                        if (cbForward != null) {
                            cbForward.onPreviewStarted();
                        }
                        break;
                    }

                    case STOP_PREVIEW: {
                        mCamera.stopPreview();
                        break;
                    }

                    case SET_PREVIEW_CALLBACK_WITH_BUFFER: {
                        mCamera.setPreviewCallbackWithBuffer((PreviewCallback) msg.obj);
                        break;
                    }

                    case SET_ONE_SHOT_PREVIEW_CALLBACK: {
                        mCamera.setOneShotPreviewCallback((PreviewCallback) msg.obj);
                        break;
                    }

                    case ADD_CALLBACK_BUFFER: {
                        mCamera.addCallbackBuffer((byte[]) msg.obj);
                        break;
                    }

                    case AUTO_FOCUS: {
                        mCameraState.setState(CAMERA_FOCUSING);
                        mCamera.autoFocus((AutoFocusCallback) msg.obj);
                        break;
                    }

                    case CANCEL_AUTO_FOCUS: {
                        mCamera.cancelAutoFocus();
                        mCameraState.setState(CAMERA_IDLE);
                        break;
                    }

                    case SET_AUTO_FOCUS_MOVE_CALLBACK: {
                        setAutoFocusMoveCallback(mCamera, msg.obj);
                        break;
                    }

                    case SET_DISPLAY_ORIENTATION: {
                        mCamera.setDisplayOrientation(msg.arg1);
                        break;
                    }

                    case SET_ZOOM_CHANGE_LISTENER: {
                        mCamera.setZoomChangeListener((OnZoomChangeListener) msg.obj);
                        break;
                    }

                    case SET_FACE_DETECTION_LISTENER: {
                        setFaceDetectionListener((FaceDetectionListener) msg.obj);
                        break;
                    }

                    case START_FACE_DETECTION: {
                        startFaceDetection();
                        break;
                    }

                    case STOP_FACE_DETECTION: {
                        stopFaceDetection();
                        break;
                    }

                    case SET_ERROR_CALLBACK: {
                        mCamera.setErrorCallback((ErrorCallback) msg.obj);
                        break;
                    }

                    case SET_PARAMETERS: {
                        mParametersIsDirty = true;
                        mParamsToSet.unflatten((String) msg.obj);
                        mCamera.setParameters(mParamsToSet);
                        break;
                    }

                    case GET_PARAMETERS: {
                        if (mParametersIsDirty) {
                            mParameters = mCamera.getParameters();
                            mParametersIsDirty = false;
                        }
                        break;
                    }

                    case SET_PREVIEW_CALLBACK: {
                        mCamera.setPreviewCallback((PreviewCallback) msg.obj);
                        break;
                    }

                    case ENABLE_SHUTTER_SOUND: {
                        enableShutterSound((msg.arg1 == 1) ? true : false);
                        break;
                    }

                    case REFRESH_PARAMETERS: {
                        mParametersIsDirty = true;
                        break;
                    }

                    case CAPTURE_PHOTO: {
                        mCameraState.setState(CAMERA_CAPTURING);
                        capture((CaptureCallbacks) msg.obj);
                        break;
                    }

                    default: {
                        throw new RuntimeException("Invalid CameraProxy message=" + msg.what);
                    }
                }
            } catch (final RuntimeException e) {
                if (msg.what != RELEASE && mCamera != null) {
                    try {
                        mCamera.release();
                        mCameraState.setState(CAMERA_UNOPENED);
                    } catch (Exception ex) {
                        Log.e(TAG, "Fail to release the camera.");
                    }
                    mCamera = null;
                } else {
                    if (mCamera == null) {
                        if (msg.what == OPEN_CAMERA) {
                            if (msg.obj != null) {
                                ((CameraOpenCallback) msg.obj).onDeviceOpenFailure(msg.arg1);
                            }
                        } else {
                            Log.w(TAG, "Cannot handle message " + msg.what + ", mCamera is null.");
                        }
                        return;
                    }
                }
                synchronized (mCameraExceptionCallback) {
                    mCameraExceptionCallbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                                mCameraExceptionCallback.onCameraException(e);
                            }
                        });
                }
            }
        }
    }

    private class DispatchThread extends Thread {

        private Queue<Runnable> mJobQueue;
        private Boolean mIsEnded;

        public DispatchThread() {
            super("Camera Job Dispatch Thread");
            mJobQueue = new LinkedList<Runnable>();
            mIsEnded = new Boolean(false);
        }

        /**
         * Queues up the job.
         *
         * @param job The job to run.
         */
        public void runJob(Runnable job) {
            if (isEnded()) {
                throw new IllegalStateException(
                        "Trying to run job on interrupted dispatcher thread");
            }
            synchronized (mJobQueue) {
                if (mJobQueue.size() == MAX_MESSAGE_QUEUE_LENGTH) {
                    throw new RuntimeException("Camera master thread job queue full");
                }

                mJobQueue.add(job);
                mJobQueue.notifyAll();
            }
        }

        /**
         * Queues up the job and wait for it to be done.
         *
         * @param job The job to run.
         * @param timeoutMs Timeout limit in milliseconds.
         * @return Whether the waiting is interrupted.
         */
        public boolean runJobSync(final Runnable job, Object waitLock, long timeoutMs) {
            synchronized (waitLock) {
                long timeoutBound = SystemClock.uptimeMillis() + timeoutMs;
                try {
                    runJob(job);
                    waitLock.wait();
                } catch (InterruptedException ex) {
                    Log.v(TAG, "Job interrupted");
                    if (SystemClock.uptimeMillis() > timeoutBound) {
                        // Timeout.
                        Log.v(TAG, "Timeout waiting camera operation");
                    }
                    return false;
                }
            }
            return true;
        }

        /**
         * Gracefully ends this thread. Will stop after all jobs are processed.
         */
        public void end() {
            synchronized (mIsEnded) {
                mIsEnded = true;
            }
            synchronized(mJobQueue) {
                mJobQueue.notifyAll();
            }
        }

        private boolean isEnded() {
            synchronized (mIsEnded) {
                return mIsEnded;
            }
        }

        @Override
        public void run() {
            while(true) {
                Runnable job = null;
                synchronized (mJobQueue) {
                    while (mJobQueue.size() == 0 && !isEnded()) {
                        try {
                            mJobQueue.wait();
                        } catch (InterruptedException ex) {
                            Log.v(TAG, "Dispatcher thread wait() interrupted, exiting");
                            break;
                        }
                    }

                    job = mJobQueue.poll();
                }

                if (job == null) {
                    // mJobQueue.poll() returning null means wait() is
                    // interrupted and the queue is empty.
                    if (isEnded()) {
                        break;
                    }
                    continue;
                }

                job.run();

                synchronized (DispatchThread.this) {
                    mCameraHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (DispatchThread.this) {
                                DispatchThread.this.notifyAll();
                            }
                        }
                    });
                    try {
                        DispatchThread.this.wait();
                    } catch (InterruptedException ex) {
                        // TODO: do something here.
                    }
                }
            }
            mCameraHandlerThread.quitSafely();
        }
    }

    @Override
    public void cameraOpen(final Handler handler, final int cameraId,
            final CameraOpenCallback callback) {
        mDispatchThread.runJob(new Runnable() {
            @Override
            public void run() {
                mCameraHandler.obtainMessage(OPEN_CAMERA, cameraId, 0,
                        CameraOpenCallbackForward.getNewInstance(handler, callback)).sendToTarget();
            }
        });
    }

    /**
     * A class which implements {@link CameraManager.CameraProxy} and
     * camera handler thread.
     */
    private class AndroidCameraProxyImpl implements CameraManager.CameraProxy {
        private final int mCameraId;
        /* TODO: remove this Camera instance. */
        private final Camera mCamera;

        private AndroidCameraProxyImpl(int cameraId, Camera camera) {
            mCamera = camera;
            mCameraId = cameraId;
        }

        @Override
        public android.hardware.Camera getCamera() {
            return mCamera;
        }

        @Override
        public int getCameraId() {
            return mCameraId;
        }

        // TODO: Make this package private.
        @Override
        public void release(boolean sync) {
            Log.v(TAG, "camera manager release");
            if (sync) {
                final WaitDoneBundle bundle = new WaitDoneBundle();
                mDispatchThread.runJobSync(new Runnable() {
                    @Override
                    public void run() {
                        mCameraHandler.sendEmptyMessage(RELEASE);
                        mCameraHandler.post(bundle.mUnlockRunnable);
                    }
                }, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS);
            } else {
                mDispatchThread.runJob(new Runnable() {
                    @Override
                    public void run() {
                        mCameraHandler.removeCallbacksAndMessages(null);
                        mCameraHandler.sendEmptyMessage(RELEASE);
                    }
                });
            }
        }

        @Override
        public void reconnect(final Handler handler, final CameraOpenCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(RECONNECT, mCameraId, 0,
                            CameraOpenCallbackForward.getNewInstance(handler, cb)).sendToTarget();
                }
            });
        }

        @Override
        public void unlock() {
            final WaitDoneBundle bundle = new WaitDoneBundle();
            mDispatchThread.runJobSync(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(UNLOCK);
                    mCameraHandler.post(bundle.mUnlockRunnable);
                }
            }, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS);
        }

        @Override
        public void lock() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(LOCK);
                }
            });
        }

        @Override
        public void setPreviewTexture(final SurfaceTexture surfaceTexture) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC, surfaceTexture)
                            .sendToTarget();
                }
            });
        }

        @Override
        public void setPreviewTextureSync(final SurfaceTexture surfaceTexture) {
            final WaitDoneBundle bundle = new WaitDoneBundle();
            mDispatchThread.runJobSync(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_PREVIEW_TEXTURE_ASYNC, surfaceTexture)
                            .sendToTarget();
                    mCameraHandler.post(bundle.mUnlockRunnable);
                }
            }, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS);
        }

        @Override
        public void setPreviewDisplay(final SurfaceHolder surfaceHolder) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_PREVIEW_DISPLAY_ASYNC, surfaceHolder)
                            .sendToTarget();
                }
            });
        }

        @Override
        public void startPreview() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(START_PREVIEW_ASYNC, null).sendToTarget();
                }
            });
        }

        @Override
        public void startPreviewWithCallback(final Handler handler,
                final CameraStartPreviewCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(START_PREVIEW_ASYNC,
                        CameraStartPreviewCallbackForward.getNewInstance(handler, cb)).sendToTarget();
                }
            });
        }

        @Override
        public void stopPreview() {
            final WaitDoneBundle bundle = new WaitDoneBundle();
            mDispatchThread.runJobSync(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(STOP_PREVIEW);
                    mCameraHandler.post(bundle.mUnlockRunnable);
                }
            }, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS);
        }

        @Override
        public void setPreviewDataCallback(
                final Handler handler, final CameraPreviewDataCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK, PreviewCallbackForward
                            .getNewInstance(handler, AndroidCameraProxyImpl.this, cb))
                            .sendToTarget();
                }
            });
        }
        @Override
        public void setOneShotPreviewCallback(final Handler handler,
                final CameraPreviewDataCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_ONE_SHOT_PREVIEW_CALLBACK,
                            PreviewCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, cb))
                            .sendToTarget();
                }
            });
        }

        @Override
        public void setPreviewDataCallbackWithBuffer(
                final Handler handler, final CameraPreviewDataCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_PREVIEW_CALLBACK_WITH_BUFFER,
                            PreviewCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, cb))
                            .sendToTarget();
                }
            });
        }

        @Override
        public void addCallbackBuffer(final byte[] callbackBuffer) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(ADD_CALLBACK_BUFFER, callbackBuffer)
                            .sendToTarget();
                }
            });
        }

        @Override
        public void autoFocus(final Handler handler, final CameraAFCallback cb) {
            final AutoFocusCallback afCallback = new AutoFocusCallback() {
                @Override
                public void onAutoFocus(final boolean b, Camera camera) {
                    if (mCameraState.getState() != CAMERA_FOCUSING) {
                        Log.w(TAG, "onAutoFocus callback returning when not focusing");
                    } else {
                        mCameraState.setState(CAMERA_IDLE);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onAutoFocus(b, AndroidCameraProxyImpl.this);
                        }
                    });
                }
            };
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraState.waitForStates(CAMERA_IDLE);
                    mCameraHandler.obtainMessage(AUTO_FOCUS, afCallback).sendToTarget();
                }
            });
        }

        @Override
        public void cancelAutoFocus() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.removeMessages(AUTO_FOCUS);
                    mCameraHandler.sendEmptyMessage(CANCEL_AUTO_FOCUS);
                }
            });
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void setAutoFocusMoveCallback(
                final Handler handler, final CameraAFMoveCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_AUTO_FOCUS_MOVE_CALLBACK, AFMoveCallbackForward
                            .getNewInstance(handler, AndroidCameraProxyImpl.this, cb))
                            .sendToTarget();
                }
            });
        }

        @Override
        public void takePicture(
                final Handler handler, final CameraShutterCallback shutter,
                final CameraPictureCallback raw, final CameraPictureCallback post,
                final CameraPictureCallback jpeg) {
            final PictureCallback jpegCallback = new PictureCallback() {
                @Override
                public void onPictureTaken(final byte[] data, Camera camera) {
                    if (mCameraState.getState() != CAMERA_CAPTURING) {
                        Log.v(TAG, "picture callback returning when not capturing");
                    } else {
                        mCameraState.setState(CAMERA_IDLE);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            jpeg.onPictureTaken(data, AndroidCameraProxyImpl.this);
                        }
                    });
                }
            };

            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraState.waitForStates(CAMERA_IDLE);
                    mCameraHandler.requestTakePicture(ShutterCallbackForward
                            .getNewInstance(handler, AndroidCameraProxyImpl.this, shutter),
                            PictureCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, raw),
                            PictureCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, post),
                            jpegCallback);
                }
            });
        }

        @Override
        public void setDisplayOrientation(final int degrees) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_DISPLAY_ORIENTATION, degrees, 0)
                            .sendToTarget();
                }
            });
        }

        @Override
        public void setZoomChangeListener(final OnZoomChangeListener listener) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_ZOOM_CHANGE_LISTENER, listener).sendToTarget();
                }
            });
        }

        @Override
        public void setFaceDetectionCallback(final Handler handler,
                final CameraFaceDetectionCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_FACE_DETECTION_LISTENER,
                            FaceDetectionCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, cb))
                            .sendToTarget();
                }
            });
        }

        @Override
        public void startFaceDetection() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(START_FACE_DETECTION);
                }
            });
        }

        @Override
        public void stopFaceDetection() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(STOP_FACE_DETECTION);
                }
            });
        }

        @Override
        public void setErrorCallback(final Handler handler, final CameraErrorCallback cb) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(SET_ERROR_CALLBACK, ErrorCallbackForward
                                    .getNewInstance(handler, AndroidCameraProxyImpl.this, cb)
                    ).sendToTarget();
                }
            });
        }

        @Override
        public void setParameters(final Parameters params) {
            if (params == null) {
                Log.v(TAG, "null parameters in setParameters()");
                return;
            }
            final String flattenedParameters = params.flatten();
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraState.waitForStates(CAMERA_IDLE | CAMERA_UNLOCKED);
                    mCameraHandler.obtainMessage(SET_PARAMETERS, flattenedParameters).sendToTarget();
                }
            });
        }

        @Override
        public Parameters getParameters() {
            final WaitDoneBundle bundle = new WaitDoneBundle();
            mDispatchThread.runJobSync(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(GET_PARAMETERS);
                    mCameraHandler.post(bundle.mUnlockRunnable);
                }
            }, bundle.mWaitLock, CAMERA_OPERATION_TIMEOUT_MS);
            return mParameters;
        }

        @Override
        public void refreshParameters() {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.sendEmptyMessage(REFRESH_PARAMETERS);
                }
            });
        }

        @Override
        public void enableShutterSound(final boolean enable) {
            mDispatchThread.runJob(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.obtainMessage(ENABLE_SHUTTER_SOUND, (enable ? 1 : 0), 0)
                            .sendToTarget();
                }
            });
        }

    }

    private static class WaitDoneBundle {
        public Runnable mUnlockRunnable;
        private Object mWaitLock;

        WaitDoneBundle() {
            mWaitLock = new Object();
            mUnlockRunnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (mWaitLock) {
                        mWaitLock.notifyAll();
                    }
                }
            };
        }
    }

    /**
     * A helper class to forward AutoFocusCallback to another thread.
     */
    private static class AFCallbackForward implements AutoFocusCallback {
        private final Handler mHandler;
        private final CameraProxy mCamera;
        private final CameraAFCallback mCallback;

        /**
         * Returns a new instance of {@link AFCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link AFCallbackForward},
         *                or null if any parameter is null.
         */
        public static AFCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraAFCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new AFCallbackForward(handler, camera, cb);
        }

        private AFCallbackForward(
                Handler h, CameraProxy camera, CameraAFCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onAutoFocus(final boolean b, Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onAutoFocus(b, mCamera);
                }
            });
        }
    }

    /**
     * A helper class to forward ErrorCallback to another thread.
     */
    private static class ErrorCallbackForward implements Camera.ErrorCallback {
        private final Handler mHandler;
        private final CameraProxy mCamera;
        private final CameraErrorCallback mCallback;

        /**
         * Returns a new instance of {@link AFCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link AFCallbackForward},
         *                or null if any parameter is null.
         */
        public static ErrorCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraErrorCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new ErrorCallbackForward(handler, camera, cb);
        }

        private ErrorCallbackForward(
                Handler h, CameraProxy camera, CameraErrorCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onError(final int error, Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onError(error, mCamera);
                }
            });
        }
    }

    /** A helper class to forward AutoFocusMoveCallback to another thread. */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static class AFMoveCallbackForward implements AutoFocusMoveCallback {
        private final Handler mHandler;
        private final CameraAFMoveCallback mCallback;
        private final CameraProxy mCamera;

        /**
         * Returns a new instance of {@link AFMoveCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link AFMoveCallbackForward},
         *                or null if any parameter is null.
         */
        public static AFMoveCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraAFMoveCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new AFMoveCallbackForward(handler, camera, cb);
        }

        private AFMoveCallbackForward(
                Handler h, CameraProxy camera, CameraAFMoveCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onAutoFocusMoving(
                final boolean moving, android.hardware.Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onAutoFocusMoving(moving, mCamera);
                }
            });
        }
    }

    /**
     * A helper class to forward ShutterCallback to to another thread.
     */
    private static class ShutterCallbackForward implements ShutterCallback {
        private final Handler mHandler;
        private final CameraShutterCallback mCallback;
        private final CameraProxy mCamera;

        /**
         * Returns a new instance of {@link ShutterCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link ShutterCallbackForward},
         *                or null if any parameter is null.
         */
        public static ShutterCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraShutterCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new ShutterCallbackForward(handler, camera, cb);
        }

        private ShutterCallbackForward(
                Handler h, CameraProxy camera, CameraShutterCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onShutter() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onShutter(mCamera);
                }
            });
        }
    }

    /**
     * A helper class to forward PictureCallback to another thread.
     */
    private static class PictureCallbackForward implements PictureCallback {
        private final Handler mHandler;
        private final CameraPictureCallback mCallback;
        private final CameraProxy mCamera;

        /**
         * Returns a new instance of {@link PictureCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link PictureCallbackForward},
         *                or null if any parameters is null.
         */
        public static PictureCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraPictureCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new PictureCallbackForward(handler, camera, cb);
        }

        private PictureCallbackForward(
                Handler h, CameraProxy camera, CameraPictureCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onPictureTaken(
                final byte[] data, android.hardware.Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onPictureTaken(data, mCamera);
                }
            });
        }
    }

    /**
     * A helper class to forward PreviewCallback to another thread.
     */
    private static class PreviewCallbackForward implements PreviewCallback {
        private final Handler mHandler;
        private final CameraPreviewDataCallback mCallback;
        private final CameraProxy mCamera;

        /**
         * Returns a new instance of {@link PreviewCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link PreviewCallbackForward},
         *                or null if any parameters is null.
         */
        public static PreviewCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraPreviewDataCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new PreviewCallbackForward(handler, camera, cb);
        }

        private PreviewCallbackForward(
                Handler h, CameraProxy camera, CameraPreviewDataCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onPreviewFrame(
                final byte[] data, android.hardware.Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onPreviewFrame(data, mCamera);
                }
            });
        }
    }

    private static class FaceDetectionCallbackForward implements FaceDetectionListener {
        private final Handler mHandler;
        private final CameraFaceDetectionCallback mCallback;
        private final CameraProxy mCamera;

        /**
         * Returns a new instance of {@link FaceDetectionCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param camera  The {@link CameraProxy} which the callback is from.
         * @param cb      The callback to be invoked.
         * @return        The instance of the {@link FaceDetectionCallbackForward},
         *                or null if any parameter is null.
         */
        public static FaceDetectionCallbackForward getNewInstance(
                Handler handler, CameraProxy camera, CameraFaceDetectionCallback cb) {
            if (handler == null || camera == null || cb == null) return null;
            return new FaceDetectionCallbackForward(handler, camera, cb);
        }

        private FaceDetectionCallbackForward(
                Handler h, CameraProxy camera, CameraFaceDetectionCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onFaceDetection(
                final Camera.Face[] faces, Camera camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onFaceDetection(faces, mCamera);
                }
            });
        }
    }

    /**
     * A callback helps to invoke the original callback on another
     * {@link android.os.Handler}.
     */
    private static class CameraOpenCallbackForward implements CameraOpenCallback {
        private final Handler mHandler;
        private final CameraOpenCallback mCallback;

        /**
         * Returns a new instance of {@link FaceDetectionCallbackForward}.
         *
         * @param handler The handler in which the callback will be invoked in.
         * @param cb The callback to be invoked.
         * @return The instance of the {@link FaceDetectionCallbackForward}, or
         *         null if any parameter is null.
         */
        public static CameraOpenCallbackForward getNewInstance(
                Handler handler, CameraOpenCallback cb) {
            if (handler == null || cb == null) {
                return null;
            }
            return new CameraOpenCallbackForward(handler, cb);
        }

        private CameraOpenCallbackForward(Handler h, CameraOpenCallback cb) {
            // Given that we are using the main thread handler, we can create it
            // here instead of holding onto the PhotoModule objects. In this
            // way, we can avoid memory leak.
            mHandler = new Handler(Looper.getMainLooper());
            mCallback = cb;
        }

        @Override
        public void onCameraOpened(final CameraProxy camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCameraOpened(camera);
                }
            });
        }

        @Override
        public void onCameraDisabled(final int cameraId) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCameraDisabled(cameraId);
                }
            });
        }

        @Override
        public void onDeviceOpenFailure(final int cameraId) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onDeviceOpenFailure(cameraId);
                }
            });
        }

        @Override
        public void onDeviceOpenedAlready(final int cameraId) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onDeviceOpenedAlready(cameraId);
                }
            });
        }

        @Override
        public void onReconnectionFailure(final CameraManager mgr) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onReconnectionFailure(mgr);
                }
            });
        }
    }

    private static class CameraStartPreviewCallbackForward
            implements CameraStartPreviewCallback {
        private final Handler mHandler;
        private final CameraStartPreviewCallback mCallback;

        public static CameraStartPreviewCallbackForward getNewInstance(
                Handler handler, CameraStartPreviewCallback cb) {
            if (handler == null || cb == null) {
                return null;
            }
            return new CameraStartPreviewCallbackForward(handler, cb);
        }

        private CameraStartPreviewCallbackForward(Handler h,
                CameraStartPreviewCallback cb) {
            mHandler = h;
            mCallback = cb;
        }

        @Override
        public void onPreviewStarted() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onPreviewStarted();
                }
            });
        }
    }
}
