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

import android.content.SharedPreferences;

import com.android.camera.debug.Log;

/**
 * The SettingsUpgrader class can be used to define an upgrade flow that
 * executes upgrade logic to a target version when a version number has changed.
 */
public abstract class SettingsUpgrader {
    private static final Log.Tag TAG = new Log.Tag("SettingsUpgrader");

    private final String mVersionKey;
    private final int mTargetVersion;

    // These values were in use by the original preferences management, before
    // SettingsManager, to represent string-based booleans via typed string
    // resource arrays. We no longer utilize such value arrays, and reference
    // these constants only within SettingsUpgraders to convert to new string-
    // based booleans.
    protected static final String OLD_SETTINGS_VALUE_NONE = "none";
    protected static final String OLD_SETTINGS_VALUE_ON = "on";
    protected static final String OLD_SETTINGS_VALUE_OFF = "off";

    public SettingsUpgrader(String versionKey, int targetVersion) {
        mVersionKey = versionKey;
        mTargetVersion = targetVersion;
    }

    /**
     * Execute an upgrade callback if an upgrade version has changed. Third
     * party modules also use this to upgrade settings local to them.
     */
    public void upgrade(SettingsManager settingsManager) {
        int lastVersion = getLastVersion(settingsManager);
        if (lastVersion != mTargetVersion) {
            upgrade(settingsManager, lastVersion, mTargetVersion);
        }
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, mVersionKey, mTargetVersion);
    }

    /**
     * Perform upgrades to bring any settings up to date to the version
     * specified in currentVersion, where the settings were last upgraded to
     * lastVersion. Typically an Upgrader will check whether lastVersion is less
     * than some known version for a particular setting, and apply upgrade logic
     * if lastVersion is less than that known version.
     */
    protected abstract void upgrade(SettingsManager settingsManager, int lastVersion,
            int targetVersion);

    /**
     * Retrieve the last persisted version for the particular upgrader.
     * Typically will be stored in SCOPE_GLOBAL in SettingsManager, but an
     * Upgrader may need to perform an upgrade analysis on the version
     * persistence itself and should do so here.
     *
     * @throws a {@link ClassCastException} if the value for Version is not a
     *             String
     */
    protected int getLastVersion(SettingsManager settingsManager) {
        return settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL, mVersionKey);
    }

    /**
     * A helper function that is used to remove a setting stored as a boolean,
     * and return the value that was removed.
     * <p>
     * This is used in the upgrade path to change all underlying
     * SharedPreferences values to Strings. It can be used by third party
     * modules to upgrade their boolean settings to Strings.
     */
    protected boolean removeBoolean(SharedPreferences oldPreferencesLocation, String key) {
        boolean value = false;
        try {
            value = oldPreferencesLocation.getBoolean(key, value);
        } catch (ClassCastException e) {
            Log.e(TAG, "error reading old value, removing and returning default", e);
        }
        oldPreferencesLocation.edit().remove(key).apply();
        return value;
    }

    /**
     * A helper function that is used to remove a setting stored as an Integer,
     * and return the value that was removed.
     * <p>
     * This is used in the upgrade path to change all underlying
     * SharedPreferences values to Strings. It can be used by third party
     * modules to upgrade their Integer settings to Strings.
     */
    protected int removeInteger(SharedPreferences oldPreferencesLocation, String key) {
        int value = 0;
        try {
            value = oldPreferencesLocation.getInt(key, value);
        } catch (ClassCastException e) {
            Log.e(TAG, "error reading old value, removing and returning default", e);
        }
        oldPreferencesLocation.edit().remove(key).apply();
        return value;
    }

    /**
     * A helper function that is used to remove a setting stored as a String,
     * and return the value that was removed.
     * <p>
     * This is used in the upgrade path to change all underlying
     * SharedPreferences values to Strings. It can be used by third party
     * modules to upgrade their boolean settings to Strings.
     */
    protected String removeString(SharedPreferences oldPreferencesLocation, String key) {
        String value = null;
        try {
            value = oldPreferencesLocation.getString(key, value);
        } catch (ClassCastException e) {
            Log.e(TAG, "error reading old value, removing and returning default", e);
        }
        oldPreferencesLocation.edit().remove(key).apply();
        return value;
    }

}
