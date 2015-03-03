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

import com.android.camera.stats.UsageStatistics;
import com.android.camera.util.Callback;
import com.google.common.logging.eventprotos;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class RecoverySuccessCallback implements Callback<String> {
    private final UsageStatistics mUsageStats;

    RecoverySuccessCallback(UsageStatistics usageStats) {
        mUsageStats = usageStats;
    }

    @Override
    public void onCallback(@Nonnull String successfulRecoveryStrategyName) {
        // Log Success
        mUsageStats.cameraFailure(eventprotos.CameraFailure.FailureReason.UNKNOWN_REASON,
                "api2_repeated_failure_recovery_success", UsageStatistics.NONE, UsageStatistics
                        .NONE);
    }
}
