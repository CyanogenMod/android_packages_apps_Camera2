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

package com.android.camera.captureintent.state;

import com.google.common.base.Optional;

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.event.EventOnSurfaceTextureAvailable;
import com.android.camera.captureintent.event.EventPause;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;

/**
 * Represents a state that module is active in foreground but waiting for
 * surface texture being available.
 */
public final class StateForeground extends StateImpl {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;

    // Can be used to transition from Background on resume.
    public static StateForeground from(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        return new StateForeground(previousState, resourceConstructed);
    }

    private StateForeground(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();  // Will be balanced in onLeave().
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        /** Handles EventPause. */
        EventHandler<EventPause> pauseHandler = new EventHandler<EventPause>() {
            @Override
            public Optional<State> processEvent(EventPause event) {
                return Optional.of((State) StateBackground.from(
                        StateForeground.this, mResourceConstructed));
            }
        };
        setEventHandler(EventPause.class, pauseHandler);

        /** Handles EventOnSurfaceTextureAvailable */
        EventHandler<EventOnSurfaceTextureAvailable> surfaceTextureAvailableHandler =
                new EventHandler<EventOnSurfaceTextureAvailable>() {
                    @Override
                    public Optional<State> processEvent(EventOnSurfaceTextureAvailable event) {
                        return Optional.of((State) StateForegroundWithSurfaceTexture.from(
                                StateForeground.this,
                                mResourceConstructed,
                                event.getSurfaceTexture()));
                    }
                };
        setEventHandler(
                EventOnSurfaceTextureAvailable.class, surfaceTextureAvailableHandler);
    }

    @Override
    public Optional<State> onEnter() {
        return NO_CHANGE;
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
    }
}