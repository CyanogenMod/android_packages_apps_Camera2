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

import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.CameraServices;
import com.android.camera.app.MediaSaver;
import com.android.camera.module.ModuleController;

public abstract class CameraModule implements ModuleController {

    /** Provides common services and functionality to the module. */
    private final CameraServices mServices;
    private final CameraProvider mCameraProvider;

    public CameraModule(AppController app) {
        mServices = app.getServices();
        mCameraProvider = app.getCameraProvider();
    }

    @Deprecated
    public abstract void onPreviewFocusChanged(boolean previewFocused);

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Deprecated
    public abstract boolean onKeyDown(int keyCode, KeyEvent event);

    @Deprecated
    public abstract boolean onKeyUp(int keyCode, KeyEvent event);

    @Deprecated
    public abstract void onSingleTapUp(View view, int x, int y);

    @Deprecated
    public abstract void onMediaSaverAvailable(MediaSaver s);

    @Deprecated
    public abstract boolean arePreviewControlsVisible();

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
     * #onCameraAvailable(com.android.camera.app.CameraManager.CameraProxy)}
     * when it's available.
     */
    protected void requestBackCamera() {
        mCameraProvider.requestCamera(mCameraProvider.getFirstBackCameraId());
    }
}
