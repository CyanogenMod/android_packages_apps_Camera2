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

package com.android.camera.module;

import android.content.res.Configuration;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraManager;

/**
 * The controller at app level.
 */
public interface ModuleController {

    /********************** Life cycle management **********************/

    /**
     * Initializes the module.
     *
     * @param app The app which initializes this module.
     * @param isSecureCamera Whether the app is in secure camera mode.
     * @param isCaptureIntent Whether the app is in capture intent mode.
     */
    public void init(AppController app, boolean isSecureCamera, boolean isCaptureIntent);

    /**
     * Resumes the module. Always call this method whenever it's being put in
     * the foreground.
     */
    public void resume();

    /**
     * Pauses the module. Always call this method whenever it's being put in the
     * background.
     */
    public void pause();

    /**
     * Destroys the module. Always call this method to release the resources used
     * by this module.
     */
    public void destroy();

    /********************** UI / Camera preview **********************/

    /**
     * Called when the preview becomes visible/invisible.
     *
     * @param visible Whether the preview is visible.
     */
    public void onPreviewVisibilityChanged(boolean visible);

    /**
     * Called by the app when the preview size is changed.
     *
     * @param width The new width.
     * @param height The new height.
     */
    public void onPreviewSizeChanged(int width, int height);

    /**
     * Called when the framework layout orientation changed.
     *
     * @param isLandscape Whether the new orientation is landscape or portrait.
     */
    public void onLayoutOrientationChanged(boolean isLandscape);

    /**
     * Called when the UI orientation is changed.
     *
     * @param orientation The new orientation, valid values are 0, 90, 180 and
     *                    270.
     */
    public void onOrientationChanged(int orientation);

    /**
     * Called when back key is pressed.
     *
     * @return Whether the back key event is processed.
     */
    public abstract boolean onBackPressed();

    /********************** App-level resources **********************/

    /**
     * Called by the app when the camera is available. The module should use
     * {@link com.android.camera.app.AppController#}
     *
     * @param cameraProxy The camera device proxy.
     */
    public void onCameraAvailable(CameraManager.CameraProxy cameraProxy);
}
