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

    private GalleryApp mApplication;
    private PathMatcher mMatcher;

    public FilterSource(GalleryApp application) {
        super("filter");
        mApplication = application;
        mMatcher = new PathMatcher();
        mMatcher.add("/filter/mediatype/*/*", FILTER_BY_MEDIATYPE);
    }

    // The name we accept is:
    // /filter/mediatype/k/{set}
    // where k is the media type we want.
    @Override
    public MediaObject createMediaObject(Path path) {
        int matchType = mMatcher.match(path);
        int mediaType = mMatcher.getIntVar(0);
        String setsName = mMatcher.getVar(1);
        DataManager dataManager = mApplication.getDataManager();
        MediaSet[] sets = dataManager.getMediaSetsFromString(setsName);
        switch (matchType) {
            case FILTER_BY_MEDIATYPE:
                return new FilterSet(path, dataManager, sets[0], mediaType);
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}
