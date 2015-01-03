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

import android.view.KeyEvent;
import android.view.View;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.CameraServices;
import com.android.camera.module.ModuleController;

public abstract class CameraModule implements ModuleController {
    /** Provides common services and functionality to the module. */
    private final CameraServices mServices;
    private final CameraProvider mCameraProvider;

    public CameraModule(AppController app) {
        mServices = app.getServices();
        mCameraProvider = app.getCameraProvider();
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public void onPreviewVisibilityChanged(int visibility) {
        // Do nothing.
    }

    @Deprecated
    public abstract boolean onKeyDown(int keyCode, KeyEvent event);

    @Deprecated
    public abstract boolean onKeyUp(int keyCode, KeyEvent event);

    @Deprecated
    public void onSingleTapUp(View view, int x, int y) {
    }

    /**
     * @return An instance containing common services to be used by the module.
     */
    protected CameraServices getServices() {
        return mServices;
    }

    /**
     * @return An instance used by the module to get the camera.
     */
    protected CameraProvider getCameraProvider() {
        return mCameraProvider;
    }

    /**
     * Requests the back camera through {@link CameraProvider}.
     * This calls {@link
     * com.android.camera.app.CameraProvider#requestCamera(int)}. The camera
     * will be returned through {@link
     * #onCameraAvailable(com.android.ex.camera2.portability.CameraAgent.CameraProxy)}
     * when it's available. This is a no-op when there's no back camera
     * available.
     */
    protected void requestBackCamera() {
        int backCameraId = mCameraProvider.getFirstBackCameraId();
        if (backCameraId != -1) {
            mCameraProvider.requestCamera(backCameraId);
        }
    }

    public void onPreviewInitialDataReceived() {}

    /**
     * Releases the back camera through {@link CameraProvider}.
     * This calls {@link
     * com.android.camera.app.CameraProvider#releaseCamera(int)}.
     * This is a no-op when there's no back camera available.
     */
    protected void releaseBackCamera() {
        int backCameraId = mCameraProvider.getFirstBackCameraId();
        if (backCameraId != -1) {
            mCameraProvider.releaseCamera(backCameraId);
        }
    }

    /**
     * @return An accessibility String to be announced during the peek animation.
     */
    public abstract String getPeekAccessibilityString();

    @Override
    public void onShutterButtonLongPressed() {
        // noop
    }
}
