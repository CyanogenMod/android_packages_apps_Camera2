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

package com.android.camera.one.v2.autofocus;

import android.hardware.camera2.CaptureResult;
import android.support.annotation.Nullable;

import com.android.camera.async.Updatable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Tracks the finite state machines used by the camera2 api for AF and AE
 * triggers. That is, the state machine waits for a TRIGGER_START followed by
 * one of the done states, at which point a callback is invoked and the state
 * machine resets.
 * <p>
 * In other words, this implements the state machine defined by the following
 * regex, such that a callback is invoked each time the state machine reaches
 * the end.
 * 
 * <pre>
 * (.* TRIGGER_START .* [DONE_STATES])+
 * </pre>
 * <p>
 * See the android documentation for {@link CaptureResult#CONTROL_AF_STATE} and
 * {@link CaptureResult#CONTROL_AE_STATE} for the transition tables which this
 * is based on.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class TriggerStateMachine {
    private static enum State {
        WAITING_FOR_TRIGGER,
        TRIGGERED
    }

    private final int mTriggerStart;
    private final Set<Integer> mDoneStates;
    private State mCurrentState;
    @Nullable
    private Long mLastTriggerFrameNumber;
    @Nullable
    private Long mLastFinishFrameNumber;

    public TriggerStateMachine(int triggerStart, Set<Integer> doneStates) {
        mTriggerStart = triggerStart;
        mDoneStates = doneStates;
        mCurrentState = State.WAITING_FOR_TRIGGER;
        mLastTriggerFrameNumber = null;
        mLastFinishFrameNumber = null;
    }

    /**
     * @return True upon completion of a cycle of the state machine.
     */
    public boolean update(long frameNumber, @Nullable Integer triggerState, @Nullable Integer
            state) {
        boolean triggeredNow = triggerState != null && triggerState == mTriggerStart;
        boolean doneNow = mDoneStates.contains(state);

        if (mCurrentState == State.WAITING_FOR_TRIGGER) {
            if (mLastTriggerFrameNumber == null || frameNumber > mLastTriggerFrameNumber) {
                if (triggeredNow) {
                    mCurrentState = State.TRIGGERED;
                    mLastTriggerFrameNumber = frameNumber;
                }
            }
        }

        if (mCurrentState == State.TRIGGERED) {
            if (mLastFinishFrameNumber == null || frameNumber > mLastFinishFrameNumber) {
                if (doneNow) {
                    mCurrentState = State.WAITING_FOR_TRIGGER;
                    mLastFinishFrameNumber = frameNumber;
                    return true;
                }
            }
        }

        return false;
    }
}
