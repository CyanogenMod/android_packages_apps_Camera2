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

class MockSource extends MediaSource {
    GalleryApp mApplication;
    PathMatcher mMatcher;

    private static final int MOCK_SET = 0;
    private static final int MOCK_ITEM = 1;

    public MockSource(GalleryApp context) {
        super("mock");
        mApplication = context;
        mMatcher = new PathMatcher();
        mMatcher.add("/mock/*", MOCK_SET);
        mMatcher.add("/mock/item/*", MOCK_ITEM);
    }

    @Override
    public MediaObject createMediaObject(Path path) {
        MediaObject obj;
        switch (mMatcher.match(path)) {
            case MOCK_SET:
                return new MockSet(path, mApplication.getDataManager());
            case MOCK_ITEM:
                return new MockItem(path);
            default:
                throw new RuntimeException("bad path: " + path);
        }
    }
}
