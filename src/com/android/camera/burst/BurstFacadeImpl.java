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

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.location.Location;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.camera.app.OrientationManager;
import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.app.OrientationManager.OnOrientationChangeListener;
import com.android.camera.data.LocalData;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.exif.ExifInterface;
import com.android.camera.gl.FrameDistributor.FrameConsumer;
import com.android.camera.gl.FrameDistributorWrapper;
import com.android.camera.gl.SurfaceTextureConsumer;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.BurstParameters;
import com.android.camera.one.OneCamera.BurstResultsCallback;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.StackSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper to manage burst, listen to burst results and saves media items.
 * <p/>
 * The UI feedback is rudimentary in form of a toast that is displayed on start
 * of the burst and when artifacts are saved. TODO: Move functionality of saving
 * burst items to a {@link com.android.camera.processing.ProcessingTask} and
 * change API to use {@link com.android.camera.processing.ProcessingService}.
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
     * type + "_" + index of artifact + "_" + index of media item + "_" +
     * timestamp
     */
    private static final String MEDIA_ITEM_FILENAME_FORMAT_STRING = "Burst_%s_%d_%d_%d";

    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    private final AtomicReference<BurstModuleState> mBurstModuleState =
            new AtomicReference<BurstModuleState>(BurstModuleState.IDLE);

    /** Lock to protect starting and stopping of the burst. */
    private final Object mStartStopBurstLock = new Object();

    private final BurstController mBurstController;
    /**
     * Results callback that is invoked by camera when results are available.
     */
    private final BurstResultsCallback mBurstExtractsResultsCallback = new BurstResultsCallback() {
        @Override
        public void onBurstComplete(ResultsAccessor resultAccessor) {
            // Pass the results accessor to the controller.
            mBurstController.stopBurst(resultAccessor);
        }
    };

    /** A stack saver for the outstanding burst request. */
    private StackSaver mActiveStackSaver;

    /** The image orientation of the active burst. */
    private int mImageOrientation;

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
                    mActiveStackSaver = null;

                    // Transition to idle state, ready to take another burst.
                    mBurstModuleState.set(BurstModuleState.IDLE);

                    // Re-enable the shutter button.
                    mReadyStateListener.onBurstReadyStateChanged(true);
                }

                @Override
                public void onBurstCompleted(BurstResult burstResult) {
                    saveBurstResultAndEnableShutterButton(burstResult, mActiveStackSaver);
                }

                @Override
                public void onArtifactCountAvailable(
                        final Map<String, Integer> artifactTypeCount) {
                    logArtifactCount(artifactTypeCount);
                }
            };

    private final OrientationManager.OnOrientationChangeListener mOrientationChangeListener =
            new OnOrientationChangeListener() {
                @Override
                public void onOrientationChanged(OrientationManager orientationManager,
                        DeviceOrientation orientation) {
                    mBurstController.onOrientationChanged(orientation.getDegrees(),
                            mCamera.getDirection() == Facing.FRONT);
                }
            };

    /** Camera instance for starting/stopping the burst. */
    private OneCamera mCamera;

    private final OrientationManager mOrientationManager;
    private final BurstReadyStateChangeListener mReadyStateListener;

    /** Used to distribute camera frames to consumers. */
    private final FrameDistributorWrapper mFrameDistributor;

    /** The frame consumer that renders frames to the preview. */
    private final SurfaceTextureConsumer mPreviewConsumer;

    /**
     * Create a new BurstManagerImpl instance.
     *
     * @param appContext the Android application context.
     * @param orientationManager orientationManager
     * @param readyStateListener gets called when the ready state of Burst
     *            changes.
     */
    public BurstFacadeImpl(Context appContext, OrientationManager orientationManager,
            BurstReadyStateChangeListener readyStateListener,
            FrameDistributorWrapper frameDistributor, SurfaceTextureConsumer previewConsumer) {
        mOrientationManager = orientationManager;
        mBurstController = new BurstControllerImpl(appContext, mBurstResultsListener);
        mReadyStateListener = readyStateListener;
        mFrameDistributor = frameDistributor;
        mPreviewConsumer = previewConsumer;
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
    public void startBurst(CaptureSession captureSession, File tempSessionDirectory) {
        synchronized (mStartStopBurstLock) {
            if (mCamera != null &&
                    mBurstModuleState.compareAndSet(BurstModuleState.IDLE,
                            BurstModuleState.RUNNING)) {
                Log.d(TAG, "Starting burst.");
                Location location = captureSession.getLocation();

                mOrientationManager.addOnOrientationChangeListener(mOrientationChangeListener);
                int orientation = mOrientationManager.getDeviceOrientation().getDegrees();
                mBurstController.onOrientationChanged(orientation,
                        mCamera.getDirection() == Facing.FRONT);

                BurstConfiguration burstConfig = mBurstController.startBurst();
                BurstParameters params = new BurstParameters(captureSession.getTitle(),
                        orientation, location, tempSessionDirectory, burstConfig,
                        mBurstExtractsResultsCallback);

                if (mActiveStackSaver != null) {
                    throw new IllegalStateException(
                            "Cannot start a burst while another is in progress.");
                }
                mActiveStackSaver = captureSession.getStackSaver();
                mImageOrientation = mOrientationManager.getDeviceOrientation().getDegrees();

                // Disable the shutter button.
                mReadyStateListener.onBurstReadyStateChanged(false);

                // Start burst.
                mCamera.startBurst(params, captureSession);
            }
        }
    }

    @Override
    public boolean isReady() {
        return mBurstModuleState.get() == BurstModuleState.IDLE;
    }

    @Override
    public boolean stopBurst() {
        synchronized (mStartStopBurstLock) {
            boolean wasStopped = false;
            if (mBurstModuleState.compareAndSet(BurstModuleState.RUNNING,
                    BurstModuleState.STOPPING)) {
                mOrientationManager.removeOnOrientationChangeListener(mOrientationChangeListener);
                if (mCamera != null) {
                    mCamera.stopBurst();
                    wasStopped = true;
                }
            }
            return wasStopped;
        }
    }

    @Override
    public void setSurfaceTexture(SurfaceTexture surfaceTexture, int width, int height) {
        mPreviewConsumer.setSurfaceTexture(surfaceTexture, width, height);
    }

    @Override
    public void initializeSurfaceTextureConsumer(int surfaceWidth, int surfaceHeight) {
        initializeSurfaceTextureConsumer(mPreviewConsumer.getSurfaceTexture(), surfaceWidth,
                surfaceHeight);
    }

    @Override
    public void initializeSurfaceTextureConsumer(SurfaceTexture surface, int surfaceWidth,
                                                 int surfaceHeight) {
        if (surface == null) {
            return;
        }

        if (mPreviewConsumer.getSurfaceTexture() != surface) {
            mPreviewConsumer.setSurfaceTexture(surface, surfaceWidth, surfaceHeight);
        } else if (mPreviewConsumer.getWidth() != surfaceWidth
                || mPreviewConsumer.getHeight() != surfaceHeight) {
            mPreviewConsumer.setSize(surfaceWidth, surfaceHeight);
        }
    }

    @Override
    public void initializeAndStartFrameDistributor() {
        // Currently, there is only one consumer to FrameDistributor for
        // rendering the frames to the preview texture.
        List<FrameConsumer> frameConsumers = new ArrayList<>();
        frameConsumers.add(mBurstController.getPreviewFrameConsumer());
        frameConsumers.add(mPreviewConsumer);
        mFrameDistributor.start(frameConsumers);
    }

    @Override
    public void updatePreviewBufferSize(int width, int height) {
        mFrameDistributor.updatePreviewBufferSize(width, height);
    }

    @Override
    public void closeFrameDistributor() {
        mFrameDistributor.close();
    }

    @Override
    public SurfaceTexture getInputSurfaceTexture() {
        if (mFrameDistributor != null) {
            return mFrameDistributor.getInputSurfaceTexture();
        } else {
            return null;
        }
    }

    @Override
    public void setPreviewConsumerSize(int previewWidth, int previewHeight) {
        mPreviewConsumer.setSize(previewWidth, previewHeight);
    }

    /**
     * Saves the burst result and on completion re-enables the shutter button.
     *
     * @param burstResult the result of the burst.
     */
    private void saveBurstResultAndEnableShutterButton(final BurstResult burstResult,
            final StackSaver stackSaver) {
        Log.i(TAG, "Saving results of of the burst.");

        AsyncTask<Void, String, Void> saveTask =
                new AsyncTask<Void, String, Void>() {
                    @Override
                    protected Void doInBackground(Void... arg0) {
                        for (String artifactType : burstResult.getTypes()) {
                            publishProgress(artifactType);
                            saveArtifacts(stackSaver, burstResult, artifactType);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        mBurstModuleState.set(BurstModuleState.IDLE);
                        // Re-enable the shutter button.
                        mReadyStateListener.onBurstReadyStateChanged(true);
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
    private void saveArtifacts(final StackSaver stackSaver, final BurstResult burstResult,
            final String artifactType) {
        List<BurstArtifact> artifactList = burstResult.getArtifactsByType(artifactType);
        for (int artifactIndex = 0; artifactIndex < artifactList.size(); artifactIndex++) {
            List<BurstMediaItem> mediaItems = artifactList.get(artifactIndex).getMediaItems();
            for (int index = 0; index < mediaItems.size(); index++) {
                saveBurstMediaItem(stackSaver, mediaItems.get(index),
                        artifactType, artifactIndex + 1, index + 1);
            }
        }
    }

    private void saveBurstMediaItem(StackSaver stackSaver, BurstMediaItem mediaItem,
            String artifactType, int artifactIndex, int index) {
        long timestamp = mediaItem.getTimestamp();
        String title = String.format(MEDIA_ITEM_FILENAME_FORMAT_STRING,
                artifactType, artifactIndex, index, timestamp);
        String mimeType = mediaItem.getMimeType();
        ExifInterface exif = null;
        if (LocalData.MIME_TYPE_JPEG.equals(mimeType)) {
            exif = new ExifInterface();
            exif.addDateTimeStampTag(
                    ExifInterface.TAG_DATE_TIME,
                    timestamp,
                    UTC_TIMEZONE);
        }

        stackSaver.saveStackedImage(mediaItem.getData(),
                title,
                mediaItem.getWidth(),
                mediaItem.getHeight(),
                mImageOrientation,
                exif,
                timestamp,
                mimeType);
    }

    private void logArtifactCount(final Map<String, Integer> artifactTypeCount) {
        final String prefix = "Finished burst. Creating ";
        List<String> artifactDescription = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : artifactTypeCount.entrySet()) {
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
