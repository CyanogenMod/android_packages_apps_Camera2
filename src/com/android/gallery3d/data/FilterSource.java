/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.gallery3d.app.GalleryApp;

class FilterSource extends MediaSource {
    private static final String TAG = "FilterSource";
    private static final int FILTER_BY_MEDIATYPE = 0;
    private static final int FILTER_BY_DELETE = 1;

    private GalleryApp mApplication;
    private PathMatcher mMatcher;

    public FilterSource(GalleryApp application) {
        super("filter");
        mApplication = application;
        mMatcher = new PathMatcher();
        mMatcher.add("/filter/mediatype/*/*", FILTER_BY_MEDIATYPE);
        mMatcher.add("/filter/delete/*", FILTER_BY_DELETE);
    }

    // The name we accept are:
    // /filter/mediatype/k/{set}    where k is the media type we want.
    // /filter/delete/{set}
    @Override
    public MediaObject createMediaObject(Path path) {
        int matchType = mMatcher.match(path);
        DataManager dataManager = mApplication.getDataManager();
        switch (matchType) {
            case FILTER_BY_MEDIATYPE: {
                int mediaType = mMatcher.getIntVar(0);
                String setsName = mMatcher.getVar(1);
                MediaSet[] sets = dataManager.getMediaSetsFromString(setsName);
                return new FilterTypeSet(path, dataManager, sets[0], mediaType);
            }
            case FILTER_BY_DELETE: {
                String setsName = mMatcher.getVar(0);
                MediaSet[] sets = dataManager.getMediaSetsFromString(setsName);
                return new FilterDeleteSet(path, sets[0]);
            }
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}
