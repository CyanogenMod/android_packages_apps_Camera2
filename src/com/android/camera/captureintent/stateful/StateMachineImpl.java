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

package com.android.camera.captureintent.stateful;

import com.google.common.base.Optional;

import com.android.camera.debug.Log;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

public class StateMachineImpl implements StateMachine {
    private static final Log.Tag TAG = new Log.Tag("StateMachine");

    /** The current state. */
    private State mState;

    /** The lock to protect mState. */
    private final ReentrantLock mStateLock;

    /** The condition to synchronize state changed event. */
    private final Condition mStateChangedCondition;

    public StateMachineImpl() {
        mStateLock = new ReentrantLock();
        mStateChangedCondition = mStateLock.newCondition();
        mState = new StateUninitialized(this);
    }

    /**
     * Jumps directly to a specific state.
     *
     * @param newState The new state.
     */
    public void jumpToState(@Nonnull State newState) {
        mStateLock.lock();
        try {
            if (newState.equals(mState)) {
                Log.d(TAG, "No op since jump to the same state.");
            } else {
                // While changing to a particular state, execute its onEnter() hook
                // and keep forwarding to new states if necessary.
                Log.d(TAG, "Change state : " + mState + " => " + newState);
                mState.onLeave();
                mState = newState;
                Optional<State> nextState = mState.onEnter();
                while (nextState.isPresent()) {
                    Log.d(TAG, "Forward state : " + mState + " => " + nextState.get());
                    mState.onLeave();
                    mState = nextState.get();
                    nextState = mState.onEnter();
                }

                mStateChangedCondition.signalAll();
            }
        } finally {
            mStateLock.unlock();
        }
    }

    @Override
    public State getCurrentState() {
        mStateLock.lock();
        try {
            return mState;
        } finally {
            mStateLock.unlock();
        }
    }

    @Override
    public boolean setInitialState(State initialState) {
        mStateLock.lock();
        try {
            if (!(mState instanceof StateUninitialized)) {
                return false;
            }
            jumpToState(initialState);
            return true;
        } finally {
            mStateLock.unlock();
        }
    }

    @Override
    public void processEvent(Event event) {
        mStateLock.lock();
        try {
            EventHandler eventHandler = mState.getEventHandler(event.getClass());
            if (eventHandler != null) {
                Log.d(TAG, "Process event : " + event);
                Optional<State> newState = eventHandler.processEvent(event);
                if (newState.isPresent()) {
                    jumpToState(newState.get());
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to process event: " + event);
            throw ex;
        } finally {
            mStateLock.unlock();
        }
    }

    /**
     * The initial state of the state machine.
     */
    public static class StateUninitialized extends StateImpl {
        public StateUninitialized(StateMachine stateMachine) {
            super(stateMachine);
        }
    }
}
