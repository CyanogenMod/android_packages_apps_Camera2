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

package com.android.camera.captureintent.resource;

import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.async.SafeCloseable;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.ui.focus.FocusController;

import android.media.MediaActionSound;

import javax.annotation.Nullable;

/**
 * Defines an interface that any implementation of this should retain necessary
 * resources to capture a photo.
 */
public interface ResourceCaptureTools extends SafeCloseable {
    public static interface CaptureLoggingInfo {
        public @Nullable TouchCoordinate getTouchPointInsideShutterButton();
        public int getCountDownDuration();
    }

    /**
     * Sends a photo capture request to the underlying camera system
     * immediately.
     *
     * @param pictureCallback A {@link com.android.camera.one.OneCamera.PictureCallback}.
     * @param captureLoggingInfo A {@link CaptureLoggingInfo}.
     */
    void takePictureNow(
            OneCamera.PictureCallback pictureCallback,
            CaptureLoggingInfo captureLoggingInfo);

    /**
     * Plays the sound for a specific remaining second when counting down.
     *
     * @param remainingSeconds The remaining seconds.
     */
    void playCountDownSound(int remainingSeconds);

    /**
     * Obtains the associated @{link ResourceConstructed}.
     *
     * @return A ref counted ResourceConstructed object.
     */
    RefCountBase<ResourceConstructed> getResourceConstructed();

    /**
     * Obtains the associated @{link ResourceSurfaceTexture}.
     *
     * @return A ref counted ResourceSurfaceTexture object.
     */
    RefCountBase<ResourceSurfaceTexture> getResourceSurfaceTexture();

    /**
     * Obtains the associated @{link ResourceOpenedCamera}.
     *
     * @return A ref counted ResourceOpenedCamera object.
     */
    RefCountBase<ResourceOpenedCamera> getResourceOpenedCamera();

    /**
     * Obtains the capture session manager to start a new capture.
     *
     * @return A {@link com.android.camera.session.CaptureSessionManager} object.
     */
    CaptureSessionManager getCaptureSessionManager();

    /**
     * Obtains the focus controller to control focus ring.
     *
     * @return A {@link com.android.camera.ui.focus.FocusController} object.
     */
    FocusController getFocusController();

    /**
     * Obtains the media action sound.
     *
     * @return A {@link android.media.MediaActionSound} object.
     */
    MediaActionSound getMediaActionSound();

    /**
     * Obtains the main thread.
     *
     * @return A {@link com.android.camera.async.MainThread} object.
     */
    MainThread getMainThread();

    /**
     * Obtains the UI object associated with this module.
     *
     * @return A {@link com.android.camera.captureintent.CaptureIntentModuleUI}
     *         object.
     */
    CaptureIntentModuleUI getModuleUI();

    /**
     * Obtains the opened camera.
     *
     * @return A {@link com.android.camera.one.OneCamera} object.
     */
    OneCamera getCamera();
}
