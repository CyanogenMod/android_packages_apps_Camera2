/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.one.v2.initialization;

import android.hardware.camera2.CaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Distributes metadata to more-specific callbacks.
 */
public class MetadataCallback implements Updatable<TotalCaptureResultProxy> {
    private final Updatable<Integer> mFocusState;
    private final Updatable<OneCamera.FocusState> mOneCameraFocusState;
    private final Updatable<Integer> mFocusMode;

    public MetadataCallback(
            Updatable<Integer> focusState,
            Updatable<OneCamera.FocusState> oneCameraFocusState,
            Updatable<Integer> focusMode) {
        mFocusState = focusState;
        mOneCameraFocusState = oneCameraFocusState;
        mFocusMode = focusMode;
    }

    @Override
    public void update(@Nonnull TotalCaptureResultProxy totalCaptureResult) {
        updateFocusMode(totalCaptureResult);
        updateFocusState(totalCaptureResult);
        updateOneCameraFocusState(totalCaptureResult);
    }

    private void updateFocusMode(TotalCaptureResultProxy totalCaptureResult) {
        Integer focusMode = totalCaptureResult.get(CaptureResult.CONTROL_AF_MODE);
        if (focusMode != null) {
            mFocusMode.update(focusMode);
        }
    }

    private void updateFocusState(TotalCaptureResultProxy totalCaptureResult) {
        Integer focusState = totalCaptureResult.get(CaptureResult.CONTROL_AF_STATE);
        if (focusState != null) {
            mFocusState.update(focusState);
        }
    }

    private void updateOneCameraFocusState(TotalCaptureResultProxy totalCaptureResult) {
        Float focusDistance = totalCaptureResult.get(CaptureResult.LENS_FOCUS_DISTANCE);
        Integer focusState = totalCaptureResult.get(CaptureResult.CONTROL_AF_STATE);
        if (focusDistance != null && focusState != null) {
            Set<Integer> activeStates = new HashSet<>();
            activeStates.add(CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN);
            activeStates.add(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN);
            boolean active = activeStates.contains(focusState);
            mOneCameraFocusState.update(new OneCamera.FocusState(focusDistance, active));
        }
    }
}
