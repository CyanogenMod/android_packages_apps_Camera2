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

import com.android.camera.CaptureModuleUtil;
import com.android.camera.app.AppController;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;

import android.graphics.SurfaceTexture;

import java.util.List;

/**
 * Represents a state that the surface texture is available to the module.
 */
public final class StateForegroundWithSurfaceTexture extends State {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;

    // Used to transition from Foreground on processOnSurfaceTextureAvailable.
    public static StateForegroundWithSurfaceTexture from(
            StateForeground foreground,
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture,
            OneCamera.OpenCallback cameraOpenCallback) {
        return new StateForegroundWithSurfaceTexture(
                foreground,
                resourceConstructed,
                surfaceTexture,
                new PreviewTransformCalculator(resourceConstructed.get().getOrientationManager()),
                cameraOpenCallback,
                resourceConstructed.get().getAppController());
    }

    private StateForegroundWithSurfaceTexture(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed,
            SurfaceTexture surfaceTexture,
            PreviewTransformCalculator previewTransformCalculator,
            OneCamera.OpenCallback cameraOpenCallback,
            AppController appController) {
        super(ID.ForegroundWithSurfaceTexture, previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in onLeave().
        mResourceSurfaceTexture = ResourceSurfaceTexture.create(
                surfaceTexture, previewTransformCalculator, cameraOpenCallback, appController);
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
    }

    @Override
    public Optional<State> onEnter() {
        try {
            // Pick a preview size with the right aspect ratio.
            final OneCamera.Facing cameraFacing =
                    mResourceConstructed.get().getCameraFacingSetting().getCameraFacing();
            final OneCameraCharacteristics characteristics =
                    mResourceConstructed.get().getCameraManager().getCameraCharacteristics(
                            cameraFacing);
            return Optional.of((State) StateOpeningCamera.from(this, mResourceConstructed,
                    mResourceSurfaceTexture, cameraFacing, characteristics));
        } catch (OneCameraAccessException ex) {
            return Optional.of((State) StateFatal.from(this, mResourceConstructed));
        }
    }
}
