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

import java.io.File;
import java.io.IOException;

/**
 * Modules use this manager to store capture results.
 */
public interface CaptureSessionManager {
    /**
     * Callback interface for session events.
     */
    public interface SessionListener {
        /**
         * Called when the session with the given Uri was queued and will be
         * processed.
         */
        public void onSessionQueued(Uri mediaUri);

        /**
         * Called when the media underlying the session with the given Uri has
         * been updated.
         */
        public void onSessionUpdated(Uri mediaUri);

        /**
         * Called when the preview of the media underlying the session with the
         * given Uri has been updated.
         */
        public void onSessionPreviewAvailable(Uri mediaUri);

        /** Called when the session with the given Uri finished. */
        public void onSessionDone(Uri mediaUri);

        /** Called when the session with the given Uri failed processing. */
        public void onSessionFailed(Uri mediaUri, CharSequence reason);

        /** Called when the session with the given Uri has progressed. */
        public void onSessionProgress(Uri mediaUri, int progress);

        /** Called when the session with the given Uri has changed its progress text. */
        public void onSessionProgressText(Uri mediaUri, CharSequence message);
    }

    /**
     * Creates a new capture session.
     *
     * @param title the title of the new session.
     * @param sessionStartMillis the start time of the new session (millis since epoch).
     * @param location the location of the new session.
     */
    CaptureSession createNewSession(String title, long sessionStartMillis, Location location);

    /**
     * Creates a session based on an existing URI in the filmstrip and media
     * store. This can be used to re-process an image.
     */
    CaptureSession createSession();

    /**
     * Returns a session by session Uri or null if it is not found.
     *
     * @param sessionUri the Uri to look up.
     *
     * @return The corresponding CaptureSession.
     */
    CaptureSession getSession(Uri sessionUri);

    /**
     * Save an image without creating a session that includes progress.
     *
     * @param data the image data to be saved.
     * @param title the title of the media item.
     * @param date the timestamp of the capture.
     * @param loc the capture location.
     * @param width the width of the captured image.
     * @param height the height of the captured image.
     * @param orientation the orientation of the captured image.
     * @param exif the EXIF data of the captured image.
     * @param listener called when saving is complete.
     */
    void saveImage(byte[] data, String title, long date, Location loc, int width, int height,
            int orientation, ExifInterface exif, OnMediaSavedListener listener);

    /**
     * Add a listener to be informed about capture session updates.
     * <p>
     * Note: It is guaranteed that the callbacks will happen on the main thread,
     * so callers have to make sure to not block execution.
     */
    public void addSessionListener(SessionListener listener);

    /**
     * Adds the session with the given uri.
     */
    public void putSession(Uri sessionUri, CaptureSession session);

    /**
     * Removes a previously added listener from receiving further capture
     * session updates.
     */
    public void removeSessionListener(SessionListener listener);

    /**
     * Calls the given listener for all the sessions that are currently
     * in-flight.
     */
    public void fillTemporarySession(SessionListener listener);

    /**
     * Gets the directory to be used for temporary data. See
     * {@link SessionStorageManager#getSessionDirectory(String)}
     */
    public File getSessionDirectory(String subDirectory) throws IOException;

    /**
     * @return Whether the session with the given URI exists and has an error
     *         message.
     */
    public boolean hasErrorMessage(Uri uri);

    /**
     * @return If existant, returns the error message for the session with the
     *         given URI.
     */
    public CharSequence getErrorMesage(Uri uri);

    /**
     * Removes any existing error messages for the session with the given URI.
     */
    public void removeErrorMessage(Uri uri);
}
