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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.MediaActionSound;
import android.net.Uri;

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.captureintent.PictureDecoder;
import com.android.camera.captureintent.event.EventCameraBusy;
import com.android.camera.captureintent.event.EventCameraQuickExpose;
import com.android.camera.captureintent.event.EventCameraReady;
import com.android.camera.captureintent.event.EventClickOnCameraKey;
import com.android.camera.captureintent.event.EventFastPictureBitmapAvailable;
import com.android.camera.captureintent.event.EventOnSurfaceTextureUpdated;
import com.android.camera.captureintent.event.EventOnTextureViewLayoutChanged;
import com.android.camera.captureintent.event.EventPause;
import com.android.camera.captureintent.event.EventPictureCompressed;
import com.android.camera.captureintent.event.EventPictureDecoded;
import com.android.camera.captureintent.event.EventTapOnCancelShutterButton;
import com.android.camera.captureintent.event.EventTapOnPreview;
import com.android.camera.captureintent.event.EventTapOnShutterButton;
import com.android.camera.captureintent.event.EventTapOnSwitchCameraButton;
import com.android.camera.captureintent.event.EventTimerCountDownToZero;
import com.android.camera.captureintent.event.EventZoomRatioChanged;
import com.android.camera.captureintent.resource.ResourceCaptureTools;
import com.android.camera.captureintent.resource.ResourceCaptureToolsImpl;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.resource.ResourceOpenedCamera;
import com.android.camera.captureintent.resource.ResourceSurfaceTexture;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.debug.Log;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.util.Size;

import com.google.common.base.Optional;

import javax.annotation.Nullable;

/**
 * Represents a state that allows users to take a picture. The capture UI
 * should be presented in this state so users can perform actions:
 * 1. tap on shutter button to take a picture.
 * 2. tap on viewfinder to focus.
 * 3. switch between front and back camera.
 */
public final class StateReadyForCapture extends StateImpl {
    private static final Log.Tag TAG = new Log.Tag("StateReadyCap");

    private final RefCountBase<ResourceCaptureTools> mResourceCaptureTools;

    private boolean mShouldUpdateTransformOnNextSurfaceTextureUpdate;
    private boolean mIsCountingDown;
    private boolean mIsTakingPicture;
    private boolean mIsDecodingPicture;

    public static StateReadyForCapture from(
            StateStartingPreview startingPreview,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera) {
        return new StateReadyForCapture(
                startingPreview, resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera);
    }

    public static StateReadyForCapture from(
            StateReviewingPicture reviewingPicture,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools) {
        return new StateReadyForCapture(reviewingPicture, resourceCaptureTools);
    }

    private StateReadyForCapture(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera) {
        super(previousState);
        mResourceCaptureTools = ResourceCaptureToolsImpl.create(
                resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera);
        mIsCountingDown = false;
        mIsTakingPicture = false;
        mIsDecodingPicture = false;
        mShouldUpdateTransformOnNextSurfaceTextureUpdate = true;
        registerEventHandlers();
    }

    private StateReadyForCapture(
            State previousState,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools) {
        super(previousState);
        mResourceCaptureTools = resourceCaptureTools;
        mResourceCaptureTools.addRef();  // Will be balanced in onLeave().
        mIsCountingDown = false;
        mIsTakingPicture = false;
        mIsDecodingPicture = false;
        mShouldUpdateTransformOnNextSurfaceTextureUpdate = true;
        registerEventHandlers();
    }

    private void takePicture(@Nullable final TouchCoordinate touchPointInsideShutterButton) {
        final int countDownDuration =
                mResourceCaptureTools.get().getResourceConstructed().get()
                        .getSettingsManager().getInteger(
                        SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);

        /** Prepare a CaptureLoggingInfo object. */
        final ResourceCaptureTools.CaptureLoggingInfo captureLoggingInfo
                = new ResourceCaptureTools.CaptureLoggingInfo() {
            @Override
            public TouchCoordinate getTouchPointInsideShutterButton() {
                return touchPointInsideShutterButton;
            }

            @Override
            public int getCountDownDuration() {
                return countDownDuration;
            }
        };

        /** Start counting down if the duration is not zero. */
        if (countDownDuration > 0) {
            startCountDown(countDownDuration, captureLoggingInfo);
        } else {
            /** Otherwise, just take a picture immediately. */
            takePictureNow(captureLoggingInfo);
        }
    }

    private void startCountDown(
            final int countDownDuration,
            final ResourceCaptureTools.CaptureLoggingInfo captureLoggingInfo) {
        mIsCountingDown = true;
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                CaptureIntentModuleUI moduleUI = mResourceCaptureTools.get().getModuleUI();
                moduleUI.setCountdownFinishedListener(
                        new CountDownView.OnCountDownStatusListener() {
                            @Override
                            public void onRemainingSecondsChanged(
                                    int remainingSeconds) {
                                mResourceCaptureTools.get()
                                        .playCountDownSound(remainingSeconds);
                            }

                            @Override
                            public void onCountDownFinished() {
                                getStateMachine().processEvent(
                                        new EventTimerCountDownToZero(
                                                captureLoggingInfo));
                            }
                        });
                moduleUI.startCountdown(countDownDuration);
            }
        });
    }

    private void cancelCountDown() {
        // Cancel in this state means that the countdown was cancelled.
        mIsCountingDown = false;
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().cancelCountDown();
                mResourceCaptureTools.get().getModuleUI().showPictureCaptureUI();
            }
        });
    }

    private void takePictureNow(ResourceCaptureTools.CaptureLoggingInfo captureLoggingInfo) {
        mIsTakingPicture = true;
        mResourceCaptureTools.get().takePictureNow(mPictureCallback, captureLoggingInfo);
    }

    private void registerEventHandlers() {
        /** Handles EventPause. */
        EventHandler<EventPause> pauseHandler = new EventHandler<EventPause>() {
            @Override
            public Optional<State> processEvent(EventPause event) {
                return Optional.of((State) StateBackgroundWithSurfaceTexture.from(
                        StateReadyForCapture.this,
                        mResourceCaptureTools.get().getResourceConstructed(),
                        mResourceCaptureTools.get().getResourceSurfaceTexture()));
            }
        };
        setEventHandler(EventPause.class, pauseHandler);

        /** Handles EventOnSurfaceTextureUpdated. */
        EventHandler<EventOnSurfaceTextureUpdated> onSurfaceTextureUpdatedHandler =
                new EventHandler<EventOnSurfaceTextureUpdated>() {
                    @Override
                    public Optional<State> processEvent(EventOnSurfaceTextureUpdated event) {
                        if (mShouldUpdateTransformOnNextSurfaceTextureUpdate) {
                            mShouldUpdateTransformOnNextSurfaceTextureUpdate = false;

                            // We have to provide a preview layout size to
                            // ResourceSurfaceTexture. Otherwise, it will
                            // not be able to calculate transform matrix.
                            Size previewSurfaceSize = mResourceCaptureTools.get().getModuleUI()
                                    .getPreviewSurfaceSize();
                            mResourceCaptureTools.get().getResourceSurfaceTexture().get()
                                    .setPreviewLayoutSize(previewSurfaceSize);

                            removeEventHandler(EventOnSurfaceTextureUpdated.class);
                        }
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventOnSurfaceTextureUpdated.class, onSurfaceTextureUpdatedHandler);

        /** Handles EventOnTextureViewLayoutChanged. */
        EventHandler<EventOnTextureViewLayoutChanged> onTextureViewLayoutChangedHandler =
                new EventHandler<EventOnTextureViewLayoutChanged>() {
                    @Override
                    public Optional<State> processEvent(EventOnTextureViewLayoutChanged event) {
                        mResourceCaptureTools.get().getResourceSurfaceTexture().get()
                                .setPreviewLayoutSize(event.getLayoutSize());
                        return NO_CHANGE;
                    }
                };
        setEventHandler(
                EventOnTextureViewLayoutChanged.class, onTextureViewLayoutChangedHandler);

        /** Handles EventCameraBusy. */
        setEventHandler(
                EventCameraBusy.class, mEventCameraBusyHandler);

        /** Handles EventCameraReady. */
        setEventHandler(
                EventCameraReady.class, mEventCameraReadyHandler);

        /** Handles EventTapOnShutterButton. */
        EventHandler<EventTapOnShutterButton> tapOnShutterButtonHandler =
                new EventHandler<EventTapOnShutterButton>() {
                    @Override
                    public Optional<State> processEvent(final EventTapOnShutterButton event) {
                        takePicture(event.getTouchCoordinate());
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventTapOnShutterButton.class, tapOnShutterButtonHandler);

        /** Handles EventClickOnCameraKey */
        EventHandler<EventClickOnCameraKey> clickOnVolumeKeyHandler =
                new EventHandler<EventClickOnCameraKey>() {
                    @Override
                    public Optional<State> processEvent(EventClickOnCameraKey event) {
                        if (mIsCountingDown) {
                            cancelCountDown();
                            return NO_CHANGE;
                        }
                        takePicture(null);
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventClickOnCameraKey.class, clickOnVolumeKeyHandler);

        /** Handles EventTimerCountDownToZero. */
        EventHandler<EventTimerCountDownToZero> timerCountDownToZeroHandler =
                new EventHandler<EventTimerCountDownToZero>() {
                    @Override
                    public Optional<State> processEvent(EventTimerCountDownToZero event) {
                        if (mIsCountingDown) {
                            mIsCountingDown = false;
                            takePictureNow(event.getCaptureLoggingInfo());
                        }
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventTimerCountDownToZero.class, timerCountDownToZeroHandler);

        /** Handles EventTapOnSwitchCameraButton. */
        EventHandler<EventTapOnSwitchCameraButton> tapOnSwitchCameraButtonHandler =
                new EventHandler<EventTapOnSwitchCameraButton>() {
                    @Override
                    public Optional<State> processEvent(EventTapOnSwitchCameraButton event) {
                        final ResourceConstructed resourceConstructed =
                                mResourceCaptureTools.get().getResourceConstructed().get();

                        // Freeze the screen.
                        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                            @Override
                            public void run() {
                                resourceConstructed.getModuleUI().freezeScreenUntilPreviewReady();
                            }
                        });

                        OneCamera.Facing cameraFacing =
                                resourceConstructed.getCameraFacingSetting().getCameraFacing();
                        CameraId cameraId =  resourceConstructed.getOneCameraManager()
                              .findFirstCameraFacing(cameraFacing);
                        OneCameraCharacteristics characteristics;
                        try {
                            characteristics = resourceConstructed.getOneCameraManager()
                                    .getOneCameraCharacteristics(cameraId);
                        } catch (OneCameraAccessException ex) {
                            return Optional.of((State) StateFatal.from(
                                    StateReadyForCapture.this,
                                    mResourceCaptureTools.get().getResourceConstructed()));
                        }

                        return Optional.of((State) StateOpeningCamera.from(
                                StateReadyForCapture.this,
                                mResourceCaptureTools.get().getResourceConstructed(),
                                mResourceCaptureTools.get().getResourceSurfaceTexture(),
                                cameraFacing,
                                cameraId,
                                characteristics));
                    }
                };
        setEventHandler(EventTapOnSwitchCameraButton.class, tapOnSwitchCameraButtonHandler);

        /** Handles EventTapOnPreview. */
        EventHandler<EventTapOnPreview> tapOnPreviewHandler = new EventHandler<EventTapOnPreview>() {
            @Override
            public Optional<State> processEvent(EventTapOnPreview event) {
                OneCameraCharacteristics cameraCharacteristics = mResourceCaptureTools.get()
                      .getResourceOpenedCamera().get().getCameraCharacteristics();
                if (cameraCharacteristics.isAutoExposureSupported() ||
                      cameraCharacteristics.isAutoFocusSupported()) {
                    final Point tapPoint = event.getTapPoint();
                    mResourceCaptureTools.get().getFocusController().showActiveFocusAt(
                          tapPoint.x, tapPoint.y);

                    RectF previewRect = mResourceCaptureTools.get().getModuleUI().getPreviewRect();
                    int rotationDegree = mResourceCaptureTools.get().getResourceConstructed().get()
                          .getOrientationManager().getDisplayRotation().getDegrees();

                    // Normalize coordinates to [0,1] per CameraOne API.
                    float points[] = new float[2];
                    points[0] = (tapPoint.x - previewRect.left) / previewRect.width();
                    points[1] = (tapPoint.y - previewRect.top) / previewRect.height();

                    // Rotate coordinates to portrait orientation per CameraOne API.
                    Matrix rotationMatrix = new Matrix();
                    rotationMatrix.setRotate(rotationDegree, 0.5f, 0.5f);
                    rotationMatrix.mapPoints(points);

                    // Invert X coordinate on front camera since the display is mirrored.
                    if (cameraCharacteristics.getCameraDirection() == Facing.FRONT) {
                        points[0] = 1 - points[0];
                    }

                    mResourceCaptureTools.get().getResourceOpenedCamera().get()
                          .triggerFocusAndMeterAtPoint(
                                new PointF(points[0], points[1]));
                }

                return NO_CHANGE;
            }
        };
        setEventHandler(EventTapOnPreview.class, tapOnPreviewHandler);

        /** Handles EventZoomRatioChanged. */
        EventHandler<EventZoomRatioChanged> zoomRatioChangedHandler =
                new EventHandler<EventZoomRatioChanged>() {
                    @Override
                    public Optional<State> processEvent(EventZoomRatioChanged event) {
                        mResourceCaptureTools.get().getResourceOpenedCamera().get().setZoomRatio(
                                event.getZoomRatio());
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventZoomRatioChanged.class, zoomRatioChangedHandler);

        /** Handles EventPictureCompressed. */
        EventHandler<EventPictureCompressed> pictureCompressedHandler =
                new EventHandler<EventPictureCompressed>() {
                    @Override
                    public Optional<State> processEvent(EventPictureCompressed event) {
                        if (mIsTakingPicture) {
                            mIsTakingPicture = false;
                            mIsDecodingPicture = true;

                            final byte[] pictureData = event.getPictureData();
                            final int pictureOrientation = event.getOrientation();
                            mResourceCaptureTools.get().getResourceConstructed().get().getCameraHandler().post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            final Bitmap pictureBitmap = PictureDecoder.decode(
                                                    pictureData,
                                                    CaptureIntentConfig.DOWN_SAMPLE_FACTOR,
                                                    pictureOrientation,
                                                    false);
                                            getStateMachine().processEvent(
                                                    new EventPictureDecoded(pictureBitmap, pictureData));
                                        }
                                    });
                        }
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventPictureCompressed.class, pictureCompressedHandler);

        /** Handles EventPictureDecoded. */
        EventHandler<EventPictureDecoded> pictureDecodedHandler =
                new EventHandler<EventPictureDecoded>() {
                    @Override
                    public Optional<State> processEvent(EventPictureDecoded event) {
                        // Do nothing if we are not in the decoding image sub-state. There is a
                        // chance that EventPictureDecoded for an old image might come after people
                        // hitting retake button. We have to ignore it or it will take us to
                        // StateReviewingPicture.
                        if (!mIsDecodingPicture) {
                            return NO_CHANGE;
                        }

                        mIsDecodingPicture = false;
                        return Optional.of((State) StateReviewingPicture.from(
                                StateReadyForCapture.this, mResourceCaptureTools,
                                event.getPictureBitmap(), Optional.of(event.getPictureData())));
                    }
                };
        setEventHandler(EventPictureDecoded.class, pictureDecodedHandler);

        /** Handles EventFastPictureBitmapAvailable. */
        EventHandler<EventFastPictureBitmapAvailable> fastPictureBitmapAvailableHandler =
                new EventHandler<EventFastPictureBitmapAvailable>() {
                    @Override
                    public Optional<State> processEvent(EventFastPictureBitmapAvailable event) {
                        if (mIsTakingPicture && !mIsDecodingPicture) {
                            return Optional.of((State) StateReviewingPicture.from(
                                    StateReadyForCapture.this, mResourceCaptureTools,
                                    event.getThumbnailBitmap(), Optional.<byte[]>absent()));
                        }
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventFastPictureBitmapAvailable.class, fastPictureBitmapAvailableHandler);

        /** Handles EventCameraQuickExpose. */
        EventHandler<EventCameraQuickExpose> cameraQuickExposeHandler =
                new EventHandler<EventCameraQuickExpose>() {
                    @Override
                    public Optional<State> processEvent(EventCameraQuickExpose event) {
                        if (mIsTakingPicture) {
                            mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                                @Override
                                public void run() {

                                    ResourceConstructed resourceConstructed =
                                            mResourceCaptureTools.get().getResourceConstructed()
                                                    .get();
                                    // Freeze the screen.
                                    resourceConstructed.getModuleUI()
                                            .freezeScreenUntilPreviewReady();
                                    // Disable shutter button.
                                    mResourceCaptureTools.get().getModuleUI()
                                            .setShutterButtonEnabled(false);
                                    // Starts the short version of the capture animation UI.
                                    mResourceCaptureTools.get().getModuleUI()
                                            .startFlashAnimation(true);
                                    mResourceCaptureTools.get().getMediaActionSound().play(
                                            MediaActionSound.SHUTTER_CLICK);
                                }
                            });
                        }
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventCameraQuickExpose.class, cameraQuickExposeHandler);

        /** Handles EventTapOnCancelShutterButton. */
        EventHandler<EventTapOnCancelShutterButton> tapOnCancelShutterButtonHandler =
                new EventHandler<EventTapOnCancelShutterButton>() {
                    @Override
                    public Optional<State> processEvent(EventTapOnCancelShutterButton event) {
                        cancelCountDown();
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventTapOnCancelShutterButton.class, tapOnCancelShutterButtonHandler);
    }

    @Override
    public Optional<State> onEnter() {
        // Register various listeners. These will be unregistered in onLeave().
        final OneCamera camera =
                mResourceCaptureTools.get().getResourceOpenedCamera().get().getCamera();
        camera.setFocusDistanceListener(mResourceCaptureTools.get().getFocusController());
        camera.setFocusStateListener(mFocusStateListener);
        camera.setReadyStateChangedListener(mReadyStateChangedListener);
        mResourceCaptureTools.get().getCaptureSessionManager()
                .addSessionListener(mCaptureSessionListener);

        // Display capture UI.
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().cancelCountDown();
                mResourceCaptureTools.get().getModuleUI().showPictureCaptureUI();
                mResourceCaptureTools.get().getModuleUI().initializeZoom(
                        mResourceCaptureTools.get().getResourceOpenedCamera().get().getZoomRatio());
            }
        });
        return NO_CHANGE;
    }

    @Override
    public void onLeave() {
        final OneCamera camera =
                mResourceCaptureTools.get().getResourceOpenedCamera().get().getCamera();
        camera.setFocusDistanceListener(null);
        camera.setFocusStateListener(null);
        camera.setReadyStateChangedListener(null);

        mResourceCaptureTools.get().getCaptureSessionManager()
                .removeSessionListener(mCaptureSessionListener);
        mResourceCaptureTools.close();
    }

    private void onFocusStateUpdated(OneCamera.AutoFocusState focusState) {
        final FocusController focusController = mResourceCaptureTools.get().getFocusController();
        switch (focusState) {
            case PASSIVE_SCAN:
                focusController.showPassiveFocusAtCenter();
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
    }

    private final OneCamera.FocusStateListener mFocusStateListener =
            new OneCamera.FocusStateListener() {
                @Override
                public void onFocusStatusUpdate(final OneCamera.AutoFocusState focusState,
                        final long frameNumber) {
                    onFocusStateUpdated(focusState);
                }
            };

    private final EventHandler<EventCameraBusy> mEventCameraBusyHandler =
            new EventHandler<EventCameraBusy>() {
                @Override
                public Optional<State> processEvent(EventCameraBusy event) {
                    mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            mResourceCaptureTools.get().getModuleUI().setShutterButtonEnabled(
                                    false);
                        }
                    });
                    return NO_CHANGE;
                }
            };

    private final EventHandler<EventCameraReady> mEventCameraReadyHandler =
            new EventHandler<EventCameraReady>() {
                @Override
                public Optional<State> processEvent(EventCameraReady event) {
                    mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            mResourceCaptureTools.get().getModuleUI().setShutterButtonEnabled(true);
                        }
                    });
                    return NO_CHANGE;
                }
            };

    private final OneCamera.ReadyStateChangedListener mReadyStateChangedListener =
            new OneCamera.ReadyStateChangedListener() {
                /**
                 * Called when the camera is either ready or not ready to take a picture
                 * right now.
                 */
                @Override
                public void onReadyStateChanged(final boolean readyForCapture) {
                    if (readyForCapture) {
                        getStateMachine().processEvent(new EventCameraReady());
                    } else {
                        getStateMachine().processEvent(new EventCameraBusy());
                    }
                }
            };

    private final OneCamera.PictureCallback mPictureCallback = new OneCamera.PictureCallback() {
        @Override
        public void onQuickExpose() {
            getStateMachine().processEvent(new EventCameraQuickExpose());
        }

        @Override
        public void onThumbnailResult(byte[] jpegData) {
        }

        @Override
        public void onPictureTaken(CaptureSession session) {
        }

        @Override
        public void onPictureSaved(Uri uri) {
        }

        @Override
        public void onPictureTakingFailed() {
        }

        @Override
        public void onTakePictureProgress(float progress) {
        }
    };

    private final CaptureSessionManager.SessionListener mCaptureSessionListener =
            new CaptureSessionManager.SessionListener() {
                @Override
                public void onSessionThumbnailUpdate(Bitmap thumbnailBitmap) {
                    getStateMachine().processEvent(
                            new EventFastPictureBitmapAvailable(thumbnailBitmap));
                }

                @Override
                public void onSessionPictureDataUpdate(byte[] pictureData, int orientation) {
                    getStateMachine().processEvent(
                            new EventPictureCompressed(pictureData, orientation));
                }

                @Override
                public void onSessionQueued(Uri sessionUri) {
                }

                @Override
                public void onSessionUpdated(Uri sessionUri) {
                }

                @Override
                public void onSessionCaptureIndicatorUpdate(Bitmap bitmap, int rotationDegrees) {
                }

                @Override
                public void onSessionDone(Uri sessionUri) {
                }

                @Override
                public void onSessionFailed(Uri sessionUri, int failureMessageId,
                        boolean removeFromFilmstrip) {
                }

                @Override
                public void onSessionCanceled(Uri mediaUri) {
                }

                @Override
                public void onSessionProgress(Uri sessionUri, int progress) {
                }

                @Override
                public void onSessionProgressText(Uri sessionUri, int messageId) {
                }
            };
}