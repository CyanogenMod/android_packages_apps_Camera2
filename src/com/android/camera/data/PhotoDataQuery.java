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
import android.provider.MediaStore;

public class PhotoDataQuery {
    // Sort all data by ID. This must be aligned with
    // {@link CameraDataAdapter.QueryTask} which relies on the highest ID
    // being first in any data returned.
    public static final String QUERY_ORDER = MediaStore.Images.ImageColumns._ID + " DESC";
    public static final Uri CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    public static final int COL_ID = 0;
    public static final int COL_TITLE = 1;
    public static final int COL_MIME_TYPE = 2;
    public static final int COL_DATE_TAKEN = 3;
    public static final int COL_DATE_MODIFIED = 4;
    public static final int COL_DATA = 5;
    public static final int COL_ORIENTATION = 6;
    public static final int COL_WIDTH = 7;
    public static final int COL_HEIGHT = 8;
    public static final int COL_SIZE = 9;
    public static final int COL_LATITUDE = 10;
    public static final int COL_LONGITUDE = 11;

    /**
     * These values should be kept in sync with column IDs (COL_*) above.
     */
    public static final String[] QUERY_PROJECTION = {
          MediaStore.Images.ImageColumns._ID,           // 0, int
          MediaStore.Images.ImageColumns.TITLE,         // 1, string
          MediaStore.Images.ImageColumns.MIME_TYPE,     // 2, string
          MediaStore.Images.ImageColumns.DATE_TAKEN,    // 3, int
          MediaStore.Images.ImageColumns.DATE_MODIFIED, // 4, int
          MediaStore.Images.ImageColumns.DATA,          // 5, string
          MediaStore.Images.ImageColumns.ORIENTATION,   // 6, int, 0, 90, 180, 270
          MediaStore.Images.ImageColumns.WIDTH,         // 7, int
          MediaStore.Images.ImageColumns.HEIGHT,        // 8, int
          MediaStore.Images.ImageColumns.SIZE,          // 9, long
          MediaStore.Images.ImageColumns.LATITUDE,      // 10, double
          MediaStore.Images.ImageColumns.LONGITUDE      // 11, double
    };
}