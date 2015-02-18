/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.session;

import android.location.Location;

import com.android.camera.app.MediaSaver;

public class CaptureSessionFactoryImpl implements CaptureSessionFactory {
    /** Sub-directory for storing temporary session files. */
    private static final String TEMP_SESSIONS = "TEMP_SESSIONS";

    private final MediaSaver mMediaSaver;
    private final PlaceholderManager mPlaceholderManager;
    private final SessionStorageManager mSessionStorageManager;
    private final StackSaverFactory mStackSaverFactory;

    public CaptureSessionFactoryImpl(MediaSaver mediaSaver, PlaceholderManager placeholderManager,
            SessionStorageManager sessionStorageManager, StackSaverFactory stackSaverFactory) {
        mMediaSaver = mediaSaver;
        mPlaceholderManager = placeholderManager;
        mSessionStorageManager = sessionStorageManager;
        mStackSaverFactory = stackSaverFactory;
    }

    @Override
    public CaptureSession createNewSession(CaptureSessionManager sessionManager,
            SessionNotifier sessionNotifier, String title, long sessionStartTime,
            Location location) {
        TemporarySessionFile temporarySessionFile = new TemporarySessionFile(
                mSessionStorageManager, TEMP_SESSIONS, title);
        return new CaptureSessionImpl(title, sessionStartTime, location, temporarySessionFile,
                sessionManager, sessionNotifier, mPlaceholderManager, mMediaSaver,
                mStackSaverFactory.create(title, location));
    }
}
