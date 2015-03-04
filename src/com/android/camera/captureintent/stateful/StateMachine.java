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

import javax.annotation.Nonnull;

/**
 * Defines a state machine interface that any implementation of this interface
 * is able to transition among different {@link State} and process various
 * {@link Event}.
 */
public interface StateMachine {
    /**
     * Obtains the current state.
     *
     * @return A {@link State} object that represents the current state.
     */
    public State getCurrentState();

    /**
     * Sets the initial state of the state machine.
     *
     * @param initialState An initial {@link State}.
     * @return True if the operation is succeeded.
     */
    public boolean setInitialState(@Nonnull State initialState);

    /**
     * Process a specified {@link Event} and potentially change the current
     * state.
     *
     * This method is thread-safe so it could be called on different threads.
     * Only one event could be processed at a time.
     *
     * @param event An {@link Event} to be processed.
     */
    public void processEvent(Event event);
}
