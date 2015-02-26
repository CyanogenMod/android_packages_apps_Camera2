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
import com.android.camera.one.OneCamera;

import android.graphics.SurfaceTexture;

/**
 * Represents a state that module is active in foreground but waiting for
 * surface texture being available.
 */
public final class StateForeground extends State {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;

    // Can be used to transition from Background on resume.
    public static StateForeground from(
            StateBackground background,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        return new StateForeground(background, resourceConstructed);
    }

    protected StateForeground(
            StateBackground background,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        super(ID.Foreground, background);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();  // Will be balanced in onLeave().
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
    }

    @Override
    public Optional<State> processPause() {
        return Optional.of((State) StateBackground.from(this, mResourceConstructed));
    }

    @Override
    public Optional<State> processOnSurfaceTextureAvailable(SurfaceTexture surfaceTexture) {
        return Optional.of((State) StateForegroundWithSurfaceTexture.from(
                this, mResourceConstructed, surfaceTexture));
    }
}