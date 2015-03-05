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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.resource.ResourceConstructed;
import com.android.camera.captureintent.resource.ResourceSurfaceTexture;
import com.android.camera.captureintent.stateful.EventHandler;
import com.android.camera.captureintent.event.EventOnSurfaceTextureDestroyed;
import com.android.camera.captureintent.event.EventResume;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.stateful.StateImpl;

/**
 * Represents a state that module is inactive in background but surface texture
 * is available.
 * <p>
 * Module is in this state when first run dialog is still presented. The module
 * will be resumed after people finish first run dialog (b/19531554).
 */
public class StateBackgroundWithSurfaceTexture extends StateImpl {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;

    /**
     * Used to transition from StateOpeningCamera, StateStartingPreview and
     * StateReadyForCapture on module got paused.
     */
    public static StateBackgroundWithSurfaceTexture from(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture) {
        return new StateBackgroundWithSurfaceTexture(
                previousState, resourceConstructed, resourceSurfaceTexture);
    }

    private StateBackgroundWithSurfaceTexture(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture) {
        super(previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in onLeave().
        mResourceSurfaceTexture = resourceSurfaceTexture;
        mResourceSurfaceTexture.addRef();  // Will be balanced in onLeave().
        registerEventHandlers();
    }

    private void registerEventHandlers() {
        /** Handles EventResume. */
        EventHandler<EventResume> resumeHandler = new EventHandler<EventResume>() {
            @Override
            public Optional<State> processEvent(EventResume eventResume) {
                return Optional.of((State) StateForegroundWithSurfaceTexture.from(
                        StateBackgroundWithSurfaceTexture.this,
                        mResourceConstructed,
                        mResourceSurfaceTexture));
            }
        };
        setEventHandler(EventResume.class, resumeHandler);

        /** Handles EventOnSurfaceTextureDestroyed. */
        EventHandler<EventOnSurfaceTextureDestroyed> surfaceTextureDestroyedHandler =
                new EventHandler<EventOnSurfaceTextureDestroyed>() {
                    @Override
                    public Optional<State> processEvent(EventOnSurfaceTextureDestroyed event) {
                        return Optional.of((State) StateBackground.from(
                                StateBackgroundWithSurfaceTexture.this, mResourceConstructed));
                    }
                };
        setEventHandler(
                EventOnSurfaceTextureDestroyed.class, surfaceTextureDestroyedHandler);
    }

    @Override
    public Optional<State> onEnter() {
        // Do nothing unless the module is resumed.
        return Optional.absent();
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
    }

    @VisibleForTesting
    public RefCountBase<ResourceSurfaceTexture> getResourceSurfaceTexture() {
        return mResourceSurfaceTexture;
    }
}
