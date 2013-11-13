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

import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.filmstrip.FilmstripController;

/**
 * The interface defining app-level UI.
 */
public interface AppUi {

    /**
     * Initializes the UI.
     *
     * @param root The layout root of app UI.
     * @param isSecureCamera Whether the app is in secure camera mode.
     * @param isCaptureIntent Whether the app is in capture intent mode.
     */
    public void init(View root, boolean isSecureCamera, boolean isCaptureIntent);

    /**
     * Returns the module layout root.
     */
    public FrameLayout getModuleLayoutRoot();

    /**
     * Returns the filmstrip controller.
     */
    public FilmstripController getFilmstripController();
}
