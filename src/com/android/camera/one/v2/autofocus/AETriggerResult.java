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
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Listens for image metadata and returns the result of an AE scan caused by an
 * AE_TRIGGER_START. The result of indicates whether flash is required.
 * <p>
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
public final class AETriggerResult implements Updatable<CaptureResultProxy> {
    private static final Set<Integer> TRIGGER_DONE_STATES = ImmutableSet.of(
            CaptureResult.CONTROL_AE_STATE_INACTIVE,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED);

    private final TriggerStateMachine mStateMachine;
    private final SettableFuture<Boolean> mFutureResult;

    public AETriggerResult() {
        mStateMachine = new TriggerStateMachine(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START,
                TRIGGER_DONE_STATES);
        mFutureResult = SettableFuture.create();
    }

    @Override
    public void update(CaptureResultProxy result) {
        Integer state = result.get(CaptureResult.CONTROL_AE_STATE);
        boolean done = mStateMachine.update(
                result.getFrameNumber(),
                result.getRequest().get(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER),
                state);
        if (done) {
            boolean flashRequired = Objects.equal(state, CaptureResult
                    .CONTROL_AE_STATE_FLASH_REQUIRED);
            mFutureResult.set(flashRequired);
        }
    }

    /**
     * Blocks until the AE scan is complete.
     *
     * @return Whether the scene requires flash to be properly exposed.
     * @throws InterruptedException
     */
    public boolean get() throws InterruptedException {
        try {
            return mFutureResult.get();
        } catch (ExecutionException impossible) {
            throw new RuntimeException(impossible);
        }
    }

    /**
     * Blocks until the AE scan is complete.
     *
     * @return Whether the scene requires flash to be properly exposed.
     * @throws InterruptedException
     */
    public boolean get(long timeout, TimeUnit timeUnit) throws InterruptedException,
            TimeoutException {
        try {
            return mFutureResult.get(timeout, timeUnit);
        } catch (ExecutionException impossible) {
            throw new RuntimeException(impossible);
        }
    }
}
