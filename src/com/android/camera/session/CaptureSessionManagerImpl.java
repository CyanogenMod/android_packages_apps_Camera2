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

import com.android.camera.async.MainThread;
import com.android.camera.debug.Log;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
    private static final Log.Tag TAG = new Log.Tag("CaptureSessMgrImpl");

    private final CaptureSessionFactory mSessionFactory;
    private final SessionStorageManager mSessionStorageManager;

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
     * @param sessionFactory used to create new capture session objects.
     * @param sessionStorageManager used to tell modules where to store
     *            temporary session data
     * @param mainHandler the main handler which listener callback is executed on.
     */
    public CaptureSessionManagerImpl(
            CaptureSessionFactory sessionFactory,
            SessionStorageManager sessionStorageManager,
            MainThread mainHandler) {
        mSessionFactory = sessionFactory;
        mSessions = new HashMap<>();
        mSessionStorageManager = sessionStorageManager;
        mMainHandler = mainHandler;
    }

    public CaptureSession createNewSession(String title, long sessionStartMillis, Location location) {
        return mSessionFactory.createNewSession(this, title, sessionStartMillis, location);
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

    @Override
    public boolean hasErrorMessage(Uri uri) {
        return mFailedSessionMessages.containsKey(uri);
    }

    @Override
    public CharSequence getErrorMessage(Uri uri) {
        return mFailedSessionMessages.get(uri);
    }

    @Override
    public void removeErrorMessage(Uri uri) {
        mFailedSessionMessages.remove(uri);
    }

    protected void putErrorMessage(Uri uri, CharSequence reason) {
        mFailedSessionMessages.put(uri, reason);
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


    void removeSession(String sessionUri) {
        synchronized (mSessions) {
            mSessions.remove(sessionUri);
        }
    }

    /**
     * Notifies all task listeners that the task with the given URI has been
     * queued.
     */
    void notifyTaskQueued(final Uri uri) {
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
    void notifyTaskDone(final Uri uri) {
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
    void notifyTaskFailed(final Uri uri, final CharSequence reason) {
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
    void notifyTaskProgress(final Uri uri, final int progressPercent) {
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
    void notifyTaskProgressText(final Uri uri, final CharSequence message) {
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
     * Notifies all task listeners that the media associated with the task has
     * been updated.
     */
    void notifySessionUpdated(final Uri uri) {
        mMainHandler.execute(new Runnable() {
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

    /**
     * Notifies all task listeners that the task with the given URI has updated
     * its media.
     *
     * @param uri the URI of the session whose preview has been updated
     * @param rotationDegrees the rotation of the updated preview
     */
    void notifySessionCaptureIndicatorAvailable(final Bitmap indicator, final int rotationDegrees) {
        mMainHandler.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (mTaskListeners) {
                    for (SessionListener listener : mTaskListeners) {
                        listener.onSessionCaptureIndicatorUpdate(indicator, rotationDegrees);
                    }
                }
            }
        });

    }
}
