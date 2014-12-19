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

package com.android.camera.one.v2.autofocus;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.core.ResponseListener;

/**
 * Maintains the current state of auto-focus scans resulting from explicit
 * trigger requests. This maintains the subset of the finite state machine of
 * {@link android.hardware.camera2.CaptureResult#CONTROL_AF_STATE} which relates
 * to AF_TRIGGER.
 */
class TriggeredAFScanStateResponseListener extends ResponseListener {
    private final Updatable<Void> mScanCompleteUpdatable;
    private boolean mTriggered;
    // If mCurrentState is SCANNING, then this is the frame number of the
    // trigger start.
    private long mTriggerFrameNumber;

    /**
     * @param scanCompleteUpdatable The {@link Updatable} to be notified when an
     *            AF scan has been completed.
     */
    public TriggeredAFScanStateResponseListener(Updatable<Void> scanCompleteUpdatable) {
        mScanCompleteUpdatable = scanCompleteUpdatable;
        mTriggered = false;
    }

    @Override
    public void onProgressed(CaptureResult result) {
        processUpdate(
                result.getRequest().get(CaptureRequest.CONTROL_AF_TRIGGER),
                result.get(CaptureResult.CONTROL_AF_STATE),
                result.getFrameNumber());
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        processUpdate(
                result.getRequest().get(CaptureRequest.CONTROL_AF_TRIGGER),
                result.get(CaptureResult.CONTROL_AF_STATE),
                result.getFrameNumber());
    }

    private void processUpdate(Integer afTrigger, Integer afState, long frameNumber) {
        if (!mTriggered) {
            if (afTrigger != null) {
                if (afTrigger == CaptureRequest.CONTROL_AF_TRIGGER_START) {
                    mTriggered = true;
                    mTriggerFrameNumber = frameNumber;
                }
            }
        } else {
            // Only process results in-order. That is, only transition from
            // SCANNING to IDLE if a result for a frame *after* the trigger
            // indicates that the AF system has stopped scanning.
            if (frameNumber > mTriggerFrameNumber) {
                if (afState != null) {
                    if (afState == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                            afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        mTriggered = false;
                        mScanCompleteUpdatable.update(null);
                    }
                }
            }
        }
    }
}
