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
import android.provider.MediaStore;

import com.android.camera.data.FilmstripContentQueries.CursorToFilmstripItemFactory;
import com.android.camera.debug.Log;

import java.util.List;

public class VideoItemFactory implements CursorToFilmstripItemFactory {
    private static final Log.Tag TAG = new Log.Tag("VideoItemFact");
    private static final String QUERY_ORDER = MediaStore.Video.VideoColumns.DATE_TAKEN
          + " DESC, " + MediaStore.Video.VideoColumns._ID + " DESC";

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final VideoDataFactory mVideoDataFactory;

    public VideoItemFactory(Context context, ContentResolver contentResolver,
          VideoDataFactory videoDataFactory) {
        mContext = context;
        mContentResolver = contentResolver;
        mVideoDataFactory = videoDataFactory;
    }

    @Override
    public VideoItem get(Cursor c) {
        VideoItemData data = mVideoDataFactory.fromCursor(c);
        return new VideoItem(mContext, data, this);
    }

    /** Query for a single video data item */
    public VideoItem get(Uri uri) {
        VideoItem newData = null;
        Cursor c = mContext.getContentResolver().query(uri, VideoDataQuery.QUERY_PROJECTION, null,
              null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                newData = get(c);
            }
            c.close();
        }

        return newData;
    }

    /** Query for all the video data items */
    public List<FilmstripItem> queryAll() {
        return queryAll(VideoDataQuery.CONTENT_URI,
              FilmstripItemBase.QUERY_ALL_MEDIA_ID);
    }

    /** Query for all the video data items */
    public List<FilmstripItem> queryAll(Uri uri, long lastId) {
        return FilmstripContentQueries
              .forCameraPath(mContentResolver, uri, VideoDataQuery.QUERY_PROJECTION, lastId,
                    QUERY_ORDER, this);
    }

    /** Query for a single data item */
    public FilmstripItem queryContentUri(Uri uri) {
        // TODO: Consider refactoring this, this approach may be slow.
        List<FilmstripItem> videos = queryAll(uri,
              FilmstripItemBase.QUERY_ALL_MEDIA_ID);
        if (videos.isEmpty()) {
            return null;
        }
        return videos.get(0);
    }
}
