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

package com.android.camera.session;

import android.location.Location;
import android.net.Uri;

import com.android.camera.app.MediaSaver.OnMediaSavedListener;
import com.android.camera.exif.ExifInterface;

/**
 * A session is an item that is in progress of being created and saved, such as
 * a photo sphere or HDR+ photo.
 */
public interface CaptureSession {

    /** Returns the title/name of this session. */
    public String getTitle();

    /** Returns the location of this session or null. */
    public Location getLocation();

    /** Sets the location of this session. */
    public void setLocation(Location location);

    /**
     * Set the progress in percent for the current session. If set to or left at
     * 0, no progress bar is shown.
     */
    public void setProgress(int percent);

    /**
     * Returns the progress of this session in percent.
     */
    public int getProgress();

    /**
     * Returns the current progress message.
     */
    public CharSequence getProgressMessage();

    /**
     * Starts the session by adding a placeholder to the filmstrip and adding
     * notifications.
     *
     * @param placeholder a valid encoded bitmap to be used as the placeholder.
     * @param progressMessage the message to be used to the progress
     *            notification initially. This can later be changed using
     *            {@link #setProgressMessage(CharSequence)}.
     */
    public void startSession(byte[] placeholder, CharSequence progressMessage);

    /**
     * Starts the session by marking the item as in-progress and adding
     * notifications.
     *
     * @param uri the URI of the item to be re-processed.
     * @param progressMessage the message to be used to the progress
     *            notification initially. This can later be changed using
     *            {@link #setProgressMessage(CharSequence)}.
     */
    public void startSession(Uri uri, CharSequence progressMessage);

    /**
     * Cancel the session without a final result. The session will be removed
     * from the film strip, progress notifications will be cancelled.
     */
    public void cancel();

    /**
     * Changes the progress status message of this session.
     *
     * @param message the new message
     */
    public void setProgressMessage(CharSequence message);

    /**
     * Finish the session by saving the image to disk. Will add the final item
     * in the film strip and remove the progress notifications.
     */
    public void saveAndFinish(byte[] data, int width, int height, int orientation,
            ExifInterface exif, OnMediaSavedListener listener);

    /**
     * Finishes the session.
     */
    public void finish();

    /**
     * Returns the path to the final output of this session. This is only
     * available after startSession has been called.
     */
    public String getPath();

    /**
     * Returns the URI to the final output of this session. This is only available
     * after startSession has been called.
     */
    public Uri getUri();

    /**
     * Whether this session already has a path. This is the case once it has
     * been started. False is returned, if the session has not been started yet
     * and no path is available
     */
    public boolean hasPath();

    /**
     * Called when the underlying media file has been changed and the session
     * should update itself.
     */
    public void onPreviewChanged();
}
