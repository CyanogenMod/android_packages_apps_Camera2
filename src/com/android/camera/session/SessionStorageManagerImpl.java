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

import android.content.Context;
import android.util.Log;

import com.android.camera.util.FileUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * Default implementation of {@link SessionStorageManager}.
 */
public class SessionStorageManagerImpl implements SessionStorageManager {
    private static final String TAG = "SessionStorageManagerImpl";

    /** Delete temporary session directory remnants after ONE day. */
    private static final int MAX_SESSION_AGE_MILLIS = 24 * 60 * 60 * 1000;

    /** The base directory for all temporary data. */
    private final File mBaseDirectory;

    /**
     * Creates a new {@link SessionStorageManager} instance.
     *
     * @param context A valid Android context to be used for determining the
     *            base directory.
     * @return A session storage manager.
     */
    public static SessionStorageManager create(Context context) {
        return new SessionStorageManagerImpl(context.getExternalFilesDir(null));
    }

    SessionStorageManagerImpl(File baseDirectory) {
        mBaseDirectory = baseDirectory;
    }

    @Override
    public File getSessionDirectory(String subDirectory) throws IOException {
        File sessionDirectory = new File(mBaseDirectory, subDirectory);
        if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) {
            throw new IOException("Could not create session directory: " + sessionDirectory);
        }

        if (!sessionDirectory.isDirectory()) {
            throw new IOException("Session directory is not a directory: " + sessionDirectory);
        }

        // Make sure there are no expired sessions in this directory.
        cleanUpExpiredSessions(sessionDirectory);
        return sessionDirectory;
    }

    /**
     * Goes through all temporary sessions and deletes the ones that are older
     * than a certain age.
     */
    private void cleanUpExpiredSessions(File baseDirectory) {
        File[] sessionDirs = baseDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });

        final long nowInMillis = System.currentTimeMillis();
        for (File sessionDir : sessionDirs) {
            if (sessionDir.lastModified() < (nowInMillis - MAX_SESSION_AGE_MILLIS)) {
                if (!FileUtil.deleteDirectoryRecursively(sessionDir)) {
                    Log.w(TAG, "Could not clean up " + sessionDir.getAbsolutePath());
                }
            }
        }
    }
}
