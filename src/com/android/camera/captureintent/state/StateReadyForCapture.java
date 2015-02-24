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

import com.android.camera.SoundPlayer;
import com.android.camera.app.LocationManager;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.PictureDecoder;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.ui.focus.FocusSound;
import com.android.camera.util.Size;
import com.android.camera2.R;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.MediaActionSound;

/**
 * Represents a state that allows users to take a picture. The capture UI
 * should be presented in this state so users can perform actions:
 * 1. tap on shutter button to take a picture.
 * 2. tap on viewfinder to focus.
 * 3. switch between front and back camera.
 */
public final class StateReadyForCapture extends State {
    private final RefCountBase<ResourceCaptureTools> mResourceCaptureTools;

    private boolean mIsCountingDown;
    private boolean mIsTakingPicture;
    private boolean mIsDecodingPicture;

    public static StateReadyForCapture from(
            StateStartingPreview startingPreview,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera,
            CaptureSessionManager captureSessionManager,
            LocationManager locationManager,
            HeadingSensor headingSensor,
            SoundPlayer soundPlayer,
            OneCamera.PictureCallback pictureCallback,
            OneCamera.PictureSaverCallback pictureSaverCallback) {
        FocusSound focusSound = new FocusSound(soundPlayer, R.raw.material_camera_focus);
        FocusController focusController = new FocusController(
                resourceConstructed.get().getModuleUI().getFocusRing(),
                focusSound,
                resourceConstructed.get().getMainThread());
        MediaActionSound mediaActionSound = new MediaActionSound();
        return new StateReadyForCapture(
                startingPreview, resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera,
                captureSessionManager, focusController, locationManager, headingSensor, soundPlayer,
                mediaActionSound, pictureCallback, pictureSaverCallback);
    }

    public static StateReadyForCapture from(
            StateReviewingPicture reviewingPicture,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools) {
        return new StateReadyForCapture(reviewingPicture, resourceCaptureTools);
    }

    private StateReadyForCapture(
            State startingPreview,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera,
            CaptureSessionManager captureSessionManager,
            FocusController focusController,
            LocationManager locationManager,
            HeadingSensor headingSensor,
            SoundPlayer soundPlayer,
            MediaActionSound mediaActionSound,
            OneCamera.PictureCallback pictureCallback,
            OneCamera.PictureSaverCallback pictureSaverCallback) {
        super(ID.ReadyForCapture, startingPreview);
        mResourceCaptureTools = ResourceCaptureTools.create(
                resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera,
                captureSessionManager, focusController, locationManager, headingSensor,
                soundPlayer, mediaActionSound, pictureCallback, pictureSaverCallback);
        mIsCountingDown = false;
        mIsTakingPicture = false;
        mIsDecodingPicture = false;
    }

    private StateReadyForCapture(
            State startingPreview,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools) {
        super(ID.ReadyForCapture, startingPreview);
        mResourceCaptureTools = resourceCaptureTools;
        mResourceCaptureTools.addRef();  // Will be balanced in onLeave().
        mIsCountingDown = false;
        mIsTakingPicture = false;
        mIsDecodingPicture = false;
    }

    @Override
    public Optional<State> onEnter() {
        mResourceCaptureTools.get().getCamera().setFocusDistanceListener(
                mResourceCaptureTools.get().getFocusController());
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().showPictureCaptureUI();
            }
        });
        return NO_CHANGE;
    }

    @Override
    public void onLeave() {
        mResourceCaptureTools.close();
    }

    @Override
    public Optional<State> processPause() {
        return Optional.of((State) StateBackground.from(
                this, mResourceCaptureTools.get().getResourceConstructed()));
    }

    @Override
    public final Optional<State> processOnTextureViewLayoutChanged(Size layoutSize) {
        mResourceCaptureTools.get().getResourceSurfaceTexture().get()
                .setPreviewLayoutSize(layoutSize);
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnReadyStateChanged(final boolean readyForCapture) {
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().setShutterButtonEnabled(readyForCapture);
            }
        });
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnShutterButtonClicked() {
        final int countDownDuration =
                mResourceCaptureTools.get().getResourceConstructed().get().getAppController()
                        .getSettingsManager().getInteger(
                        SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
        if (countDownDuration > 0) {
            mIsCountingDown = true;
            mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                @Override
                public void run() {
                    mResourceCaptureTools.get().getModuleUI().startCountdown(countDownDuration);
                }
            });
            return NO_CHANGE;
        }
        mIsTakingPicture = true;
        mResourceCaptureTools.get().takePictureNow();
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnCountDownFinished() {
        if (mIsCountingDown) {
            mIsCountingDown = false;
            mIsTakingPicture = true;
            mResourceCaptureTools.get().takePictureNow();
        }
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnSwitchButtonClicked() {
        // Freeze the screen.
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().freezeScreenUntilPreviewReady();
            }
        });

        return Optional.of((State) StateOpeningCamera.from(
                this,
                mResourceCaptureTools.get().getResourceConstructed(),
                mResourceCaptureTools.get().getResourceSurfaceTexture()));
    }

    @Override
    public Optional<State> processOnSingleTapOnPreview(Point point) {
        mResourceCaptureTools.get().getFocusController().showActiveFocusAt(point.x, point.y);
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnFocusStateUpdated(
            OneCamera.AutoFocusState focusState, long frameNumber) {
        final FocusController focusController = mResourceCaptureTools.get().getFocusController();
        switch (focusState) {
            case PASSIVE_SCAN:
                final Size previewLayoutSize = mResourceCaptureTools.get()
                        .getResourceSurfaceTexture().get().getPreviewLayoutSize();
                focusController.showPassiveFocusAt(
                        (int) (previewLayoutSize.width() / 2.0f),
                        (int) (previewLayoutSize.height() / 2.0f));
                break;
            case ACTIVE_SCAN:
                break;
            case PASSIVE_FOCUSED:
            case PASSIVE_UNFOCUSED:
                focusController.clearFocusIndicator();
                break;
            case ACTIVE_FOCUSED:
            case ACTIVE_UNFOCUSED:
                focusController.clearFocusIndicator();
                break;
        }
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnZoomRatioChanged(float zoomRatio) {
        mResourceCaptureTools.get().getResourceOpenedCamera().get().setZoomRatio(zoomRatio);
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnPictureCompressed(
            final byte[] pictureData, final int pictureOrientation) {
        if (mIsTakingPicture) {
            mIsTakingPicture = false;
            mIsDecodingPicture = true;
            mResourceCaptureTools.get().getResourceConstructed().get().getCameraHandler().post(
                    new Runnable() {
                @Override
                public void run() {
                    final Bitmap pictureBitmap = PictureDecoder.decode(
                            pictureData,
                            CaptureIntentConfig.DOWN_SAMPLE_FACTOR,
                            pictureOrientation,
                            false);
                    getStateMachine().processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnPictureDecoded(pictureBitmap, pictureData);
                        }
                    });
                }
            });
        }
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnPictureDecoded(Bitmap pictureBitmap, byte[] pictureData) {
        return Optional.of((State) StateReviewingPicture.from(
                this, mResourceCaptureTools, pictureBitmap, Optional.of(pictureData)));
    }

    @Override
    public Optional<State> processOnPictureBitmapAvailable(Bitmap thumbnailBitmap) {
        if (mIsTakingPicture && !mIsDecodingPicture) {
            return Optional.of((State) StateReviewingPicture.from(
                    this, mResourceCaptureTools, thumbnailBitmap, Optional.<byte[]>absent()));
        }
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnQuickExpose() {
        if (mIsTakingPicture) {
            mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                @Override
                public void run() {
                    // Starts the short version of the capture animation UI.
                    mResourceCaptureTools.get().getModuleUI().startFlashAnimation(true);
                    mResourceCaptureTools.get().getMediaActionSound().play(
                            MediaActionSound.SHUTTER_CLICK);
                }
            });
        }
        return NO_CHANGE;
    }
}