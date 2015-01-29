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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;

import com.android.camera.app.MediaSaver;
import com.android.camera.app.MediaSaver.OnMediaSavedListener;
import com.android.camera.async.MainThread;
import com.android.camera.data.FilmstripItemData;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.FileUtil;
import com.android.camera.util.Size;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * Implementation for the {@link CaptureSessionManager}.
 * <p>
 * Basic usage:
 * <ul>
 * <li>Create a new capture session.</li>
 * <li>Pass it around to anywhere where the status of a session needs to be
 * updated.</li>
 * <li>If this is a longer operation, use one of the start* methods to indicate
 * that processing of this session has started. The Camera app right now will
 * use this to add a new item to the filmstrip and indicate the current
 * progress.</li>
 * <li>If the final result is already available and no processing is required,
 * store the final image using saveAndFinish</li>
 * <li>For longer operations, update the thumbnail and status message using the
 * provided methods.</li>
 * <li>For longer operations, update the thumbnail and status message using the
 * provided methods.</li>
 * <li>Once processing is done, the final image can be saved using saveAndFinish
 * </li>
 * </ul>
 * </p>
 * It's OK to call saveAndFinish either before or after the session has been
 * started.
 * <p>
 * If startSession is called after the session has been finished, it will be
 * treated as a no-op.
 * </p>
 */
public class CaptureSessionManagerImpl implements CaptureSessionManager {

    private class CaptureSessionImpl implements CaptureSession {
        /** The title of the item being processed. */
        private final String mTitle;
        /** These listeners get informed about progress updates. */
        private final HashSet<ProgressListener> mProgressListeners = new HashSet<>();
        private final long mSessionStartMillis;
        /**
         * The file that can be used to write the final JPEG output temporarily,
         * before it is copied to the final location.
         */
        private final TemporarySessionFile mTempOutputFile;
        /** Saver that is used to store a stack of images. */
        private final StackSaver mStackSaver;
        /** A URI of the item being processed. */
        private Uri mUri;
        /** The location this session was created at. Used for media store. */
        private Location mLocation;
        /** The current progress of this session in percent. */
        private int mProgressPercent = 0;
        /** A message ID for the current progress state. */
        private CharSequence mProgressMessage;
        /** A place holder for this capture session. */
        private PlaceholderManager.Session mPlaceHolderSession;
        private Uri mContentUri;
        /** Whether this image was finished. */
        private volatile boolean mIsFinished;

        /**
         * Creates a new {@link CaptureSession}.
         *
         * @param title the title of this session.
         * @param sessionStartMillis the timestamp of this capture session
         *            (since epoch).
         * @param location the location of this session, used for media store.
         * @param stackSaver used to save stacks of images that belong to this session.
         * @throws IOException in case the storage required to store session
         *             data is not available.
         */
        private CaptureSessionImpl(String title, long sessionStartMillis, Location location,
                StackSaver stackSaver) {
            mTitle = title;
            mSessionStartMillis = sessionStartMillis;
            mLocation = location;
            mTempOutputFile = new TemporarySessionFile(mSessionStorageManager, TEMP_SESSIONS, mTitle);
            mStackSaver = stackSaver;
            mIsFinished = false;
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
        public synchronized int getProgress() {
            return mProgressPercent;
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
        public void updateThumbnail(Bitmap bitmap) {
            mPlaceholderManager.replacePlaceholder(mPlaceHolderSession, bitmap);
            notifyTaskQueued(mUri);
            onPreviewAvailable();
        }

        @Override
        public synchronized void startEmpty(Size pictureSize) {
            if (mIsFinished) {
                return;
            }

            mProgressMessage = "";
            mPlaceHolderSession = mPlaceholderManager.insertEmptyPlaceholder(mTitle, pictureSize,
                    mSessionStartMillis);
            mUri = mPlaceHolderSession.outputUri;
            putSession(mUri, this);
            notifyTaskQueued(mUri);
            onPreviewAvailable();
        }

        @Override
        public synchronized void startSession(Bitmap placeholder, CharSequence progressMessage) {
            if (mIsFinished) {
                return;
            }

            mProgressMessage = progressMessage;
            mPlaceHolderSession = mPlaceholderManager.insertPlaceholder(mTitle, placeholder,
                    mSessionStartMillis);
            mUri = mPlaceHolderSession.outputUri;
            putSession(mUri, this);
            notifyTaskQueued(mUri);
            onPreviewAvailable();
        }

        @Override
        public synchronized void startSession(byte[] placeholder, CharSequence progressMessage) {
            if (mIsFinished) {
                return;
            }

            mProgressMessage = progressMessage;

            mPlaceHolderSession = mPlaceholderManager.insertPlaceholder(mTitle, placeholder,
                    mSessionStartMillis);
            mUri = mPlaceHolderSession.outputUri;
            putSession(mUri, this);
            notifyTaskQueued(mUri);
            onPreviewAvailable();
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
            if (isStarted()) {
                removeSession(mUri.toString());
            }
        }

        @Override
        public synchronized void saveAndFinish(byte[] data, int width, int height, int orientation,
                ExifInterface exif, final OnMediaSavedListener listener) {
            mIsFinished = true;
            if (mPlaceHolderSession == null) {
                mMediaSaver.addImage(
                        data, mTitle, mSessionStartMillis, mLocation, width, height,
                        orientation, exif, listener, mContentResolver);
                return;
            }
            mContentUri = mPlaceholderManager.finishPlaceholder(mPlaceHolderSession, mLocation,
                    orientation, exif, data, width, height, FilmstripItemData.MIME_TYPE_JPEG);

            removeSession(mUri.toString());
            notifyTaskDone(mPlaceHolderSession.outputUri);
        }

        @Override
        public StackSaver getStackSaver() {
            return mStackSaver;
        }

        @Override
        public void finish() {
            if (mPlaceHolderSession == null) {
                throw new IllegalStateException(
                        "Cannot call finish without calling startSession first.");
            }

            mIsFinished = true;
            AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] jpegDataTemp;
                    if (mTempOutputFile.isUsable()) {
                        try {
                            jpegDataTemp = FileUtil.readFileToByteArray(mTempOutputFile.getFile());
                        } catch (IOException e) {
                            return;
                        }
                    } else {
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
        public TemporarySessionFile getTempOutputFile() {
            return mTempOutputFile;
        }

        @Override
        public Uri getUri() {
            return mUri;
        }

        @Override
        public void updatePreview() {
            final File path;
            if (mTempOutputFile.isUsable()) {
                path = mTempOutputFile.getFile();
            } else {
                Log.e(TAG, "Cannot update preview");
                return;
            }
            AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] jpegDataTemp;
                    try {
                        jpegDataTemp = FileUtil.readFileToByteArray(path);
                    } catch (IOException e) {
                        return;
                    }
                    final byte[] jpegData = jpegDataTemp;

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    Bitmap placeholder = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length,
                            options);
                    mPlaceholderManager.replacePlaceholder(mPlaceHolderSession, placeholder);
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

        private void onPreviewAvailable() {
            notifySessionPreviewAvailable(mPlaceHolderSession.outputUri);
        }

        private boolean isStarted() {
            return mUri != null;
        }
    }
    private static final String TEMP_SESSIONS = "TEMP_SESSIONS";
    private static final Log.Tag TAG = new Log.Tag("CaptureSessMgrImpl");
    private final MediaSaver mMediaSaver;
    private final ContentResolver mContentResolver;
    private final PlaceholderManager mPlaceholderManager;
    private final SessionStorageManager mSessionStorageManager;
    private final StackSaverFactory mStackSaverFactory;

    /** Failed session messages. Uri -> message. */
    private final HashMap<Uri, CharSequence> mFailedSessionMessages = new HashMap<>();

    /**
     * We use this to fire events to the session listeners from the main thread.
     */
    private final MainThread mMainHandler;

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
            PlaceholderManager placeholderManager, SessionStorageManager sessionStorageManager,
            StackSaverFactory stackSaverProvider, MainThread mainHandler) {
        mSessions = new HashMap<String, CaptureSession>();
        mMediaSaver = mediaSaver;
        mContentResolver = contentResolver;
        mPlaceholderManager = placeholderManager;
        mSessionStorageManager = sessionStorageManager;
        mStackSaverFactory = stackSaverProvider;
        mMainHandler = mainHandler;
    }

    @Override
    public CaptureSession createNewSession(String title, long sessionStartTime, Location location) {
        return new CaptureSessionImpl(title, sessionStartTime, location, mStackSaverFactory.create(
                title, location));
    }

    @Override
    public void putSession(Uri sessionUri, CaptureSession session) {
        synchronized (mSessions) {
            mSessions.put(sessionUri.toString(), session);
        }
    }

    @Override
    public CaptureSession getSession(Uri sessionUri) {
        synchronized (mSessions) {
            return mSessions.get(sessionUri.toString());
        }
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
        mMainHandler.execute(new Runnable() {
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
        mMainHandler.execute(new Runnable() {
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
        mMainHandler.execute(new Runnable() {
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
        mMainHandler.execute(new Runnable() {
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
     * Notifies all task listeners that the task with the given URI has changed
     * its progress message.
     */
    private void notifyTaskProgressText(final Uri uri, final CharSequence message) {
        mMainHandler.execute(new Runnable() {
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
        mMainHandler.execute(new Runnable() {
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
        mMainHandler.execute(new Runnable() {
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
