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
import com.android.camera.captureintent.PictureDecoder;
import com.android.camera.captureintent.event.Event;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.util.Size;
import com.google.common.base.Optional;

/**
 * Represents a state that allows users to take a picture. The capture UI
 * should be presented in this state so users can perform actions:
 * 1. tap on shutter button to take a picture.
 * 2. tap on viewfinder to focus.
 * 3. switch between front and back camera.
 */
public final class StateReadyForCapture extends State {
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
            State startingPreview,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera) {
        super(ID.ReadyForCapture, startingPreview);
        mResourceCaptureTools = ResourceCaptureTools.create(
                resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera);
        mIsCountingDown = false;
        mIsTakingPicture = false;
        mIsDecodingPicture = false;
        mShouldUpdateTransformOnNextSurfaceTextureUpdate = true;
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
        mShouldUpdateTransformOnNextSurfaceTextureUpdate = true;
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

    @Override
    public Optional<State> processPause() {
        return Optional.of((State) StateBackgroundWithSurfaceTexture.from(
                this,
                mResourceCaptureTools.get().getResourceConstructed(),
                mResourceCaptureTools.get().getResourceSurfaceTexture()));
    }

    @Override
    public Optional<State> processOnSurfaceTextureUpdated() {
        if (mShouldUpdateTransformOnNextSurfaceTextureUpdate) {
            mShouldUpdateTransformOnNextSurfaceTextureUpdate = false;
            mResourceCaptureTools.get().getResourceSurfaceTexture().get().updatePreviewTransform();
        }
        return NO_CHANGE;
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
            mResourceCaptureTools.get().getModuleUI()
                    .setCountdownFinishedListener(mOnCountDownStatusListener);
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
        mResourceCaptureTools.get().takePictureNow(mPictureCallback);
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnCountDownFinished() {
        if (mIsCountingDown) {
            mIsCountingDown = false;
            mIsTakingPicture = true;
            mResourceCaptureTools.get().takePictureNow(mPictureCallback);
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

        RectF previewRect = mResourceCaptureTools.get().getModuleUI().getPreviewRect();
        int rotationDegree = mResourceCaptureTools.get().getResourceConstructed().get()
                .getOrientationManager().getDisplayRotation().getDegrees();

        // Normalize coordinates to [0,1] per CameraOne API.
        float points[] = new float[2];
        points[0] = (point.x - previewRect.left) / previewRect.width();
        points[1] = (point.y - previewRect.top) / previewRect.height();

        // Rotate coordinates to portrait orientation per CameraOne API.
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(rotationDegree, 0.5f, 0.5f);
        rotationMatrix.mapPoints(points);
        mResourceCaptureTools.get().getResourceOpenedCamera().get().triggerFocusAndMeterAtPoint(
                new PointF(points[0], points[1]));
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
        Log.d(TAG, "processOnPictureDecoded");
        return Optional.of((State) StateReviewingPicture.from(
                this, mResourceCaptureTools, pictureBitmap, Optional.of(pictureData)));
    }

    @Override
    public Optional<State> processOnPictureBitmapAvailable(Bitmap thumbnailBitmap) {
        Log.d(TAG, "processOnPictureBitmapAvailable");
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

    public Optional<State> processOnCancelShutterButtonClicked() {
        // Cancel in this state means that the countdown was cancelled.
        mIsCountingDown = false;
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().cancelCountDown();
                mResourceCaptureTools.get().getModuleUI().showPictureCaptureUI();
            }
        });
        return NO_CHANGE;
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

    private final OneCamera.ReadyStateChangedListener mReadyStateChangedListener =
            new OneCamera.ReadyStateChangedListener() {
                /**
                 * Called when the camera is either ready or not ready to take a picture
                 * right now.
                 */
                @Override
                public void onReadyStateChanged(final boolean readyForCapture) {
                    getStateMachine().processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnReadyStateChanged(readyForCapture);
                        }
                    });
                }
            };

    private final CountDownView.OnCountDownStatusListener mOnCountDownStatusListener =
            new CountDownView.OnCountDownStatusListener() {
                @Override
                public void onRemainingSecondsChanged(int remainingSeconds) {
                    mResourceCaptureTools.get().playCountDownSound(remainingSeconds);
                }

                @Override
                public void onCountDownFinished() {
                    getStateMachine().processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnCountDownFinished();
                        }
                    });
                }
            };

    private final OneCamera.PictureCallback mPictureCallback = new OneCamera.PictureCallback() {
        @Override
        public void onQuickExpose() {
            getStateMachine().processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnReadyStateChanged(false);
                }
            });
            getStateMachine().processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnQuickExpose();
                }
            });
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
                public void onSessionThumbnailUpdate(final Bitmap thumbnailBitmap) {
                    getStateMachine().processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnPictureBitmapAvailable(thumbnailBitmap);
                        }
                    });
                }

                @Override
                public void onSessionPictureDataUpdate(
                        final byte[] pictureData, final int orientation) {
                    getStateMachine().processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnPictureCompressed(pictureData, orientation);
                        }
                    });
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
                public void onSessionFailed(Uri sessionUri, CharSequence reason) {
                }

                @Override
                public void onSessionProgress(Uri sessionUri, int progress) {
                }

                @Override
                public void onSessionProgressText(Uri sessionUri, CharSequence message) {
                }
            };
}