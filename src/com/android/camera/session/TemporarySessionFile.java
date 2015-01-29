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

import java.io.File;
import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Used to create temporary session files to be used by e.g. Photo Sphere to
 * write into.
 * <p>
 * This file also handles the correct creation of the file and makes sure that
 * it is available for writing into from e.g. native code.
 */
public class TemporarySessionFile {

    private final SessionStorageManager mSessionStorageManager;
    private final String mSessionDirectory;
    private final String mTitle;

    @Nullable
    private File mFile;

    public TemporarySessionFile(SessionStorageManager sessionStorageManager, String
            sessionDirectory, String title) {
        mSessionStorageManager = sessionStorageManager;
        mSessionDirectory = sessionDirectory;
        mTitle = title;
        mFile = null;
    }

    /**
     * Creates the file and all the directories it is in if necessary.
     * <p>
     * If file was prepared successfully, additional calls to this method will
     * be no-ops and 'true' will be returned.
     *
     * @return Whether the file could be created and is ready to be written to.
     */
    public synchronized boolean prepare() {
        if (mFile != null) {
            return true;
        }

        try {
            mFile = mSessionStorageManager.createTemporaryOutputPath(mSessionDirectory, mTitle);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * @return Whether the file has been created and is usable.
     */
    public synchronized boolean isUsable() {
        return mFile != null;
    }

    /**
     * @return The file or null, if {@link #prepare} has not be called yet or
     *         preparation failed.
     */
    @Nullable
    public synchronized File getFile() {
        return mFile;
    }

}
