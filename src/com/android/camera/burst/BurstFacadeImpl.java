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
import android.view.Surface;

import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstController.ImageStreamProperties;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.StackSaver;

import java.util.Map;
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

    private static final int DEFAULT_PREVIEW_WIDTH = 320;
    private static final int DEFAULT_PREVIEW_HEIGHT = 240;

    private final AtomicReference<BurstModuleState> mBurstModuleState =
            new AtomicReference<BurstModuleState>(BurstModuleState.IDLE);
    private final AtomicReference<BurstTaker> mBurstTaker =
            new AtomicReference<>(null);

    private final BurstController mBurstController;

    /** A stack saver for the outstanding burst request. */
    private StackSaver mActiveStackSaver;

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
                }

                @Override
                public void onBurstCompleted(BurstResult burstResult) {
                    BurstResultsSaver.saveBurstResultsInBackground(burstResult, mActiveStackSaver,
                            new Runnable() {
                        @Override
                        public void run() {
                            mBurstModuleState.set(BurstModuleState.IDLE);
                            }
                        });
                }

                @Override
                public void onArtifactCountAvailable(
                        final Map<String, Integer> artifactTypeCount) {
                    BurstResultsSaver.logArtifactCount(artifactTypeCount);
                }
            };

    private final OrientationLockController mOrientationLockController;
    private final BurstReadyStateChangeListener mReadyStateListener;

    private final AtomicReference<SurfaceTextureContainer> mSurfaceTextureContainer =
            new AtomicReference<>();

    /**
     * Create a new BurstManagerImpl instance.
     *
     * @param appContext the application context
     * @param orientationLockController for locking orientation when burst is
     *            running.
     * @param readyStateListener gets called when the ready state of Burst
     *            changes.
     */
    public BurstFacadeImpl(Context appContext,
            OrientationLockController orientationLockController,
            BurstReadyStateChangeListener readyStateListener) {
        mOrientationLockController = orientationLockController;
        mBurstController = new BurstControllerImpl(appContext);
        mReadyStateListener = readyStateListener;
    }

    @Override
    public void startBurst(CaptureSession.CaptureSessionCreator captureSessionCreator,
            DeviceOrientation deviceOrientation,
            Facing cameraFacing,
            int imageOrientationDegrees) {
        MainThread.checkMainThread();
        if (mBurstTaker.get() != null &&
                mBurstModuleState.compareAndSet(BurstModuleState.IDLE,
                        BurstModuleState.RUNNING)) {
            // Only create a session if we do start a burst.
            CaptureSession captureSession = captureSessionCreator.createAndStartEmpty();
            mActiveStackSaver = captureSession.getStackSaver();

            mOrientationLockController.lockOrientation();
            // Disable the shutter button.
            mReadyStateListener.onBurstReadyStateChanged(false);

            Log.d(TAG, "Starting burst. Device orientation: " + deviceOrientation.getDegrees()
                    + " image orientation: " + imageOrientationDegrees);
            int width = DEFAULT_PREVIEW_WIDTH;
            int height = DEFAULT_PREVIEW_HEIGHT;
            if (imageOrientationDegrees % 180 == 90) {
                int tmp = width;
                width = height;
                height = tmp;
            }

            ImageStreamProperties imageStreamProperties =
                    new ImageStreamProperties(width, height,
                            imageOrientationDegrees, cameraFacing == Facing.FRONT);
            EvictionHandler evictionHandler =
                    mBurstController.startBurst(
                            mSurfaceTextureContainer.get().getSurfaceTexture(),
                            imageStreamProperties,
                            mBurstResultsListener,
                            captureSession);

            // Start burst.
            mBurstTaker.get().startBurst(evictionHandler, mBurstController);
        } else {
            Log.e(TAG, "Cannot start burst.");
        }
    }

    @Override
    public boolean stopBurst() {
        MainThread.checkMainThread();
            boolean wasStopped = false;
            if (mBurstModuleState.compareAndSet(BurstModuleState.RUNNING,
                    BurstModuleState.STOPPING)) {
                mBurstTaker.get().stopBurst();
                wasStopped = true;
                reEnableUI();
            }
            return wasStopped;
    }

    @Override
    public Surface getInputSurface() {
        return mSurfaceTextureContainer.get().getSurface();
    }

    @Override
    public void initialize(SurfaceTexture surfaceTexture) {
        MainThread.checkMainThread();
        // TODO: Use preview sizes from Camera API here instead of using the
        // default.
        surfaceTexture.setDefaultBufferSize(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT);

        // Detach from GL context, to allow frame distributor to attach to the
        // GL context.
        surfaceTexture.detachFromGLContext();
        mSurfaceTextureContainer.set(new SurfaceTextureContainer(surfaceTexture));
    }

    @Override
    public void release() {
        MainThread.checkMainThread();
        stopBurst();
        if (mSurfaceTextureContainer.get() != null) {
            mSurfaceTextureContainer.get().close();
            mSurfaceTextureContainer.set(null);
        }
    }

    @Override
    public void setBurstTaker(BurstTaker burstTaker) {
        mBurstTaker.set(burstTaker);
    }

    private void reEnableUI() {
        MainThread.checkMainThread();
        mOrientationLockController.unlockOrientation();
        // Re-enable the shutter button.
        mReadyStateListener.onBurstReadyStateChanged(true);
    }
}
