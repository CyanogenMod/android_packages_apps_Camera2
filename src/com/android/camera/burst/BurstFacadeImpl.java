/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import android.content.ContentResolver;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.camera.app.AppController;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.OrientationManager;
import com.android.camera.data.LocalData;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.exif.ExifInterface;
import com.android.camera.gl.FrameDistributor.FrameConsumer;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.BurstParameters;
import com.android.camera.one.OneCamera.BurstResultsCallback;
import com.android.camera.session.CaptureSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper to manage burst, listen to burst results and saves media items.
 * <p/>
 * The UI feedback is rudimentary in form of a toast that is displayed on start of the
 * burst and when artifacts are saved.
 *
 * TODO: Move functionality of saving burst items to a
 * {@link com.android.camera.processing.ProcessingTask} and change API to use
 * {@link com.android.camera.processing.ProcessingService}.
 *
 * TODO: Hook UI to the listener.
 */
class BurstFacadeImpl implements BurstFacade {
    /**
     * The state of the burst module.
     */
    private static enum BurstModuleState {
        IDLE,
        RUNNING,
        STOPPING
    }

    private static final Tag TAG = new Tag("BurstFacadeImpl");

    /**
     * The format string of burst media item file name (without extension).
     * <p/>
     * An media item file name has the following format: "Burst_" + artifact
     * type + "_" + index of artifact + "_" + timestamp
     */
    private static final String MEDIA_ITEM_FILENAME_FORMAT_STRING = "Burst_%s_%d_%d";
    /**
     * The title of Capture session for Burst.
     * <p/>
     * Title is of format: Burst_timestamp
     */
    private static final String BURST_TITLE_FORMAT_STRING = "Burst_%d";

    private final AtomicReference<BurstModuleState> mBurstModuleState =
            new AtomicReference<BurstModuleState>(BurstModuleState.IDLE);

    /** Lock to protect starting and stopping of the burst. */
    private final Object mStartStopBurstLock = new Object();

    private final BurstController mBurstController;
    private final AppController mAppController;
    private final File mDebugDataDir;

    private final MediaSaver.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mAppController.notifyNewMedia(uri);
                    }
                }
            };

    /**
     * Results callback that is invoked by camera when results are available.
     */
    private final BurstResultsCallback
            mBurstExtractsResultsCallback = new BurstResultsCallback() {
                @Override
                public void onBurstComplete(ResultsAccessor resultAccessor) {
                    // Pass the results accessor to the controller.
                    mBurstController.stopBurst(resultAccessor);
                }
            };

    /**
     * Listener for burst controller. Saves the results and interacts with the
     * UI.
     */
    private final BurstResultsListener mBurstResultsListener =
            new BurstResultsListener() {
                @Override
                public void onBurstStarted() {
                }

                @Override
                public void onBurstError(Exception error) {
                    Log.e(TAG, "Exception while running the burst" + error);
                    mBurstModuleState.set(BurstModuleState.IDLE);
                    // Re-enable the shutter button.
                    mAppController.setShutterEnabled(true);
                }

                @Override
                public void onBurstCompleted(BurstResult burstResult) {
                    saveBurstResultAndEnableShutterButton(burstResult);
                }

                @Override
                public void onArtifactCountAvailable(
                        final Map<String, Integer> artifactTypeCount) {
                    logArtifactCount(artifactTypeCount);
                }
            };

    /** Camera instance for starting/stopping the burst. */
    private OneCamera mCamera;

    private final MediaSaver mMediaSaver;
    private final LocationManager mLocationManager;
    private final OrientationManager mOrientationManager;
    private volatile ContentResolver mContentResolver;

    /**
     * Create a new BurstManagerImpl instance.
     *
     * @param appController the app level controller for controlling the shutter
     *            button.
     * @param mediaSaver the {@link MediaSaver} instance for saving results of
     *            burst.
     * @param locationManager for querying location of burst.
     * @param orientationManager for querying orientation of burst.
     * @param debugDataDir the debug directory to use for burst.
     */
    public BurstFacadeImpl(AppController appController,
            MediaSaver mediaSaver,
            LocationManager locationManager,
            OrientationManager orientationManager,
            File debugDataDir) {
        mAppController = appController;
        mMediaSaver = mediaSaver;
        mLocationManager = locationManager;
        mDebugDataDir = debugDataDir;
        mOrientationManager = orientationManager;
        mBurstController = new BurstControllerImpl(
                mAppController.getAndroidContext(),
                mBurstResultsListener);
    }

    /**
     * Set the content resolver to be updated when saving burst results.
     *
     * @param contentResolver to be updated when burst results are saved.
     */
    @Override
    public void setContentResolver(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    @Override
    public void onCameraAttached(OneCamera camera) {
        synchronized (mStartStopBurstLock) {
            mCamera = camera;
        }
    }

    @Override
    public void onCameraDetached() {
        synchronized (mStartStopBurstLock) {
            mCamera = null;
        }
    }

    @Override
    public FrameConsumer getPreviewFrameConsumer() {
        return mBurstController.getPreviewFrameConsumer();
    }

    @Override
    public void startBurst() {
        startBurstImpl();
    }

    @Override
    public boolean isBurstRunning() {
        return (mBurstModuleState.get() == BurstModuleState.RUNNING
                || mBurstModuleState.get() == BurstModuleState.STOPPING);
    }

    private void startBurstImpl() {
        synchronized (mStartStopBurstLock) {
            if (mCamera != null &&
                    mBurstModuleState.compareAndSet(BurstModuleState.IDLE,
                            BurstModuleState.RUNNING)) {
                // TODO: Use localized strings everywhere.
                Log.d(TAG, "Starting burst.");
                Location location = mLocationManager.getCurrentLocation();

                // Set up the capture session.
                long sessionTime = System.currentTimeMillis();
                String title = String.format(BURST_TITLE_FORMAT_STRING, sessionTime);

                // TODO: Fix the capture session and use it for saving
                // intermediate results.
                CaptureSession session = null;

                BurstConfiguration burstConfig = mBurstController.startBurst();
                BurstParameters params = new BurstParameters();
                params.callback = mBurstExtractsResultsCallback;
                params.burstConfiguration = burstConfig;
                params.title = title;
                params.orientation = mOrientationManager.getDeviceOrientation().getDegrees();
                params.debugDataFolder = mDebugDataDir;
                params.location = location;

                // Disable the shutter button.
                mAppController.setShutterEnabled(false);

                // start burst.
                mCamera.startBurst(params, session);
            }
        }
    }

    @Override
    public void stopBurst() {
        synchronized (mStartStopBurstLock) {
            if (mBurstModuleState.compareAndSet(BurstModuleState.RUNNING,
                    BurstModuleState.STOPPING)) {
                if (mCamera != null) {
                    mCamera.stopBurst();
                }
            }
        }
    }

    /**
     * Saves the burst result and on completion re-enables the shutter button.
     *
     * @param burstResult the result of the burst.
     */
    private void saveBurstResultAndEnableShutterButton(final BurstResult burstResult) {
        Log.i(TAG, "Saving results of of the burst.");

        AsyncTask<Void, String, Void> saveTask =
                new AsyncTask<Void, String, Void>() {
                    @Override
                    protected Void doInBackground(Void... arg0) {
                        for (String artifactType : burstResult.getTypes()) {
                            publishProgress(artifactType);
                            saveArtifacts(burstResult, artifactType);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        mBurstModuleState.set(BurstModuleState.IDLE);
                        // Re-enable the shutter button.
                        mAppController.setShutterEnabled(true);
                    }

                    @Override
                    protected void onProgressUpdate(String... artifactTypes) {
                        logProgressUpdate(artifactTypes, burstResult);
                    }
                };
        saveTask.execute(null, null, null);
    }

    /**
     * Save individual artifacts for bursts.
     */
    private void saveArtifacts(final BurstResult burstResult,
            final String artifactType) {
        int index = 0;
        for (BurstArtifact artifact : burstResult.getArtifactsByType(artifactType)) {
            for (BurstMediaItem mediaItem : artifact.getMediaItems()) {
                saveBurstMediaItem(mediaItem,
                        artifactType, ++index);
            }
        }
    }

    private void saveBurstMediaItem(BurstMediaItem mediaItem,
            String artifactType,
            int index) {
        long timestamp = System.currentTimeMillis();
        final String mimeType = mediaItem.getMimeType();
        final String title = String.format(MEDIA_ITEM_FILENAME_FORMAT_STRING,
                artifactType, index, timestamp);
        byte[] data = mediaItem.getData();
        ExifInterface exif = null;
        if (LocalData.MIME_TYPE_JPEG.equals(mimeType)) {
            exif = new ExifInterface();
            exif.addDateTimeStampTag(
                    ExifInterface.TAG_DATE_TIME,
                    timestamp,
                    TimeZone.getDefault());

        }
        mMediaSaver.addImage(data,
                title,
                timestamp,
                mLocationManager.getCurrentLocation(),
                mediaItem.getWidth(),
                mediaItem.getHeight(),
                mOrientationManager.getDeviceOrientation().getDegrees(),
                exif, // exif,
                mOnMediaSavedListener,
                mContentResolver,
                mimeType);
    }

    private void logArtifactCount(final Map<String, Integer> artifactTypeCount) {
        final String prefix = "Finished burst. Creating ";
        List<String> artifactDescription = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry :
                artifactTypeCount.entrySet()) {
            artifactDescription.add(entry.getValue() + " " + entry.getKey());
        }

        String message = prefix + TextUtils.join(" and ", artifactDescription) + ".";
        Log.d(TAG, message);
    }

    private void logProgressUpdate(String[] artifactTypes, BurstResult burstResult) {
        for (String artifactType : artifactTypes) {
            List<BurstArtifact> artifacts =
                    burstResult.getArtifactsByType(artifactType);
            if (!artifacts.isEmpty()) {
                Log.d(TAG, "Saving " + artifacts.size()
                        + " " + artifactType + "s.");
            }
        }
    }
}
