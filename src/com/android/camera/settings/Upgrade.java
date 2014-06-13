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
import android.hardware.Camera;

import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;
import com.android.ex.camera2.portability.Size;

import java.util.List;
import java.util.Map;

/**
 * The settings Upgrade class defines an upgrade callback that can be
 * used to execute upgrade logic when a version number has changed.
 *
 * This class also defines the upgrade path for AOSP.  Third party modules
 * can define an upgrade path for their local settings, and they can keep
 * a separate version number.
 */
public class Upgrade {
    private static final Log.Tag TAG = new Log.Tag("Upgrade");

    private static final String OLD_GLOBAL_PREFERENCES_FILENAME = "_preferences_camera";

    /**
     * A callback for executing upgrade logic.
     */
    public interface UpgradeSteps {
        public void upgrade(SettingsManager settingsManager, int version);
    }

    /**
     * Execute an upgrade callback if a upgrade version has changed.
     * Third party modules also use this to upgrade settings
     * local to their module.
     */
    public static void executeUpgradeOnVersionChanged(SettingsManager settingsManager,
            String versionKey, int version, Upgrade.UpgradeSteps upgradeSteps) {
        int lastVersion;
        try {
            lastVersion = settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL, versionKey);
        } catch (ClassCastException e) {
            upgradeTypesToStrings(settingsManager);
            lastVersion = settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL, versionKey);
        }
        if (lastVersion != version) {
            upgradeSteps.upgrade(settingsManager, lastVersion);
        }
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, versionKey, version);
    }

    /**
     * A helper function that is used to remove a settings stored as a boolean,
     * and return the value that was removed.
     *
     * This is used in the upgrade path to change all underlying SharedPreferences
     * values to Strings.  It can be used by third party modules to upgrade
     * their boolean settings to Strings.
     */
    public static boolean removeBoolean(SharedPreferences oldPreferencesLocation,
                                        String key) {
        boolean value = oldPreferencesLocation.getBoolean(key, false);
        oldPreferencesLocation.edit().remove(key);
        return value;
    }

    /**
     * A helper function that is used to remove a settings stored as a Integer,
     * and return the value that was removed.
     *
     * This is used in the upgrade path to change all underlying SharedPreferences
     * values to Strings.  It can be used by third party modules to upgrade
     * their Integer settings to Strings.
     */
    public static int removeInteger(SharedPreferences oldPreferencesLocation,
                                    String key) {
        int value = oldPreferencesLocation.getInt(key, 0);
        oldPreferencesLocation.edit().remove(key);
        return value;
    }

    /**
     * Converts settings that were stored in SharedPreferences as
     * non-Strings, to Strings.  This is necessary due to a recent SettingsManager
     * API refactoring.
     */
    private static void upgradeTypesToStrings(SettingsManager settingsManager) {
        SharedPreferences defaultPreferences = settingsManager.getDefaultPreferences();
        SharedPreferences oldGlobalPreferences =
            settingsManager.openPreferences(OLD_GLOBAL_PREFERENCES_FILENAME);

        // Location: boolean -> String, from default.
        boolean location = removeBoolean(defaultPreferences, Keys.KEY_RECORD_LOCATION);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION, location);

        // User selected aspect ratio: boolean -> String, from default.
        boolean userSelectedAspectRatio = removeBoolean(defaultPreferences,
            Keys.KEY_USER_SELECTED_ASPECT_RATIO);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_USER_SELECTED_ASPECT_RATIO,
                            userSelectedAspectRatio);

        // Manual exposure compensation: boolean -> String, from default.
        boolean manualExposureCompensationEnabled = removeBoolean(defaultPreferences,
            Keys.KEY_EXPOSURE_COMPENSATION_ENABLED);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_EXPOSURE_COMPENSATION_ENABLED,
                            manualExposureCompensationEnabled);

        // Hint: boolean -> String, from default.
        boolean hint = removeBoolean(defaultPreferences, Keys.KEY_CAMERA_FIRST_USE_HINT_SHOWN);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_FIRST_USE_HINT_SHOWN,
                            hint);

        // Startup module index: Integer -> String, from default.
        int startupModuleIndex = removeInteger(defaultPreferences,
            Keys.KEY_STARTUP_MODULE_INDEX);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_STARTUP_MODULE_INDEX,
                            startupModuleIndex);

        // Last camera used module index: Integer -> String, from default.
        int lastCameraUsedModuleIndex = removeInteger(defaultPreferences,
            Keys.KEY_CAMERA_MODULE_LAST_USED);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_MODULE_LAST_USED,
                            lastCameraUsedModuleIndex);

        // Flash supported back camera setting: boolean -> String, from old global.
        boolean flashSupportedBackCamera = removeBoolean(oldGlobalPreferences,
            Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA);
        if (flashSupportedBackCamera) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA,
                                flashSupportedBackCamera);
        }

        // Strict upgrade version: Integer -> String, from default.
        int strictUpgradeVersion = removeInteger(defaultPreferences, Keys.KEY_UPGRADE_VERSION);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_UPGRADE_VERSION,
                            strictUpgradeVersion);

        // Request return to HDR+: boolean -> String, from module.
        boolean requestReturnHdrPlus = removeBoolean(defaultPreferences,
            Keys.KEY_REQUEST_RETURN_HDR_PLUS);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_REQUEST_RETURN_HDR_PLUS,
                            requestReturnHdrPlus);

        // Should show refocus viewer: boolean -> String, from default.
        boolean shouldShowRefocusViewer = removeBoolean(defaultPreferences,
            Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING,
                            shouldShowRefocusViewer);

        // Should show settings button cling: boolean -> String, from default.
        boolean shouldShowSettingsButtonCling = removeBoolean(defaultPreferences,
            Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING);
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING,
                            shouldShowSettingsButtonCling);
    }
}