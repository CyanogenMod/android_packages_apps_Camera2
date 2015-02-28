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

/**
 * Defines an event handler interface that any implementation of this interface
 * can be used by {@link StateMachine} to handle a particular type of event
 * which extends {@link Event}.
 *
 * @param <T> The particular type of event.
 */
public interface EventHandler<T extends Event> {

    /**
     * Process a single event.
     *
     * @param event The event to be processed.
     * @return The next desired state. If it is not NO_CHANGE, the state
     *         machine will change to the next desired state.
     */
    public Optional<State> processEvent(T event);
}