/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.ContentResolver;
import android.location.Location;

import java.io.File;

/**
 * Creates {@link StackSaver} instances.
 */
public class StackSaverFactory {
    private final String mCameraDirectory;
    private final ContentResolver mContentResolver;

    /**
     * Create a new stack saver factory.
     *
     * @param cameraDirectory the directory in which the camera stores images.
     * @param contentResolver the Android content resolver used to include
     *            images into the media store.
     */
    public StackSaverFactory(String cameraDirectory,
            ContentResolver contentResolver) {
        mCameraDirectory = cameraDirectory;
        mContentResolver = contentResolver;
    }

    /**
     * Creates a new StackSaver.
     *
     * @param mTitle the title of this stack session.
     * @param location the GPS location that the media in this session was
     *            created at.
     * @return A StackSaver that is set up to save images in a stacked location.
     */
    public StackSaver create(String mTitle, Location location) {
        return new StackSaverImpl(new File(mCameraDirectory, mTitle), location, mContentResolver);
    }
}
