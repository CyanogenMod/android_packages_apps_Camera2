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

package com.android.camera.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.SparseArray;

import com.android.camera.ListPreference;
import com.android.camera.app.AppController;
import com.android.camera.app.LocationManager;
import com.android.camera.debug.Log;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.SettingsHelper;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.List;

/**
 * SettingsManager class provides an api for getting and setting both global and
 * local SharedPreferences.
 */
public class SettingsManager {
    private static final Log.Tag TAG = new Log.Tag("SettingsManager");

    private final Context mContext;
    private final SharedPreferences mDefaultSettings;
    private final SettingsCache mSettingsCache;
    private SharedPreferences mGlobalSettings;
    private SharedPreferences mCameraSettings;
    private final SparseArray<SharedPreferences> mModuleSettings = new SparseArray<SharedPreferences>();
    private SettingsCapabilities mCapabilities;

    private int mCameraId = -1;
    private final AppController mAppController;

    /**
     * General settings upgrade model:
     *
     *  On app upgrade, there are three main ways Settings can be stale/incorrect:
     *  (1) if the type of a setting has changed.
     *  (2) if a set value is no longer a member of the set of possible values.
     *  (3) if the SharedPreferences backing file has changed for a setting.
     *
     *  Recovery strategies:
     *  (1) catch the ClassCastException or NumberFormatException and try to do a
     *      type conversion, or reset the setting to whatever default is valid.
     *  (2) sanitize sets, and reset to default if set value is no longer valid.
     *  (3) use the default value by virtue of the setting not yet being in the
     *      new file.
     *
     * Special cases:
     *
     *  There are some settings which shouldn't be reset to default on upgrade if
     *  possible.  We provide a callback which is executed only on strict upgrade.
     *  This callback does special case upgrades to a subset of the settings.  This
     *  contrasts  with the general upgrade strategies, which happen lazily, once a
     *  setting is used.
     *
     * Removing obsolete key/value pairs:
     *
     *  This can be done in the strict upgrade callback.  The strict upgrade callback
     *  should be idempotent, so it is important to leave removal code in the upgrade
     *  callback so the key/value pairs are removed even if a user skips a version.
     */
    public interface StrictUpgradeCallback {
        /**
         * Will be executed in the SettingsManager constructor if the strict
         * upgrade version counter has changed.
         */
        public void upgrade(SettingsManager settingsManager, int version);
    }

    /**
     * Increment this value whenever a new StrictUpgradeCallback needs to
     * be executed.  This defines upgrade behavior that should be executed
     * strictly on app upgrades, when the upgrade behavior differs from the general,
     * lazy upgrade strategies.
     */
    private static final int STRICT_UPGRADE_VERSION = 2;

    /**
     * A List of OnSettingChangedListener's, maintained to compare to new
     * listeners and prevent duplicate registering.
     */
    private final List<OnSettingChangedListener> mListeners = new ArrayList<OnSettingChangedListener>();

    /**
     * A List of OnSharedPreferenceChangeListener's, maintained to hold pointers
     * to actually registered listeners, so they can be unregistered.
     */
    private final List<OnSharedPreferenceChangeListener> mSharedPreferenceListeners = new ArrayList<OnSharedPreferenceChangeListener>();

    public SettingsManager(Context context, AppController app, int nCameras,
                           StrictUpgradeCallback upgradeCallback) {
        mContext = context;
        mAppController = app;

        SettingsCache.ExtraSettings extraSettings = new SettingsHelper();
        mSettingsCache = new SettingsCache(mContext, extraSettings);

        mDefaultSettings = PreferenceManager.getDefaultSharedPreferences(context);
        initGlobal();

        if (upgradeCallback != null) {
            // Check for a strict version upgrade.
            int version = getInt(SETTING_STRICT_UPGRADE_VERSION);
            if (STRICT_UPGRADE_VERSION != version) {
                upgradeCallback.upgrade(this, STRICT_UPGRADE_VERSION);
            }
            setInt(SETTING_STRICT_UPGRADE_VERSION, STRICT_UPGRADE_VERSION);
        }
    }

    /**
     * Initialize global SharedPreferences.
     */
    private void initGlobal() {
        String globalKey = mContext.getPackageName() + "_preferences_camera";
        mGlobalSettings = mContext.getSharedPreferences(globalKey, Context.MODE_PRIVATE);
    }

    /**
     * Load and cache a module specific SharedPreferences.
     */
    public SharedPreferences getModulePreferences(int modeIndex) {
        SharedPreferences sharedPreferences = mModuleSettings.get(modeIndex);
        if (sharedPreferences == null) {
            String moduleKey = mContext.getPackageName() + "_preferences_module_" + modeIndex;
            sharedPreferences = mContext.getSharedPreferences(moduleKey, Context.MODE_PRIVATE);
            mModuleSettings.put(modeIndex, sharedPreferences);
        }
        return sharedPreferences;
    }

    /**
     * Initialize SharedPreferences for other cameras.
     */
    public void changeCamera(int cameraId, SettingsCapabilities capabilities) {
        mCapabilities = capabilities;
        mSettingsCache.setCapabilities(mCapabilities);

        if (cameraId == mCameraId) {
            return;
        }

        // We've changed camera ids, that means we need to flush the
        // settings cache of settings dependent on SettingsCapabilities.
        mSettingsCache.flush();

        // Cache the camera id so we don't need to reload preferences
        // if we're using the same camera.
        mCameraId = cameraId;

        String cameraKey = mContext.getPackageName() + "_preferences_" + cameraId;
        mCameraSettings = mContext.getSharedPreferences(
                cameraKey, Context.MODE_PRIVATE);

        for (OnSharedPreferenceChangeListener listener : mSharedPreferenceListeners) {
            mCameraSettings.registerOnSharedPreferenceChangeListener(listener);
        }
    }

    /**
     * Interface with Camera Parameters and Modules.
     */
    public interface OnSettingChangedListener {
        /**
         * Called every time a SharedPreference has been changed.
         */
        public void onSettingChanged(SettingsManager settingsManager, int setting);
    }

    private OnSharedPreferenceChangeListener getSharedPreferenceListener(
            final OnSettingChangedListener listener) {
        return new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(
                    SharedPreferences sharedPreferences, String key) {
                Integer settingId = mSettingsCache.getId(key);
                if (settingId != null) {
                    listener.onSettingChanged(SettingsManager.this, settingId);
                } else {
                    Log.w(TAG, "Setting id from key=" + key + " is null");
                }
            }
        };
    }

    /**
     * Add an OnSettingChangedListener to the SettingsManager, which will
     * execute onSettingsChanged when any SharedPreference has been updated.
     */
    public void addListener(final OnSettingChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OnSettingChangedListener cannot be null.");
        }

        if (mListeners.contains(listener)) {
            return;
        }

        mListeners.add(listener);
        OnSharedPreferenceChangeListener sharedPreferenceListener =
                getSharedPreferenceListener(listener);
        mSharedPreferenceListeners.add(sharedPreferenceListener);

        if (mGlobalSettings != null) {
            mGlobalSettings.registerOnSharedPreferenceChangeListener(sharedPreferenceListener);
        }

        if (mCameraSettings != null) {
            mCameraSettings.registerOnSharedPreferenceChangeListener(sharedPreferenceListener);
        }

        if (mDefaultSettings != null) {
            mDefaultSettings.registerOnSharedPreferenceChangeListener(sharedPreferenceListener);
        }
    }

    /**
     * Remove a specific SettingsListener. This should be done in onPause if a
     * listener has been set.
     */
    public void removeListener(OnSettingChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        if (!mListeners.contains(listener)) {
            return;
        }

        int index = mListeners.indexOf(listener);
        mListeners.remove(listener);

        // Get the reference to the actual OnSharedPreferenceChangeListener
        // that was registered.
        OnSharedPreferenceChangeListener sharedPreferenceListener =
                mSharedPreferenceListeners.get(index);
        mSharedPreferenceListeners.remove(index);

        if (mGlobalSettings != null) {
            mGlobalSettings.unregisterOnSharedPreferenceChangeListener(
                    sharedPreferenceListener);
        }

        if (mCameraSettings != null) {
            mCameraSettings.unregisterOnSharedPreferenceChangeListener(
                    sharedPreferenceListener);
        }

        if (mDefaultSettings != null) {
            mDefaultSettings.unregisterOnSharedPreferenceChangeListener(
                    sharedPreferenceListener);
        }
    }

    /**
     * Remove all OnSharedPreferenceChangedListener's. This should be done in
     * onDestroy.
     */
    public void removeAllListeners() {
        for (OnSharedPreferenceChangeListener listener : mSharedPreferenceListeners) {
            if (mGlobalSettings != null) {
                mGlobalSettings.unregisterOnSharedPreferenceChangeListener(listener);
            }

            if (mCameraSettings != null) {
                mCameraSettings.unregisterOnSharedPreferenceChangeListener(listener);
            }

            if (mDefaultSettings != null) {
                mDefaultSettings.unregisterOnSharedPreferenceChangeListener(listener);
            }
        }
        mSharedPreferenceListeners.clear();
        mListeners.clear();
    }

    /**
     * SettingsCapabilities defines constraints around settings that need to be
     * queried from external sources, like the camera parameters. This interface
     * is camera api agnostic.
     */
    public interface SettingsCapabilities {
        /**
         * Returns a dynamically calculated list of exposure values, based on
         * the min and max exposure compensation supported by the camera device.
         */
        public String[] getSupportedExposureValues();

        /**
         * Returns a list of camera ids based on the number of cameras available
         * on the device.
         */
        public String[] getSupportedCameraIds();
    }

    /**
     * Get the camera id for which the SettingsManager has loaded camera
     * specific preferences.
     */
    public int getRegisteredCameraId() {
        return mCameraId;
    }

    // Manage individual settings.
    public static final String VALUE_NONE = "none";
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    public static final String TYPE_STRING = "string";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_INTEGER = "integer";

    public static final String SOURCE_DEFAULT = "default";
    public static final String SOURCE_GLOBAL = "global";
    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_MODULE = "module";

    public static final boolean FLUSH_ON = true;
    public static final boolean FLUSH_OFF = false;

    // For quick lookup from id to Setting.
    public static final int SETTING_RECORD_LOCATION = 0;
    public static final int SETTING_VIDEO_QUALITY_BACK = 1;
    public static final int SETTING_VIDEO_QUALITY_FRONT = 2;
    public static final int SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL = 3;
    public static final int SETTING_PICTURE_SIZE_BACK = 4;
    public static final int SETTING_PICTURE_SIZE_FRONT = 5;
    public static final int SETTING_JPEG_QUALITY = 6;
    public static final int SETTING_FOCUS_MODE = 7;
    public static final int SETTING_FLASH_MODE = 8;
    public static final int SETTING_VIDEOCAMERA_FLASH_MODE = 9;
    public static final int SETTING_SCENE_MODE = 11;
    public static final int SETTING_EXPOSURE = 12;
    public static final int SETTING_VIDEO_EFFECT = 15;
    public static final int SETTING_CAMERA_ID = 16;
    public static final int SETTING_CAMERA_HDR = 17;
    public static final int SETTING_CAMERA_HDR_PLUS = 18;
    public static final int SETTING_CAMERA_FIRST_USE_HINT_SHOWN = 19;
    public static final int SETTING_VIDEO_FIRST_USE_HINT_SHOWN = 20;
    public static final int SETTING_STARTUP_MODULE_INDEX = 21;
    public static final int SETTING_KEY_CAMERA_MODULE_LAST_USED_INDEX = 23;
    public static final int SETTING_CAMERA_PANO_ORIENTATION = 24;
    public static final int SETTING_CAMERA_GRID_LINES = 25;
    public static final int SETTING_RELEASE_DIALOG_LAST_SHOWN_VERSION = 26;
    public static final int SETTING_FLASH_SUPPORTED_BACK_CAMERA = 27;
    public static final int SETTING_STRICT_UPGRADE_VERSION = 28;
    // A boolean for requesting to return to HDR plus
    // as soon as possible, if a user requests a setting/mode option
    // that forces them to leave HDR plus.
    public static final int SETTING_REQUEST_RETURN_HDR_PLUS = 30;

    // Shared preference keys.
    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY_BACK = "pref_video_quality_back_key";
    public static final String KEY_VIDEO_QUALITY_FRONT = "pref_video_quality_front_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL =
            "pref_video_time_lapse_frame_interval_key";
    public static final String KEY_PICTURE_SIZE_BACK = "pref_camera_picturesize_back_key";
    public static final String KEY_PICTURE_SIZE_FRONT = "pref_camera_picturesize_front_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";
    public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
    public static final String KEY_CAMERA_HDR_PLUS = "pref_camera_hdr_plus_key";
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN =
            "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN =
            "pref_video_first_use_hint_shown_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";
    public static final String KEY_CAMERA_MODULE_LAST_USED =
            "pref_camera_module_last_used_index";
    public static final String KEY_CAMERA_PANO_ORIENTATION = "pref_camera_pano_orientation";
    public static final String KEY_CAMERA_GRID_LINES = "pref_camera_grid_lines";
    public static final String KEY_RELEASE_DIALOG_LAST_SHOWN_VERSION =
            "pref_release_dialog_last_shown_version";
    public static final String KEY_FLASH_SUPPORTED_BACK_CAMERA =
            "pref_flash_supported_back_camera";
    public static final String KEY_STRICT_UPGRADE_VERSION = "pref_strict_upgrade_version";
    public static final String KEY_REQUEST_RETURN_HDR_PLUS = "pref_request_return_hdr_plus";

    public static final int WHITE_BALANCE_DEFAULT_INDEX = 2;

    /**
     * Defines a simple class for holding a the spec of a setting. This spec is
     * used by the generic api methods to query and update a setting.
     */
    public static class Setting {
        private final String mSource;
        private final String mType;
        private final String mDefault;
        private final String mKey;
        private final String[] mValues;
        private final boolean mFlushOnCameraChange;

        /**
         * A constructor used to store a setting's profile.
         */
        public Setting(String source, String type, String defaultValue, String key,
                String[] values, boolean flushOnCameraChange) {
            mSource = source;
            mType = type;
            mDefault = defaultValue;
            mKey = key;
            mValues = values;
            mFlushOnCameraChange = flushOnCameraChange;
        }

        /**
         * Returns the id of a SharedPreferences instance from which this
         * Setting may be found. Possible values are {@link #SOURCE_DEFAULT},
         * {@link #SOURCE_GLOBAL}, {@link #SOURCE_CAMERA}.
         */
        public String getSource() {
            return mSource;
        }

        /**
         * Returns the type of the setting stored in SharedPreferences. Possible
         * values are {@link #TYPE_STRING}, {@link #TYPE_INTEGER},
         * {@link #TYPE_BOOLEAN}.
         */
        public String getType() {
            return mType;
        }

        /**
         * Returns the default value of this setting.
         */
        public String getDefault() {
            return mDefault;
        }

        /**
         * Returns the SharedPreferences key for this setting.
         */
        public String getKey() {
            return mKey;
        }

        /**
         * Returns an array of possible String values for this setting. If this
         * setting is not of type {@link #TYPE_STRING}, or it's not possible to
         * generate the string values, this should return null;
         */
        public String[] getStringValues() {
            return mValues;
        }

        /**
         * Returns whether the setting should be flushed from the cache when the
         * camera device has changed.
         */
        public boolean isFlushedOnCameraChanged() {
            return mFlushOnCameraChange;
        }
    }

    /**
     * Get the SharedPreferences needed to query/update the setting.
     */
    public SharedPreferences getSettingSource(Setting setting) {
        return getSettingSource(setting.getSource());
    }

    private SharedPreferences getSettingSource(String source) {
        if (source.equals(SOURCE_DEFAULT)) {
            return mDefaultSettings;
        }
        if (source.equals(SOURCE_GLOBAL)) {
            return mGlobalSettings;
        }
        if (source.equals(SOURCE_CAMERA)) {
            return mCameraSettings;
        }
        if (source.equals(SOURCE_MODULE)) {
            int modeIndex = CameraUtil.getCameraModeParentModeId(
                mAppController.getCurrentModuleIndex(), mAppController.getAndroidContext());
            return getModulePreferences(modeIndex);
        }
        return null;
    }

    /**
     * Based on Setting id, finds the index of a Setting's String value in an
     * array of possible String values. If the Setting is not of type String,
     * this returns -1.
     * <p>
     * TODO: make this a supported api call for all types.
     * </p>
     */
    public int getStringValueIndex(int id) {
        Setting setting = mSettingsCache.get(id);
        if (setting == null || !TYPE_STRING.equals(setting.getType())) {
            return -1;
        }
        return getStringValueIndex(setting.getStringValues(), get(id));
    }

    private int getStringValueIndex(String[] possibleValues, String value) {
        if (value != null) {
            if (possibleValues != null) {
                for (int i = 0; i < possibleValues.length; i++) {
                    if (value.equals(possibleValues[i])) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Based on Setting id, sets a Setting's String value using the index into
     * an array of possible String values. Fails to set a value if the index is
     * out of bounds or the Setting is not of type String.
     *
     * @return Whether the value was set.
     */
    public boolean setStringValueIndex(int id, int index) {
        Setting setting = mSettingsCache.get(id);
        if (setting == null || setting.getType() != TYPE_STRING) {
            return false;
        }

        String[] possibleValues = setting.getStringValues();
        if (possibleValues != null) {
            if (index >= 0 && index < possibleValues.length) {
                set(id, possibleValues[index]);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this Setting was last set as a String.
     */
    private boolean isString(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        try {
            preferences.getString(setting.getKey(), null);
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    /**
     * Returns whether this Setting was last set as a boolean.
     */
    private boolean isBoolean(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        try {
            preferences.getBoolean(setting.getKey(), false);
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    /**
     * Returns whether this Setting was last set as an Integer.
     */
    private boolean isInteger(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        try {
            preferences.getInt(setting.getKey(), 0);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Recover a Setting by converting it to a String if the type
     * is known and the type conversion is successful, otherwise
     * reset to the default.
     */
    private String recoverToString(int id, String source) {
        String value;
        try {
            if (isBoolean(id, source)) {
                value = (getBoolean(id, source) ? VALUE_ON : VALUE_OFF);
            } else if (isInteger(id, source)) {
                value = Integer.toString(getInt(id, source));
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            value = mSettingsCache.get(id).getDefault();
        }
        set(id, source, value);
        return value;
    }

    /**
     * Recover a Setting by converting it to a boolean if the type
     * is known and the type conversion is successful, otherwise
     * reset to the default.
     */
    private boolean recoverToBoolean(int id, String source) {
        boolean value;
        try {
            if (isString(id, source)) {
                value = VALUE_ON.equals(get(id, source));
            } else if (isInteger(id, source)) {
                value = getInt(id, source) != 0;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            value = VALUE_ON.equals(mSettingsCache.get(id).getDefault());
        }
        setBoolean(id, source, value);
        return value;
    }

    /**
     * Recover a Setting by converting it to an Integer if the type
     * is known and the type conversion is successful, otherwise
     * reset to the default.
     */
    private int recoverToInteger(int id, String source) {
        int value;
        try {
            if (isString(id, source)) {
                value = Integer.parseInt(get(id, source));
            } else if (isBoolean(id, source)) {
                value = getBoolean(id, source) ? 1 : 0;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            value = Integer.parseInt(mSettingsCache.get(id).getDefault());
        }
        setInt(id, value);
        return value;
    }

    /**
     * Check if a String value is in the set of possible values for a Setting.
     * We only keep track of possible values for String types for now.
     */
    private String sanitize(Setting setting, String value) {
        if (setting.getStringValues() != null &&
                getStringValueIndex(setting.getStringValues(), value) < 0) {
            // If the set of possible values is not empty, and the value
            // is not in the set of possible values, use the default, because
            // the set of possible values probably changed.
            return setting.getDefault();
        }
        return value;
    }

    /**
     * Get a Setting's String value based on Setting id.
     */
    // TODO: rename to something more descriptive like getString.
    public String get(int id) {
        Setting setting = mSettingsCache.get(id);
        return get(id, setting.getSource());
    }

    /**
     * Get a Setting's String value based on Setting id and a source file id.
     */
    public String get(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        if (preferences != null) {
            try {
                String value = preferences.getString(setting.getKey(), setting.getDefault());
                return sanitize(setting, value);
            } catch (ClassCastException e) {
                // If the api defines this Setting as a String, but the
                // last set saved it as a different type, try to recover
                // the value, but if impossible reset to default.
                return recoverToString(id, source);
            }
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Get a Setting's boolean value based on Setting id.
     */
    public boolean getBoolean(int id) {
        Setting setting = mSettingsCache.get(id);
        return getBoolean(id, setting.getSource());
    }

    /**
     * Get a Setting's boolean value based on a Setting id and a source file id.
     */
    public boolean getBoolean(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        boolean defaultValue = VALUE_ON.equals(setting.getDefault());
        if (preferences != null) {
            try {
                return preferences.getBoolean(setting.getKey(), defaultValue);
            } catch (ClassCastException e) {
                // If the api defines this Setting as a boolean, but the
                // last set saved it as a different type, try to recover
                // the value, but if impossible reset to default.
                return recoverToBoolean(id, source);
            }
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Get a Setting's int value based on Setting id.
     */
    public int getInt(int id) {
        Setting setting = mSettingsCache.get(id);
        return getInt(id, setting.getSource());
    }

    /**
     * Get a Setting's int value based on Setting id and a source file id.
     */
    public int getInt(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        int defaultValue = Integer.parseInt(setting.getDefault());
        if (preferences != null) {
            try {
                return preferences.getInt(setting.getKey(), defaultValue);
            } catch (NumberFormatException e) {
                // If the api defines this Setting as an Integer, but the
                // last set saved it as a different type, try to recover
                // the value, but if impossible reset to default.
                return recoverToInteger(id, source);
            }
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Set a Setting with a String value based on Setting id.
     */
    // TODO: rename to something more descriptive.
    public void set(int id, String value) {
        Setting setting = mSettingsCache.get(id);
        set(id, setting.getSource(), value);
    }

    /**
     * Set a Setting with a String value based on Setting id and a source file id.
     */
    public void set(int id, String source, String value) {
        Setting setting = mSettingsCache.get(id);
        value = sanitize(setting, value);
        SharedPreferences preferences = getSettingSource(source);
        if (preferences != null) {
            preferences.edit().putString(setting.getKey(), value).apply();
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Set a Setting with a boolean value based on Setting id.
     */
    public void setBoolean(int id, boolean value) {
        Setting setting = mSettingsCache.get(id);
        setBoolean(id, setting.getSource(), value);
    }
    /**
     * Set a Setting with a boolean value based on Setting id and a source file id.
     */
    public void setBoolean(int id, String source, boolean value) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        if (preferences != null) {
            preferences.edit().putBoolean(setting.getKey(), value).apply();
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Set a Setting with an int value based on Setting id.
     */
    public void setInt(int id, int value) {
        Setting setting = mSettingsCache.get(id);
        setInt(id, setting.getSource(), value);
    }

    /**
     * Set a Setting with an int value based on Setting id and a source file id.
     */
    public void setInt(int id, String source, int value) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        if (preferences != null) {
            preferences.edit().putInt(setting.getKey(), value).apply();
        } else {
            throw new IllegalStateException(
                "Setting source=" + source + " is unitialized.");
        }
    }

    /**
     * Check if a Setting has ever been set based on Setting id.
     */
    public boolean isSet(int id) {
        Setting setting = mSettingsCache.get(id);
        return isSet(id, setting.getSource());
    }

    /**
     * Check if a Setting has ever been set based on Setting id and a source file id.
     */
    public boolean isSet(int id, String source) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(source);
        if (preferences != null) {
            return preferences.contains(setting.getKey());
        } else {
            throw new IllegalStateException(
                "Setting source=" + setting.getSource() + " is unitialized.");
        }
    }

    /**
     * Set a Setting to its default value based on Setting id.
     */
    public void setDefault(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putString(setting.getKey(), setting.getDefault());
        } else {
            throw new IllegalStateException(
                "Setting source=" + setting.getSource() + " is unitialized.");
        }
    }

    /**
     * Check if a Setting is set to its default value.
     */
    public boolean isDefault(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            String type = setting.getType();
            if (TYPE_STRING.equals(type)) {
                String value = get(id);
                return (value.equals(setting.getDefault()));
            } else if (TYPE_BOOLEAN.equals(type)) {
                boolean value = getBoolean(id);
                boolean defaultValue = VALUE_ON.equals(setting.getDefault());
                return (value == defaultValue);
            } else if (TYPE_INTEGER.equals(type)) {
                int value = getInt(id);
                int defaultValue = Integer.parseInt(setting.getDefault());
                return (value == defaultValue);
            } else {
                throw new IllegalArgumentException("Type " + type + " is not known.");
            }
        } else {
            throw new IllegalStateException(
                "Setting source=" + setting.getSource() + " is unitialized.");
        }
    }

    /**
     * Remove a Setting from SharedPreferences.
     */
    public void remove(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().remove(setting.getKey()).apply();
        } else {
            throw new IllegalStateException(
                "Setting source=" + setting.getSource() + " is unitialized.");
        }
    }

    public static Setting getLocationSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_none_value);
        String[] values = null;
        return new Setting(SOURCE_DEFAULT, TYPE_BOOLEAN, defaultValue, KEY_RECORD_LOCATION,
                values, FLUSH_OFF);
    }

    public static Setting getPictureSizeBackSetting(Context context) {
        String defaultValue = null;
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues);
        return new Setting(SOURCE_DEFAULT, TYPE_STRING, defaultValue, KEY_PICTURE_SIZE_BACK,
                values, FLUSH_OFF);
    }

    public static Setting getPictureSizeFrontSetting(Context context) {
        String defaultValue = null;
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues);
        return new Setting(SOURCE_DEFAULT, TYPE_STRING, defaultValue, KEY_PICTURE_SIZE_FRONT,
                values, FLUSH_OFF);
    }

    public static Setting getDefaultCameraIdSetting(Context context,
            SettingsCapabilities capabilities) {
        String defaultValue = context.getString(R.string.pref_camera_id_default);
        String[] values = null;
        if (capabilities != null) {
            values = capabilities.getSupportedCameraIds();
        }
        return new Setting(SOURCE_MODULE, TYPE_STRING, defaultValue, KEY_CAMERA_ID,
                values, FLUSH_ON);
    }

    public static Setting getHdrSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_hdr_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_hdr_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_CAMERA_HDR,
                values, FLUSH_OFF);
    }

    public static Setting getHdrPlusSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_hdr_plus_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_hdr_plus_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_CAMERA_HDR_PLUS,
                values, FLUSH_OFF);
    }

    public static Setting getSceneModeSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_scenemode_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_scenemode_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_SCENE_MODE,
                values, FLUSH_OFF);
    }

    public static Setting getFlashSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_flashmode_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_flashmode_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_FLASH_MODE,
                values, FLUSH_OFF);
    }

    public static Setting getExposureSetting(Context context,
            SettingsCapabilities capabilities) {
        String defaultValue = context.getString(R.string.pref_exposure_default);
        String[] values = null;
        if (capabilities != null) {
            values = capabilities.getSupportedExposureValues();
        }
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_EXPOSURE,
                values, FLUSH_ON);
    }

    public static Setting getHintSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_on_value);
        String[] values = null;
        return new Setting(SOURCE_GLOBAL, TYPE_BOOLEAN, defaultValue,
                KEY_CAMERA_FIRST_USE_HINT_SHOWN, values, FLUSH_OFF);
    }

    public static Setting getFocusModeSetting(Context context) {
        String defaultValue = null;
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_focusmode_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_FOCUS_MODE,
                values, FLUSH_OFF);
    }

    public static Setting getVideoQualityBackSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_video_quality_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_video_quality_entryvalues);
        return new Setting(SOURCE_DEFAULT, TYPE_STRING, defaultValue, KEY_VIDEO_QUALITY_BACK,
                values, FLUSH_OFF);
    }

    public static Setting getVideoQualityFrontSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_video_quality_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_video_quality_entryvalues);
        return new Setting(SOURCE_DEFAULT, TYPE_STRING, defaultValue, KEY_VIDEO_QUALITY_FRONT,
                values, FLUSH_OFF);
    }

    public static Setting getTimeLapseFrameIntervalSetting(Context context) {
        String defaultValue = context.getString(
                R.string.pref_video_time_lapse_frame_interval_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_video_time_lapse_frame_interval_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue,
                KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL, values, FLUSH_OFF);
    }

    public static Setting getJpegQualitySetting(Context context) {
        String defaultValue = context.getString(
                R.string.pref_camera_jpeg_quality_normal);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_jpeg_quality_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_JPEG_QUALITY,
                values, FLUSH_OFF);
    }

    public static Setting getVideoFlashSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_video_flashmode_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_video_flashmode_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue,
                KEY_VIDEOCAMERA_FLASH_MODE, values, FLUSH_OFF);
    }

    public static Setting getVideoEffectSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_video_effect_default);
        String[] values = context.getResources().getStringArray(
                R.array.pref_video_effect_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_VIDEO_EFFECT,
                values, FLUSH_OFF);
    }

    public static Setting getHintVideoSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_on_value);
        String[] values = null;
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue,
                KEY_VIDEO_FIRST_USE_HINT_SHOWN, values, FLUSH_OFF);
    }

    public static Setting getStartupModuleSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_startup_index_default);
        String[] values = null;
        return new Setting(SOURCE_DEFAULT, TYPE_INTEGER, defaultValue,
                KEY_STARTUP_MODULE_INDEX, values, FLUSH_OFF);
    }

    public static Setting getLastUsedCameraModule(Context context) {
        String defaultValue = Integer.toString(context.getResources()
                .getInteger(R.integer.camera_mode_photo));
        return new Setting(SOURCE_DEFAULT, TYPE_INTEGER, defaultValue,
                KEY_CAMERA_MODULE_LAST_USED, null, FLUSH_OFF);
    }

    public static Setting getPanoOrientationSetting(Context context) {
        String defaultValue = context.getString(R.string.pano_orientation_horizontal);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_pano_orientation_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue,
                KEY_CAMERA_PANO_ORIENTATION, values, FLUSH_OFF);
    }

    public static Setting getGridLinesSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_off_value);
        String[] values = context.getResources().getStringArray(
                R.array.pref_camera_gridlines_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue,
                KEY_CAMERA_GRID_LINES, values, FLUSH_OFF);
    }

    public static Setting getReleaseDialogLastShownVersionSetting(Context context) {
        return new Setting(SOURCE_DEFAULT, TYPE_STRING, null,
                KEY_RELEASE_DIALOG_LAST_SHOWN_VERSION, null, FLUSH_OFF);
    }

    public static Setting getFlashSupportedBackCameraSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_none_value);
        return new Setting(SOURCE_GLOBAL, TYPE_BOOLEAN, defaultValue,
                KEY_FLASH_SUPPORTED_BACK_CAMERA, null, FLUSH_OFF);
    }

    public static Setting getStrictUpgradeVersionSetting(Context context) {
        String defaultValue = "0";
        return new Setting(SOURCE_DEFAULT, TYPE_INTEGER, defaultValue,
                KEY_STRICT_UPGRADE_VERSION, null, FLUSH_OFF);
    }

    public static Setting getRequestReturnHdrPlusSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_none_value);
        return new Setting(SOURCE_MODULE, TYPE_BOOLEAN, VALUE_OFF,
                KEY_REQUEST_RETURN_HDR_PLUS, null, FLUSH_OFF);
    }

    // Utilities.

    /**
     * Returns whether the camera has been set to back facing in settings.
     */
    public boolean isCameraBackFacing() {
        String cameraFacing = get(SETTING_CAMERA_ID);
        String backFacing = mContext.getString(R.string.pref_camera_id_default);
        return (Integer.parseInt(cameraFacing) == Integer.parseInt(backFacing));
    }

    /**
     * Returns whether hdr plus mode is set on.
     */
    public boolean isHdrPlusOn() {
        String hdrOn = get(SettingsManager.SETTING_CAMERA_HDR_PLUS);
        return hdrOn.equals(SettingsManager.VALUE_ON);
    }

    /**
     * Returns whether hdr mode is set on.
     */
    public boolean isHdrOn() {
        String hdrOn = get(SettingsManager.SETTING_CAMERA_HDR);
        return hdrOn.equals(SettingsManager.VALUE_ON);
    }

    /**
     * Returns whether the app should return to hdr plus mode if possible.
     */
    public boolean requestsReturnToHdrPlus() {
        return getBoolean(SettingsManager.SETTING_REQUEST_RETURN_HDR_PLUS);
    }

    /**
     * Returns whether grid lines are set on.
     */
    public boolean areGridLinesOn() {
        String gridLinesOn = get(SettingsManager.SETTING_CAMERA_GRID_LINES);
        return gridLinesOn.equals(SettingsManager.VALUE_ON);
    }

    /**
     * Returns whether pano orientation is horizontal.
     */
    public boolean isPanoOrientationHorizontal() {
        String orientation = get(SettingsManager.SETTING_CAMERA_PANO_ORIENTATION);
        String horizontal = mContext.getString(R.string.pano_orientation_horizontal);
        return orientation.equals(horizontal);
    }

    // TODO: refactor this into a separate utils module.

    /**
     * Get a String value from first the ListPreference, and if not found from
     * the SettingsManager. This is a wrapper that adds backwards compatibility
     * to views that rely on PreferenceGroups.
     */
    public String getValueFromPreference(ListPreference pref) {
        String value = pref.getValue();
        if (value == null) {
            Integer id = mSettingsCache.getId(pref.getKey());
            if (id == null) {
                return null;
            }
            value = get(id);
        }
        return value;
    }

    /**
     * Set a String value first from the ListPreference, and if unable from the
     * SettingsManager. This is a wrapper that adds backwards compatibility to
     * views that rely on PreferenceGroups.
     */
    public void setValueFromPreference(ListPreference pref, String value) {
        boolean set = pref.setValue(value);
        if (!set) {
            Integer id = mSettingsCache.getId(pref.getKey());
            if (id != null) {
                set(id, value);
            }
        }
    }

    /**
     * Set a String value first from the ListPreference based on a
     * ListPreference index, and if unable use the ListPreference key to set the
     * value using the SettingsManager. This is a wrapper that adds backwards
     * compatibility to views that rely on PreferenceGroups.
     */
    public void setValueIndexFromPreference(ListPreference pref, int index) {
        boolean set = pref.setValueIndex(index);
        if (!set) {
            Integer id = mSettingsCache.getId(pref.getKey());
            if (id != null) {
                String value = pref.getValueAtIndex(index);
                set(id, value);
            }
        }
    }

    /**
     * Sets the settings for whether location recording should be enabled or
     * not. Also makes sure to pass on the change to the location manager.
     */
    public void setLocation(boolean on, LocationManager locationManager) {
        setBoolean(SettingsManager.SETTING_RECORD_LOCATION, on);
        locationManager.recordLocation(on);
    }

    /**
     * Reads the current location recording settings and passes it on to the
     * given location manager.
     */
    public void syncLocationManager(LocationManager locationManager) {
        boolean value = getBoolean(SettingsManager.SETTING_RECORD_LOCATION);
        locationManager.recordLocation(value);
    }
}
