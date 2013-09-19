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

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import com.android.camera.CameraActivity;

/**
 * Listening to the changes to the local image and video data. onChange will
 * happen on the main thread.
 */
public class LocalMediaObserver extends ContentObserver {

    private final CameraActivity mActivity;

    public LocalMediaObserver(Handler handler, CameraActivity activity) {
        super(handler);
        mActivity = activity;
    }

    @Override
    public void onChange(boolean selfChange) {
        this.onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        mActivity.setDirtyWhenPaused();
    }
}
