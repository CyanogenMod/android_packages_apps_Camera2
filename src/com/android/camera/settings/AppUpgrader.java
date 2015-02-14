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
     * With this version, port over power shutter settings.
     */
    private static final int CAMERA_SETTINGS_POWER_SHUTTER = 6;

    /**
     * With this version, port over max brightness settings.
     */
    private static final int CAMERA_SETTINGS_MAX_BRIGHTNESS = 7;

    /**
     * Increment this value whenever new AOSP UpgradeSteps need to be executed.
     */
    public static final int APP_UPGRADE_VERSION = 7;

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
                    .getAndroidCameraAgent(context, CameraAgentFactory.CameraApi.API_1)
                    .getCameraDeviceInfo();
            upgradeCameraSizeSetting(settingsManager, context, infos,
                    SettingsUtil.CAMERA_FACING_FRONT);
            upgradeCameraSizeSetting(settingsManager, context, infos,
                    SettingsUtil.CAMERA_FACING_BACK);
            // We changed size handling and aspect ratio placement, put user
            // back into Camera mode this time to ensure they see the ratio
            // chooser if applicable.
            settingsManager.remove(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_STARTUP_MODULE_INDEX);
        }

        if (lastVersion < CAMERA_MODULE_SETTINGS_FILES_RENAMED_VERSION) {
            upgradeCameraSettingsFiles(settingsManager, context);
            upgradeModuleSettingsFiles(settingsManager, context,
                    mAppController);
        }

        if (lastVersion < CAMERA_SETTINGS_SELECTED_MODULE_INDEX) {
            upgradeSelectedModeIndex(settingsManager, context);
        }

        if (lastVersion < CAMERA_SETTINGS_POWER_SHUTTER) {
            upgradePowerShutter(settingsManager);
        }

        if (lastVersion < CAMERA_SETTINGS_MAX_BRIGHTNESS) {
            upgradeMaxBrightness(settingsManager);
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

        // HDR plus on setting: String on/off -> String, from old global.
        if (oldGlobalPreferences.contains(Keys.KEY_CAMERA_HDR_PLUS)) {
            String hdrPlus = removeString(oldGlobalPreferences, Keys.KEY_CAMERA_HDR_PLUS);
            if (OLD_SETTINGS_VALUE_ON.equals(hdrPlus)) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS, true);
            }
        }

        // HDR on setting: String on/off -> String, from old global.
        if (oldGlobalPreferences.contains(Keys.KEY_CAMERA_HDR)) {
            String hdrPlus = removeString(oldGlobalPreferences, Keys.KEY_CAMERA_HDR);
            if (OLD_SETTINGS_VALUE_ON.equals(hdrPlus)) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR, true);
            }
        }

        // Grid on setting: String on/off -> String, from old global.
        if (oldGlobalPreferences.contains(Keys.KEY_CAMERA_GRID_LINES)) {
            String hdrPlus = removeString(oldGlobalPreferences, Keys.KEY_CAMERA_GRID_LINES);
            if (OLD_SETTINGS_VALUE_ON.equals(hdrPlus)) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_GRID_LINES,
                        true);
            }
        }
    }

    /**
     * Part of the AOSP upgrade path, forces the user to choose their location
     * again if it was originally set to false.
     */
    private void forceLocationChoice(SettingsManager settingsManager) {
        SharedPreferences oldGlobalPreferences =
                settingsManager.openPreferences(OLD_GLOBAL_PREFERENCES_FILENAME);
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
        } else if (oldGlobalPreferences.contains(Keys.KEY_RECORD_LOCATION)) {
            // Location is not set, check to see if we're upgrading from
            // a different source file.
            String location = removeString(oldGlobalPreferences, Keys.KEY_RECORD_LOCATION);
            if (OLD_SETTINGS_VALUE_ON.equals(location)) {
                    settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION,
                            true);
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

        // infos might be null if the underlying camera device is broken. In
        // that case, just delete the old settings and force the user to
        // reselect, it's the least evil solution given we want to only upgrade
        // settings once.
        if (infos == null) {
            settingsManager.remove(SettingsManager.SCOPE_GLOBAL, key);
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
     * Settings that are not a known supported format (int/boolean/String)
     * are dropped with warning.
     */
    private void copyPreferences(SharedPreferences oldPrefs,
            SharedPreferences newPrefs) {
        Map<String, ?> entries = oldPrefs.getAll();
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                Log.w(TAG, "skipped upgrade for null key " + key);
            } else if (value instanceof Boolean) {
                String boolValue = SettingsManager.convert((Boolean) value);
                newPrefs.edit().putString(key, boolValue).apply();
            } else if (value instanceof Integer) {
                String intValue = SettingsManager.convert((Integer) value);
                newPrefs.edit().putString(key, intValue).apply();
            } else if (value instanceof Long){
                // New SettingsManager only supports int values. Attempt to
                // recover any longs which happen to be present if they are
                // within int range.
                long longValue = (Long) value;
                if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                    String intValue = SettingsManager.convert((int) longValue);
                    newPrefs.edit().putString(key, intValue).apply();
                } else {
                    Log.w(TAG, "skipped upgrade for out of bounds long key " +
                            key + " : " + longValue);
                }
            } else if (value instanceof String){
                newPrefs.edit().putString(key, (String) value).apply();
            } else {
                Log.w(TAG,"skipped upgrade for unrecognized key type " +
                        key + " : " + value.getClass());
            }
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

    private void upgradePowerShutter(SettingsManager settingsManager) {
        SharedPreferences oldGlobalPreferences =
                settingsManager.openPreferences(OLD_GLOBAL_PREFERENCES_FILENAME);
        if (oldGlobalPreferences.contains(Keys.KEY_POWER_SHUTTER)) {
            String powerShutter = removeString(oldGlobalPreferences, Keys.KEY_POWER_SHUTTER);
            if (OLD_SETTINGS_VALUE_ON.equals(powerShutter)) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_POWER_SHUTTER,
                        true);
            }
        }
    }

    private void upgradeMaxBrightness(SettingsManager settingsManager) {
        SharedPreferences oldGlobalPreferences =
                settingsManager.openPreferences(OLD_GLOBAL_PREFERENCES_FILENAME);
        if (oldGlobalPreferences.contains(Keys.KEY_MAX_BRIGHTNESS)) {
            String maxBrightness = removeString(oldGlobalPreferences, Keys.KEY_MAX_BRIGHTNESS);
            if (OLD_SETTINGS_VALUE_ON.equals(maxBrightness)) {
                settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_MAX_BRIGHTNESS,
                        true);
            }
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
