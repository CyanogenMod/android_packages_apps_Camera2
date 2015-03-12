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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.ButtonManager;
import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.module.ModuleController;
import com.android.camera.one.OneCameraOpener;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.settings.ResolutionSetting;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.AbstractTutorialOverlay;
import com.android.camera.ui.PreviewStatusListener;

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
     * @return the {@link android.content.Context} being used.
     */
    public Context getAndroidContext();

    /** @return the camera feature configuration for the device. */
    public OneCameraFeatureConfig getCameraFeatureConfig();

    /**
     * Creates a new dialog which can be shown in the app.
     *
     * @return  {@link android.app.Dialog} of the app.
     */
    public Dialog createDialog();

    /**
     * @return a String scope uniquely identifing the current module.
     */
    public String getModuleScope();

    /**
     * @return a String scope uniquely identifing the current camera id.
     */
    public String getCameraScope();

    /**
     * Starts an activity.
     *
     * @param intent Used to start the activity.
     */
    public void launchActivityByIntent(Intent intent);

    /**
     * See {@link Activity#openContextMenu(View)}
     */
    public void openContextMenu(View view);

    /**
     * See {@link Activity#registerForContextMenu(View)}
     */
    public void registerForContextMenu(View view);

    /**
     * Returns whether the app is currently paused.
     */
    public boolean isPaused();

    /**
     * Returns the current module controller.
     */
    public ModuleController getCurrentModuleController();

    /**
     * Returns the currently active module index.
     */
    public int getCurrentModuleIndex();

    /**
     * Returns the module ID for a specific mode.
     */
    public int getModuleId(int modeIndex);

    /**
     * Gets the mode that can be switched to from the given mode id through
     * quick switch.
     *
     * @param currentModuleIndex index of the current mode
     * @return mode id to quick switch to if index is valid, otherwise returns
     *         the given mode id itself
     */
    public int getQuickSwitchToModuleId(int currentModuleIndex);

    /**
     * Based on a mode switcher index, choose the correct module index.
     *
     * @param modeIndex mode switcher index.
     * @return module index.
     */
    public int getPreferredChildModeIndex(int modeIndex);

    /**
     * This gets called when mode is changed.
     *
     * @param moduleIndex index of the new module to switch to
     */
    public void onModeSelected(int moduleIndex);

    /**
     * This gets called when settings is selected and settings dialog needs to open.
     */
    public void onSettingsSelected();

    /********************** UI / Camera preview **********************/


    /**
     * Freeze what is currently shown on screen until the next preview frame comes
     * in. This can be used for camera switch to hide the UI changes underneath
     * until preview is ready.
     */
    public void freezeScreenUntilPreviewReady();

    /**
     * Returns the {@link android.graphics.SurfaceTexture} used by the preview
     * UI.
     */
    public SurfaceTexture getPreviewBuffer();

    /**
     * Gets called from module when preview is ready to start.
     */
    public void onPreviewReadyToStart();

    /**
     * Gets called from module when preview is started.
     */
    public void onPreviewStarted();

    /**
     * Adds a listener to receive callbacks when preview area changes.
     */
    public void addPreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener);

    /**
     * Removes a listener that receives callbacks when preview area changes.
     */
    public void removePreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener);

    /**
     * Sets up one shot preview callback in order to notify UI when the next
     * preview frame comes in.
     */
    public void setupOneShotPreviewListener();

    /**
     * Gets called from module when preview aspect ratio has changed.
     *
     * @param aspectRatio aspect ratio of preview stream
     */
    public void updatePreviewAspectRatio(float aspectRatio);

    /**
     * Gets called from module when the module needs to change the transform
     * matrix of the preview TextureView. It does not modify the matrix before
     * applying it.
     *
     * @param matrix transform matrix to be set on preview TextureView
     * @param aspectRatio the desired aspect ratio of the preview
     */
    public void updatePreviewTransformFullscreen(Matrix matrix, float aspectRatio);

    /**
     * Call this to find the full rect available for a full screen preview
     *
     * @return the rect of the full screen minus any decor.
     */
    public RectF getFullscreenRect();

    /**
     * Gets called from module when the module needs to change the transform
     * matrix of the preview TextureView. It is encouraged to use
     * {@link #updatePreviewAspectRatio(float)} over this function, unless the
     * module needs to rotate the surface texture using transform matrix.
     *
     * @param matrix transform matrix to be set on preview TextureView
     */
    public void updatePreviewTransform(Matrix matrix);

    /**
     * Sets the preview status listener, which will get notified when TextureView
     * surface has changed
     *
     * @param previewStatusListener the listener to get callbacks
     */
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener);

    /**
     * Returns the {@link android.widget.FrameLayout} as the root of the module
     * layout.
     */
    public FrameLayout getModuleLayoutRoot();

    /**
     * Locks the system orientation.
     */
    public void lockOrientation();

    /**
     * Unlocks the system orientation.
     */
    public void unlockOrientation();

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
     * Starts flash animation with optional shorter flash.
     *
     * @param shortFlash true for shorter flash (faster cameras).
     */
    public void startFlashAnimation(boolean shortFlash);

    /**
     * Starts normal pre-capture animation.
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
    public void cancelPostCaptureAnimation();

    /********************** Media saving **********************/

    /**
     * Notifies the app of the newly captured media.
     */
    public void notifyNewMedia(Uri uri);

    /********************** App-level resources **********************/

    /**
     * Keeps the screen turned on.
     *
     * @param enabled Whether to keep the screen on.
     */
    public void enableKeepScreenOn(boolean enabled);

    /**
     * Returns the {@link com.android.camera.app.CameraProvider}.
     */
    public CameraProvider getCameraProvider();

    /**
     * Returns the new camera API manager.
     */
    public OneCameraOpener getCameraOpener();

    /**
     * Returns the {@link OrientationManagerImpl}.
     *
     * @return {@code null} if not available yet.
     */
    public OrientationManager getOrientationManager();

    /**
     * Returns the {@link LocationManager}.
     *
     * @return {@code null} if not available yet.
     */
    public LocationManager getLocationManager();

    /**
     * Returns the {@link SettingsManager}.
     */
    public SettingsManager getSettingsManager();

    /**
     * Returns the {@link com.android.camera.settings.ResolutionSetting}.
     *
     * @return the current resolution setting.
     */
    public ResolutionSetting getResolutionSetting();

    /**
     * @return Common services and functionality to be shared.
     */
    public CameraServices getServices();

    /**
     * @return The error handler to invoke for errors.
     */
    public FatalErrorHandler getFatalErrorHandler();

    /**
     * Returns the {@link com.android.camera.app.CameraAppUI}.
     *
     * @return {@code null} if not available yet.
     */
    public CameraAppUI getCameraAppUI();

    /**
     * Returns the {@link com.android.camera.app.ModuleManager}.
     *
     * @return {@code null} if not available yet.
     */
    public ModuleManager getModuleManager();

    /**
     * Returns the {@link com.android.camera.ButtonManager}.
     */
    public ButtonManager getButtonManager();

    /** Returns a sound player that can be used to play custom sounds. */
    public SoundPlayer getSoundPlayer();

    /** Whether auto-rotate is enabled.*/
    public boolean isAutoRotateScreen();

    /**
     * Shows the given tutorial overlay.
     */
    public void showTutorial(AbstractTutorialOverlay tutorial);

    /**
     * Finishes the activity since the intent is completed successfully.
     *
     * @param resultIntent The intent that carries the result.
     */
    public void finishActivityWithIntentCompleted(Intent resultIntent);

    /**
     * Finishes the activity since the intent got canceled.
     */
    public void finishActivityWithIntentCanceled();
}
