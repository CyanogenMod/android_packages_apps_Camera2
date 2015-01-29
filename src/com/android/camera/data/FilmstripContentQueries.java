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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.android.camera.Storage;
import com.android.camera.debug.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of queries for loading data from a content resolver.
 */
public class FilmstripContentQueries {
    private static final Log.Tag TAG = new Log.Tag("LocalDataQuery");
    private static final String CAMERA_PATH = Storage.DIRECTORY + "%";
    private static final String SELECT_BY_PATH = MediaStore.MediaColumns.DATA + " LIKE ?";

    public interface CursorToFilmstripItemFactory<I extends FilmstripItem> {

        /**
         * Convert a cursor at a given location to a Local Data object.
         *
         * @param cursor the current cursor state.
         * @return a LocalData object that represents the current cursor state.
         */
        public I get(Cursor cursor);
    }

    /**
     * Query the camera storage directory and convert it to local data
     * objects.
     *
     * @param contentResolver to resolve content with.
     * @param contentUri to resolve an item at
     * @param projection the columns to extract
     * @param minimumId the lower bound of results
     * @param orderBy the order by clause
     * @param factory an object that can turn a given cursor into a LocalData object.
     * @return A list of LocalData objects that satisfy the query.
     */
    public static <I extends FilmstripItem> List<I> forCameraPath(ContentResolver contentResolver,
          Uri contentUri, String[] projection, long minimumId, String orderBy,
          CursorToFilmstripItemFactory<I> factory) {
        String selection = SELECT_BY_PATH + " AND " + MediaStore.MediaColumns._ID + " > ?";
        String[] selectionArgs = new String[] { CAMERA_PATH, Long.toString(minimumId) };

        Cursor cursor = contentResolver.query(contentUri, projection,
              selection, selectionArgs, orderBy);
        List<I> result = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                I item = factory.get(cursor);
                if (item != null) {
                    result.add(item);
                } else {
                    final int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    Log.e(TAG, "Error loading data:" + cursor.getString(dataIndex));
                }
            }

            cursor.close();
        }
        return result;
    }
}