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

public class VideoDataQuery {
    public static final Uri CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

    public static final int COL_ID = 0;
    public static final int COL_TITLE = 1;
    public static final int COL_MIME_TYPE = 2;
    public static final int COL_DATE_TAKEN = 3;
    public static final int COL_DATE_MODIFIED = 4;
    public static final int COL_DATA = 5;
    public static final int COL_WIDTH = 6;
    public static final int COL_HEIGHT = 7;
    public static final int COL_SIZE = 8;
    public static final int COL_LATITUDE = 9;
    public static final int COL_LONGITUDE = 10;
    public static final int COL_DURATION = 11;

    /**
     * These values should be kept in sync with column IDs (COL_*) above.
     */
    public static final String[] QUERY_PROJECTION = {
          MediaStore.Video.VideoColumns._ID,           // 0, int
          MediaStore.Video.VideoColumns.TITLE,         // 1, string
          MediaStore.Video.VideoColumns.MIME_TYPE,     // 2, string
          MediaStore.Video.VideoColumns.DATE_TAKEN,    // 3, int
          MediaStore.Video.VideoColumns.DATE_MODIFIED, // 4, int
          MediaStore.Video.VideoColumns.DATA,          // 5, string
          MediaStore.Video.VideoColumns.WIDTH,         // 6, int
          MediaStore.Video.VideoColumns.HEIGHT,        // 7, int
          MediaStore.Video.VideoColumns.SIZE,          // 8 long
          MediaStore.Video.VideoColumns.LATITUDE,      // 9 double
          MediaStore.Video.VideoColumns.LONGITUDE,     // 10 double
          MediaStore.Video.VideoColumns.DURATION       // 11 long
    };
}
