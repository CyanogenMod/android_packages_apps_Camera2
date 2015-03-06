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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Provides a basic implementation for {@link State} interface.
 */
@ParametersAreNonnullByDefault
public abstract class StateImpl implements State {
    private final StateMachine mStateMachine;
    private final Map<Class, EventHandler> mEventHandlerMap;

    protected StateImpl(StateMachine stateMachine) {
        mStateMachine = stateMachine;
        mEventHandlerMap = new HashMap<>();
    }

    protected StateImpl(State previousState) {
        this(previousState.getStateMachine());
    }

    @Override
    public StateMachine getStateMachine() {
        return mStateMachine;
    }

    @Override
    public Optional<State> onEnter() {
        return NO_CHANGE;
    }

    @Override
    public void onLeave() {
    }

    @Override
    public int getEventHandlerCount() {
        return mEventHandlerMap.size();
    }

    @Override
    public final <T extends Event> EventHandler<T> getEventHandler(Class<T> eventClass) {
        return mEventHandlerMap.get(eventClass);
    }

    @Override
    public final <T extends Event> void setEventHandler(
            Class<T> eventClass, EventHandler<T> eventHandler) {
        mEventHandlerMap.put(eventClass, eventHandler);
    }

    @Override
    public <T extends Event> void removeEventHandler(Class<T> eventClass) {
        mEventHandlerMap.remove(eventClass);
    }
}