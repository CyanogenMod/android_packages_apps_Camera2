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
import android.content.Context;
import android.content.DialogInterface;
import android.view.ViewGroup;

import com.android.camera.device.CameraId;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.Keys;
import com.android.camera.settings.ResolutionSetting;
import com.android.camera.settings.ResolutionUtil;
import com.android.camera.settings.SettingsManager;
import com.android.camera.util.ApiHelper;
import com.android.camera.widget.AspectRatioDialogLayout;
import com.android.camera.widget.LocationDialogLayout;

/**
 * The dialog to show when users open the app for the first time.
 */
public class FirstRunDialog {

    public interface FirstRunDialogListener {
        public void onFirstRunStateReady();
        public void onFirstRunDialogCancelled();
        public void onCameraAccessException();
    }

    /** The default preference of aspect ratio. */
    private static final Rational DEFAULT_ASPECT_RATIO = ResolutionUtil.ASPECT_RATIO_4x3;

    /** The default preference of whether enabling location recording. */
    private static final boolean DEFAULT_LOCATION_RECORDING_ENABLED = true;

    /** Listener to receive events. */
    private final FirstRunDialogListener mListener;

    /** The app controller. */
    private final AppController mAppController;

    /** The hardware manager. */
    private final OneCameraManager mOneCameraManager;

    /** The app context. */
    private final Context mContext;

    /** The resolution settings. */
    private final ResolutionSetting mResolutionSetting;

    /** The settings manager. */
    private final SettingsManager mSettingsManager;

    /** Aspect ratio preference dialog */
    private Dialog mAspectRatioPreferenceDialog;

    /** Location preference dialog */
    private Dialog mLocationPreferenceDialog;

    /**
     * Constructs a first run dialog.
     *
     */
    public FirstRunDialog(
          AppController appController,
          Context androidContext,
          ResolutionSetting resolutionSetting,
          SettingsManager settingManager,
          OneCameraManager hardwareManager,
          FirstRunDialogListener listener) {
        mAppController = appController;
        mContext = androidContext;
        mResolutionSetting = resolutionSetting;
        mSettingsManager = settingManager;
        mOneCameraManager = hardwareManager;
        mListener = listener;
    }

    /**
     * Shows first run dialogs if necessary.
     */
    public void showIfNecessary() {
        if (shouldShowLocationDialog()) {
            // When people open the app for the first time, prompt two dialogs to
            // ask preferences about location and aspect ratio. The first dialog is
            // location reference.
            promptLocationPreferenceDialog();
        } else if (shouldShowAspectRatioDialog()) {
            /**
             * If people already set location preference, prompt aspect ratio dialog.
             */
            promptAspectRatioPreferenceDialog();
        } else {
            mListener.onFirstRunStateReady();
        }
    }

    /**
     * Dismiss all shown dialogs.
     */
    public void dismiss() {
        if (mAspectRatioPreferenceDialog != null) {
            // Remove the listener since we actively dismiss the dialog.
            mAspectRatioPreferenceDialog.setOnDismissListener(null);
            mAspectRatioPreferenceDialog.dismiss();
            mAspectRatioPreferenceDialog = null;
        }
        if (mLocationPreferenceDialog != null) {
            // Remove the listener since we actively dismiss the dialog.
            mLocationPreferenceDialog.setOnDismissListener(null);
            mLocationPreferenceDialog.dismiss();
            mLocationPreferenceDialog = null;
        }
    }

    /**
     * Whether first run dialogs should be presented to the user.
     *
     * @return Whether first run dialogs should be presented to the user.
     */
    private boolean shouldShowLocationDialog() {
        return !mSettingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION);
    }

    private boolean shouldShowAspectRatioDialog() {
        return mAppController.getCameraAppUI().shouldShowAspectRatioDialog();
    }

    /**
     * Prompts a dialog to allow people to choose aspect ratio preference when
     * people open the app for the first time. If the preference has been set,
     * this will return false.
     */
    private void promptAspectRatioPreferenceDialog() {
        // Create a content view for the dialog.
        final AspectRatioDialogLayout dialogLayout = new AspectRatioDialogLayout(
                mContext, DEFAULT_ASPECT_RATIO);
        dialogLayout.setListener(new AspectRatioDialogLayout.AspectRatioDialogListener() {
            @Override
            public void onConfirm(Rational aspectRatio) {
                // Change resolution setting based on the chosen aspect ratio.
                try {
                    CameraId backCameraId = mOneCameraManager.findFirstCameraFacing(Facing.BACK);
                    if (backCameraId != null) {
                        mResolutionSetting.setPictureAspectRatio(backCameraId, aspectRatio);
                    }
                    CameraId frontCameraId = mOneCameraManager.findFirstCameraFacing(Facing.FRONT);
                    if (frontCameraId != null) {
                        mResolutionSetting.setPictureAspectRatio(frontCameraId, aspectRatio);
                    }
                } catch (OneCameraAccessException ex) {
                    mListener.onCameraAccessException();
                    return;
                }

                // Mark that user has made the choice.
                mSettingsManager.set(
                        SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_USER_SELECTED_ASPECT_RATIO,
                        true);

                // Dismiss all dialogs.
                dismiss();

                // Notify that the app is ready to go.
                mListener.onFirstRunStateReady();
            }
        });

        // Create the dialog.
        mAspectRatioPreferenceDialog = mAppController.createDialog();
        mAspectRatioPreferenceDialog.setContentView(dialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Detect if the dialog is dismissed by back button.
        mAspectRatioPreferenceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mAspectRatioPreferenceDialog = null;
                dismiss();
                mListener.onFirstRunDialogCancelled();
            }
        });

        // Show the dialog.
        mAspectRatioPreferenceDialog.show();
    }

    /**
     * Prompts a dialog to allow people to choose location preference when
     * people open the app for the first time. If the preference has been set,
     * this will return false.
     */
    private void promptLocationPreferenceDialog() {
        // Create a content view for the dialog.
        final LocationDialogLayout dialogLayout = new LocationDialogLayout(
                mContext, DEFAULT_LOCATION_RECORDING_ENABLED);
        dialogLayout.setListener(new LocationDialogLayout.LocationDialogListener() {
            @Override
            public void onConfirm(boolean locationRecordingEnabled) {
                // Change the location preference setting.
                mSettingsManager.set(
                        SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_RECORD_LOCATION,
                        locationRecordingEnabled);

                if (shouldShowAspectRatioDialog()) {
                    // Prompt the second dialog about aspect ratio preference.
                    promptAspectRatioPreferenceDialog();
                } else {
                    // Dismiss all dialogs.
                    dismiss();
                    // Notify that the app is ready to go.
                    mListener.onFirstRunStateReady();
                }
            }
        });

        // Create the dialog.
        mLocationPreferenceDialog = mAppController.createDialog();
        mLocationPreferenceDialog.setContentView(dialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Detect if the dialog is dismissed by back button.
        mLocationPreferenceDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mLocationPreferenceDialog = null;
                dismiss();
                mListener.onFirstRunDialogCancelled();
            }
        });

        // Show the dialog.
        mLocationPreferenceDialog.show();
    }
}
