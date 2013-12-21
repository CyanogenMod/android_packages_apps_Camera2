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

import android.content.ContentResolver;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.android.camera.app.MediaSaver;
import com.android.camera.app.MediaSaver.OnMediaSavedListener;
import com.android.camera.crop.ImageLoader;
import com.android.camera.data.LocalData;
import com.android.camera.exif.ExifInterface;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Implementation for the {@link CaptureSessionManager}.
 */
public class CaptureSessionManagerImpl implements CaptureSessionManager {

    private class CaptureSessionImpl implements CaptureSession {
        /** A URI of the item being processed. */
        private Uri mUri;
        /** The title of the item being processed. */
        private final String mTitle;
        /** The current progress of this session in percent. */
        private int mProgressPercent = 0;
        /** An associated notification ID, else -1. */
        private int mNotificationId = -1;
        /** A message ID for the current progress state. */
        private CharSequence mProgressMessage;
        /** A place holder for this capture session. */
        private PlaceholderManager.Session mPlaceHolderSession;

        private CaptureSessionImpl(String title) {
            mTitle = title;
        }

        @Override
        public String getTitle() {
            return mTitle;
        }

        @Override
        public synchronized void setProgress(int percent) {
            mProgressPercent = percent;
            notifyTaskProgress(mUri, mProgressPercent);
            mNotificationManager.setProgress(mProgressPercent, mNotificationId);
        }

        @Override
        public synchronized int getProgress() {
            return mProgressPercent;
        }

        @Override
        public synchronized CharSequence getProgressMessage() {
            return mProgressMessage;
        }

        @Override
        public synchronized void setProgressMessage(CharSequence message) {
            mProgressMessage = message;
            mNotificationManager.setStatus(mProgressMessage, mNotificationId);
        }

        @Override
        public synchronized void startSession(byte[] placeholder, CharSequence progressMessage) {
            if (mNotificationId > 0) {
                throw new RuntimeException("startSession cannot be called a second time.");
            }

            mProgressMessage = progressMessage;
            mNotificationId = mNotificationManager.notifyStart(mProgressMessage);

            final long now = System.currentTimeMillis();
            // TODO: This needs to happen outside the UI thread.
            mPlaceHolderSession = mPlaceholderManager.insertPlaceholder(mTitle, placeholder, now);
            mUri = mPlaceHolderSession.outputUri;
            mSessions.put(mUri.toString(), this);
            notifyTaskQueued(mUri);
        }

        @Override
        public synchronized void startSession(Uri uri, CharSequence progressMessage) {
            if (mNotificationId > 0) {
                throw new RuntimeException("startSession cannot be called a second time.");
            }
            mUri = uri;
            mProgressMessage = progressMessage;
            mNotificationId = mNotificationManager.notifyStart(mProgressMessage);
            mPlaceHolderSession = mPlaceholderManager.convertToPlaceholder(uri);

            mSessions.put(mUri.toString(), this);
            notifyTaskQueued(mUri);
        }

        @Override
        public synchronized void cancel() {
            if (mUri != null) {
                removeSession(mUri.toString());
            }
        }

        @Override
        public synchronized void saveAndFinish(byte[] data, Location loc, int width, int height,
                int orientation, ExifInterface exif, OnMediaSavedListener listener) {
            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call saveAndFinish without calling startSession first.");
            }

            // TODO: This needs to happen outside the UI thread.
            mPlaceholderManager.replacePlaceholder(mPlaceHolderSession, loc, orientation, exif,
                    data, width, height, LocalData.MIME_TYPE_JPEG);

            mNotificationManager.notifyCompletion(mNotificationId);
            removeSession(mUri.toString());
            notifyTaskDone(mPlaceHolderSession.outputUri);
        }

        @Override
        public void finish() {
            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call finish without calling startSession first.");
            }

            // Set final values in media store, such as mime type and size.
            mPlaceholderManager.replacePlaceHolder(mPlaceHolderSession, LocalData.MIME_TYPE_JPEG,
                    /* finalImage */ true);
            mNotificationManager.notifyCompletion(mNotificationId);
            removeSession(mUri.toString());
            notifyTaskDone(mPlaceHolderSession.outputUri);
        }

        @Override
        public String getPath() {
            if (mUri == null) {
                throw new IllegalStateException("Cannot retrieve URI of not started session.");
            }
            return ImageLoader.getLocalPathFromUri(mContentResolver, mUri);
        }

        @Override
        public Uri getUri() {
            return mUri;
        }

        @Override
        public boolean hasPath() {
            return mUri != null;
        }

        @Override
        public void onPreviewChanged() {
            mPlaceholderManager.replacePlaceHolder(mPlaceHolderSession,
                    PlaceholderManager.PLACEHOLDER_MIME_TYPE, /* finalImage */ false);
            notifySessionUpdate(mPlaceHolderSession.outputUri);
        }
    }

    private final MediaSaver mMediaSaver;
    private final ProcessingNotificationManager mNotificationManager;
    private final PlaceholderManager mPlaceholderManager;
    private final ContentResolver mContentResolver;

    /**
     * We use this to fire events to the session listeners from the main thread.
     */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /** Sessions in progress, keyed by URI. */
    private final Map<String, CaptureSession> mSessions;

    /** Listeners interested in task update events. */
    private final LinkedList<SessionListener> mTaskListeners = new LinkedList<SessionListener>();

    /**
     * Initializes a new {@link CaptureSessionManager} implementation.
     *
     * @param mediaSaver used to store the resulting media item
     * @param contentResolver required by the media saver
     * @param notificationManager used to update system notifications about the
     *            progress
     * @param placeholderManager used to manage placeholders in the filmstrip
     *            before the final result is ready
     */
    public CaptureSessionManagerImpl(MediaSaver mediaSaver,
            ContentResolver contentResolver, ProcessingNotificationManager notificationManager,
            PlaceholderManager placeholderManager) {
        mSessions = new HashMap<String, CaptureSession>();
        mMediaSaver = mediaSaver;
        mContentResolver = contentResolver;
        mNotificationManager = notificationManager;
        mPlaceholderManager = placeholderManager;
    }

    @Override
    public CaptureSession createNewSession(String title) {
        return new CaptureSessionImpl(title);
    }

    @Override
    public CaptureSession createSession() {
        return new CaptureSessionImpl(null);
    }

    @Override
    public void saveImage(byte[] data, String title, long date, Location loc,
            int width, int height, int orientation, ExifInterface exif,
            OnMediaSavedListener listener) {
        mMediaSaver.addImage(data, title, date, loc, width, height, orientation, exif,
                listener, mContentResolver);
    }

    @Override
    public void addSessionListener(SessionListener listener) {
        synchronized (mTaskListeners) {
            mTaskListeners.add(listener);
        }
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        synchronized (mTaskListeners) {
            mTaskListeners.remove(listener);
        }
    }

    @Override
    public int getSessionProgress(Uri uri) {
        CaptureSession session = mSessions.get(uri.toString());
        if (session != null) {
            return session.getProgress();
        }

        // Return -1 to indicate we don't have progress for the given session.
        return -1;
    }

    @Override
    public CharSequence getSessionProgressMessage(Uri uri) {
        CaptureSession session = mSessions.get(uri.toString());
        if (session == null) {
            throw new IllegalArgumentException("Session with given URI does not exist: " + uri);
        }
        return session.getProgressMessage();
    }

    private void removeSession(String sessionUri) {
        mSessions.remove(sessionUri);
    }

    /**
     * Notifies all task listeners that the task with the given URI has been
     * queued.
     */
    private void notifyTaskQueued(final Uri uri) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionQueued(uri);
                    }
                }
            }
        });
    }

    /**
     * Notifies all task listeners that the task with the given URI has been
     * finished.
     */
    private void notifyTaskDone(final Uri uri) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionDone(uri);
                    }
                }
            }
        });
    }

    /**
     * Notifies all task listeners that the task with the given URI has
     * progressed to the given state.
     */
    private void notifyTaskProgress(final Uri uri, final int progressPercent) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionProgress(uri, progressPercent);
                    }
                }
            }
        });
    }

    /**
     * Notifies all task listeners that the task with the given URI has updated
     * its media.
     */
    private void notifySessionUpdate(final Uri uri) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionUpdated(uri);
                    }
                }
            }
        });
    }
}
