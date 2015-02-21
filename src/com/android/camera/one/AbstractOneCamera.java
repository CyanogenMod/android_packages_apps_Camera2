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

package com.android.camera.one;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * A common abstract {@link OneCamera} implementation that contains some utility
 * functions and plumbing we don't want every sub-class of {@link OneCamera} to
 * duplicate. Hence all {@link OneCamera} implementations should sub-class this
 * class instead.
 */
public abstract class AbstractOneCamera implements OneCamera {
    protected FocusStateListener mFocusStateListener;
    protected ReadyStateChangedListener mReadyStateChangedListener;
    protected FocusDistanceListener mFocusDistanceListener;

    /**
     * Number of characters from the end of the device serial number used to
     * construct folder names for debugging output.
     */
    static final int DEBUG_FOLDER_SERIAL_LENGTH = 4;

    @Override
    public final void setFocusStateListener(FocusStateListener listener) {
        mFocusStateListener = listener;
    }

    @Override
    public void setFocusDistanceListener(FocusDistanceListener listener) {
        mFocusDistanceListener = listener;
    }

    @Override
    public void setReadyStateChangedListener(ReadyStateChangedListener listener) {
        mReadyStateChangedListener = listener;
    }

    /**
     * Create a directory we can use to store debugging information during Gcam
     * captures.
     * <br />
     * The directory created is [root]/[folderName]/SSSS_YYYYMMDD_HHMMSS_XXX,
     * where 'SSSS' are the last 'DEBUG_FOLDER_SERIAL_LENGTH' digits of the
     * devices serial number, and 'XXX' are milliseconds of the timestamp.
     *
     * @param root the root into which we put a session-specific sub-directory.
     * @param folderName the sub-folder within 'root' where the data should be
     *            put.
     * @return The session-specific directory (absolute path) into which to
     *         store debug information.
     */
    protected static String makeDebugDir(File root, String folderName) {
        if (root == null) {
            return null;
        }
        if (!root.exists() || !root.isDirectory()) {
            throw new RuntimeException("Gcam debug directory not valid or doesn't exist: "
                    + root.getAbsolutePath());
        }

        String serialSubstring = "";
        String serial = android.os.Build.SERIAL;
        if (serial != null) {
            int length = serial.length();

            if (length > DEBUG_FOLDER_SERIAL_LENGTH) {
                serialSubstring = serial.substring(length - DEBUG_FOLDER_SERIAL_LENGTH, length);
            } else {
                serialSubstring = serial;
            }
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        simpleDateFormat.setTimeZone(TimeZone.getDefault());
        String currentDateAndTime = simpleDateFormat.format(new Date());

        String burstFolderName = String.format("%s_%s", serialSubstring, currentDateAndTime);
        File destFolder = new File(new File(root, folderName), burstFolderName);
        if (!destFolder.mkdirs()) {
            throw new RuntimeException("Could not create Gcam debug data folder.");
        }
        String destFolderPath = destFolder.getAbsolutePath();
        return destFolderPath;
    }

    /**
     * If set, tells the ready state changed listener the new state.
     */
    protected void broadcastReadyState(boolean readyForCapture) {
        if (mReadyStateChangedListener != null) {
            mReadyStateChangedListener.onReadyStateChanged(readyForCapture);
        }
    }

    @Override
    public float getMaxZoom() {
        // If not implemented, return 1.0.
        return 1f;
    }

    @Override
    public void setZoom(float zoom) {
        // If not implemented, no-op.
    }
}
