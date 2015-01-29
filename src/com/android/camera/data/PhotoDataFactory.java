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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.util.Size;

import java.util.Date;

public class PhotoDataFactory {
    private static final Log.Tag TAG = new Log.Tag("PhotoDataFact");

    public FilmstripItemData fromCursor(Cursor c) {
        long id = c.getLong(PhotoDataQuery.COL_ID);
        String title = c.getString(PhotoDataQuery.COL_TITLE);
        String mimeType = c.getString(PhotoDataQuery.COL_MIME_TYPE);
        long dateTakenInMilliSeconds = c.getLong(PhotoDataQuery.COL_DATE_TAKEN);
        long dateModifiedInSeconds = c.getLong(PhotoDataQuery.COL_DATE_MODIFIED);
        Date creationDate = new Date(dateTakenInMilliSeconds);
        Date lastModifiedDate = new Date(dateModifiedInSeconds * 1000);

        String filePath = c.getString(PhotoDataQuery.COL_DATA);
        int orientation = c.getInt(PhotoDataQuery.COL_ORIENTATION);
        int width = c.getInt(PhotoDataQuery.COL_WIDTH);
        int height = c.getInt(PhotoDataQuery.COL_HEIGHT);

        Size dimensions;
        // If the width or height is unknown, attempt to decode it from
        // the physical bitmaps.
        if (width <= 0 || height <= 0) {

            Log.w(TAG, "Zero dimension in ContentResolver for "
                  + filePath + ":" + width + "x" + height);

            dimensions = decodeBitmapDimensions(filePath);
            if (dimensions == null) {
                // If we are unable to decode non-zero bitmap dimensions
                // we should not create a filmstrip item data for this
                // entry in the media store.
                return null;
            }
        } else {
            dimensions = new Size(width, height);
        }

        long sizeInBytes = c.getLong(PhotoDataQuery.COL_SIZE);
        double latitude = c.getDouble(PhotoDataQuery.COL_LATITUDE);
        double longitude = c.getDouble(PhotoDataQuery.COL_LONGITUDE);
        Location location = Location.from(latitude, longitude);

        Uri uri = PhotoDataQuery.CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build();

        return new FilmstripItemData(
              id,
              title,
              mimeType,
              creationDate,
              lastModifiedDate,
              filePath,
              uri,
              dimensions,
              sizeInBytes,
              orientation,
              location);
    }

    /**
     * Given a file path, decode the bitmap dimensions if possible.
     */
    private Size decodeBitmapDimensions(String filePath) {
        int width;
        int height;

        // Ensure we only decode the dimensions, not the whole
        // file if at all possible.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            width = opts.outWidth;
            height = opts.outHeight;
        } else {
            Log.w(TAG, "Dimension decode failed for " + filePath);

            // Fall back on decoding the entire file
            Bitmap b = BitmapFactory.decodeFile(filePath);
            if (b == null) {
                Log.w(TAG, "PhotoData skipped."
                      + " Decoding " + filePath + " failed.");
                return null;
            }
            width = b.getWidth();
            height = b.getHeight();
            if (width == 0 || height == 0) {
                Log.w(TAG, "PhotoData skipped. Bitmap size 0 for " + filePath);
                return null;
            }
        }

        return new Size(width, height);
    }
}