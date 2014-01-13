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
import android.hardware.Camera.Size;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.camera.ListPreference;
import com.android.camera.util.SettingsHelper;
import com.android.camera2.R;

import java.util.List;
import java.util.ArrayList;

/**
 * SettingsManager class provides an api for getting and setting both
 * global and local SharedPreferences.
 */
public class SettingsManager {
    private static final String TAG = "SettingsManager";

    private final Context mContext;
    private final SharedPreferences mDefaultSettings;
    private final SettingsCache mSettingsCache;
    private SharedPreferences mGlobalSettings;
    private SharedPreferences mCameraSettings;
    private SettingsCapabilities mCapabilities;

    private int mCameraId = -1;

    private final List<OnSharedPreferenceChangeListener>
        mSharedPreferenceListeners =
        new ArrayList<OnSharedPreferenceChangeListener>();

    public SettingsManager(Context context, int nCameras) {
        mContext = context;

        SettingsCache.ExtraSettings extraSettings = new SettingsHelper();
        mSettingsCache = new SettingsCache(mContext, extraSettings);

        mDefaultSettings = PreferenceManager.getDefaultSharedPreferences(context);
        initGlobal();

        int cameraId = Integer.parseInt(get(SETTING_CAMERA_ID));
        if (cameraId < 0 || cameraId >= nCameras) {
            setDefault(SETTING_CAMERA_ID);
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
                    }
                }
            };
    }

    /**
     * Add an OnSettingChangedListener to the SettingsManager, which will execute
     * onSettingsChanged when any SharedPreference has been updated.
     */
    public void addListener(final OnSettingChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OnSettingChangedListener cannot be null.");
        }

        OnSharedPreferenceChangeListener sharedPreferenceListener =
            getSharedPreferenceListener(listener);

        if (!mSharedPreferenceListeners.contains(sharedPreferenceListener)) {
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
    }

    /**
     * Remove a specific SettingsListener.
     * This should be done in onPause if a listener has been set.
     */
    public void removeListener(OnSettingChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException();
        }

        OnSharedPreferenceChangeListener sharedPreferenceListener =
            getSharedPreferenceListener(listener);

        if (mSharedPreferenceListeners.contains(sharedPreferenceListener)) {
            mSharedPreferenceListeners.remove(sharedPreferenceListener);

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
    }

    /**
     * Remove all OnSharedPreferenceChangedListener's.
     * This should be done in onDestroy.
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
    }

    /**
     * SettingsCapabilities defines constraints around settings that need to be
     * queried from external sources, like the camera parameters.
     *
     * This interface is camera api agnostic.
     */
    public interface SettingsCapabilities {
        /**
         * Returns a list of the picture sizes currently
         * supported by the camera device.
         */
        public List<Size> getSupportedPictureSizes();

        /**
         * Returns a dynamically calculated list of
         * exposure values, based on the min and max
         * exposure compensation supported by the camera device.
         */
        public String[] getSupportedExposureValues();

        /**
         * Returns a list of camera ids based on the number
         * of cameras available on the device.
         */
        public String[] getSupportedCameraIds();
    }

    /**
     * Exposes SettingsCapabilities functionality.
     */
    public List<Size> getSupportedPictureSizes() {
        if (mCapabilities != null) {
            return mCapabilities.getSupportedPictureSizes();
        } else {
            return null;
        }
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

    public static final boolean FLUSH_ON = true;
    public static final boolean FLUSH_OFF = false;

    // For quick lookup from id to Setting.
    public static final int SETTING_RECORD_LOCATION = 0;
    public static final int SETTING_VIDEO_QUALITY = 1;
    public static final int SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL = 2;
    public static final int SETTING_PICTURE_SIZE = 3;
    public static final int SETTING_JPEG_QUALITY = 4;
    public static final int SETTING_FOCUS_MODE = 5;
    public static final int SETTING_FLASH_MODE = 6;
    public static final int SETTING_VIDEOCAMERA_FLASH_MODE = 7;
    public static final int SETTING_WHITE_BALANCE = 8;
    public static final int SETTING_SCENE_MODE = 9;
    public static final int SETTING_EXPOSURE = 10;
    public static final int SETTING_TIMER = 11;
    public static final int SETTING_TIMER_SOUND_EFFECTS = 12;
    public static final int SETTING_VIDEO_EFFECT = 13;
    public static final int SETTING_CAMERA_ID = 14;
    public static final int SETTING_CAMERA_HDR = 15;
    public static final int SETTING_CAMERA_HDR_PLUS = 16;
    public static final int SETTING_CAMERA_FIRST_USE_HINT_SHOWN = 17;
    public static final int SETTING_VIDEO_FIRST_USE_HINT_SHOWN = 18;
    public static final int SETTING_STARTUP_MODULE_INDEX = 19;
    public static final int SETTING_CAMERA_REFOCUS = 20;
    public static final int SETTING_SHIMMY_REMAINING_PLAY_TIMES_INDEX = 21;

    // Shared preference keys.
    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL =
        "pref_video_time_lapse_frame_interval_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_TIMER = "pref_camera_timer_key";
    public static final String KEY_TIMER_SOUND_EFFECTS = "pref_camera_timer_sound_key";
    public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";
    public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
    public static final String KEY_CAMERA_HDR_PLUS = "pref_camera_hdr_plus_key";
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN =
        "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN =
        "pref_video_first_use_hint_shown_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";
    public static final String KEY_CAMERA_REFOCUS = "pref_camera_refocus";
    public static final String KEY_SHIMMY_REMAINING_PLAY_TIMES =
            "pref_shimmy_remaining_play_times";

    public static final int WHITE_BALANCE_DEFAULT_INDEX = 2;


    /**
     * Defines a simple class for holding a the spec of a setting.
     * This spec is used by the generic api methods to query and
     * update a setting.
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
        Setting(String source, String type, String defaultValue, String key,
                String[] values, boolean flushOnCameraChange) {
            mSource = source;
            mType = type;
            mDefault = defaultValue;
            mKey = key;
            mValues = values;
            mFlushOnCameraChange = flushOnCameraChange;
        }

        /**
         * Returns the id of a SharedPreferences instance from which
         * this Setting may be found.
         * Possible values are {@link #SOURCE_DEFAULT}, {@link #SOURCE_GLOBAL},
         * {@link #SOURCE_CAMERA}.
         */
        public String getSource() {
            return mSource;
        }

        /**
         * Returns the type of the setting stored in SharedPreferences.
         * Possible values are {@link #TYPE_STRING}, {@link #TYPE_INTEGER},
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
         * Returns an array of possible String values for this setting.
         * If this setting is not of type {@link #TYPE_STRING}, or
         * it's not possible to generate the string values, this should
         * return null;
         */
        public String[] getStringValues() {
            return mValues;
        }

        /**
         * Returns whether the setting should be flushed from the cache
         * when the camera device has changed.
         */
        public boolean isFlushedOnCameraChanged() {
            return mFlushOnCameraChange;
        }
    }

    /**
     * Get the SharedPreferences needed to query/update the setting.
     */
    public SharedPreferences getSettingSource(Setting setting) {
        String source = setting.getSource();
        if (source.equals(SOURCE_DEFAULT)) {
            return mDefaultSettings;
        }
        if (source.equals(SOURCE_GLOBAL)) {
            return mGlobalSettings;
        }
        if (source.equals(SOURCE_CAMERA)) {
            return mCameraSettings;
        }
        return null;
    }

    /**
     * Based on Setting id, finds the index of a Setting's
     * String value in an array of possible String values.
     *
     * If the Setting is not of type String, this returns -1.
     *
     * <p>TODO: make this a supported api call for all types.</p>
     */
    public int getStringValueIndex(int id) {
        Setting setting = mSettingsCache.get(id);
        if (setting == null || !TYPE_STRING.equals(setting.getType())) {
            return -1;
        }

        String value = get(id);
        if (value != null) {
            String[] possibleValues = setting.getStringValues();
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
     * Based on Setting id, sets a Setting's String value using the
     * index into an array of possible String values.
     *
     * Fails to set a value if the index is out of bounds or the Setting
     * is not of type String.
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
     * Get a Setting's String value based on Setting id.
     */
    // TODO: rename to something more descriptive.
    public String get(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            return preferences.getString(setting.getKey(), setting.getDefault());
        } else {
            return null;
        }
    }

    /**
     * Get a Setting's boolean value based on Setting id.
     */
    public boolean getBoolean(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        boolean defaultValue = setting.getDefault().equals(VALUE_ON);
        if (preferences != null) {
            return preferences.getBoolean(setting.getKey(), defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Get a Setting's int value based on Setting id.
     */
    public int getInt(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        int defaultValue = Integer.parseInt(setting.getDefault());
        if (preferences != null) {
            return preferences.getInt(setting.getKey(), defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Set a Setting with a String value based on Setting id.
     */
    // TODO: rename to something more descriptive.
    public void set(int id, String value) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putString(setting.getKey(), value).apply();
        }
    }

    /**
     * Set a Setting with a boolean value based on Setting id.
     */
    public void setBoolean(int id, boolean value) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putBoolean(setting.getKey(), value).apply();
        }
    }

    /**
     * Set a Setting with an int value based on Setting id.
     */
    public void setInt(int id, int value) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putInt(setting.getKey(), value).apply();
        }
    }

    /**
     * Check if a Setting has ever been set based on Setting id.
     */
    public boolean isSet(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            return (preferences.getString(setting.getKey(), null) != null);
        } else {
            return false;
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
            return false;
        }
    }

    public static Setting getLocationSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_none_value);
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_recordlocation_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_RECORD_LOCATION,
            values, FLUSH_OFF);
    }

    public static Setting getPictureSizeSetting(Context context) {
        String defaultValue = null;
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_picturesize_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_PICTURE_SIZE,
            values, FLUSH_OFF);
    }

    public static Setting getDefaultCameraIdSetting(Context context,
            SettingsCapabilities capabilities) {
        String defaultValue = context.getString(R.string.pref_camera_id_default);
        String[] values = null;
        if (capabilities != null) {
            values = capabilities.getSupportedCameraIds();
        }
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_CAMERA_ID,
            values, FLUSH_ON);
    }

    public static Setting getWhiteBalanceSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_whitebalance_default);
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_whitebalance_entryvalues);
        return new Setting(SOURCE_CAMERA, TYPE_STRING, defaultValue, KEY_WHITE_BALANCE,
            values, FLUSH_OFF);
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

    public static Setting getTimerSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_timer_default);
        String[] values = null; // TODO: get the values dynamically.
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_TIMER,
            values, FLUSH_OFF);
    }

    public static Setting getTimerSoundSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_camera_timer_sound_default);
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_timer_sound_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_TIMER_SOUND_EFFECTS,
            values, FLUSH_OFF);
    }

    public static Setting getVideoQualitySetting(Context context) {
        String defaultValue = context.getString(R.string.pref_video_quality_default);
        String[] values = context.getResources().getStringArray(
            R.array.pref_video_quality_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue, KEY_VIDEO_QUALITY,
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

    public static Setting getShimmyRemainingTimesSetting(Context context) {
        String defaultValue = context.getString(R.string.pref_shimmy_play_times);
        return new Setting(SOURCE_DEFAULT, TYPE_INTEGER, defaultValue,
                KEY_SHIMMY_REMAINING_PLAY_TIMES, null, FLUSH_OFF);
    }

    public static Setting getRefocusSetting(Context context) {
        String defaultValue = context.getString(R.string.setting_off_value);
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_refocus_entryvalues);
        return new Setting(SOURCE_GLOBAL, TYPE_STRING, defaultValue,
            KEY_CAMERA_REFOCUS, values, FLUSH_OFF);
    }

    // Utilities.

    /**
     * Returns whether the camera has been set to back facing
     * in settings.
     */
    public boolean isCameraBackFacing() {
        int cameraFacingIndex = getStringValueIndex(SETTING_CAMERA_ID);
        String backFacingIndex = mContext.getString(R.string.pref_camera_id_index_back);
        return (cameraFacingIndex == Integer.parseInt(backFacingIndex));
    }

    /**
     * Returns whether refocus mode is set on.
     */
    public boolean isRefocusOn() {
        String refocusOn = get(SettingsManager.SETTING_CAMERA_REFOCUS);
        return refocusOn.equals(SettingsManager.VALUE_ON);
    }

    /**
     * Returns whether hdr plus mode is set on.
     */
    public boolean isHdrPlusOn() {
        String hdrOn = get(SettingsManager.SETTING_CAMERA_HDR);
        return hdrOn.equals(SettingsManager.VALUE_ON);
    }

    //TODO: refactor this into a separate utils module.

    /**
     * Get a String value from first the ListPreference, and if not found
     * from the SettingsManager.
     *
     * This is a wrapper that adds backwards compatibility to views that
     * rely on PreferenceGroups.
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
     * Set a String value first from the ListPreference, and if unable
     * from the SettingsManager.
     *
     * This is a wrapper that adds backwards compatibility to views that
     * rely on PreferenceGroups.
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
     * ListPreference index, and if unable use the ListPreference key
     * to set the value using the SettingsManager.
     *
     * This is a wrapper that adds backwards compatibility to views that
     * rely on PreferenceGroups.
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
}
