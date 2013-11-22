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

package com.android.camera.app;

import android.app.Application;
import android.content.Context;

import com.android.camera.MediaSaverImpl;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManagerImpl;
import com.android.camera.session.PlaceholderManager;
import com.android.camera.session.ProcessingNotificationManager;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import com.android.camera2.R;

/**
 * The Camera application class containing important services and functionality
 * to be used across modules.
 */
public class CameraApp extends Application implements CameraServices {
    private MediaSaver mMediaSaver;
    private CaptureSessionManager mSessionManager;
    private MemoryManagerImpl mMemoryManager;
    private ProcessingNotificationManager mNotificationManager;
    private PlaceholderManager mPlaceHolderManager;

    @Override
    public void onCreate() {
        super.onCreate();
        UsageStatistics.initialize(this);
        CameraUtil.initialize(this);

        Context context = getApplicationContext();
        mMediaSaver = new MediaSaverImpl();
        mNotificationManager = new ProcessingNotificationManager(this);
        mPlaceHolderManager = new PlaceholderManager(context);
        CharSequence defaultProgressMessage = getText(R.string.processing);

        mSessionManager = new CaptureSessionManagerImpl(mMediaSaver, getContentResolver(),
                mNotificationManager, mPlaceHolderManager, defaultProgressMessage);
        mMemoryManager = MemoryManagerImpl.create(getApplicationContext(), mMediaSaver);
    }

    @Override
    public CaptureSessionManager getCaptureSessionManager() {
        return mSessionManager;
    }

    @Override
    public MemoryManager getMemoryManager() {
        return mMemoryManager;
    }

    @Override
    @Deprecated
    public MediaSaver getMediaSaver() {
        return mMediaSaver;
    }
}
