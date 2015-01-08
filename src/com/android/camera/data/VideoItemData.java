/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.camera.util.Size;

import java.util.Date;

/**
 * Filmstrip item data with video specific fields.
 */
public class VideoItemData extends FilmstripItemData {
    private long mVideoDurationMillis;

    public VideoItemData(long contentId, String title, String mimeType, Date creationDate,
          Date lastModifiedDate, String filePath, Uri uri,
          Size dimensions, long sizeInBytes, int orientation,
          Location location, long videoDurationMillis) {
        super(contentId, title, mimeType, creationDate, lastModifiedDate, filePath, uri, dimensions,
              sizeInBytes, orientation, location);
        mVideoDurationMillis = videoDurationMillis;
    }

    public long getVideoDurationMillis() {
        return mVideoDurationMillis;
    }
}
