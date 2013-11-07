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
import android.net.Uri;

/**
 * The controller at app level.
 */
public interface AppController {

    /**
     * Returns the {@link android.content.Context} being used.
     */
    public Context getAndroidContext();

    // Shutter button.

    /**
     * Sets the callback when the shutter is clicked.
     */
    public void setOnShutterClickedCallback(Runnable r);

    /**
     * Sets the callback when the shutter is long-pressed.
     */
    public void setOnShutterLongPressCallback(Runnable r);

    /**
     * Sets the callback when the shutter is pressed.
     */
    public void setOnShutterPressedCallback(Runnable r);

    /**
     * Sets the callback when the shutter is released.
     */
    public void setOnShutterReleasedCallback(Runnable r);

    /**
     * Enables/Disables the shutter.
     */
    public void setShutterEnabled(boolean enabled);

    /**
     * Checks whether the shutter is enabled.
     */
    public boolean isShutterEnabled();

    // Media saving.

    /**
     * Adds a new media to the filmstrip.
     */
    public void addNewMediaToFilmstrip(Uri uri);
}
