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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Camera.Size;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.camera.CameraActivity;
import com.android.camera.ListPreference;
import com.android.camera2.R;

import java.util.List;

/**
 * SettingsManager class provides an api for getting and setting both
 * global and local SharedPreferences.
 */
public class SettingsManager {
    private static final String TAG = "CAM_SettingsManager";

    private Context mContext;
    private SharedPreferences mDefaultSettings;
    private SharedPreferences mGlobalSettings;
    private SharedPreferences mCameraSettings;
    private OnSharedPreferenceChangeListener mListener;
    private SettingsCapabilities mCapabilities;
    private SettingsCache mSettingsCache;

    private int mCameraId = -1;

    public SettingsManager(Context context,
            OnSharedPreferenceChangeListener globalListener,
            int nCameras) {
        mContext = context;
        mSettingsCache = new SettingsCache();
        mDefaultSettings = PreferenceManager.getDefaultSharedPreferences(context);
        initGlobal(globalListener);

        DefaultCameraIdSetting cameraIdSetting = new DefaultCameraIdSetting();
        int cameraId = Integer.parseInt(get(SETTING_CAMERA_ID));
        if (cameraId < 0 || cameraId >= nCameras) {
            setDefault(SETTING_CAMERA_ID);
        }
    }

    /**
     * Initialize global SharedPreferences.
     */
    private void initGlobal(OnSharedPreferenceChangeListener listener) {
        String globalKey = mContext.getPackageName() + "_preferences_camera";
        mGlobalSettings = mContext.getSharedPreferences(
            globalKey, Context.MODE_PRIVATE);
        mGlobalSettings.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Initialize SharedPreferences for other cameras.
     */
    public void changeCamera(int cameraId, SettingsCapabilities capabilities) {
        mCapabilities = capabilities;

        if (cameraId == mCameraId) {
            if (mCameraSettings != null) {
                mCameraSettings.registerOnSharedPreferenceChangeListener(mListener);
            }
            return;
        }
        // Cache the camera id so we don't need to reload preferences
        // if we're using the same camera.
        mCameraId = cameraId;

        String cameraKey = mContext.getPackageName() + "_preferences_" + cameraId;
        mCameraSettings = mContext.getSharedPreferences(
            cameraKey, Context.MODE_PRIVATE);
        mCameraSettings.registerOnSharedPreferenceChangeListener(mListener);
    }

    /**
     * Interface with Camera Parameters and Modules.
     */
    public interface SettingsListener {
        public void onSettingsChanged();
    }

    /**
     * Add a SettingsListener to the SettingsManager, which will execute
     * onSettingsChanged when camera specific SharedPreferences has been updated.
     */
    public void addListener(final SettingsListener listener) {
        mListener =
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    listener.onSettingsChanged();
                }
            };
    }

    /**
     * Remove a SettingsListener.
     *
     * This should be done in onPause if a listener has been set.
     */
    public void removeListener() {
        if (mCameraSettings != null && mListener != null) {
            mCameraSettings.unregisterOnSharedPreferenceChangeListener(mListener);
            mListener = null;
        }
    }

    /**
     * SettingsCapabilities defines constraints around settings that need to be
     * queried from external sources, like the camera parameters.
     *
     * This interface is camera api agnostic.
     */
    public interface SettingsCapabilities {
        public List<Size> getSupportedPictureSizes();
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

    public static final String VALUE_STRING = "string";
    public static final String VALUE_BOOLEAN = "boolean";
    public static final String VALUE_INTEGER = "integer";

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_GLOBAL = "global";
    public static final String VALUE_CAMERA = "camera";

    // For quick lookup from id to Setting.
    public static final int SETTING_VERSION = 0;
    public static final int SETTING_LOCAL_VERSION = 1;
    public static final int SETTING_RECORD_LOCATION = 2;
    public static final int SETTING_VIDEO_QUALITY = 3;
    public static final int SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL = 4;
    public static final int SETTING_PICTURE_SIZE = 5;
    public static final int SETTING_JPEG_QUALITY = 6;
    public static final int SETTING_FOCUS_MODE = 7;
    public static final int SETTING_FLASH_MODE = 8;
    public static final int SETTING_VIDEOCAMERA_FLASH_MODE = 9;
    public static final int SETTING_WHITE_BALANCE = 10;
    public static final int SETTING_SCENE_MODE = 11;
    public static final int SETTING_EXPOSURE = 12;
    public static final int SETTING_TIMER = 13;
    public static final int SETTING_TIMER_SOUND_EFFECTS = 14;
    public static final int SETTING_VIDEO_EFFECT = 15;
    public static final int SETTING_CAMERA_ID = 16;
    public static final int SETTING_CAMERA_HDR = 17;
    public static final int SETTING_CAMERA_HDR_PLUS = 18;
    public static final int SETTING_CAMERA_FIRST_USE_HINT_SHOWN = 19;
    public static final int SETTING_VIDEO_FIRST_USE_HINT_SHOWN = 20;
    public static final int SETTING_PHOTOSPHERE_PICTURESIZE = 21;
    public static final int SETTING_STARTUP_MODULE_INDEX = 22;

    // Shared preference keys.
    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
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
    public static final String KEY_PHOTOSPHERE_PICTURESIZE = "pref_photosphere_picturesize_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";

    public static final int WHITE_BALANCE_DEFAULT_INDEX = 2;


    /**
     * Defines an interface that all queriable settings
     * must implement.
     */
    public interface Setting {
        public String getSource();
        public String getType();
        public String getDefault(Context context);
        public String getKey();
    }

    /**
     * Get the SharedPreferences needed to query/update the setting.
     */
    public SharedPreferences getSettingSource(Setting setting) {
        String source = setting.getSource();
        if (source.equals(VALUE_DEFAULT)) {
            return mDefaultSettings;
        }
        if (source.equals(VALUE_GLOBAL)) {
            return mGlobalSettings;
        }
        if (source.equals(VALUE_CAMERA)) {
            return mCameraSettings;
        }
        return null;
    }

    /**
     * Get a Setting's String value based on Setting id.
     */
    public String get(int id) {
        Setting setting = mSettingsCache.get(id);
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            return preferences.getString(setting.getKey(), setting.getDefault(mContext));
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
        boolean defaultValue = setting.getDefault(mContext).equals(VALUE_ON);
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
        int defaultValue = Integer.parseInt(setting.getDefault(mContext));
        if (preferences != null) {
            return preferences.getInt(setting.getKey(), defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Set a Setting with a String value based on Setting id.
     */
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
            preferences.edit().putString(setting.getKey(),
                setting.getDefault(mContext));
        }
    }

    public static class VersionSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return "0";
        }

        public String getKey() {
            return KEY_VERSION;
        }
    }

    public static class LocalVersionSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return "0";
        }

        public String getKey() {
            return KEY_LOCAL_VERSION;
        }
    }

    public static class LocationSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return VALUE_NONE;
        }

        public String getKey() {
            return KEY_RECORD_LOCATION;
        }
    }

    public static class PictureSizeSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return null;
        }

        public String getKey() {
            return KEY_PICTURE_SIZE;
        }
    }

    public static class DefaultCameraIdSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return "0";
        }

        public String getKey() {
            return KEY_CAMERA_ID;
        }
    }

    public static class WhiteBalanceSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_whitebalance_default);
        }

        public String getKey() {
            return KEY_WHITE_BALANCE;
        }
    }

    public static class HdrSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_hdr_default);
        }

        public String getKey() {
            return KEY_CAMERA_HDR;
        }
    }

    public static class HdrPlusSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_hdr_plus_default);
        }

        public String getKey() {
            return KEY_CAMERA_HDR_PLUS;
        }
    }

    public static class SceneModeSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_scenemode_default);
        }

        public String getKey() {
            return KEY_SCENE_MODE;
        }
    }

    public static class FlashSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_flashmode_default);
        }

        public String getKey() {
            return KEY_FLASH_MODE;
        }
    }

    public static class ExposureSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return "0";
        }

        public String getKey() {
            return KEY_EXPOSURE;
        }
    }

    public static class HintSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_BOOLEAN;
        }

        public String getDefault(Context context) {
            return VALUE_ON;
        }

        public String getKey() {
            return KEY_CAMERA_FIRST_USE_HINT_SHOWN;
        }
    }

    public static class FocusModeSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return null;
        }

        public String getKey() {
            return KEY_FOCUS_MODE;
        }
    }

    public static class TimerSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_timer_default);
        }

        public String getKey() {
            return KEY_TIMER;
        }
    }

    public static class TimerSoundSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_timer_sound_default);
        }

        public String getKey() {
            return KEY_TIMER_SOUND_EFFECTS;
        }
    }

    public static class VideoQualitySetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_video_quality_default);
        }

        public String getKey() {
            return KEY_VIDEO_QUALITY;
        }
    }

    public static class TimeLapseFrameIntervalSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_video_time_lapse_frame_interval_default);
        }

        public String getKey() {
            return KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL;
        }
    }

    public static class JpegQualitySetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return "85";
        }

        public String getKey() {
            return KEY_JPEG_QUALITY;
        }
    }

    public static class VideoFlashSetting implements Setting {
        public String getSource() {
            return VALUE_CAMERA;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_camera_video_flashmode_default);
        }

        public String getKey() {
            return KEY_VIDEOCAMERA_FLASH_MODE;
        }
    }

    public static class VideoEffectSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return context.getString(R.string.pref_video_effect_default);
        }

        public String getKey() {
            return KEY_VIDEO_EFFECT;
        }
    }

    public static class HintVideoSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_BOOLEAN;
        }

        public String getDefault(Context context) {
            return VALUE_ON;
        }

        public String getKey() {
            return KEY_VIDEO_FIRST_USE_HINT_SHOWN;
        }
    }

    public static class PhotoSpherePictureSizeSetting implements Setting {
        public String getSource() {
            return VALUE_GLOBAL;
        }

        public String getType() {
            return VALUE_STRING;
        }

        public String getDefault(Context context) {
            return null;
        }

        public String getKey() {
            return KEY_PHOTOSPHERE_PICTURESIZE;
        }
    }

    public static class StartupModuleSetting implements Setting {
        public String getSource() {
            return VALUE_DEFAULT;
        }

        public String getType() {
            return VALUE_INTEGER;
        }

        public String getDefault(Context context) {
            return "0";
        }

        public String getKey() {
            return KEY_STARTUP_MODULE_INDEX;
        }
    }

    // Utilities.
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

    /**
     * Utility method for converting a white balance value gotten from
     * a SettingsManager query to an index in the array of possible values.
     */
    public static int getWhiteBalanceIndex(Context context, String whiteBalance) {
        String[] values = context.getResources().getStringArray(
            R.array.pref_camera_whitebalance_entryvalues);

        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(whiteBalance)) {
                return i;
            }
        }
        return WHITE_BALANCE_DEFAULT_INDEX;
    }
}
