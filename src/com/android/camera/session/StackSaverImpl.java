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
import android.net.Uri;

import com.android.camera.Storage;
import com.android.camera.debug.Log;

import java.io.File;

/**
 * Default implementation of the {@link StackSaver} interface. It creates a
 * directory for each stack and stores images and if needed also metadata inside
 * that directory.
 * <p>
 * TODO: Add placeholder support for stack writing.
 * </p>
 */
public class StackSaverImpl implements StackSaver {
    private static final Log.Tag TAG = new Log.Tag("StackSaverImpl");
    /** The stacked images are stored in this directory. */
    private final File mStackDirectory;
    private final ContentResolver mContentResolver;
    private final Location mGpsLocation;

    /**
     * Instantiate a new stack saver implementation.
     *
     * @param stackDirectory the directory, which either exists already or can
     *            be created, into which images belonging to this stack are
     *            belonging.
     * @param gpsLocation the GPS location to attach to all stacked images.
     * @param contentResolver content resolver for storing the data in media
     *            store. TODO: Replace with a media storage storer that can be
     *            mocked out in tests.
     */
    public StackSaverImpl(File stackDirectory, Location gpsLocation,
            ContentResolver contentResolver) {
        mStackDirectory = stackDirectory;
        mGpsLocation = gpsLocation;
        mContentResolver = contentResolver;
    }

    @Override
    public Uri saveStackedImage(File inputImagePath, String title, int width, int height,
            int imageOrientation, long captureTimeEpoch, String mimeType) {
        String filePath =
                Storage.generateFilepath(mStackDirectory.getAbsolutePath(), title, mimeType);
        Log.d(TAG, "Saving using stack image saver: " + filePath);
        File outputImagePath = new File(filePath);

        if (Storage.renameFile(inputImagePath, outputImagePath)) {
            long fileLength = outputImagePath.length();
            if (fileLength > 0) {
                return Storage.addImageToMediaStore(mContentResolver, title, captureTimeEpoch,
                        mGpsLocation, imageOrientation, fileLength, filePath, width, height,
                        mimeType);
            }
        }

        Log.e(TAG, String.format("Unable to rename file from %s to %s.",
                inputImagePath.getPath(),
                filePath));
        return null;
    }
}
