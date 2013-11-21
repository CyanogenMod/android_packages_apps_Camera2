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

public class SettingsManager {
    private static final String TAG = "CAM_SettingsManager";

    private Context mContext;
    private SharedPreferences mDefaultSettings;
    private SharedPreferences mGlobalSettings;
    private SharedPreferences mCameraSettings;
    private OnSharedPreferenceChangeListener mListener;
    private SettingsCapabilities mCapabilities;

    private int mCameraId = -1;

    public SettingsManager(Context context,
            OnSharedPreferenceChangeListener globalListener,
            int nCameras) {
        mContext = context;
        mDefaultSettings = PreferenceManager.getDefaultSharedPreferences(context);
        initGlobal(globalListener);

        DefaultCameraIdSetting cameraIdSetting = new DefaultCameraIdSetting();
        int cameraId = Integer.parseInt(get(cameraIdSetting));
        if (cameraId < 0 || cameraId >= nCameras) {
            setDefault(cameraIdSetting);
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

    public void removeListener() {
        if (mCameraSettings != null && mListener != null) {
            mCameraSettings.unregisterOnSharedPreferenceChangeListener(mListener);
            mListener = null;
        }
    }

    public interface SettingsCapabilities {
        public List<Size> getSupportedPictureSizes();
    }

    public List<Size> getSupportedPictureSizes() {
        if (mCapabilities != null) {
            return mCapabilities.getSupportedPictureSizes();
        } else {
            return null;
        }
    }

    public int getRegisteredCameraId() {
        return mCameraId;
    }

    /**
     * Manage individual settings.
     */
    public static final String VALUE_NONE = "none";
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    public static final String VALUE_STRING = "string";
    public static final String VALUE_BOOLEAN = "boolean";
    public static final String VALUE_INTEGER = "integer";

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_GLOBAL = "global";
    public static final String VALUE_CAMERA = "camera";

    // TODO: Order theses by global/camera.
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
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN = "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN = "pref_video_first_use_hint_shown_key";
    public static final String KEY_PHOTOSPHERE_PICTURESIZE = "pref_photosphere_picturesize_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";

    public static final int WHITE_BALANCE_DEFAULT_INDEX = 2;

    public interface Setting {
        public String getSource();
        public String getType();
        public String getDefault(Context context);
        public String getKey();
    }

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

    public String get(Setting setting) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            return preferences.getString(setting.getKey(), setting.getDefault(mContext));
        } else {
            return null;
        }
    }

    public boolean getBoolean(Setting setting) {
        SharedPreferences preferences = getSettingSource(setting);
        boolean defaultValue = setting.getDefault(mContext).equals(VALUE_ON);
        if (preferences != null) {
            return preferences.getBoolean(setting.getKey(), defaultValue);
        } else {
            return defaultValue;
        }
    }

    public int getInt(Setting setting) {
        SharedPreferences preferences = getSettingSource(setting);
        int defaultValue = Integer.parseInt(setting.getDefault(mContext));
        if (preferences != null) {
            return preferences.getInt(setting.getKey(), defaultValue);
        } else {
            return defaultValue;
        }
    }

    public String get(String key) {
        Setting setting = settingFromKey(key);
        if (setting == null) {
            return null;
        }
        return get(setting);
    }

    public void set(String key, String value) {
        Setting setting = settingFromKey(key);
        if (setting != null) {
            set(setting, value);
        }
    }

    public void set(Setting setting, String value) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putString(setting.getKey(), value).apply();
        }
    }

    public void setBoolean(Setting setting, boolean value) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putBoolean(setting.getKey(), value).apply();
        }
    }

    public void setInt(Setting setting, int value) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putInt(setting.getKey(), value).apply();
        }
    }

    public boolean isSet(Setting setting) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            return (preferences.getString(setting.getKey(), null) != null);
        } else {
            return false;
        }
    }

    public void setDefault(Setting setting) {
        SharedPreferences preferences = getSettingSource(setting);
        if (preferences != null) {
            preferences.edit().putString(setting.getKey(),
                setting.getDefault(mContext));
        }
    }

    public Setting settingFromKey(String key) {
        if (key.equals(KEY_VERSION)) {
            return new VersionSetting();
        }
        if (key.equals(KEY_LOCAL_VERSION)) {
            return new LocalVersionSetting();
        }
        if (key.equals(KEY_RECORD_LOCATION)) {
            return new LocationSetting();
        }
        if (key.equals(KEY_VIDEO_QUALITY)) {
            return new VideoQualitySetting();
        }
        if (key.equals(KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL)) {
            return new TimeLapseFrameIntervalSetting();
        }
        if (key.equals(KEY_PICTURE_SIZE)) {
            return new PictureSizeSetting();
        }
        if (key.equals(KEY_JPEG_QUALITY)) {
            return new JpegQualitySetting();
        }
        if (key.equals(KEY_FOCUS_MODE)) {
            return new FocusModeSetting();
        }
        if (key.equals(KEY_FLASH_MODE)) {
            return new FlashSetting();
        }
        if (key.equals(KEY_VIDEOCAMERA_FLASH_MODE)) {
            return new VideoFlashSetting();
        }
        if (key.equals(KEY_WHITE_BALANCE)) {
            return new WhiteBalanceSetting();
        }
        if (key.equals(KEY_SCENE_MODE)) {
            return new SceneModeSetting();
        }
        if (key.equals(KEY_EXPOSURE)) {
            return new ExposureSetting();
        }
        if (key.equals(KEY_TIMER)) {
            return new TimerSetting();
        }
        if (key.equals(KEY_TIMER_SOUND_EFFECTS)) {
            return new TimerSoundSetting();
        }
        if (key.equals(KEY_VIDEO_EFFECT)) {
            return new VideoEffectSetting();
        }
        if (key.equals(KEY_CAMERA_ID)) {
            return new DefaultCameraIdSetting();
        }
        if (key.equals(KEY_CAMERA_HDR)) {
            return new HdrSetting();
        }
        if (key.equals(KEY_CAMERA_HDR_PLUS)) {
            return new HdrPlusSetting();
        }
        if (key.equals(KEY_CAMERA_FIRST_USE_HINT_SHOWN)) {
            return new HintSetting();
        }
        if (key.equals(KEY_VIDEO_FIRST_USE_HINT_SHOWN)) {
            return new HintVideoSetting();
        }
        if (key.equals(KEY_PHOTOSPHERE_PICTURESIZE)) {
            return new PhotoSpherePictureSizeSetting();
        }
        if (key.equals(KEY_STARTUP_MODULE_INDEX)) {
            return new StartupModuleSetting();
        }
        return null;
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
            return null;
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

    /**
     * Utilities.
     * TODO: refactor this into a separate utils module.
     */

    public String getValueFromPreference(ListPreference pref) {
        String value = pref.getValue();
        if (value == null) {
            value = get(pref.getKey());
        }
        return value;
    }

    public void setValueFromPreference(ListPreference pref, String value) {
        boolean set = pref.setValue(value);
        if (!set) {
            set(pref.getKey(), value);
        }
    }

    public void setValueIndexFromPreference(ListPreference pref, int index) {
        boolean set = pref.setValueIndex(index);
        if (!set) {
            String value = pref.getValueAtIndex(index);
            set(pref.getKey(), value);
        }
    }

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
