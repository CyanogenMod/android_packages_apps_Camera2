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
import android.view.Surface;

import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.session.CaptureSession;

/**
 * Factory for creating burst manager objects.
 */
public class BurstFacadeFactory {
    private BurstFacadeFactory() {
        /* cannot be instantiated */
    }

    /**
     * An empty burst manager that is instantiated when burst is not supported.
     * <p>
     * It keeps a hold of the current surface texture so it can be used when
     * burst is not enabled.
     */
    public static class BurstFacadeStub implements BurstFacade {
        @Override
        public void startBurst(CaptureSession.CaptureSessionCreator captureSessionCreator,
                DeviceOrientation deviceOrientation, Facing cameraFacing,
                int imageOrientationDegrees) {
        }

        @Override
        public boolean stopBurst() {
            return false;
        }

        @Override
        public void initialize(SurfaceTexture surfaceTexture) {}

        @Override
        public void release() {}

        @Override
        public Surface getInputSurface() {
            return null;
        }

        @Override
        public void setBurstTaker(BurstTaker burstTaker) {}
    }

    /**
     * Creates and returns an instance of {@link BurstFacade}
     *
     * @param appContext the Android application context which is passes through
     *            to the burst controller.
     * @param orientationController for locking orientation when burst is running.
     * @param readyStateListener gets called when the ready state of Burst
     *            changes.
     */
    public static BurstFacade create(Context appContext,
            OrientationLockController orientationController,
            BurstReadyStateChangeListener readyStateListener) {
        if (BurstControllerImpl.isBurstModeSupported(appContext.getContentResolver())) {
            BurstFacade burstController = new BurstFacadeImpl(appContext, orientationController,
                    readyStateListener);
            ToastingBurstFacadeDecorator.BurstToaster toaster =
                    new ToastingBurstFacadeDecorator.BurstToaster(appContext);
            return new ToastingBurstFacadeDecorator(burstController, toaster);
        } else {
            // Burst is not supported return a stub instance.
            return new BurstFacadeStub();
        }
    }
}
