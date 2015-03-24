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

import android.content.Context;
import android.view.View;

import com.android.camera.data.LocalData.ActionCallback;

/**
 * An interface which defines the interactions between the
 * {@link ImageData} and the
 * {@link com.android.camera.widget.FilmstripView}.
 */
public interface DataAdapter {
    /**
     * An interface which defines the update reporter used to return to the
     * {@link com.android.camera.filmstrip.FilmstripController.FilmstripListener}.
     */
    public interface UpdateReporter {
        /** Checks if the data of dataID is removed. */
        public boolean isDataRemoved(int dataID);

        /** Checks if the data of dataID is updated. */
        public boolean isDataUpdated(int dataID);
    }

    /**
     * An interface which defines the listener for data events over
     * {@link ImageData}. Usually
     * {@link com.android.camera.widget.FilmstripView} itself.
     */
    public interface Listener {
        /**
         * Called when the whole data loading is done. There is not any
         * assumption on the previous data.
         */
        public void onDataLoaded();

        /**
         * Called when some of the data is updated.
         *
         * @param reporter Use this reporter to know what happened.
         */
        public void onDataUpdated(UpdateReporter reporter);

        /**
         * Called when a new data item is inserted.
         *
         * @param dataID The ID of the inserted data.
         * @param data The inserted data.
         */
        public void onDataInserted(int dataID, ImageData data);

        /**
         * Called when a data item is removed.
         *
         * @param dataID The ID of the removed data.
         * @param data The data.
         */
        public void onDataRemoved(int dataID, ImageData data);
    }

    /** Returns the total number of image data. */
    public int getTotalNumber();

    /**
     * Returns the view to visually present the image data.
     *
     * @param context The {@link android.content.Context} to create the view.
     * @param recycled A view that can be reused if one is available, or null.
     * @param dataID The ID of the image data to be presented.
     * @return The view representing the image data. Null if unavailable or
     *         the {@code dataID} is out of range.
     */
    public View getView(Context context, View recycled, int dataID, ActionCallback actionCallback);

    /** Returns a unique identifier for the view created by this data so that the view
     * can be reused.
     *
     * @see android.widget.BaseAdapter#getItemViewType(int)
     */
    public int getItemViewType(int dataId);

    /**
     * Resizes the view used to visually present the image data.  This is
     * useful when the view contains a bitmap.
     *
     * @param context The {@link android.content.Context} to create the view.
     * @param dataID The ID of the resize data to be presented.
     * @param view The view to update that was created by getView().
     * @param w Width in pixels of rendered view.
     * @param h Height in pixels of rendered view.
     */
    public void resizeView(Context context, int dataID, View view, int w, int h);

    /**
     * Returns the {@link ImageData} specified by the ID.
     *
     * @param dataID The ID of the {@link ImageData}.
     * @return The specified {@link ImageData}. Null if not available.
     */
    public ImageData getImageData(int dataID);

    /**
     * Suggests the data adapter the maximum possible size of the layout so
     * the {@link DataAdapter} can optimize the view returned for the
     * {@link ImageData}.
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

    /**
     * Returns whether the view of the data can be moved by swipe
     * gesture when in full-screen.
     *
     * @param dataID The ID of the data.
     * @return Whether the view can be moved.
     */
    public boolean canSwipeInFullScreen(int dataID);
}
