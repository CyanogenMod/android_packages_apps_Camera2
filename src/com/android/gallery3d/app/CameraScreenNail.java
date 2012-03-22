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

package com.android.gallery3d.app;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.ScreenNailHolder;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

// This is a ScreenNail which displays camera preview. This demos the usage of
// SurfaceTextureScreenNail. It is not intended for production use.
class CameraScreenNail extends SurfaceTextureScreenNail {
    private static final String TAG = "CameraScreenNail";
    private static final int CAMERA_ID = 0;
    private static final int PREVIEW_WIDTH = 960;
    private static final int PREVIEW_HEIGHT = 720;
    private static final int MSG_START_CAMERA = 1;
    private static final int MSG_STOP_CAMERA = 2;

    public interface Listener {
        void requestRender();
    }

    private Activity mActivity;
    private Listener mListener;
    private int mOrientation;
    private Camera mCamera;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private volatile boolean mVisible;
    private volatile boolean mHasFrame;

    public CameraScreenNail(Activity activity, Listener listener) {
        mActivity = activity;
        mListener = listener;

        mOrientation = getCameraDisplayOrientation(mActivity, CAMERA_ID);
        if (mOrientation % 180 == 0) {
            setSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        } else {
            setSize(PREVIEW_HEIGHT, PREVIEW_WIDTH);
        }

        mHandlerThread = new HandlerThread("Camera");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
                public void handleMessage(Message message) {
                    if (message.what == MSG_START_CAMERA && mCamera == null) {
                        startCamera();
                    } else if (message.what == MSG_STOP_CAMERA && mCamera != null) {
                        stopCamera();
                    }
                }
            };
        mHandler.sendEmptyMessage(MSG_START_CAMERA);
    }

    private void startCamera() {
        try {
            acquireSurfaceTexture();
            Camera camera = Camera.open(CAMERA_ID);
            Camera.Parameters param = camera.getParameters();
            param.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            camera.setParameters(param);
            camera.setDisplayOrientation(mOrientation);
            camera.setPreviewTexture(getSurfaceTexture());
            camera.startPreview();
            synchronized (this) {
                mCamera = camera;
            }
        } catch (Throwable th) {
            Log.e(TAG, "cannot open camera", th);
        }
    }

    private void stopCamera() {
        releaseSurfaceTexture();
        mCamera.stopPreview();
        mCamera.release();
        synchronized (this) {
            mCamera = null;
            notifyAll();
        }
        mHasFrame = false;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        if (!mVisible) {
            mVisible = true;
            // Only send one message when mVisible makes transition from
            // false to true.
            mHandler.sendEmptyMessage(MSG_START_CAMERA);
        }

        if (mVisible && mHasFrame) {
            super.draw(canvas, x, y, width, height);
        }
    }

    @Override
    public void noDraw() {
        mVisible = false;
    }

    @Override
    public void pauseDraw() {
        mVisible = false;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mHasFrame = true;
        if (mVisible) {
            // We need to ask for re-render if the SurfaceTexture receives a new
            // frame (and we are visible).
            mListener.requestRender();
        }
    }

    public void destroy() {
        synchronized (this) {
            mHandler.sendEmptyMessage(MSG_STOP_CAMERA);

            // Wait until camera is closed.
            while (mCamera != null) {
                try {
                    wait();
                } catch (Exception ex) {
                    // ignore.
                }
            }
        }
        mHandlerThread.quit();
    }

    // The three methods below are copied from Camera.java
    private static int getCameraDisplayOrientation(
            Activity activity, int cameraId) {
        int displayRotation = getDisplayRotation(activity);
        int displayOrientation = getDisplayOrientation(
                displayRotation, cameraId);
        return displayOrientation;
    }

    private static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }

    private static int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }
}

// This holds a CameraScreenNail, so we can pass it to a PhotoPage.
class CameraScreenNailHolder extends ScreenNailHolder
        implements CameraScreenNail.Listener {
    private static final String TAG = "CameraScreenNailHolder";
    private GalleryActivity mActivity;
    private CameraScreenNail mCameraScreenNail;

    public CameraScreenNailHolder(GalleryActivity activity) {
        mActivity = activity;
    }

    @Override
    public void requestRender() {
        mActivity.getGLRoot().requestRender();
    }

    @Override
    public ScreenNail attach() {
        mCameraScreenNail = new CameraScreenNail((Activity) mActivity, this);
        return mCameraScreenNail;
    }

    @Override
    public void detach() {
        mCameraScreenNail.destroy();
        mCameraScreenNail = null;
    }
}
