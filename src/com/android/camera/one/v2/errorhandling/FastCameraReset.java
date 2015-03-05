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

import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.stats.UsageStatistics;
import com.google.common.logging.eventprotos;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Resets camera usage by calling abortCaptures(), flushing (interrupting) any
 * currently-executing camera commands, and restarting the preview.
 * <p>
 * Workaround for Bug: 19061883
 */
@ParametersAreNonnullByDefault
final class FastCameraReset implements FailureHandler {
    private final Logger mLog;
    private final CameraCaptureSessionProxy mCaptureSession;
    private final CameraCommandExecutor mCommandExecutor;
    private final Runnable mPreviewStarter;
    private final UsageStatistics mUsageStats;

    FastCameraReset(Logger.Factory logFactory, CameraCaptureSessionProxy captureSession,
            CameraCommandExecutor commandExecutor, Runnable previewStarter,
            UsageStatistics usageStats) {
        mLog = logFactory.create(new Log.Tag("FastCameraReset"));
        mCaptureSession = captureSession;
        mCommandExecutor = commandExecutor;
        mPreviewStarter = previewStarter;
        mUsageStats = usageStats;
    }

    @Override
    public void run() {
        // TODO: Replace UNKNOWN_REASON with enum for this error.
        mUsageStats.cameraFailure(eventprotos.CameraFailure.FailureReason.UNKNOWN_REASON,
                "api2_repeated_failure", UsageStatistics.NONE, UsageStatistics.NONE);

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
