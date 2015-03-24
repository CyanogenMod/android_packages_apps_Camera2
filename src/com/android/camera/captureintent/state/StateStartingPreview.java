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

import com.android.camera.CaptureModuleUtil;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.event.EventOnStartPreviewFailed;
import com.android.camera.captureintent.event.EventOnStartPreviewSucceeded;
import com.android.camera.captureintent.event.EventOnTextureViewLayoutChanged;
import com.android.camera.captureintent.event.EventPause;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.resource.ResourceOpenedCamera;
import com.android.camera.captureintent.resource.ResourceOpenedCameraImpl;
import com.android.camera.captureintent.resource.ResourceSurfaceTexture;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.debug.Log;
import com.android.camera.device.CameraId;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;
import com.google.common.base.Optional;

import java.util.List;

/**
 * Represents a state that the module is waiting for the preview video stream
 * to be started.
 */
public final class StateStartingPreview extends StateImpl {
    private static final Log.Tag TAG = new Log.Tag("StStartingPreview");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;
    private final RefCountBase<ResourceOpenedCamera> mResourceOpenedCamera;

    public static StateStartingPreview from(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            OneCamera camera,
            CameraId cameraId,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics,
            Size pictureSize,
            OneCameraCaptureSetting captureSetting) {
        return new StateStartingPreview(
                previousState,
                resourceConstructed,
                resourceSurfaceTexture,
                camera,
                cameraId,
                cameraFacing,
                cameraCharacteristics,
                pictureSize,
                captureSetting);
    }

    private StateStartingPreview(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            OneCamera camera,
            CameraId cameraId,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics,
            Size pictureSize,
            OneCameraCaptureSetting captureSetting) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in onLeave().
        mResourceSurfaceTexture = resourceSurfaceTexture;
        mResourceSurfaceTexture.addRef();  // Will be balanced in onLeave().
        mResourceOpenedCamera = ResourceOpenedCameraImpl.create(
                camera, cameraId, cameraFacing, cameraCharacteristics, pictureSize, captureSetting);
        registerEventHandlers();
    }

    public void registerEventHandlers() {
        /** Handles EventPause. */
        EventHandler<EventPause> pauseHandler = new EventHandler<EventPause>() {
            @Override
            public Optional<State> processEvent(EventPause event) {
                return Optional.of((State) StateBackgroundWithSurfaceTexture.from(
                        StateStartingPreview.this,
                        mResourceConstructed,
                        mResourceSurfaceTexture));
            }
        };
        setEventHandler(EventPause.class, pauseHandler);

        /** Handles EventOnTextureViewLayoutChanged. */
        EventHandler<EventOnTextureViewLayoutChanged> onTextureViewLayoutChangedHandler =
                new EventHandler<EventOnTextureViewLayoutChanged>() {
                    @Override
                    public Optional<State> processEvent(EventOnTextureViewLayoutChanged event) {
                        mResourceSurfaceTexture.get().setPreviewLayoutSize(event.getLayoutSize());
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventOnTextureViewLayoutChanged.class, onTextureViewLayoutChangedHandler);

        /** Handles EventOnStartPreviewSucceeded. */
        EventHandler<EventOnStartPreviewSucceeded> onStartPreviewSucceededHandler =
                new EventHandler<EventOnStartPreviewSucceeded>() {
                    @Override
                    public Optional<State> processEvent(EventOnStartPreviewSucceeded event) {
                        mResourceConstructed.get().getMainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                mResourceConstructed.get().getModuleUI().onPreviewStarted();
                            }
                        });
                        return Optional.of((State) StateReadyForCapture.from(
                                StateStartingPreview.this,
                                mResourceConstructed,
                                mResourceSurfaceTexture,
                                mResourceOpenedCamera));
                    }
                };
        setEventHandler(EventOnStartPreviewSucceeded.class, onStartPreviewSucceededHandler);

        /** Handles EventOnStartPreviewFailed. */
        EventHandler<EventOnStartPreviewFailed> onStartPreviewFailedHandler =
                new EventHandler<EventOnStartPreviewFailed>() {
                    @Override
                    public Optional<State> processEvent(EventOnStartPreviewFailed event) {
                        Log.e(TAG, "processOnPreviewSetupFailed");
                        return Optional.of((State) StateFatal.from(
                                StateStartingPreview.this, mResourceConstructed));
                    }
                };
        setEventHandler(EventOnStartPreviewFailed.class, onStartPreviewFailedHandler);
    }

    @Override
    public Optional<State> onEnter() {
        final Size previewSize;
        try {
            // Pick a preview size with the right aspect ratio.
            final List<Size> supportedPreviewSizes = mResourceOpenedCamera.get()
                    .getCameraCharacteristics().getSupportedPreviewSizes();
            if (supportedPreviewSizes.isEmpty()) {
                return Optional.of((State) StateFatal.from(this, mResourceConstructed));
            }

            final Rational pictureAspectRatio =
                    mResourceConstructed.get().getResolutionSetting().getPictureAspectRatio(
                          mResourceOpenedCamera.get().getCameraId(),
                          mResourceOpenedCamera.get().getCameraFacing());
            previewSize = CaptureModuleUtil.getOptimalPreviewSize(
                    supportedPreviewSizes.toArray(new Size[(supportedPreviewSizes.size())]),
                    pictureAspectRatio.toDouble(),
                    null);
            if (previewSize == null) {
                // TODO: Try to avoid entering StateFatal by seeing if there is
                // another way to get the correct preview size.
                return Optional.of((State) StateFatal.from(this, mResourceConstructed));
            }
        } catch (OneCameraAccessException ex) {
            return Optional.of((State) StateFatal.from(this, mResourceConstructed));
        }

        // Must do this before calling ResourceOpenedCamera.startPreview()
        // since SurfaceTexture.setDefaultBufferSize() needs to be called
        // before starting preview. Otherwise the size of preview video stream
        // will be wrong.
        mResourceSurfaceTexture.get().setPreviewSize(previewSize);

        OneCamera.CaptureReadyCallback captureReadyCallback =
                new OneCamera.CaptureReadyCallback() {
                    @Override
                    public void onSetupFailed() {
                        getStateMachine().processEvent(new EventOnStartPreviewFailed());
                    }

                    @Override
                    public void onReadyForCapture() {
                        getStateMachine().processEvent(new EventOnStartPreviewSucceeded());
                    }
                };

        // Start preview right away. Don't dispatch it on other threads or it
        // will cause race condition. b/19522251.
        mResourceOpenedCamera.get().startPreview(
                mResourceSurfaceTexture.get().createPreviewSurface(), captureReadyCallback);
        return Optional.absent();
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
        mResourceOpenedCamera.close();
    }
}
