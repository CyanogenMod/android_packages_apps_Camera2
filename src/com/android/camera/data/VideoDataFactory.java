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

import android.database.Cursor;
import android.media.CamcorderProfile;
import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.util.Size;

import java.util.Date;

public class VideoDataFactory {
    private static final Log.Tag TAG = new Log.Tag("VideoDataFact");

    // TODO: Consider replacing this with 0,0 and possibly a shared
    // ZERO size value.
    private static final Size UNKNOWN_SIZE = new Size(-2, -2);

    public VideoItemData fromCursor(Cursor c) {
        long id = c.getLong(VideoDataQuery.COL_ID);
        String title = c.getString(VideoDataQuery.COL_TITLE);
        String mimeType = c.getString(VideoDataQuery.COL_MIME_TYPE);
        long creationDateInMilliSeconds = c.getLong(VideoDataQuery.COL_DATE_TAKEN);
        long lastModifiedDateInSeconds = c.getLong(VideoDataQuery.COL_DATE_MODIFIED);
        Date creationDate = new Date(creationDateInMilliSeconds);
        Date lastModifiedDate = new Date(lastModifiedDateInSeconds * 1000);

        String filePath = c.getString(VideoDataQuery.COL_DATA);
        int width = c.getInt(VideoDataQuery.COL_WIDTH);
        int height = c.getInt(VideoDataQuery.COL_HEIGHT);

        Size dimensions;

        // If the media store doesn't contain a width and a height, use the width and height
        // of the default camera mode instead. When the metadata loader runs, it will set the
        // correct values.
        if (width == 0 || height == 0) {
            Log.w(TAG, "failed to retrieve width and height from the media store, defaulting " +
                  " to camera profile");
            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            if(profile != null) {
                dimensions = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
            } else {
                Log.w(TAG, "Video profile was null, defaulting to unknown width and height.");
                dimensions = UNKNOWN_SIZE;
            }
        } else {
            dimensions = new Size(width, height);
        }

        long sizeInBytes = c.getLong(VideoDataQuery.COL_SIZE);
        double latitude = c.getDouble(VideoDataQuery.COL_LATITUDE);
        double longitude = c.getDouble(VideoDataQuery.COL_LONGITUDE);
        long videoDurationMillis = c.getLong(VideoDataQuery.COL_DURATION);
        Location location = Location.from(latitude, longitude);

        Uri uri = VideoDataQuery.CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build();

        return new VideoItemData(
              id,
              title,
              mimeType,
              creationDate,
              lastModifiedDate,
              filePath,
              uri,
              dimensions,
              sizeInBytes,
              0 /* orientation */,
              location,
              videoDurationMillis);
    }
}