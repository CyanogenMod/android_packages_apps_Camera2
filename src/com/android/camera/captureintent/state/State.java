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
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.util.Size;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;

public abstract class State {
    public final static Optional<State> NO_CHANGE = Optional.absent();

    public static enum ID {
        Uninitialized,
        Background,
        Fatal,
        Foreground,
        ForegroundWithSurfaceTexture,
        IntentCompleted,
        OpeningCamera,
        ReadyForCapture,
        ReviewingPicture,
        SavingPicture,
        StartingPreview
    }

    private final ID mId;
    private final StateMachine mStateMachine;

    protected State(ID id, StateMachine stateMachine) {
        mId = id;
        mStateMachine = stateMachine;
    }

    protected State(ID id, State previousState) {
        mId = id;
        mStateMachine = previousState.mStateMachine;
    }

    protected StateMachine getStateMachine() {
        return mStateMachine;
    }

    @Override
    public String toString() {
        return getClass().getName();
    }

    @Override
    public boolean equals(Object object) {
        boolean result;
        if (object == null) {
            result = false;
        } else {
            State otherState = (State) object;
            result = (mId == otherState.mId);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    /**
     * Called when the state machine just transitioned into the state.
     *
     * @return the next desired state.
     */
    public Optional<State> onEnter() {
        return NO_CHANGE;
    }

    /**
     * Called when the state machine is about to transition from this state to
     * another state.
     */
    public void onLeave() {
    }

    /**
     * All possible events.
     * TODO: Split this into smaller interfaces.
     */
    public Optional<State> processResume() {
        return NO_CHANGE;
    }
    public Optional<State> processPause() {
        return NO_CHANGE;
    }
    public Optional<State> processOnSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
        return NO_CHANGE;
    }
    public Optional<State> processOnTextureViewLayoutChanged(Size layoutSize) {
        return NO_CHANGE;
    }
    public Optional<State> processOnCameraOpened(OneCamera camera) {
        camera.close();
        return NO_CHANGE;
    }
    public Optional<State> processOnCameraOpenFailure() {
        return NO_CHANGE;
    }
    public Optional<State> processOnPreviewSetupSucceeded() {
        return NO_CHANGE;
    }
    public Optional<State> processOnPreviewSetupFailed() {
        return NO_CHANGE;
    }
    public Optional<State> processOnReadyStateChanged(boolean readyForCapture) {
        return NO_CHANGE;
    }
    public Optional<State> processOnZoomRatioChanged(float zoomRatio) {
        return NO_CHANGE;
    }
    public Optional<State> processOnFocusStateUpdated(
            OneCamera.AutoFocusState focusState, long frameNumber) {
        return NO_CHANGE;
    }
    public Optional<State> processOnSingleTapOnPreview(Point point) {
        return NO_CHANGE;
    }
    public Optional<State> processOnShutterButtonClicked() {
        return NO_CHANGE;
    }
    public Optional<State> processOnQuickExpose() {
        return NO_CHANGE;
    }
    public Optional<State> processOnPictureBitmapAvailable(Bitmap bitmap) {
        return NO_CHANGE;
    }
    public Optional<State> processOnPictureCompressed(
            byte[] pictureData, int pictureOrientation) {
        return NO_CHANGE;
    }
    public Optional<State> processOnPictureDecoded(Bitmap pictureBitmap, byte[] pictureData) {
        return NO_CHANGE;
    }
    public Optional<State> processOnSwitchButtonClicked() {
        return NO_CHANGE;
    }
    public Optional<State> processOnCancelButtonClicked() {
        return NO_CHANGE;
    }
    public Optional<State> processOnDoneButtonClicked() {
        return NO_CHANGE;
    }
    public Optional<State> processOnRetakeButtonClicked() {
        return NO_CHANGE;
    }
    public Optional<State> processOnCountDownFinished() {
        return NO_CHANGE;
    }
}