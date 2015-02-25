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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.stats.UsageStatistics;
import com.google.common.logging.eventprotos;

import java.net.UnknownServiceException;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Listens for repeated capture failure and resets the camera by calling
 * {@link CameraCaptureSession#abortCaptures()}, flushing/interrupting any
 * currently-running camera interactions, and restarting the preview.
 * <p>
 * Workaround for bug: 19061883
 */
@ParametersAreNonnullByDefault
public final class RepeatFailureDetector extends ResponseListener {
    private final Logger mLog;
    private final UsageStatistics mUsageStats;
    private final CameraCaptureSessionProxy mCaptureSession;
    private final CameraCommandExecutor mCommandExecutor;
    private final Runnable mPreviewStarter;
    private final int mConsecutiveFailureThreshold;
    private int mConsecutiveErrorCount;
    private boolean mExecutedReset;

    /**
     * @param logFactory Used for logging.
     * @param usageStats Used for logging.
     * @param captureSession The camera capture session to abort captures upon
     *            repeated failure.
     * @param commandExecutor The command executor to flush upon repeated
     *            failure.
     * @param previewStarter Used for restarting the preview.
     * @param consecutiveFailureThreshold The number of consecutive failures to
     *            classify as a repeat-failure.
     */
    public RepeatFailureDetector(Logger.Factory logFactory,
            UsageStatistics usageStats, CameraCaptureSessionProxy captureSession,
            CameraCommandExecutor commandExecutor, Runnable previewStarter,
            int consecutiveFailureThreshold) {
        mConsecutiveFailureThreshold = consecutiveFailureThreshold;
        mLog = logFactory.create(new Log.Tag("RepeatFailureDtctr"));
        mUsageStats = usageStats;
        mCaptureSession = captureSession;
        mCommandExecutor = commandExecutor;
        mPreviewStarter = previewStarter;

        mConsecutiveErrorCount = 0;
        mExecutedReset = false;
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        mConsecutiveErrorCount = 0;
    }

    @Override
    public void onSequenceAborted(int sequenceId) {
        mExecutedReset = false;
        mConsecutiveErrorCount = 0;
    }

    @Override
    public void onSequenceCompleted(int sequenceId, long frameNumber) {

    }

    @Override
    public void onFailed(CaptureFailure failure) {
        if (failure.getReason() == CaptureFailure.REASON_ERROR) {
            mConsecutiveErrorCount++;
            mLog.e("onCaptureFailed() REASON_ERROR:  Consecutive error count = " +
                    mConsecutiveErrorCount);
            if (mConsecutiveErrorCount >= mConsecutiveFailureThreshold && !
                    mExecutedReset) {
                mLog.e("onCaptureFailed() REASON_ERROR:  Running repeat error callback");
                // TODO: Replace UNKNOWN_REASON with enum for this error.
                mUsageStats.cameraFailure(eventprotos.CameraFailure.FailureReason.UNKNOWN_REASON,
                        "api2_repeated_failure", UsageStatistics.NONE, UsageStatistics.NONE);
                mExecutedReset = true;
                reset();
            }
        }
    }

    private void reset() {
        mLog.w("beginning reset()");
        try {
            mLog.w("abortCaptures()");
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (CameraCaptureSessionClosedException e) {
            e.printStackTrace();
        }
        mLog.w("flushing existing camera commands");
        mCommandExecutor.flush();
        mLog.w("restarting the preview");
        mPreviewStarter.run();
        mLog.w("finished reset()");
    }
}
