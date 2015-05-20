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

package com.android.camera.captureintent;

import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.SessionNotifier;
import com.android.camera.session.StackSaver;
import com.android.camera.session.TemporarySessionFile;
import com.android.camera.stats.CaptureSessionStatsCollector;
import com.android.camera.util.Size;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nonnull;

/**
 * An implementation of {@link CaptureSession} which is used by
 * {@link CaptureIntentModule}.
 */
public class CaptureIntentSession implements CaptureSession {
    private static final Log.Tag TAG = new Log.Tag("CapIntSession");

    /** For aggregation of capture information */
    // TODO: Implement mCaptureSessionStatsCollector.decorateAtTimeCaptureRequest call.
    private final CaptureSessionStatsCollector mCaptureSessionStatsCollector =
            new CaptureSessionStatsCollector();
    /** The capture session manager responsible for this session. */
    private final CaptureSessionManager mSessionManager;
    /** Used to inform about session status updates. */
    private final SessionNotifier mSessionNotifier;
    /** The title of the item being processed. */
    private final String mTitle;
    /** The location this session was created at. Used for media store. */
    private Location mLocation;
    /** Whether one of start methods are called. */
    private boolean isStarted;

    /**
     * Creates a new {@link CaptureSession}.
     *
     * @param title the title of this session.
     * @param location the location of this session, used for media store.
     * @param captureSessionManager the capture session manager responsible for
     *            this session.
     */
    public CaptureIntentSession(String title, Location location,
            CaptureSessionManager captureSessionManager, SessionNotifier sessionNotifier) {
        mTitle = title;
        mLocation = location;
        mSessionManager = captureSessionManager;
        mSessionNotifier = sessionNotifier;
        isStarted = false;
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

    // TODO: Support progress in the future once HDR is enabled for capture intent.
    @Override
    public synchronized int getProgress() {
        return 0;
    }

    @Override
    public synchronized void setProgress(int percent) {
        // Do nothing.
    }

    @Override
    public synchronized int getProgressMessageId() {
        return -1;
    }

    @Override
    public synchronized void setProgressMessage(int messageId) {
    }

    @Override
    public void updateThumbnail(Bitmap bitmap) {
        mSessionNotifier.notifySessionThumbnailAvailable(bitmap);
    }

    @Override
    public void updateCaptureIndicatorThumbnail(Bitmap indicator, int rotationDegrees) {
        // Do nothing
    }

    @Override
    public synchronized void startEmpty(ImageLifecycleListener listener, @Nonnull Size pictureSize) {
        isStarted = true;
    }

    @Override
    public synchronized void startSession(ImageLifecycleListener listener, @Nonnull Bitmap placeholder,
          int progressMessageId) {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public synchronized void startSession(ImageLifecycleListener listener, @Nonnull byte[] placeholder,
          int progressMessageId) {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public synchronized void startSession(ImageLifecycleListener listener, @Nonnull Uri uri,
          @Nonnull int progressMessageId) {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public synchronized void cancel() {
    }

    @Override
    public synchronized ListenableFuture<Optional<Uri>> saveAndFinish(byte[] data, int width,
            int height, int orientation, ExifInterface exif) {
        mSessionNotifier.notifySessionPictureDataAvailable(data, orientation);
        return Futures.immediateFuture(Optional.<Uri> absent());
    }

    @Override
    public StackSaver getStackSaver() {
        return null;
    }

    @Override
    public void finish() {
        // Do nothing.
    }

    @Override
    public TemporarySessionFile getTempOutputFile() {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public Uri getUri() {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public void updatePreview() {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public void finishWithFailure(int progressMessageId, boolean removeFromFilmstrip) {
        throw new RuntimeException("Not supported.");
    }

    @Override
    public void finalizeSession() {
        // Do nothing.
    }

    @Override
    public void addProgressListener(CaptureSession.ProgressListener listener) {
        // Do nothing.
    }

    @Override
    public void removeProgressListener(CaptureSession.ProgressListener listener) {
        // Do nothing.
    }

    @Override
    public CaptureSessionStatsCollector getCollector() {
        return mCaptureSessionStatsCollector;
    }

    private boolean isStarted() {
        return isStarted;
    }
}
