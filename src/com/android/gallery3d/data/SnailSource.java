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

import android.util.SparseArray;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.ui.ScreenNail;

public class SnailSource extends MediaSource {
    private static final String TAG = "SnailSource";
    private static final int SNAIL_ALBUM = 0;
    private static final int SNAIL_ITEM = 1;

    private GalleryApp mApplication;
    private PathMatcher mMatcher;
    private static int sNextId;
    private static SparseArray<ScreenNail> sRegistry = new SparseArray<ScreenNail>();

    public SnailSource(GalleryApp application) {
        super("snail");
        mApplication = application;
        mMatcher = new PathMatcher();
        mMatcher.add("/snail/set/*", SNAIL_ALBUM);
        mMatcher.add("/snail/item/*", SNAIL_ITEM);
    }

    // The only path we accept is "/snail/set/id" and "/snail/item/id"
    @Override
    public MediaObject createMediaObject(Path path) {
        DataManager dataManager = mApplication.getDataManager();
        switch (mMatcher.match(path)) {
            case SNAIL_ALBUM:
                String itemPath = "/snail/item/" + mMatcher.getVar(0);
                MediaItem item =
                        (MediaItem) dataManager.getMediaObject(itemPath);
                return new SnailAlbum(path, item);
            case SNAIL_ITEM: {
                int id = mMatcher.getIntVar(0);
                return new SnailItem(path, lookupScreenNail(id));
            }
        }
        return null;
    }

    // Register a ScreenNail. Returns the Path of the MediaSet
    // containing the MediaItem associated with the ScreenNail.
    public static synchronized Path registerScreenNail(ScreenNail s) {
        int id = sNextId++;
        sRegistry.put(id, s);
        return Path.fromString("/snail/set").getChild(id);
    }

    public static synchronized void unregisterScreenNail(ScreenNail s) {
        int index = sRegistry.indexOfValue(s);
        sRegistry.removeAt(index);
    }

    private static synchronized ScreenNail lookupScreenNail(int id) {
        return sRegistry.get(id);
    }
}
