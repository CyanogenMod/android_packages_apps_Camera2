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

package com.android.camera.module;

import com.android.camera.CameraActivity;
import com.android.camera.ShutterButton;
import com.android.camera.app.CameraAppUI.BottomBarUISpec;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.settings.SettingsManager;
import com.android.ex.camera2.portability.CameraAgent;

/**
 * The controller at app level.
 */
public interface ModuleController extends ShutterButton.OnShutterButtonListener {
    /** Preview is fully visible. */
    public static final int VISIBILITY_VISIBLE = 0;
    /** Preview is covered by e.g. the transparent mode drawer. */
    public static final int VISIBILITY_COVERED = 1;
    /** Preview is fully hidden, e.g. by the filmstrip. */
    public static final int VISIBILITY_HIDDEN = 2;

    /********************** Life cycle management **********************/

    /**
     * Initializes the module.
     *
     * @param activity The camera activity.
     * @param isSecureCamera Whether the app is in secure camera mode.
     * @param isCaptureIntent Whether the app is in capture intent mode.
     */
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent);

    /**
     * Resumes the module. Always call this method whenever it's being put in
     * the foreground.
     */
    public void resume();

    /**
     * Pauses the module. Always call this method whenever it's being put in the
     * background.
     */
    public void pause();

    /**
     * Destroys the module. Always call this method to release the resources used
     * by this module.
     */
    public void destroy();

    /********************** UI / Camera preview **********************/

    /**
     * Called when the preview becomes visible/invisible.
     *
     * @param visible Whether the preview is visible, one of
     *            {@link #VISIBILITY_VISIBLE}, {@link #VISIBILITY_COVERED},
     *            {@link #VISIBILITY_HIDDEN}
     */
    public void onPreviewVisibilityChanged(int visibility);

    /**
     * Called when the framework layout orientation changed.
     *
     * @param isLandscape Whether the new orientation is landscape or portrait.
     */
    public void onLayoutOrientationChanged(boolean isLandscape);

    /**
     * Called when back key is pressed.
     *
     * @return Whether the back key event is processed.
     */
    public abstract boolean onBackPressed();

    /********************** App-level resources **********************/

    /**
     * Called by the app when the camera is available. The module should use
     * {@link com.android.camera.app.AppController#}
     *
     * @param cameraProxy The camera device proxy.
     */
    public void onCameraAvailable(CameraAgent.CameraProxy cameraProxy);

    /**
     * Called by the app on startup or module switches, this allows the module
     * to perform a hard reset on specific settings.
     */
    public void hardResetSettings(SettingsManager settingsManager);

    /**
     * Returns a {@link com.android.camera.hardware.HardwareSpec}
     * based on the module's open camera device.
     */
    public HardwareSpec getHardwareSpec();

    /**
     * Returns a {@link com.android.camera.app.CameraAppUI.BottomBarUISpec}
     * which represents the module's ideal bottom bar layout of the
     * mode options.  The app edits the final layout based on the
     * {@link com.android.camera.hardware.HardwareSpec}.
     */
    public BottomBarUISpec getBottomBarSpec();

    /**
     * Used by the app on configuring the bottom bar color and visibility.
     */
    // Necessary because not all modules have a bottom bar.
    // TODO: once all modules use the generic module UI, move this
    // logic into the app.
    public boolean isUsingBottomBar();
}
