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

package com.android.camera.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.camera.CameraActivity;
import com.android.camera.app.AppController;
import com.android.camera.app.ModuleManagerImpl;
import com.android.camera.debug.Log;
import com.android.camera.module.ModuleController;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgentFactory;
import com.android.ex.camera2.portability.CameraDeviceInfo;
import com.android.ex.camera2.portability.Size;

import java.util.List;
import java.util.Map;

/**
 * Defines the general upgrade path for the app. Modules may define specific
 * upgrade logic, but upgrading for preferences across modules, CameraActivity
 * or application-wide can be added here.
 */
public class AppUpgrader extends SettingsUpgrader {
    private static final Log.Tag TAG = new Log.Tag("AppUpgrader");

    private static final String OLD_CAMERA_PREFERENCES_PREFIX = "_preferences_";
    private static final String OLD_MODULE_PREFERENCES_PREFIX = "_preferences_module_";
    private static final String OLD_GLOBAL_PREFERENCES_FILENAME = "_preferences_camera";

    /**
     * With this version everyone was forced to choose their location settings
     * again.
     */
    private static final int FORCE_LOCATION_CHOICE_VERSION = 2;

    /**
     * With this version, the camera size setting changed from a "small",
     * "medium" and "default" to strings representing the actual resolutions,
     * i.e. "1080x1776".
     */
    private static final int CAMERA_SIZE_SETTING_UPGRADE_VERSION = 3;

    /**
     * With this version, the names of the files storing camera specific and
     * module specific settings changed.
     */
    private static final int CAMERA_MODULE_SETTINGS_FILES_RENAMED_VERSION = 4;

    /**
     * With this version, timelapse mode was removed and mode indices need to be
     * resequenced.
     */
    private static final int CAMERA_SETTINGS_SELECTED_MODULE_INDEX = 5;

    /**
     * Increment this value whenever new AOSP UpgradeSteps need to be executed.
     */
    public static final int APP_UPGRADE_VERSION = 5;

    private final AppController mAppController;

    public AppUpgrader(final AppController appController) {
        super(Keys.KEY_UPGRADE_VERSION, APP_UPGRADE_VERSION);
        mAppController = appController;
    }

    @Override
    protected int getLastVersion(SettingsManager settingsManager) {
        // Prior appwide versions were stored in the default preferences. If
        // current
        // state indicates this is still the case, port the version and then
        // process
        // all other known app settings to the new SettingsManager string
        // scheme.
        try {
            return super.getLastVersion(settingsManager);
        } catch (ClassCastException e) {
            // We infer that a ClassCastException here means we have pre-String
            // settings that need to be upgraded, so we hack in a full upgrade
            // here.
            upgradeTypesToStrings(settingsManager);
            // Retrieve version as default now that we're sure it is converted
            return super.getLastVersion(settingsManager);
        }
    }

    @Override
    public void upgrade(SettingsManager settingsManager, int lastVersion, int currentVersion) {
        Context context = mAppController.getAndroidContext();

        if (lastVersion < FORCE_LOCATION_CHOICE_VERSION) {
            forceLocationChoice(settingsManager);
        }

        if (lastVersion < CAMERA_SIZE_SETTING_UPGRADE_VERSION) {
            CameraDeviceInfo infos = CameraAgentFactory
                    .getAndroidCameraAgent(context).getCameraDeviceInfo();
            upgradeCameraSizeSetting(settingsManager, context, infos,
                    SettingsUtil.CAMERA_FACING_FRONT);
            upgradeCameraSizeSetting(settingsManager, context, infos,
                    SettingsUtil.CAMERA_FACING_BACK);
        }

        if (lastVersion < CAMERA_MODULE_SETTINGS_FILES_RENAMED_VERSION) {
            upgradeCameraSettingsFiles(settingsManager, context);
            upgradeModuleSettingsFiles(settingsManager, context,
                    mAppController);
            settingsManager.remove(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_STARTUP_MODULE_INDEX);
        }

        if (lastVersion < CAMERA_SETTINGS_SELECTED_MODULE_INDEX) {
            upgradeSelectedModeIndex(settingsManager, context);
        }
    }

    /**
     * Converts settings that were stored in SharedPreferences as non-Strings,
     * to Strings. This is necessary due to a SettingsManager API refactoring.
     * Should only be executed if we detected a change in
     * Keys.KEY_UPGRADE_VERSION type from int to string; rerunning this on
     * string values will result in ClassCastExceptions when trying to retrieve
     * an int or boolean as a String.
     */
    private void upgradeTypesToStrings(SettingsManager settingsManager) {
        SharedPreferences defaultPreferences = settingsManager.getDefaultPreferences();
        SharedPreferences oldGlobalPreferences =
                settingsManager.openPreferences(OLD_GLOBAL_PREFERENCES_FILENAME);

        // Strict upgrade version: Integer -> String, from default.
        int strictUpgradeVersion = removeInteger(defaultPreferences, Keys.KEY_UPGRADE_VERSION);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_UPGRADE_VERSION,
                strictUpgradeVersion);

        // Location: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_RECORD_LOCATION)) {
            boolean location = removeBoolean(defaultPreferences, Keys.KEY_RECORD_LOCATION);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION, location);
        }

        // User selected aspect ratio: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_USER_SELECTED_ASPECT_RATIO)) {
            boolean userSelectedAspectRatio = removeBoolean(defaultPreferences,
                    Keys.KEY_USER_SELECTED_ASPECT_RATIO);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_USER_SELECTED_ASPECT_RATIO,
                    userSelectedAspectRatio);
        }

        // Manual exposure compensation: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_EXPOSURE_COMPENSATION_ENABLED)) {
            boolean manualExposureCompensationEnabled = removeBoolean(defaultPreferences,
                    Keys.KEY_EXPOSURE_COMPENSATION_ENABLED);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_EXPOSURE_COMPENSATION_ENABLED, manualExposureCompensationEnabled);
        }

        // Hint: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_CAMERA_FIRST_USE_HINT_SHOWN)) {
            boolean hint = removeBoolean(defaultPreferences, Keys.KEY_CAMERA_FIRST_USE_HINT_SHOWN);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_FIRST_USE_HINT_SHOWN,
                    hint);
        }

        // Startup module index: Integer -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_STARTUP_MODULE_INDEX)) {
            int startupModuleIndex = removeInteger(defaultPreferences,
                    Keys.KEY_STARTUP_MODULE_INDEX);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_STARTUP_MODULE_INDEX,
                    startupModuleIndex);
        }

        // Last camera used module index: Integer -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_CAMERA_MODULE_LAST_USED)) {
            int lastCameraUsedModuleIndex = removeInteger(defaultPreferences,
                    Keys.KEY_CAMERA_MODULE_LAST_USED);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_MODULE_LAST_USED,
                    lastCameraUsedModuleIndex);
        }

        // Flash supported back camera setting: boolean -> String, from old
        // global.
        if (oldGlobalPreferences.contains(Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA)) {
            boolean flashSupportedBackCamera = removeBoolean(oldGlobalPreferences,
                    Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA);
            if (flashSupportedBackCamera) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA, flashSupportedBackCamera);
            }
        }

        // Request return to HDR+: boolean -> String, from module.
        if (defaultPreferences.contains(Keys.KEY_REQUEST_RETURN_HDR_PLUS)) {
            boolean requestReturnHdrPlus = removeBoolean(defaultPreferences,
                    Keys.KEY_REQUEST_RETURN_HDR_PLUS);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_REQUEST_RETURN_HDR_PLUS,
                    requestReturnHdrPlus);
        }

        // Should show refocus viewer cling: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING)) {
            boolean shouldShowRefocusViewer = removeBoolean(defaultPreferences,
                    Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING, shouldShowRefocusViewer);
        }

        // Should show settings button cling: boolean -> String, from default.
        if (defaultPreferences.contains(Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING)) {
            boolean shouldShowSettingsButtonCling = removeBoolean(defaultPreferences,
                    Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING, shouldShowSettingsButtonCling);
        }
    }

    /**
     * Part of the AOSP upgrade path, forces the user to choose their location
     * again if it was originally set to false.
     */
    private void forceLocationChoice(SettingsManager settingsManager) {
        // Show the location dialog on upgrade if
        // (a) the user has never set this option (status quo).
        // (b) the user opt'ed out previously.
        if (settingsManager.isSet(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_RECORD_LOCATION)) {
            // Location is set in the source file defined for this setting.
            // Remove the setting if the value is false to launch the dialog.
            if (!settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_RECORD_LOCATION)) {
                settingsManager.remove(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION);
            }
        }
    }

    /**
     * Part of the AOSP upgrade path, sets front and back picture sizes.
     */
    private void upgradeCameraSizeSetting(SettingsManager settingsManager,
            Context context, CameraDeviceInfo infos,
            SettingsUtil.CameraDeviceSelector facing) {
        String key;
        if (facing == SettingsUtil.CAMERA_FACING_FRONT) {
            key = Keys.KEY_PICTURE_SIZE_FRONT;
        } else if (facing == SettingsUtil.CAMERA_FACING_BACK) {
            key = Keys.KEY_PICTURE_SIZE_BACK;
        } else {
            Log.w(TAG, "Ignoring attempt to upgrade size of unhandled camera facing direction");
            return;
        }

        String pictureSize = settingsManager.getString(SettingsManager.SCOPE_GLOBAL, key);
        int camera = SettingsUtil.getCameraId(infos, facing);
        if (camera != -1) {
            List<Size> supported = CameraPictureSizesCacher.getSizesForCamera(camera, context);
            if (supported != null) {
                Size size = SettingsUtil.getPhotoSize(pictureSize, supported, camera);
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, key,
                        SettingsUtil.sizeToSetting(size));
            }
        }
    }

    /**
     * Part of the AOSP upgrade path, copies all of the keys and values in a
     * SharedPreferences file to another SharedPreferences file, as Strings.
     */
    private void copyPreferences(SharedPreferences oldPrefs,
            SharedPreferences newPrefs) {
        Map<String, ?> entries = oldPrefs.getAll();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            newPrefs.edit().putString(key, value).apply();
        }
    }

    /**
     * Part of the AOSP upgrade path, copies all of the key and values in the
     * old camera SharedPreferences files to new files.
     */
    private void upgradeCameraSettingsFiles(SettingsManager settingsManager,
            Context context) {
        String[] cameraIds =
                context.getResources().getStringArray(R.array.camera_id_entryvalues);

        for (int i = 0; i < cameraIds.length; i++) {
            SharedPreferences oldCameraPreferences =
                    settingsManager.openPreferences(
                            OLD_CAMERA_PREFERENCES_PREFIX + cameraIds[i]);
            SharedPreferences newCameraPreferences =
                    settingsManager.openPreferences(CameraActivity.CAMERA_SCOPE_PREFIX
                            + cameraIds[i]);

            copyPreferences(oldCameraPreferences, newCameraPreferences);
        }
    }

    private void upgradeModuleSettingsFiles(SettingsManager settingsManager,
            Context context, AppController app) {
        int[] moduleIds = context.getResources().getIntArray(R.array.camera_modes);

        for (int i = 0; i < moduleIds.length; i++) {
            String moduleId = Integer.toString(moduleIds[i]);
            SharedPreferences oldModulePreferences =
                    settingsManager.openPreferences(
                            OLD_MODULE_PREFERENCES_PREFIX + moduleId);

            ModuleManagerImpl.ModuleAgent agent =
                    app.getModuleManager().getModuleAgent(moduleIds[i]);
            if (agent == null) {
                continue;
            }
            ModuleController module = agent.createModule(app);
            SharedPreferences newModulePreferences =
                    settingsManager.openPreferences(CameraActivity.MODULE_SCOPE_PREFIX
                            + module.getModuleStringIdentifier());

            copyPreferences(oldModulePreferences, newModulePreferences);
        }
    }

    /**
     * The R.integer.camera_mode_* indices were cleaned up, resulting in
     * removals and renaming of certain values. In particular camera_mode_gcam
     * is now 5, not 6. We modify any persisted user settings that may refer to
     * the old value.
     */
    private void upgradeSelectedModeIndex(SettingsManager settingsManager, Context context) {
        int oldGcamIndex = 6; // from hardcoded previous mode index resource
        int gcamIndex = context.getResources().getInteger(R.integer.camera_mode_gcam);

        int lastUsedCameraIndex = settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_CAMERA_MODULE_LAST_USED);
        if (lastUsedCameraIndex == oldGcamIndex) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_MODULE_LAST_USED,
                    gcamIndex);
        }

        int startupModuleIndex = settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_STARTUP_MODULE_INDEX);
        if (startupModuleIndex == oldGcamIndex) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_STARTUP_MODULE_INDEX,
                    gcamIndex);
        }
    }
}
