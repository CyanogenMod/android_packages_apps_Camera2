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

import com.android.camera.FatalErrorHandler;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.stats.UsageStatistics;

import java.util.Arrays;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Creates a ResponseListener which will handle repeat failures.
 * <p>
 * This is to workaround bug: 19061883
 */
@ParametersAreNonnullByDefault
public final class RepeatFailureHandlerComponent {
    private final RepeatFailureDetector mRepeatFailureHandler;

    private RepeatFailureHandlerComponent(RepeatFailureDetector repeatFailureHandler) {
        mRepeatFailureHandler = repeatFailureHandler;
    }

    public ResponseListener provideResponseListener() {
        return mRepeatFailureHandler;
    }

    public static RepeatFailureHandlerComponent create(Logger.Factory logFactory,
            FatalErrorHandler fatalErrorHandler, CameraCaptureSessionProxy captureSession,
            CameraCommandExecutor commandExecutor, Runnable previewStarter,
            UsageStatistics usageStats, int consecutiveFailureThreshold) {
        FastCameraReset fastCameraReset = new FastCameraReset(logFactory, captureSession,
                commandExecutor, previewStarter, usageStats);
        FatalErrorDialogFailureHandler fatalErrorDialog = new FatalErrorDialogFailureHandler
                (fatalErrorHandler, usageStats);

        RecoverySuccessCallback recoverySuccessCallback = new RecoverySuccessCallback(usageStats);

        List<FailureHandler> recoveryStrategies = Arrays.asList(fastCameraReset, fatalErrorDialog);
        RepeatFailureDetector failureDetector = new RepeatFailureDetector(logFactory,
                consecutiveFailureThreshold,
                recoveryStrategies,
                recoverySuccessCallback);
        return new RepeatFailureHandlerComponent(failureDetector);
    }
}
