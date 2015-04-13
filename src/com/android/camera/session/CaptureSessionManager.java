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

import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;

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
         * Called when the capture indicator for the given session has changed
         * and should be updated.
         *
         * @param bitmap the capture indicator bitmap
         * @param rotationDegrees the rotation of the updated preview
         */
        public void onSessionCaptureIndicatorUpdate(Bitmap bitmap, int rotationDegrees);

        /** Called when the session with the given Uri finished. */
        public void onSessionDone(Uri mediaUri);

        /** Called when the session with the given Uri failed processing. */
        public void onSessionFailed(Uri mediaUri, int failureMessageId, boolean removeFromFilmstrip);

        /** Called when the session with the given Uri was canceled. */
        public void onSessionCanceled(Uri mediaUri);

        /** Called when the session with the given Uri has progressed. */
        public void onSessionProgress(Uri mediaUri, int progress);

        /** Called when the session with the given Uri has changed its progress text. */
        public void onSessionProgressText(Uri mediaUri, int messageId);

        /**
         * Called when the thumbnail for the given session has changed and
         * should be updated. This is only used by @{link CaptureIntentModule}.
         * Filmstrip uses onSessionUpdated to refresh the thumbnail.
         *
         * @param bitmap the thumbnail bitmap
         */
        public void onSessionThumbnailUpdate(Bitmap bitmap);

        /**
         * Called when the compressed picture data for the given session has
         * changed and should be updated.
         *
         * @param pictureData the picture JPEG byte array.
         * @param orientation the picture orientation.
         */
        public void onSessionPictureDataUpdate(byte[] pictureData, int orientation);
    }

    /**
     * Creates a new capture session.
     *
     * @param title the title of the new session.
     * @param sessionStartMillis the start time of the new session (millis since epoch).
     * @param location the location of the new session.
     */
    public CaptureSession createNewSession(String title, long sessionStartMillis, Location location);

    /**
     * Returns a session by session Uri or null if it is not found.
     *
     * @param sessionUri the Uri to look up.
     *
     * @return The corresponding CaptureSession.
     */
    public CaptureSession getSession(Uri sessionUri);

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
     * Removes the session with the given uri from the manager. This may not
     * remove temporary in memory resources from the session itself, see
     * {@link CaptureSession#finalizeSession()} to complete session removal.
     */
    public CaptureSession removeSession(Uri sessionUri);

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
     * @return If existant, returns the error message ID for the session with the
     *         given URI, -1 otherwise.
     */
    public int getErrorMessageId(Uri uri);

    /**
     * Removes any existing error messages for the session with the given URI.
     */
    public void removeErrorMessage(Uri uri);

    /** Sets the error message for the session with the given URI. */
    public void putErrorMessage(Uri uri, int failureMessageId);
}
