/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.widget.FrameLayout;

import com.android.camera.CameraManager;
import com.android.camera.LocationManager;

/**
 * The controller at app level.
 */
public interface AppController {

    /**
     * An interface which defines the shutter events listener.
     */
    public interface ShutterEventsListener {
        /**
         * Called when the shutter state is changed to pressed.
         */
        public void onShutterPressed();

        /**
         * Called when the shutter state is changed to released.
         */
        public void onShutterReleased();

        /**
         * Called when the shutter is clicked.
         */
        public void onShutterClicked();

        /**
         * Called when the shutter is long pressed.
         */
        public void onShutterLongPressed();
    }

    /**
     * Returns the {@link android.content.Context} being used.
     */
    public Context getAndroidContext();

    /********************** UI / Camera preview**********************/

    /**
     * Returns the {@link android.graphics.SurfaceTexture} used by the preview
     * UI.
     */
    public SurfaceTexture getPreviewBuffer();

    /**
     * Returns the {@link android.widget.FrameLayout} as the root of the module
     * layout.
     */
    public FrameLayout getModuleLayoutRoot();

    /********************** Shutter button  **********************/

    /**
     * Sets the shutter events listener.
     *
     * @param listener The listener.
     */
    public void setShutterEventsListener(ShutterEventsListener listener);

    /**
     * Enables/Disables the shutter.
     */
    public void setShutterEnabled(boolean enabled);

    /**
     * Checks whether the shutter is enabled.
     */
    public boolean isShutterEnabled();

    /********************** Capture animation **********************/

    /**
     * Starts the pre-capture animation.
     */
    public void startPreCaptureAnimation();

    /**
     * Cancels the pre-capture animation.
     */
    public void cancelPreCaptureAnimation();

    /**
     * Starts the post-capture animation with the current preview image.
     */
    public void startPostCaptureAnimation();

    /**
     * Starts the post-capture animation with the given thumbnail.
     *
     * @param thumbnail The thumbnail for the animation.
     */
    public void startPostCaptureAnimation(Bitmap thumbnail);

    /**
     * Cancels the post-capture animation.
     */
    public void cancelPostCaptureAnimatio();

    /********************** Media saving **********************/

    /**
     * Adds a new media to the filmstrip.
     */
    public void addNewMediaToFilmstrip(Uri uri);

    /********************** App-level resources **********************/

    /**
     * Returns the camera device proxy.
     *
     * @return {@code null} if not available yet.
     */
    public CameraManager.CameraProxy getCameraProxy();

    /**
     * Returns the {@link com.android.camera.app.MediaSaver}.
     *
     * @return {@code null} if not available yet.
     */
    public MediaSaver getMediaSaver();

    /**
     * Returns the {@link com.android.camera.app.OrientationManager}.
     *
     * @return {@code null} if not available yet.
     */
    public OrientationManager getOrientationManager();

    /**
     * Returns the {@link com.android.camera.LocationManager}.
     *
     * @return {@code null} if not available yet.
     */
    public LocationManager getLocationManager();
}
