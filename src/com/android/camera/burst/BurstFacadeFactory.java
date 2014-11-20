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

import android.content.ContentResolver;

import com.android.camera.app.AppController;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.OrientationManager;
import com.android.camera.gl.FrameDistributor;
import com.android.camera.gl.FrameDistributor.FrameConsumer;
import com.android.camera.one.OneCamera;

import java.io.File;

/**
 * Factory for creating burst manager objects.
 */
public class BurstFacadeFactory {
    private BurstFacadeFactory() {/* cannot be instantiated */}

    /**
     * An empty burst manager that is instantiated when burst is not supported.
     */
    private static class BurstFacadeStub implements BurstFacade {
        @Override
        public void setContentResolver(ContentResolver contentResolver) {}

        @Override
        public void onCameraAttached(OneCamera camera) {}

        @Override
        public void onCameraDetached() {}

        @Override
        public FrameConsumer getPreviewFrameConsumer() {
            return new FrameConsumer() {

                @Override
                public void onStop() {}

                @Override
                public void onStart() {}

                @Override
                public void onNewFrameAvailable(FrameDistributor frameDistributor,
                        long timestampNs) {}
            };
        }

        @Override
        public void startBurst() {}

        @Override
        public boolean isBurstRunning() {
            return false;
        }

        @Override
        public void stopBurst() {}
    }

    /**
     * Creates and returns an instance of {@link BurstFacade}
     *
     * @param appController the app level controller for controlling the shutter
     *            button.
     * @param mediaSaver the {@link MediaSaver} instance for saving results of
     *            burst.
     * @param locationManager for querying location of burst.
     * @param orientationManager for querying orientation of burst.
     * @param debugDataDir the debug directory to use for burst.
     */
    public static BurstFacade create(AppController appController,
            MediaSaver mediaSaver,
            LocationManager locationManager,
            OrientationManager orientationManager,
            File debugDataDir) {
        if (BurstControllerImpl.isBurstModeSupported()) {
            return new BurstFacadeImpl(appController, mediaSaver,
                    locationManager, orientationManager,
                    debugDataDir);
        } else {
            // Burst is not supported return a stub instance.
            return new BurstFacadeStub();
        }
    }
}
