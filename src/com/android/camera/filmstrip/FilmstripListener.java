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

package com.android.camera.filmstrip;

/**
 * An interface which defines the FilmStripView UI action listener.
 */
public interface FilmstripListener {
    /**
     * Callback when the data is promoted.
     *
     * @param dataID The ID of the promoted data.
     */
    public void onDataPromoted(int dataID);

    /**
     * Callback when the data is demoted.
     *
     * @param dataID The ID of the demoted data.
     */
    public void onDataDemoted(int dataID);

    /**
     * The callback when the item enters/leaves full-screen. TODO: Call this
     * function actually.
     *
     * @param dataID The ID of the image data.
     * @param fullScreen {@code true} if the data is entering full-screen.
     *            {@code false} otherwise.
     */
    public void onDataFullScreenChange(int dataID, boolean fullScreen);

    /**
     * Called when the data is reloaded.
     */
    public void onReload();

    /**
     * Called when the data is centered in the film strip.
     *
     * @param dataID the ID of the local data
     */
    public void onCurrentDataCentered(int dataID);

    /**
     * Called when the data is off centered in the film strip.
     *
     * @param dataID the ID of the local data
     */
    public void onCurrentDataOffCentered(int dataID);

    /**
     * The callback when the item is centered/off-centered.
     *
     * @param dataID The ID of the image data.
     * @param focused {@code true} if the data is focused.
     *            {@code false} otherwise.
     */
    public void onDataFocusChanged(int dataID, boolean focused);

    /**
     * Toggles the visibility of the ActionBar.
     *
     * @param dataID The ID of the image data.
     */
    public void onToggleSystemDecorsVisibility(int dataID);

    /**
     * Sets the visibility of system decors, including action bar and nav bar
     * @param visible The visibility of the system decors
     */
    public void setSystemDecorsVisibility(boolean visible);
}
