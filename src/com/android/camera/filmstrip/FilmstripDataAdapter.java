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

import android.view.View;

import com.android.camera.data.FilmstripItem;
import com.android.camera.data.FilmstripItem.VideoClickedCallback;

/**
 * An interface which defines the interactions between the
 * {@link FilmstripItem} and the
 * {@link com.android.camera.widget.FilmstripView}.
 */
public interface FilmstripDataAdapter {
    /**
     * An interface which defines the update reporter used to return to the
     * {@link com.android.camera.filmstrip.FilmstripController.FilmstripListener}.
     */
    public interface UpdateReporter {
        /** Checks if the data of dataID is removed. */
        public boolean isDataRemoved(int index);

        /** Checks if the data of dataID is updated. */
        public boolean isDataUpdated(int index);
    }

    /**
     * An interface which defines the listener for data events over
     * {@link FilmstripItem}. Usually
     * {@link com.android.camera.widget.FilmstripView} itself.
     */
    public interface Listener {
        /**
         * Called when the whole data loading is done. There is not any
         * assumption on the previous data.
         */
        public void onFilmstripItemLoaded();

        /**
         * Called when some of the data is updated.
         *
         * @param reporter Use this reporter to know what happened.
         */
        public void onFilmstripItemUpdated(UpdateReporter reporter);

        /**
         * Called when a new data item is inserted.
         *
         * @param index The ID of the inserted data.
         * @param item The inserted data.
         */
        public void onFilmstripItemInserted(int index, FilmstripItem item);

        /**
         * Called when a data item is removed.
         *
         * @param index The ID of the removed data.
         * @param item The data.
         */
        public void onFilmstripItemRemoved(int index, FilmstripItem item);
    }

    /** Returns the total number of image data. */
    public int getTotalNumber();

    /**
     * Returns the view to visually present the image data.
     *
     * @param recycled A view that can be reused if one is available, or null.
     * @param index The ID of the image data to be presented.
     * @return The view representing the image data. Null if unavailable or
     *         the {@code dataID} is out of range.
     */
    public View getView(View recycled, int index,
          VideoClickedCallback videoClickedCallback);

    /** Returns a unique identifier for the view created by this data so that the view
     * can be reused.
     *
     * @see android.widget.BaseAdapter#getItemViewType(int)
     */
    public int getItemViewType(int index);

    /**
     * Returns the {@link FilmstripItem} specified by the ID.
     *
     * @param index The ID of the {@link FilmstripItem}.
     * @return The specified {@link FilmstripItem}. Null if not available.
     */
    public FilmstripItem getFilmstripItemAt(int index);

    /**
     * Suggests the data adapter the maximum possible size of the layout so
     * the {@link FilmstripDataAdapter} can optimize the view returned for the
     * {@link FilmstripItem}.
     *
     * @param widthPixels Width in pixels of rendered view.
     * @param heightPixels Height in pixels of rendered view.
     */
    public void suggestViewSizeBound(int widthPixels, int heightPixels);

    /**
     * Sets the listener for data events over the ImageData. Replaces the
     * previous listener if it exists.
     *
     * @param listener The listener to use.
     */
    public void setListener(Listener listener);
}
