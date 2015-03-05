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
import com.android.camera.captureintent.CaptureIntentConfig;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.resource.ResourceSurfaceTexture;
import com.android.camera.captureintent.resource.ResourceSurfaceTextureImpl;
import com.android.camera.captureintent.resource.ResourceSurfaceTextureNexus4Impl;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.event.EventOnSurfaceTextureAvailable;
import com.android.camera.captureintent.event.EventResume;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;
import com.android.camera.captureintent.stateful.StateMachine;

/**
 * Represents a state that module is inactive in background. This is also the
 * initial state of CaptureIntentModule.
 */
public final class StateBackground extends StateImpl {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;

    public static StateBackground create(
            StateMachine stateMachine,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        return new StateBackground(stateMachine, resourceConstructed);
    }

    public static StateBackground from(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        return new StateBackground(previousState, resourceConstructed);
    }

    private StateBackground(
            StateMachine stateMachine,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        super(stateMachine);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();  // Will be balanced in onLeave().
        registerEventHandlers();
    }

    private StateBackground(State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();  // Will be balanced in onLeave().
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        /** Handles EventResume. */
        EventHandler<EventResume> resumeHandler = new EventHandler<EventResume>() {
            @Override
            public Optional<State> processEvent(EventResume eventResume) {
                return Optional.of((State) StateForeground.from(
                        StateBackground.this, mResourceConstructed));
            }
        };
        setEventHandler(EventResume.class, resumeHandler);

        /** Handles EventOnSurfaceTextureAvailable */
        EventHandler<EventOnSurfaceTextureAvailable> onSurfaceTextureAvailableHandler =
                new EventHandler<EventOnSurfaceTextureAvailable>() {
                    @Override
                    public Optional<State> processEvent(EventOnSurfaceTextureAvailable event) {
                        RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture;
                        if (CaptureIntentConfig.WORKAROUND_PREVIEW_STRETCH_BUG_NEXUS4) {
                            resourceSurfaceTexture = ResourceSurfaceTextureNexus4Impl.create(
                                    mResourceConstructed,
                                    event.getSurfaceTexture());
                        } else {
                            resourceSurfaceTexture = ResourceSurfaceTextureImpl.create(
                                    mResourceConstructed,
                                    event.getSurfaceTexture());
                        }
                        State nextState = StateBackgroundWithSurfaceTexture.from(
                                StateBackground.this,
                                mResourceConstructed,
                                resourceSurfaceTexture);
                        // Release ResourceSurfaceTexture after it was handed
                        // over to StateBackgroundWithSurfaceTexture.
                        resourceSurfaceTexture.close();
                        return Optional.of(nextState);
                    }
                };
        setEventHandler(EventOnSurfaceTextureAvailable.class, onSurfaceTextureAvailableHandler);
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
