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
     * Callback when the data item is promoted. A data is promoted if the user
     * swipe up a data vertically.
     *
     * @param dataID The ID of the promoted data.
     */
    public void onDataPromoted(int dataID);

    /**
     * Callback when the data item is demoted. A data is promoted if the user
     * swipe down a data vertically.
     *
     * @param dataID The ID of the demoted data.
     */
    public void onDataDemoted(int dataID);

    /**
     *
     * Called when all the data has been reloaded.
     */
    public void onDataReloaded();

    /**
     * The callback when the item enters full-screen state.
     *
     * @param dataId The ID of the current focused image data.
     */
    public void onEnterFullScreen(int dataId);

    /**
     * The callback when the item leaves full-screen.
     *
     * @param dataId The ID of the current focused image data.
     */
    public void onLeaveFullScreen(int dataId);

    /**
     * The callback when the item enters filmstrip.
     *
     * @param dataId The ID of the current focused image data.
     */
    public void onEnterFilmstrip(int dataId);

    /**
     * The callback when the item leaves filmstrip.
     *
     * @param dataId The ID of the current focused image data.
     */
    public void onLeaveFilmstrip(int dataId);

    /**
     * The callback when the item enters zoom view.
     *
     * @param dataID
     */
    public void onEnterZoomView(int dataID);

    /**
     * The callback when the data focus changed.
     *
     * @param prevDataId The ID of the previously focused data or {@code -1} if
     *                   none.
     * @param newDataId The ID of the focused data of {@code -1} if none.
     */
    public void onDataFocusChanged(int prevDataId, int newDataId);
}
