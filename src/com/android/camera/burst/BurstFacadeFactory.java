/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.burst;

import android.content.Context;
import android.graphics.SurfaceTexture;

import com.android.camera.app.OrientationManager;
import com.android.camera.gl.FrameDistributorWrapper;
import com.android.camera.gl.SurfaceTextureConsumer;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;

import java.io.File;

/**
 * Factory for creating burst manager objects.
 */
public class BurstFacadeFactory {
    private BurstFacadeFactory() {
        /* cannot be instantiated */
    }

    /**
     * An empty burst manager that is instantiated when burst is not supported.
     */
    private static class BurstFacadeStub implements BurstFacade {
        @Override
        public void onCameraAttached(OneCamera camera) {
        }

        @Override
        public void onCameraDetached() {
        }

        @Override
        public void startBurst(CaptureSession captureSession, File tempSessionDirectory) {
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean stopBurst() {
            return false;
        }

        @Override
        public void setSurfaceTexture(SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public void initializeSurfaceTextureConsumer(int surfaceWidth, int surfaceHeight) {
        }

        @Override
        public void initializeSurfaceTextureConsumer(SurfaceTexture surfaceTexture, int
                surfaceWidth, int surfaceHeight) {
        }

        @Override
        public void updatePreviewBufferSize(int width, int height) {
        }

        @Override
        public void initializeAndStartFrameDistributor() {
        }

        @Override
        public void closeFrameDistributor() {
        }

        @Override
        public SurfaceTexture getInputSurfaceTexture() {
            return null;
        }

        @Override
        public void setPreviewConsumerSize(int width, int height) {
        }
    }

    /**
     * Creates and returns an instance of {@link BurstFacade}
     *
     * @param appContext the Android application context which is passes through
     *            to the burst controller.
     * @param orientationManager for querying orientation of burst.
     * @param readyStateListener gets called when the ready state of Burst
     *            changes.
     */
    public static BurstFacade create(Context appContext, OrientationManager orientationManager,
            BurstReadyStateChangeListener readyStateListener) {
        if (BurstControllerImpl.isBurstModeSupported(appContext)) {
            return new BurstFacadeImpl(appContext, orientationManager, readyStateListener,
                    new FrameDistributorWrapper(), new SurfaceTextureConsumer());
        } else {
            // Burst is not supported return a stub instance.
            return new BurstFacadeStub();
        }
    }
}
