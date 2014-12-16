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

package com.android.camera.app;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.view.ViewGroup;

import com.android.camera.debug.Log;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.Keys;
import com.android.camera.settings.ResolutionUtil;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;
import com.android.camera.widget.AspectRatioDialogLayout;
import com.android.camera.widget.LocationDialogLayout;

import java.util.List;

/**
 * The dialog to show when users open the app for the first time.
 */
public class FirstRunDialog {

    public interface FirstRunDialogListener {

        public void onLocationPreferenceConfirmed(boolean locationRecordingEnabled);

        public void onAspectRatioPreferenceConfirmed(Rational chosenAspectRatio);
    }

    private static final Log.Tag TAG = new Log.Tag("FirstRunDialog");

    /** The default preference of aspect ratio. */
    private static final Rational DEFAULT_ASPECT_RATIO = ResolutionUtil.ASPECT_RATIO_4x3;

    /** The default preference of whether enabling location recording. */
    private static final boolean DEFAULT_LOCATION_RECORDING_ENABLED = true;

    /** The app controller. */
    private final AppController mAppController;

    /** The camera manager used to query camera characteristics. */
    private final OneCameraManager mCameraManager;

    /** Aspect ratio preference dialog */
    private Dialog mAspectRatioPreferenceDialog;

    /** Location preference dialog */
    private Dialog mLocationPreferenceDialog;

    /** Listener to receive events. */
    private FirstRunDialogListener mListener;

    /**
     * Constructs a first run dialog.
     *
     * @param appController The app controller.
     * @param cameraManager The camera manager used to query supported aspect
     *            ratio by camera devices.
     */
    public FirstRunDialog(AppController appController, OneCameraManager cameraManager) {
        mAppController = appController;
        mCameraManager = cameraManager;
    }

    /**
     * Set a dialog listener.
     *
     * @param listener The dialog listener to be set.
     */
    public void setListener(FirstRunDialogListener listener) {
        mListener = listener;
    }

    /**
     * Whether first run dialogs should be presented to the user.
     *
     * @return Whether first run dialogs should be presented to the user.
     */
    public boolean shouldShow() {
        return shouldShowAspectRatioPreferenceDialog() || shouldShowLocationPreferenceDialog();
    }

    /**
     * Shows first run dialogs if necessary.
     *
     * @return Whether first run dialogs are shown.
     */
    public boolean show() {
        // When people open the app for the first time, prompt two dialogs to
        // ask preferences about
        // location and aspect ratio.
        if (promptLocationPreferenceDialog()) {
            return true;
        } else {
            // This should be a rare case because location and aspect ratio
            // preferences usually got
            // set at the same time when people open the app for the first time.
            return promptAspectRatioPreferenceDialog();
        }
    }

    /**
     * Dismiss all shown dialogs.
     */
    public void dismiss() {
        if (mAspectRatioPreferenceDialog != null) {
            mAspectRatioPreferenceDialog.dismiss();
        }
        if (mLocationPreferenceDialog != null) {
            mLocationPreferenceDialog.dismiss();
        }
    }

    /**
     * Whether a aspect ratio dialog should be presented to the user.
     *
     * @return Whether a aspect ratio dialog should be presented to the user.
     */
    private boolean shouldShowAspectRatioPreferenceDialog() {
        final SettingsManager settingsManager = mAppController.getSettingsManager();
        final boolean isAspectRatioPreferenceSet = settingsManager.getBoolean(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_USER_SELECTED_ASPECT_RATIO);
        return ApiHelper.shouldShowAspectRatioDialog() && !isAspectRatioPreferenceSet;
    }

    /**
     * Prompts a dialog to allow people to choose aspect ratio preference when
     * people open the app for the first time. If the preference has been set,
     * this will return false.
     *
     * @return Whether the dialog will be prompted or not.
     */
    private boolean promptAspectRatioPreferenceDialog() {
        // Do nothing if the preference is already set.
        if (!shouldShowAspectRatioPreferenceDialog()) {
            return false;
        }

        // Create a content view for the dialog.
        final AspectRatioDialogLayout dialogLayout = new AspectRatioDialogLayout(
                mAppController.getAndroidContext(), DEFAULT_ASPECT_RATIO);
        dialogLayout.setListener(new AspectRatioDialogLayout.AspectRatioDialogListener() {
            @Override
            public void onConfirm(Rational aspectRatio) {
                try {
                    final SettingsManager settingsManager =
                            mAppController.getSettingsManager();

                    // Save the picture size setting for back camera.
                    OneCameraCharacteristics backCameraChars =
                            mCameraManager.getCameraCharacteristics(OneCamera.Facing.BACK);
                    List<Size> backCameraPictureSizes =
                            backCameraChars.getSupportedPictureSizes(ImageFormat.JPEG);
                    Size backCameraChosenPictureSize =
                            ResolutionUtil.getLargestPictureSize(
                                    aspectRatio, backCameraPictureSizes);
                    settingsManager.set(
                            SettingsManager.SCOPE_GLOBAL,
                            Keys.KEY_PICTURE_SIZE_BACK,
                            SettingsUtil.sizeToSettingString(backCameraChosenPictureSize));

                    // Save the picture size setting for front camera.
                    OneCameraCharacteristics frontCameraChars =
                            mCameraManager.getCameraCharacteristics(OneCamera.Facing.FRONT);
                    List<Size> frontCameraPictureSizes =
                            frontCameraChars.getSupportedPictureSizes(ImageFormat.JPEG);
                    Size frontCameraChosenPictureSize =
                            ResolutionUtil.getLargestPictureSize(
                                    aspectRatio, frontCameraPictureSizes);
                    settingsManager.set(
                            SettingsManager.SCOPE_GLOBAL,
                            Keys.KEY_PICTURE_SIZE_FRONT,
                            SettingsUtil.sizeToSettingString(frontCameraChosenPictureSize));

                    // Indicate the aspect ratio is selected.
                    settingsManager.set(
                            SettingsManager.SCOPE_GLOBAL,
                            Keys.KEY_USER_SELECTED_ASPECT_RATIO,
                            true);
                } catch (OneCameraAccessException ex) {
                    throw new RuntimeException(ex);
                }

                mListener.onAspectRatioPreferenceConfirmed(aspectRatio);
            }
        });

        // Create the dialog.
        mAspectRatioPreferenceDialog = mAppController.createDialog();
        mAspectRatioPreferenceDialog.setContentView(dialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mAspectRatioPreferenceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mAspectRatioPreferenceDialog = null;
            }
        });

        // Show the dialog.
        mAspectRatioPreferenceDialog.show();
        return true;
    }

    /**
     * Whether a location dialog should be presented to the user.
     *
     * @return Whether a location dialog should be presented to the user.
     */
    private boolean shouldShowLocationPreferenceDialog() {
        final SettingsManager settingsManager = mAppController.getSettingsManager();
        return !settingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION);
    }

    /**
     * Prompts a dialog to allow people to choose location preference when
     * people open the app for the first time. If the preference has been set,
     * this will return false.
     *
     * @return Whether the dialog will be prompted or not.
     */
    private boolean promptLocationPreferenceDialog() {
        // Do nothing if the preference is already set.
        if (!shouldShowLocationPreferenceDialog()) {
            return false;
        }

        // Create a content view for the dialog.
        final LocationDialogLayout dialogLayout = new LocationDialogLayout(
                mAppController.getAndroidContext(), DEFAULT_LOCATION_RECORDING_ENABLED);
        dialogLayout.setListener(new LocationDialogLayout.LocationDialogListener() {
            @Override
            public void onConfirm(boolean locationRecordingEnabled) {
                mAppController.getSettingsManager().set(
                        SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_RECORD_LOCATION,
                        locationRecordingEnabled);
                mAppController.getLocationManager().recordLocation(
                        locationRecordingEnabled);

                mListener.onLocationPreferenceConfirmed(locationRecordingEnabled);

                promptAspectRatioPreferenceDialog();
            }
        });

        // Create the dialog.
        mLocationPreferenceDialog = mAppController.createDialog();
        mLocationPreferenceDialog.setContentView(dialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLocationPreferenceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mLocationPreferenceDialog = null;
            }
        });

        // Show the dialog.
        mLocationPreferenceDialog.show();
        return true;
    }
}
