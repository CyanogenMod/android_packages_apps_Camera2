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
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.PictureDecoder;
import com.android.camera.util.Size;

import android.graphics.Bitmap;

/**
 * A state that shows the taken picture for review. The Cancel, Done or
 * Take buttons are presented. The state handles 3 events:
 * - OnCancelButtonClicked
 * - OnRetakeButtonClicked
 * - OnDoneButtonClicked
 */
public class StateReviewingPicture extends State {
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
            StateReadyForCapture previousState,
            RefCountBase<ResourceCaptureTools> resourceCaptureTools,
            Bitmap pictureBitmap,
            Optional<byte[]> pictureData) {
        super(ID.ReviewingPicture, previousState);
        mResourceCaptureTools = resourceCaptureTools;
        mResourceCaptureTools.addRef();  // Will be balanced in onLeave().
        mPictureBitmap = pictureBitmap;
        mPictureData = pictureData;
        mIsReviewingThumbnail = !pictureData.isPresent();
        mShouldFinishWhenReceivePictureData = false;
    }

    @Override
    public Optional<State> onEnter() {
        showPicture(mPictureBitmap);
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
        ResourceSurfaceTexture resourceSurfaceTexture =
                mResourceCaptureTools.get().getResourceSurfaceTexture().get();
        resourceSurfaceTexture.setPreviewLayoutSize(layoutSize);
        resourceSurfaceTexture.updatePreviewTransform();
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnCancelButtonClicked() {
        return Optional.of((State) StateIntentCompleted.from(
                this, mResourceCaptureTools.get().getResourceConstructed()));
    }

    @Override
    public Optional<State> processOnDoneButtonClicked() {
        // If the compressed data is not available, need to wait until it arrives.
        if (!mPictureData.isPresent()) {
            mShouldFinishWhenReceivePictureData = true;
            return NO_CHANGE;
        }

        // If the compressed data is available, just saving the picture and finish.
        return Optional.of((State) StateSavingPicture.from(
                this, mResourceCaptureTools.get().getResourceConstructed(), mPictureData.get()));
    }

    @Override
    public Optional<State> processOnRetakeButtonClicked() {
        return Optional.of((State) StateReadyForCapture.from(this, mResourceCaptureTools));
    }

    @Override
    public Optional<State> processOnPictureCompressed(
            final byte[] pictureData, final int pictureOrientation) {
        // Users have clicked the done button, save the data and finish now.
        if (mShouldFinishWhenReceivePictureData) {
            return Optional.of((State) StateSavingPicture.from(
                    this, mResourceCaptureTools.get().getResourceConstructed(), pictureData));
        }

        if (mIsReviewingThumbnail) {
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
                            getStateMachine().processEvent(new Event() {
                                @Override
                                public Optional<State> apply(State state) {
                                    return state.processOnPictureDecoded(pictureBitmap, pictureData);
                                }
                            });
                        }
                    });
        }
        // Wait until the picture got decoded.
        return NO_CHANGE;
    }

    @Override
    public Optional<State> processOnPictureDecoded(Bitmap pictureBitmap, byte[] pictureData) {
        mPictureData = Optional.of(pictureData);
        showPicture(pictureBitmap);
        return NO_CHANGE;
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

}