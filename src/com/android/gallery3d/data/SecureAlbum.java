/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.data;

import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

import com.android.gallery3d.app.GalleryApp;

import java.util.ArrayList;

// This class lists all media items added by the client.
public class SecureAlbum extends MediaSet {
    @SuppressWarnings("unused")
    private static final String TAG = "SecureAlbum";
    private ArrayList<Path> mItems = new ArrayList<Path>();
    private DataManager mDataManager;
    private static final Uri[] mWatchUris =
        {Images.Media.EXTERNAL_CONTENT_URI, Video.Media.EXTERNAL_CONTENT_URI};
    private final ChangeNotifier mNotifier;

    public SecureAlbum(Path path, GalleryApp application) {
        super(path, nextVersionNumber());
        mDataManager = application.getDataManager();
        mNotifier = new ChangeNotifier(this, mWatchUris, application);
    }

    public void addMediaItem(boolean isVideo, int id) {
        if (isVideo) {
            mItems.add(0, Path.fromString("/local/video/item/" + id));
        } else {
            mItems.add(0, Path.fromString("/local/image/item/" + id));
        }
        mNotifier.fakeChange();
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        if (start >= mItems.size()) {
            return new ArrayList<MediaItem>();
        }
        int end = Math.min(start + count, mItems.size());
        ArrayList<Path> subset = new ArrayList<Path>(mItems.subList(start, end));
        final MediaItem[] buf = new MediaItem[end - start];
        ItemConsumer consumer = new ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                buf[index] = item;
            }
        };
        mDataManager.mapMediaItems(subset, consumer, 0);
        ArrayList<MediaItem> result = new ArrayList<MediaItem>(end - start);
        for (int i = 0; i < buf.length; i++) {
            result.add(buf[i]);
        }
        return result;
    }

    @Override
    public int getMediaItemCount() {
        return mItems.size();
    }

    @Override
    public String getName() {
        return "secure";
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
        }
        return mDataVersion;
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }
}
