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

package com.android.camera.captureintent.state;

import com.google.common.base.Optional;

import com.android.camera.async.RefCountBase;
import com.android.camera.burst.BurstFacadeFactory;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.resource.ResourceSurfaceTexture;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.event.EventOnOpenCameraFailed;
import com.android.camera.captureintent.event.EventOnOpenCameraSucceeded;
import com.android.camera.captureintent.event.EventPause;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.one.v2.photo.ImageRotationCalculatorImpl;
import com.android.camera.util.Size;

/**
 * Represents a state that the module is waiting for a camera to be opened.
 */
public final class StateOpeningCamera extends StateImpl {
    private static final Log.Tag TAG = new Log.Tag("StateOpeningCamera");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;
    private final OneCamera.Facing mCameraFacing;
    private final OneCameraCharacteristics mCameraCharacteristics;

    /** The desired picture size. */
    private Size mPictureSize;

    /** Whether is paused in the middle of opening camera. */
    private boolean mIsPaused;

    private OneCamera.OpenCallback mCameraOpenCallback = new OneCamera.OpenCallback() {
        @Override
        public void onFailure() {
            getStateMachine().processEvent(new EventOnOpenCameraFailed());
        }

        @Override
        public void onCameraClosed() {
            // Not used anymore.
        }

        @Override
        public void onCameraOpened(final OneCamera camera) {
            getStateMachine().processEvent(new EventOnOpenCameraSucceeded(camera));
        }
    };

    public static StateOpeningCamera from(
            StateForegroundWithSurfaceTexture foregroundWithSurfaceTexture,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics) {
        return new StateOpeningCamera(foregroundWithSurfaceTexture, resourceConstructed,
                resourceSurfaceTexture, cameraFacing, cameraCharacteristics);
    }

    public static StateOpeningCamera from(
            StateReadyForCapture readyForCapture,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture) {
        OneCamera.Facing cameraFacing =
                resourceConstructed.get().getCameraFacingSetting().getCameraFacing();
        OneCameraCharacteristics characteristics;
        try {
            characteristics =
                    resourceConstructed.get().getCameraManager().getCameraCharacteristics(cameraFacing);
        } catch (OneCameraAccessException ex) {
            characteristics = null;
        }
        return new StateOpeningCamera(readyForCapture, resourceConstructed, resourceSurfaceTexture,
                cameraFacing, characteristics);
    }

    private StateOpeningCamera(State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in onLeave().
        mResourceSurfaceTexture = resourceSurfaceTexture;
        mResourceSurfaceTexture.addRef();  // Will be balanced in onLeave().
        mCameraFacing = cameraFacing;
        mCameraCharacteristics = cameraCharacteristics;
        mIsPaused = false;
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        /** Handles EventPause. */
        EventHandler<EventPause> pauseHandler = new EventHandler<EventPause>() {
            @Override
            public Optional<State> processEvent(EventPause event) {
                mIsPaused = true;
                return NO_CHANGE;
            }
        };
        setEventHandler(EventPause.class, pauseHandler);

        /** Handles EventOnOpenCameraSucceeded. */
        EventHandler<EventOnOpenCameraSucceeded> onOpenCameraSucceededHandler =
                new EventHandler<EventOnOpenCameraSucceeded>() {
                    @Override
                    public Optional<State> processEvent(EventOnOpenCameraSucceeded event) {
                        final OneCamera camera = event.getCamera();
                        if (mIsPaused) {
                            // Just close the camera and finish.
                            camera.close();
                            return Optional.of((State) StateBackgroundWithSurfaceTexture.from(
                                    StateOpeningCamera.this,
                                    mResourceConstructed,
                                    mResourceSurfaceTexture));
                        }
                        return Optional.of((State) StateStartingPreview.from(
                                StateOpeningCamera.this,
                                mResourceConstructed,
                                mResourceSurfaceTexture,
                                camera,
                                mCameraFacing,
                                mCameraCharacteristics,
                                mPictureSize));
                    }
                };
        setEventHandler(EventOnOpenCameraSucceeded.class, onOpenCameraSucceededHandler);

        /** Handles EventOnOpenCameraFailed. */
        EventHandler<EventOnOpenCameraFailed> onOpenCameraFailedHandler =
                new EventHandler<EventOnOpenCameraFailed>() {
                    @Override
                    public Optional<State> processEvent(EventOnOpenCameraFailed event) {
                        Log.e(TAG, "processOnCameraOpenFailure");
                        return Optional.of((State) StateFatal.from(
                                StateOpeningCamera.this, mResourceConstructed));
                    }
                };
        setEventHandler(EventOnOpenCameraFailed.class, onOpenCameraFailedHandler);
    }

    @Override
    public Optional<State> onEnter() {
        if (mCameraCharacteristics == null) {
            Log.e(TAG, "mCameraCharacteristics is null");
            return Optional.of((State) StateFatal.from(this, mResourceConstructed));
        }

        try {
            /** Read the picture size from the setting. */
            mPictureSize = mResourceConstructed.get().getResolutionSetting().getPictureSize(
                    mCameraFacing);
        } catch (OneCameraAccessException ex) {
            Log.e(TAG, "Failed while open camera", ex);
            return Optional.of((State) StateFatal.from(this, mResourceConstructed));
        }

        final ImageRotationCalculator imageRotationCalculator = ImageRotationCalculatorImpl.from(
                mResourceConstructed.get().getOrientationManager(), mCameraCharacteristics);
        mResourceConstructed.get().getCameraManager().open(
                mCameraFacing,
                false,
                mPictureSize,
                mCameraOpenCallback,
                mResourceConstructed.get().getCameraHandler(),
                mResourceConstructed.get().getMainThread(),
                imageRotationCalculator,
                new BurstFacadeFactory.BurstFacadeStub());
        return Optional.absent();
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
    }
}