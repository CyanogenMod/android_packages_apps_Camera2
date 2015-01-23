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

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Maintains the current state of auto-exposure scans resulting from explicit
 * precapture trigger requests. This maintains the subset of the finite state
 * machine of {@link CaptureResult#CONTROL_AE_STATE} which relates to
 * CONTROL_AE_PRECAPTURE_TRIGGER
 * <p>
 * That is, it invokes the given callback when a scan is complete, according to
 * the following sequence:
 *
 * <pre>
 * .* CONTROL_AE_PRECAPTURE_TRIGGER_START .*
 * (STATE_INACTIVE|STATE_FLASH_REQUIRED|STATE_CONVERGED|STATE_LOCKED)
 * </pre>
 * <p>
 * See the android documentation for {@link CaptureResult#CONTROL_AE_STATE} for
 * further documentation on the state machine this class implements.
 */
@ParametersAreNonnullByDefault
public final class AETriggerStateMachine implements Updatable<CaptureResultProxy> {
    private static final Set<Integer> TRIGGER_DONE_STATES = ImmutableSet.of(
            CaptureRequest.CONTROL_AE_STATE_INACTIVE,
            CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED,
            CaptureRequest.CONTROL_AE_STATE_CONVERGED,
            CaptureRequest.CONTROL_AE_STATE_LOCKED);

    private final TriggerStateMachine mStateMachine;

    /**
     * @param scanCompleteCallback The {@link Updatable} to be notified when an
     *            AF scan has been completed.
     */
    public AETriggerStateMachine(Updatable<Void> scanCompleteCallback) {
        mStateMachine = new TriggerStateMachine(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                TRIGGER_DONE_STATES,
                scanCompleteCallback);
    }

    @Override
    public void update(CaptureResultProxy result) {
        mStateMachine.update(
                result.getFrameNumber(),
                result.getRequest().get(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER),
                result.get(CaptureResult.CONTROL_AE_STATE));
    }
}
