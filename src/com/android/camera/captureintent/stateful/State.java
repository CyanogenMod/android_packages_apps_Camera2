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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Defines an state interface that any implementation of the interface can be
 * operated by {@link StateMachine}.
 */
@ParametersAreNonnullByDefault
public interface State {

    public static Optional<State> NO_CHANGE = Optional.absent();

    /**
     * Obtains the associated state machine.
     *
     * @return The associated state machine.
     */
    public StateMachine getStateMachine();

    /**
     * Notifies the state that the state machine is about to transition into
     * the state.
     *
     * @return The next desired state. If it is not NO_CHANGE, the state
     * machine will change to the next desired state immediately.
     */
    public Optional<State> onEnter();

    /**
     * Notifies the state that the state machine is about to transition out
     * from this state to another state. The state has to release all resources
     * it holds at this point.
     */
    public void onLeave();

    /**
     * Obtains the number of registered event handlers in the state.
     *
     * @return The number of registered event handlers in the state.
     */
    @VisibleForTesting
    public int getEventHandlerCount();

    /**
     * Obtains the event handler for a particular type of event.
     *
     * @param eventClass The type of the event.
     * @return An event handler that is able to process the event. Null if the
     * state doesn't observe the event.
     */
    public <T extends Event> EventHandler<T> getEventHandler(Class<T> eventClass);

    /**
     * Specifies a handler for a particular type of event.
     *
     * @param eventClass   The event class.
     * @param eventHandler The event handler.
     */
    public <T extends Event> void setEventHandler(
            Class<T> eventClass, EventHandler<T> eventHandler);

    /**
     * Removes the handler for a specific type of event.
     *
     * @param eventClass The event class.
     */
    public <T extends Event> void removeEventHandler(Class<T> eventClass);
}