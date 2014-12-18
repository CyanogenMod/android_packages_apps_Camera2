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

package com.android.camera.data;

import android.net.Uri;
import android.os.AsyncTask;

import com.android.camera.filmstrip.FilmstripDataAdapter;
import com.android.camera.util.Callback;
import com.android.camera.widget.Preloader;

import java.util.List;

/**
 * An interface which extends {@link com.android.camera.filmstrip.FilmstripDataAdapter}
 * and defines operations on the data in the local camera folder.
 */
public interface LocalFilmstripDataAdapter extends FilmstripDataAdapter,
        Preloader.ItemLoader<Integer, AsyncTask>, Preloader.ItemSource<Integer> {

    public interface FilmstripItemListener {
        /**
         * Metadata of a {@link FilmstripItem} is loaded on
         * demand. Once the metadata is loaded this listener is notified.
         *
         * @param indexes The indexes of the data whose metadata has been
         *            updated.
         */
        public void onMetadataUpdated(List<Integer> indexes);
    }

    /**
     * Request for loading any photos that may have been added to the
     * media store since the last update.
     */
    public void requestLoadNewPhotos();

    /**
     * Request for loading the local data.
     */
    public void requestLoad(Callback<Void> onDone);

    /**
     * Returns the specified {@link FilmstripItem}.
     *
     * @param index The ID of the {@link FilmstripItem} to get.
     * @return The {@link FilmstripItem} to get. {@code null} if not available.
     */
    public FilmstripItem getItemAt(int index);

    /**
     * Remove the data in the local camera folder.
     *
     * @param index of data to be deleted.
     */
    public void removeAt(int index);

    /**
     * Adds new local data. The data is either inserted or updated, depending
     * on the existence of the Uri.
     *
     * @param item The new data.
     * @return Whether the data is newly inserted.
     */
    public boolean addOrUpdate(FilmstripItem item);

    /**
     * Refresh the data by {@link Uri}.
     *
     * @param uri The {@link Uri} of the data to refresh.
     */
    public void refresh(Uri uri);

    /**
     * Finds the {@link FilmstripItem} of the specified content Uri.
     *
     * @param uri The content Uri of the {@link FilmstripItem}.
     * @return The index of the data. {@code -1} if not found.
     */
    public int findByContentUri(Uri uri);

    /**
     * Clears all the data currently loaded.
     */
    public void clear();

    /**
     * Executes the deletion task. Delete the data waiting in the deletion
     * queue.
     *
     * @return Whether the task has been executed
     */
    public boolean executeDeletion();

    /**
     * Undo a deletion. If there is any data waiting to be deleted in the queue,
     * move it out of the deletion queue.
     *
     * @return Whether there are items in the queue.
     */
    public boolean undoDeletion();

    /**
     * Update the data in a specific position.
     *
     * @param index The position of the data to be updated.
     * @param item The new data.
     */
    public void updateItemAt(int index, FilmstripItem item);

    /** Sets the listener for the LocalData change. */
    public void setLocalDataListener(FilmstripItemListener listener);

    /**
     * Updates the metadata in the background. The completion of the updating
     * will be notified through
     * {@link LocalFilmstripDataAdapter.FilmstripItemListener}.
     *
     * @param index The ID of the data to update the metadata for.
     * @return An {@link android.os.AsyncTask} performing the background load
     *      that can be used to cancel the load if it's no longer needed.
     */
    public AsyncTask updateMetadataAt(int index);

    /**
     * @return whether the metadata is already updated.
     */
    public boolean isMetadataUpdatedAt(int index);
}
