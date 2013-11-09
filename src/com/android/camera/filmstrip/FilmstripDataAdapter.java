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

import android.app.Activity;
import android.view.View;

/**
 * An interfaces which defines the interactions between the
 * {@link FilmstripImageData} and the {@link com.android.camera.ui.FilmstripView}.
 */
public interface FilmstripDataAdapter {
    /**
     * An interface which defines the update report used to return to the
     * {@link FilmstripListener}.
     */
    public interface UpdateReporter {
        /** Checks if the data of dataID is removed. */
        public boolean isDataRemoved(int dataID);

        /** Checks if the data of dataID is updated. */
        public boolean isDataUpdated(int dataID);
    }

    /**
     * An interface which defines the listener for data events over
     * {@link FilmstripImageData}. Usually {@link com.android.camera.ui.FilmstripView} itself.
     */
    public interface Listener {
        // Called when the whole data loading is done. No any assumption
        // on previous data.
        public void onDataLoaded();

        // Only some of the data is changed. The listener should check
        // if any thing needs to be updated.
        public void onDataUpdated(UpdateReporter reporter);

        public void onDataInserted(int dataID, FilmstripImageData data);

        public void onDataRemoved(int dataID, FilmstripImageData data);
    }

    /** Returns the total number of image data */
    public int getTotalNumber();

    /**
     * Returns the view to visually present the image data.
     *
     * @param activity The {@link android.app.Activity} context to create the view.
     * @param dataID The ID of the image data to be presented.
     * @return The view representing the image data. Null if unavailable or
     *         the {@code dataID} is out of range.
     */
    public View getView(Activity activity, int dataID);

    /**
     * Returns the {@link FilmstripImageData} specified by the ID.
     *
     * @param dataID The ID of the {@link FilmstripImageData}.
     * @return The specified {@link FilmstripImageData}. Null if not available.
     */
    public FilmstripImageData getImageData(int dataID);

    /**
     * Suggests the data adapter the maximum possible size of the layout so
     * the {@link FilmstripDataAdapter} can optimize the view returned for the
     * {@link FilmstripImageData}.
     *
     * @param w Maximum width.
     * @param h Maximum height.
     */
    public void suggestViewSizeBound(int w, int h);

    /**
     * Sets the listener for data events over the ImageData.
     *
     * @param listener The listener to use.
     */
    public void setListener(Listener listener);

    /**
     * Returns {@code true} if the view of the data can be moved by swipe
     * gesture when in full-screen.
     *
     * @param dataID The ID of the data.
     * @return {@code true} if the view can be moved, {@code false}
     *         otherwise.
     */
    public boolean canSwipeInFullScreen(int dataID);
}
