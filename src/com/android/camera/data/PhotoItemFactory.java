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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.android.camera.data.FilmstripContentQueries.CursorToFilmstripItemFactory;

import java.util.List;

public class PhotoItemFactory implements CursorToFilmstripItemFactory {
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final PhotoDataFactory mPhotoDataFactory;

    public PhotoItemFactory(Context context, ContentResolver contentResolver,
          PhotoDataFactory photoDataFactory) {
        mContext = context;
        mContentResolver = contentResolver;
        mPhotoDataFactory = photoDataFactory;
    }

    public PhotoItem get(Cursor c) {
        FilmstripItemData data = mPhotoDataFactory.fromCursor(c);
        return new PhotoItem(mContext, data, this);
    }

    public PhotoItem get(Uri uri) {
        PhotoItem newData = null;
        Cursor c = mContentResolver.query(uri, PhotoDataQuery.QUERY_PROJECTION,
              null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                newData = get(c);
            }
            c.close();
        }

        return newData;
    }

    /** Query for all the photo data items */
    public List<FilmstripItem> queryAll() {
        return queryAll(PhotoDataQuery.CONTENT_URI, FilmstripItemBase.QUERY_ALL_MEDIA_ID);
    }

    /** Query for all the photo data items */
    public List<FilmstripItem> queryAll(Uri uri, long lastId) {
        return FilmstripContentQueries
              .forCameraPath(mContentResolver, uri, PhotoDataQuery.QUERY_PROJECTION, lastId,
                    PhotoDataQuery.QUERY_ORDER, this);
    }

    /** Query for a single data item */
    public FilmstripItem queryContentUri(Uri uri) {
        // TODO: Consider refactoring this, this approach may be slow.
        List<FilmstripItem> newPhotos = queryAll(uri, FilmstripItemBase.QUERY_ALL_MEDIA_ID);
        if (newPhotos.isEmpty()) {
            return null;
        }
        return newPhotos.get(0);
    }
}
