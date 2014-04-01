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

import com.android.camera.filmstrip.DataAdapter;
import com.android.camera.util.Callback;
import com.android.camera.widget.Preloader;

import java.util.List;

/**
 * An interface which extends {@link com.android.camera.filmstrip.DataAdapter}
 * and defines operations on the data in the local camera folder.
 */
public interface LocalDataAdapter extends DataAdapter,
        Preloader.ItemLoader<Integer, AsyncTask>, Preloader.ItemSource<Integer> {

    public interface LocalDataListener {
        /**
         * Metadata of a {@link com.android.camera.data.LocalData} is loaded on
         * demand. Once the metadata is loaded this listener is notified.
         *
         * @param updatedData The IDs of the data whose metadata has been
         *            updated.
         */
        public void onMetadataUpdated(List<Integer> updatedData);
    }

    /**
     * Request for loading any photos that may have been added to the
     * media store since the last update.
     */
    public void requestLoadNewPhotos();

    /**
     * Request for loading the local data.
     */
    public void requestLoad(Callback<Void> doneCallback);

    /**
     * Returns the specified {@link LocalData}.
     *
     * @param dataID The ID of the {@link LocalData} to get.
     * @return The {@link LocalData} to get. {@code null} if not available.
     */
    public LocalData getLocalData(int dataID);

    /**
     * Remove the data in the local camera folder.
     *
     * @param dataID ID of data to be deleted.
     */
    public void removeData(int dataID);

    /**
     * Adds new local data. The data is either inserted or updated, depending
     * on the existence of the Uri.
     *
     * @param data The new data.
     * @return Whether the data is newly inserted.
     */
    public boolean addData(LocalData data);

    /**
     * Refresh the data by {@link Uri}.
     *
     * @param uri The {@link Uri} of the data to refresh.
     */
    public void refresh(Uri uri);

    /**
     * Finds the {@link LocalData} of the specified content Uri.
     *
     * @param uri The content Uri of the {@link LocalData}.
     * @return The index of the data. {@code -1} if not found.
     */
    public int findDataByContentUri(Uri uri);

    /**
     * Clears all the data currently loaded.
     */
    public void flush();

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
    public boolean undoDataRemoval();

    /**
     * Update the data in a specific position.
     *
     * @param pos The position of the data to be updated.
     * @param data The new data.
     */
    public void updateData(int pos, LocalData data);

    /** Sets the listener for the LocalData change. */
    public void setLocalDataListener(LocalDataListener listener);

    /**
     * Updates the metadata in the background. The completion of the updating
     * will be notified through
     * {@link com.android.camera.data.LocalDataAdapter.LocalDataListener}.
     *
     * @param dataId The ID of the data to update the metadata for.
     * @return An {@link android.os.AsyncTask} performing the background load
     *      that can be used to cancel the load if it's no longer needed.
     */
    public AsyncTask updateMetadata(int dataId);

    /**
     * @return whether the metadata is already updated.
     */
    public boolean isMetadataUpdated(int dataId);
}
