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

import android.content.ContentResolver;
import android.content.Context;
import android.os.Looper;

import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootStub;

class GalleryAppMock extends GalleryAppStub {
    GLRoot mGLRoot = new GLRootStub();
    DataManager mDataManager = new DataManager(this);
    ContentResolver mResolver;
    Context mContext;
    Looper mMainLooper;

    GalleryAppMock(Context context,
            ContentResolver resolver, Looper mainLooper) {
        mContext = context;
        mResolver = resolver;
        mMainLooper = mainLooper;
    }

    @Override
    public GLRoot getGLRoot() { return mGLRoot; }
    @Override
    public DataManager getDataManager() { return mDataManager; }
    @Override
    public Context getAndroidContext() { return mContext; }
    @Override
    public ContentResolver getContentResolver() { return mResolver; }
    @Override
    public Looper getMainLooper() { return mMainLooper; }
}
