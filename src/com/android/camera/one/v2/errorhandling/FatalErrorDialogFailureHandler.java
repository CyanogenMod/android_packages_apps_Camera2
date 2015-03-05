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
import com.android.camera.stats.UsageStatistics;
import com.google.common.logging.eventprotos;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Handles repeat failure by displaying the fatal error dialog (which also
 * finishes the activity).
 */
@ParametersAreNonnullByDefault
final class FatalErrorDialogFailureHandler implements FailureHandler {
    private final FatalErrorHandler mFatalErrorHandler;
    private final UsageStatistics mUsageStats;

    FatalErrorDialogFailureHandler(FatalErrorHandler fatalErrorHandler,
            UsageStatistics usageStats) {
        mFatalErrorHandler = fatalErrorHandler;
        mUsageStats = usageStats;
    }

    @Override
    public void run() {
        mUsageStats.cameraFailure(eventprotos.CameraFailure.FailureReason.UNKNOWN_REASON,
                "api2_repeated_failure_2", UsageStatistics.NONE, UsageStatistics.NONE);
        // TODO Add another {@link FatalErrorHandler.Reason} for this situation
        mFatalErrorHandler.handleFatalError(FatalErrorHandler.Reason.CANNOT_CONNECT_TO_CAMERA);
    }
}
