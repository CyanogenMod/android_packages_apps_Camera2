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

import com.android.camera.app.AppController;

/**
 * The controller at app level.
 */
public interface ModuleController {
    // Lifecycle controls.

    /**
     * Initializes the module.
     * @param app The app which initializes this module.
     */
    public void init(AppController app);

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
    public void destory();

    /**
     * Called by the app when the preview size is changed.
     *
     * @param width The new width.
     * @param height The new height.
     */
    public void onPreviewSizeChanged(int width, int height);

    /**
     * Called by the app when the camera is available. The module should use
     * {@link com.android.camera.app.AppController#}
     */
    public void onCameraAvailable();

    /**
     * Called by the app when the {@link com.android.camera.app.MediaSaver} is
     * available.
     */
    public void onMediaSaverAvailable();
}
