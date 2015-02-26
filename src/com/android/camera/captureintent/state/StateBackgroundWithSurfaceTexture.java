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

import com.android.camera.app.AppController;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.PreviewTransformCalculator;

import android.graphics.SurfaceTexture;

/**
 * Represents a state that module is inactive in background but surface texture
 * is available.
 * <p>
 * Module is in this state when first run dialog is still presented. The module
 * will be resumed after people finish first run dialog (b/19531554).
 */
public class StateBackgroundWithSurfaceTexture extends State {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;

    // Used to transition from Foreground on processOnSurfaceTextureAvailable.
    public static StateBackgroundWithSurfaceTexture from(
            StateBackground background,
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture) {
        return new StateBackgroundWithSurfaceTexture(
                background,
                resourceConstructed,
                surfaceTexture,
                new PreviewTransformCalculator(resourceConstructed.get().getOrientationManager()),
                resourceConstructed.get().getAppController());
    }

    private StateBackgroundWithSurfaceTexture(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            AppController appController) {
        super(State.ID.BackgroundWithSurfaceTexture, previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in onLeave().
        mResourceSurfaceTexture = ResourceSurfaceTexture.create(
                surfaceTexture, previewTransformCalculator, appController);
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

    @Override
    public Optional<State> processResume() {
        return Optional.of((State) StateForegroundWithSurfaceTexture.from(
                this, mResourceConstructed, mResourceSurfaceTexture));
    }
}
