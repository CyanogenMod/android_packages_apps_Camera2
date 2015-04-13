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
import android.net.Uri;

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.PictureDecoder;
import com.android.camera.captureintent.event.EventOnTextureViewLayoutChanged;
import com.android.camera.captureintent.event.EventPause;
import com.android.camera.captureintent.event.EventPictureCompressed;
import com.android.camera.captureintent.event.EventPictureDecoded;
import com.android.camera.captureintent.event.EventTapOnCancelIntentButton;
import com.android.camera.captureintent.event.EventTapOnConfirmPhotoButton;
import com.android.camera.captureintent.event.EventTapOnRetakePhotoButton;
import com.android.camera.captureintent.resource.ResourceCaptureTools;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.debug.Log;
import com.android.camera.session.CaptureSessionManager;
import com.google.common.base.Optional;

/**
 * A state that shows the taken picture for review. The Cancel, Done or
 * Take buttons are presented. The state handles 3 events:
 * - OnCancelButtonClicked
 * - OnRetakeButtonClicked
 * - OnDoneButtonClicked
 */
public class StateReviewingPicture extends StateImpl {
    private static final Log.Tag TAG = new Log.Tag("StateReviewPic");

    private final RefCountBase<ResourceCaptureTools> mResourceCaptureTools;

    /** The picture bitmap to be shown. */
    private Bitmap mPictureBitmap;

    /** The compressed picture byte array. */
    private Optional<byte[]> mPictureData;

    private boolean mIsReviewingThumbnail;
    private boolean mShouldFinishWhenReceivePictureData;

    public static StateReviewingPicture from(
            StateReadyForCapture readyForCapture,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools,
            Bitmap pictureBitmap,
            Optional<byte[]> pictureData) {
        return new StateReviewingPicture(
                readyForCapture, resourceCaptureTools, pictureBitmap, pictureData);
    }

    private StateReviewingPicture(
            State previousState,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools,
            Bitmap pictureBitmap,
            Optional<byte[]> pictureData) {
        super(previousState);
        mResourceCaptureTools = resourceCaptureTools;
        mResourceCaptureTools.addRef();  // Will be balanced in onLeave().
        mPictureBitmap = pictureBitmap;
        mPictureData = pictureData;
        mIsReviewingThumbnail = !pictureData.isPresent();
        mShouldFinishWhenReceivePictureData = false;
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        /** Handles EventPause. */
        EventHandler<EventPause> pauseHandler = new EventHandler<EventPause>() {
            @Override
            public Optional<State> processEvent(EventPause event) {
                return Optional.of((State) StateBackgroundWithSurfaceTexture.from(
                        StateReviewingPicture.this,
                        mResourceCaptureTools.get().getResourceConstructed(),
                        mResourceCaptureTools.get().getResourceSurfaceTexture()));
            }
        };
        setEventHandler(EventPause.class, pauseHandler);

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
        setEventHandler(EventOnTextureViewLayoutChanged.class, onTextureViewLayoutChangedHandler);

        /** Handles EventTapOnCancelIntentButton. */
        EventHandler<EventTapOnCancelIntentButton> tapOnCancelIntentButtonHandler =
                new EventHandler<EventTapOnCancelIntentButton>() {
                    @Override
                    public Optional<State> processEvent(EventTapOnCancelIntentButton event) {
                        return Optional.of((State) StateIntentCompleted.from(
                                StateReviewingPicture.this,
                                mResourceCaptureTools.get().getResourceConstructed()));
                    }
                };
        setEventHandler(EventTapOnCancelIntentButton.class, tapOnCancelIntentButtonHandler);

        /** Handles EventTapOnConfirmPhotoButton. */
        EventHandler<EventTapOnConfirmPhotoButton> tapOnConfirmPhotoButtonHandler =
                new EventHandler<EventTapOnConfirmPhotoButton>() {
                    @Override
                    public Optional<State> processEvent(EventTapOnConfirmPhotoButton event) {
                        // If the compressed data is not available, need to wait until it arrives.
                        if (!mPictureData.isPresent()) {
                            mShouldFinishWhenReceivePictureData = true;
                            return NO_CHANGE;
                        }

                        // If the compressed data is available, just saving the picture and finish.
                        return Optional.of((State) StateSavingPicture.from(
                                StateReviewingPicture.this,
                                mResourceCaptureTools.get().getResourceConstructed(),
                                mPictureData.get()));
                    }
                };
        setEventHandler(EventTapOnConfirmPhotoButton.class, tapOnConfirmPhotoButtonHandler);

        /** Handles EventTapOnRetakePhotoButton. */
        EventHandler<EventTapOnRetakePhotoButton> tapOnRetakePhotoButtonHandler =
                new EventHandler<EventTapOnRetakePhotoButton>() {
                    @Override
                    public Optional<State> processEvent(EventTapOnRetakePhotoButton event) {
                        return Optional.of((State) StateReadyForCapture.from(
                                StateReviewingPicture.this, mResourceCaptureTools));
                    }
                };
        setEventHandler(EventTapOnRetakePhotoButton.class, tapOnRetakePhotoButtonHandler);

        /** Handles EventPictureCompressed. */
        EventHandler<EventPictureCompressed> pictureCompressedHandler =
                new EventHandler<EventPictureCompressed>() {
                    @Override
                    public Optional<State> processEvent(EventPictureCompressed event) {
                        // Users have clicked the done button, save the data and finish now.
                        if (mShouldFinishWhenReceivePictureData) {
                            return Optional.of((State) StateSavingPicture.from(
                                    StateReviewingPicture.this,
                                    mResourceCaptureTools.get().getResourceConstructed(),
                                    event.getPictureData()));
                        }

                        if (mIsReviewingThumbnail) {
                            final byte[] pictureData = event.getPictureData();
                            final int pictureOrientation = event.getOrientation();
                            ResourceConstructed resourceConstructed =
                                    mResourceCaptureTools.get().getResourceConstructed().get();
                            resourceConstructed.getCameraHandler().post(
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
                        // Wait until the picture got decoded.
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventPictureCompressed.class, pictureCompressedHandler);

        /** Handles EventPictureDecoded. */
        EventHandler<EventPictureDecoded> pictureDecodedHandler =
                new EventHandler<EventPictureDecoded>() {
                    @Override
                    public Optional<State> processEvent(EventPictureDecoded event) {
                        mPictureData = Optional.of(event.getPictureData());
                        showPicture(event.getPictureBitmap());
                        return NO_CHANGE;
                    }
                };
        setEventHandler(EventPictureDecoded.class, pictureDecodedHandler);
    }

    @Override
    public Optional<State> onEnter() {
        mResourceCaptureTools.get().getCaptureSessionManager()
                .addSessionListener(mCaptureSessionListener);  // Will be balanced in onLeave().
        showPicture(mPictureBitmap);
        return NO_CHANGE;
    }

    @Override
    public void onLeave() {
        mResourceCaptureTools.close();
        mResourceCaptureTools.get().getCaptureSessionManager()
                .removeSessionListener(mCaptureSessionListener);
    }

    private void showPicture(final Bitmap bitmap) {
        mPictureBitmap = bitmap;
        mResourceCaptureTools.get().getMainThread().execute(new Runnable() {
            @Override
            public void run() {
                mResourceCaptureTools.get().getModuleUI().showPictureReviewUI(mPictureBitmap);
            }
        });
    }

    private final CaptureSessionManager.SessionListener mCaptureSessionListener =
            new CaptureSessionManager.SessionListener() {
                @Override
                public void onSessionThumbnailUpdate(final Bitmap thumbnailBitmap) {
                    // Not waiting for thumbnail anymore.
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