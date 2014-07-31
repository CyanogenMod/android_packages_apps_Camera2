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
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import com.android.camera.app.MediaSaver;
import com.android.camera.app.MediaSaver.OnMediaSavedListener;
import com.android.camera.data.LocalData;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * Implementation for the {@link CaptureSessionManager}.
 */
public class CaptureSessionManagerImpl implements CaptureSessionManager {

    private static final Log.Tag TAG = new Log.Tag("CaptureSessMgrImpl");
    public static final String TEMP_SESSIONS = "TEMP_SESSIONS";

    private class CaptureSessionImpl implements CaptureSession {
        /** A URI of the item being processed. */
        private Uri mUri;
        /** The title of the item being processed. */
        private final String mTitle;
        /** The location this session was created at. Used for media store.*/
        private Location mLocation;
        /** The current progress of this session in percent. */
        private int mProgressPercent = 0;
        /** A message ID for the current progress state. */
        private CharSequence mProgressMessage;
        /** A place holder for this capture session. */
        private PlaceholderManager.Session mPlaceHolderSession;
        private boolean mNoPlaceHolderRequired = false;
        private Uri mContentUri;
        /** These listeners get informed about progress updates. */
        private final HashSet<ProgressListener> mProgressListeners =
                new HashSet<ProgressListener>();
        private final long mSessionStartMillis;

        /**
         * Creates a new {@link CaptureSession}.
         *
         * @param title the title of this session.
         * @param sessionStartMillis the timestamp of this capture session (since epoch).
         * @param location the location of this session, used for media store.
         */
        private CaptureSessionImpl(String title, long sessionStartMillis, Location location) {
            mTitle = title;
            mSessionStartMillis = sessionStartMillis;
            mLocation = location;
        }

        @Override
        public String getTitle() {
            return mTitle;
        }

        @Override
        public Location getLocation() {
            return mLocation;
        }

        @Override
        public void setLocation(Location location) {
            mLocation = location;
        }

        @Override
        public synchronized void setProgress(int percent) {
            mProgressPercent = percent;
            notifyTaskProgress(mUri, mProgressPercent);
            for (ProgressListener listener : mProgressListeners) {
                listener.onProgressChanged(percent);
            }
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
            notifyTaskProgressText(mUri, message);
            for (ProgressListener listener : mProgressListeners) {
                listener.onStatusMessageChanged(message);
            }
        }

        @Override
        public void startEmpty() {
            mNoPlaceHolderRequired = true;
        }

        @Override
        public synchronized void startSession(byte[] placeholder, CharSequence progressMessage) {
            mProgressMessage = progressMessage;

            // TODO: This needs to happen outside the UI thread.
            mPlaceHolderSession = mPlaceholderManager.insertPlaceholder(mTitle, placeholder,
                    mSessionStartMillis);
            mUri = mPlaceHolderSession.outputUri;
            putSession(mUri, this);
            notifyTaskQueued(mUri);
        }

        @Override
        public synchronized void startSession(Uri uri, CharSequence progressMessage) {
            mUri = uri;
            mProgressMessage = progressMessage;
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
        public synchronized void saveAndFinish(byte[] data, int width, int height, int orientation,
                ExifInterface exif, final OnMediaSavedListener listener) {
            if (mNoPlaceHolderRequired) {
                mMediaSaver.addImage(
                        data, mTitle, mSessionStartMillis, null, width, height,
                        orientation, exif, listener, mContentResolver);
                return;
            }

            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call saveAndFinish without calling startSession first.");
            }

            // TODO: This needs to happen outside the UI thread.
            mContentUri = mPlaceholderManager.finishPlaceholder(mPlaceHolderSession, mLocation,
                    orientation, exif, data, width, height, LocalData.MIME_TYPE_JPEG);

            removeSession(mUri.toString());
            notifyTaskDone(mPlaceHolderSession.outputUri);
        }

        @Override
        public void finish() {
            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call finish without calling startSession first.");
            }

            final String path = this.getPath();

            AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] jpegDataTemp;
                    try {
                        jpegDataTemp = FileUtil.readFileToByteArray(new File(path));
                    } catch (IOException e) {
                        return;
                    }
                    final byte[] jpegData = jpegDataTemp;

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
                    int width = options.outWidth;
                    int height = options.outHeight;
                    int rotation = 0;
                    ExifInterface exif = null;
                    try {
                        exif = new ExifInterface();
                        exif.readExif(jpegData);
                    } catch (IOException e) {
                        Log.w(TAG, "Could not read exif", e);
                        exif = null;
                    }
                    CaptureSessionImpl.this.saveAndFinish(jpegData, width, height, rotation, exif,
                            null);
                }
            });

        }

        @Override
        public String getPath() {
            if (mUri == null) {
                throw new IllegalStateException("Cannot retrieve URI of not started session.");
            }

            File tempDirectory = null;
            try {
                tempDirectory = new File(
                        getSessionDirectory(TEMP_SESSIONS), mTitle);
            } catch (IOException e) {
                Log.e(TAG, "Could not get temp session directory", e);
                throw new RuntimeException("Could not get temp session directory", e);
            }
            tempDirectory.mkdirs();
            File tempFile = new File(tempDirectory, mTitle  + ".jpg");
            try {
                if (!tempFile.exists()) {
                    tempFile.createNewFile();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not create temp session file", e);
                throw new RuntimeException("Could not create temp session file", e);
            }
            return tempFile.getPath();
        }

        @Override
        public Uri getUri() {
            return mUri;
        }

        @Override
        public Uri getContentUri() {
            return mContentUri;
        }

        @Override
        public boolean hasPath() {
            return mUri != null;
        }

        @Override
        public void onPreviewAvailable() {
            notifySessionPreviewAvailable(mPlaceHolderSession.outputUri);
        }

        @Override
        public void updatePreview(String previewPath) {

            final String path = this.getPath();

            AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] jpegDataTemp;
                    try {
                        jpegDataTemp = FileUtil.readFileToByteArray(new File(path));
                    } catch (IOException e) {
                        return;
                    }
                    final byte[] jpegData = jpegDataTemp;

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
                    int width = options.outWidth;
                    int height = options.outHeight;

                    mPlaceholderManager.replacePlaceholder(mPlaceHolderSession, jpegData, width, height);
                    onPreviewAvailable();
                }
            });
        }

        @Override
        public void finishWithFailure(CharSequence reason) {
            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call finish without calling startSession first.");
            }
            mProgressMessage = reason;

            removeSession(mUri.toString());
            mFailedSessionMessages.put(mPlaceHolderSession.outputUri, reason);
            notifyTaskFailed(mPlaceHolderSession.outputUri, reason);
        }

        @Override
        public void addProgressListener(ProgressListener listener) {
            listener.onStatusMessageChanged(mProgressMessage);
            listener.onProgressChanged(mProgressPercent);
            mProgressListeners.add(listener);
        }

        @Override
        public void removeProgressListener(ProgressListener listener) {
            mProgressListeners.remove(listener);
        }
    }

    private final MediaSaver mMediaSaver;
    private final PlaceholderManager mPlaceholderManager;
    private final SessionStorageManager mSessionStorageManager;
    private final ContentResolver mContentResolver;

    /** Failed session messages. Uri -> message. */
    private final HashMap<Uri, CharSequence> mFailedSessionMessages =
            new HashMap<Uri, CharSequence>();

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
     * @param placeholderManager used to manage placeholders in the filmstrip
     *            before the final result is ready
     * @param sessionStorageManager used to tell modules where to store
     *            temporary session data
     */
    public CaptureSessionManagerImpl(MediaSaver mediaSaver, ContentResolver contentResolver,
            PlaceholderManager placeholderManager, SessionStorageManager sessionStorageManager) {
        mSessions = new HashMap<String, CaptureSession>();
        mMediaSaver = mediaSaver;
        mContentResolver = contentResolver;
        mPlaceholderManager = placeholderManager;
        mSessionStorageManager = sessionStorageManager;
    }

    @Override
    public CaptureSession createNewSession(String title, long sessionStartTime, Location location) {
        return new CaptureSessionImpl(title, sessionStartTime, location);
    }

    @Override
    public CaptureSession createSession() {
        return new CaptureSessionImpl(null, System.currentTimeMillis(), null);
    }

    @Override
    public void putSession(Uri sessionUri, CaptureSession session) {
        synchronized (mSessions)  {
            mSessions.put(sessionUri.toString(), session);
        }
    }

    @Override
    public CaptureSession getSession(Uri sessionUri) {
        synchronized (mSessions)  {
            return mSessions.get(sessionUri.toString());
        }
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
    public File getSessionDirectory(String subDirectory) throws IOException {
      return mSessionStorageManager.getSessionDirectory(subDirectory);
    }

    private void removeSession(String sessionUri) {
        synchronized (mSessions) {
            mSessions.remove(sessionUri);
        }
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
     * Notifies all task listeners that the task with the given URI has been
     * failed to process.
     */
    private void notifyTaskFailed(final Uri uri, final CharSequence reason) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionFailed(uri, reason);
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
     * Notifies all task listeners that the task with the given URI has
     * changed its progress message.
     */
    private void notifyTaskProgressText(final Uri uri, final CharSequence message) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionProgressText(uri, message);
                    }
                }
            }
        });
    }

    /**
     * Notifies all task listeners that the task with the given URI has updated
     * its media.
     */
    private void notifySessionPreviewAvailable(final Uri uri) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionPreviewAvailable(uri);
                    }
                }
            }
        });
    }

    @Override
    public boolean hasErrorMessage(Uri uri) {
        return mFailedSessionMessages.containsKey(uri);
    }

    @Override
    public CharSequence getErrorMesage(Uri uri) {
        return mFailedSessionMessages.get(uri);
    }

    @Override
    public void removeErrorMessage(Uri uri) {
        mFailedSessionMessages.remove(uri);
    }

    @Override
    public void fillTemporarySession(final SessionListener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mSessions) {
                    for (String sessionUri : mSessions.keySet()) {
                        CaptureSession session = mSessions.get(sessionUri);
                        listener.onSessionQueued(session.getUri());
                        listener.onSessionProgress(session.getUri(), session.getProgress());
                        listener.onSessionProgressText(session.getUri(),
                                session.getProgressMessage());
                    }
                }
            }
        });
    }
}
