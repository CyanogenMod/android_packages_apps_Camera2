/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;

import com.android.camera.Storage;
import com.android.camera.app.MediaSaver;
import com.android.camera.data.FilmstripItemData;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.FileUtil;
import com.android.camera.util.Size;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * The default implementation of the CaptureSession interface. This is the
 * implementation we use for normal Camera use.
 */
public class CaptureSessionImpl implements CaptureSession {
    private static final Log.Tag TAG = new Log.Tag("CaptureSessionImpl");

    /** The capture session manager responsible for this session. */
    private final CaptureSessionManager mSessionManager;
    /** Used to inform about session status updates. */
    private final SessionNotifier mSessionNotifier;
    /** Used for adding/removing/updating placeholders for in-progress sessions. */
    private final PlaceholderManager mPlaceholderManager;
    /** Used to store images on disk and to add them to the media store. */
    private final MediaSaver mMediaSaver;
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
     * @param sessionStartMillis the timestamp of this capture session (since
     *            epoch).
     * @param location the location of this session, used for media store.
     * @param temporarySessionFile used to create a temporary session file if
     *            necessary.
     * @param captureSessionManager the capture session manager responsible for
     *            this session.
     * @param placeholderManager used to add/update/remove session placeholders.
     * @param mediaSaver used to store images on disk and add them to the media
     *            store.
     * @param stackSaver used to save stacks of images that belong to this
     *            session.
     */
    /* package */CaptureSessionImpl(String title,
            long sessionStartMillis, Location location, TemporarySessionFile temporarySessionFile,
            CaptureSessionManager captureSessionManager, SessionNotifier sessionNotifier,
            PlaceholderManager placeholderManager, MediaSaver mediaSaver, StackSaver stackSaver) {
        mTitle = title;
        mSessionStartMillis = sessionStartMillis;
        mLocation = location;
        mTempOutputFile = temporarySessionFile;
        mSessionManager = captureSessionManager;
        mSessionNotifier = sessionNotifier;
        mPlaceholderManager = placeholderManager;
        mMediaSaver = mediaSaver;
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
        mSessionNotifier.notifyTaskProgress(mUri, mProgressPercent);
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
        mSessionNotifier.notifyTaskProgressText(mUri, message);
        for (ProgressListener listener : mProgressListeners) {
            listener.onStatusMessageChanged(message);
        }
    }

    @Override
    public void updateThumbnail(Bitmap bitmap) {
        mPlaceholderManager.replacePlaceholder(mPlaceHolderSession, bitmap);
        mSessionNotifier.notifySessionUpdated(mUri);
    }

    @Override
    public void updateCaptureIndicatorThumbnail(Bitmap indicator, int rotationDegrees) {
        onCaptureIndicatorUpdate(indicator, rotationDegrees);
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
        mSessionManager.putSession(mUri, this);
        mSessionNotifier.notifyTaskQueued(mUri);
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
        mSessionManager.putSession(mUri, this);
        mSessionNotifier.notifyTaskQueued(mUri);
        onCaptureIndicatorUpdate(placeholder, 0);
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
        mSessionManager.putSession(mUri, this);
        mSessionNotifier.notifyTaskQueued(mUri);
        Bitmap placeholderBitmap = Storage.getPlacerHolderForSession(mUri);
        onCaptureIndicatorUpdate(placeholderBitmap, 0);
    }

    @Override
    public synchronized void startSession(Uri uri, CharSequence progressMessage) {
        mUri = uri;
        mProgressMessage = progressMessage;
        mPlaceHolderSession = mPlaceholderManager.convertToPlaceholder(uri);

        mSessionManager.putSession(mUri, this);
        mSessionNotifier.notifyTaskQueued(mUri);
    }

    @Override
    public synchronized void cancel() {
        if (isStarted()) {
            mSessionNotifier.removeSession(mUri.toString());
        }
    }

    @Override
    public synchronized void saveAndFinish(byte[] data, int width, int height, int orientation,
            ExifInterface exif, final MediaSaver.OnMediaSavedListener listener) {
        mIsFinished = true;
        if (mPlaceHolderSession == null) {
            mMediaSaver.addImage(
                    data, mTitle, mSessionStartMillis, mLocation, width, height,
                    orientation, exif, listener);
            return;
        }
        mContentUri = mPlaceholderManager.finishPlaceholder(mPlaceHolderSession, mLocation,
                orientation, exif, data, width, height, FilmstripItemData.MIME_TYPE_JPEG);

        mSessionNotifier.removeSession(mUri.toString());
        mSessionNotifier.notifyTaskDone(mPlaceHolderSession.outputUri);
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
                mSessionNotifier.notifySessionUpdated(mUri);
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
        mSessionNotifier.removeSession(mUri.toString());
        mSessionManager.putErrorMessage(mPlaceHolderSession.outputUri, reason);
        mSessionNotifier.notifyTaskFailed(mPlaceHolderSession.outputUri, reason);
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


    private void onCaptureIndicatorUpdate(Bitmap indicator, int rotationDegrees) {
        mSessionNotifier.notifySessionCaptureIndicatorAvailable(indicator, rotationDegrees);
    }

    private boolean isStarted() {
        return mUri != null;
    }
}
