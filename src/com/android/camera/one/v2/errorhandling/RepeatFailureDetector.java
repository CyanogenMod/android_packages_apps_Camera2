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

package com.android.camera.one.v2.errorhandling;

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.util.Callback;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Listens for repeated capture failure and invokes recovery strategies,
 * in-order as the repeated failures continue.
 * <p>
 * Workaround for bug: 19061883
 */
@ParametersAreNonnullByDefault
final class RepeatFailureDetector extends com.android.camera.one.v2.core.ResponseListener {
    private final Logger mLog;
    private final int mConsecutiveFailureThreshold;
    private final List<FailureHandler> mRecoveryStrategies;
    private final Callback<String> mRecoverySuccessCallback;
    /**
     * Indicates the number of consecutive times repeat failure has been
     * detected.
     * <p>
     * 0 indicates normal operation
     * <p>
     * Positive values also indicate the index of the recovery strategy which
     * has been used.
     */
    private int mFailureLevel;
    /**
     * The frame number of the failure which resulted in a recovery strategy
     * being invoked. This is used to determine if a frame success corresponds
     * to a frame from before or after the recovery strategy was run.
     * <p>
     * This
     */
    private long mFailureFrameNumber;
    /**
     * The number of consecutive
     */
    private int mConsecutiveErrorCount;

    /**
     * @param logFactory Used for logging.
     * @param consecutiveFailureThreshold The number of consecutive failures to
     *            consider a "repeat failure".
     * @param recoveryStrategies A list of strategies to try to recover from or
     *            handle (in other ways) a repeat failure. Strategies are
     *            invoked in-order each time the number of consecutive failures
     *            reaches over the threshold. That is, the Nth strategy is
     *            invoked after N * consecutiveFailureThreshold consecutive
     *            failures are detected.
     * @param recoverySuccessCallback Invoked upon success of a recovery
     *            strategy, with the string name of the recovery strategy which
     *            worked.
     */
    public RepeatFailureDetector(Logger.Factory logFactory,
            int consecutiveFailureThreshold, List<FailureHandler> recoveryStrategies,
            Callback<String> recoverySuccessCallback) {
        mLog = logFactory.create(new Log.Tag("RepeatFailureDtctr"));

        mConsecutiveFailureThreshold = consecutiveFailureThreshold;
        mRecoveryStrategies = recoveryStrategies;
        mRecoverySuccessCallback = recoverySuccessCallback;

        mFailureLevel = 0;
        mConsecutiveErrorCount = 0;
        mFailureFrameNumber = -1;
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        mConsecutiveErrorCount = 0;
        if (mFailureLevel > 0) {
            if (result.getFrameNumber() > mFailureFrameNumber) {
                // Success! Recovery worked, and a frame was completed
                // successfully.
                mRecoverySuccessCallback.onCallback(mRecoveryStrategies.get(mFailureLevel)
                        .toString());
                mFailureLevel = 0;
                mFailureFrameNumber = -1;
            }
        }
    }

    @Override
    public void onFailed(CaptureFailure failure) {
        if (failure.getReason() == CaptureFailure.REASON_ERROR) {
            mConsecutiveErrorCount++;
            mLog.e(String.format("onCaptureFailed() REASON_ERROR:  Consecutive error count = %d x" +
                    " %d", mConsecutiveErrorCount, mFailureLevel));
            if (mConsecutiveErrorCount >= mConsecutiveFailureThreshold) {
                mConsecutiveErrorCount = 0;
                mFailureFrameNumber = failure.getFrameNumber();
                if (mFailureLevel < mRecoveryStrategies.size()) {
                    mLog.e(String.format("onCaptureFailed() REASON_ERROR:  Repeat failure " +
                            "detected (x%d).  Attempting recovery strategy:  %s",
                            mConsecutiveErrorCount, mRecoveryStrategies.get(mFailureLevel)
                                    .toString()));
                    mRecoveryStrategies.get(mFailureLevel).run();
                }
                mFailureLevel++;
            }
        }
    }
}
