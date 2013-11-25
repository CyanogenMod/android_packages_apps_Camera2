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

import android.util.SparseArray;

import com.android.camera.settings.SettingsManager.Setting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * SettingsCache class caches Settings descriptors and also maintains
 * an efficient mapping between SharedPreferences String keys and Setting id.
 */
public class SettingsCache {
    private SparseArray<Setting> mCache = new SparseArray<Setting>();
    private static Map<String, Integer> mKeyMap = initKeyMap();

    private static HashMap<String, Integer> initKeyMap() {
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        map.put(SettingsManager.KEY_VERSION, SettingsManager.SETTING_VERSION);
        map.put(SettingsManager.KEY_LOCAL_VERSION, SettingsManager.SETTING_LOCAL_VERSION);
        map.put(SettingsManager.KEY_RECORD_LOCATION, SettingsManager.SETTING_RECORD_LOCATION);
        map.put(SettingsManager.KEY_VIDEO_QUALITY, SettingsManager.SETTING_VIDEO_QUALITY);
        map.put(SettingsManager.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                SettingsManager.SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        map.put(SettingsManager.KEY_PICTURE_SIZE, SettingsManager.SETTING_PICTURE_SIZE);
        map.put(SettingsManager.KEY_JPEG_QUALITY, SettingsManager.SETTING_JPEG_QUALITY);
        map.put(SettingsManager.KEY_FOCUS_MODE, SettingsManager.SETTING_FOCUS_MODE);
        map.put(SettingsManager.KEY_FLASH_MODE, SettingsManager.SETTING_FLASH_MODE);
        map.put(SettingsManager.KEY_VIDEOCAMERA_FLASH_MODE,
                SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE);
        map.put(SettingsManager.KEY_WHITE_BALANCE, SettingsManager.SETTING_WHITE_BALANCE);
        map.put(SettingsManager.KEY_SCENE_MODE, SettingsManager.SETTING_SCENE_MODE);
        map.put(SettingsManager.KEY_EXPOSURE, SettingsManager.SETTING_EXPOSURE);
        map.put(SettingsManager.KEY_TIMER, SettingsManager.SETTING_TIMER);
        map.put(SettingsManager.KEY_TIMER_SOUND_EFFECTS,
                SettingsManager.SETTING_TIMER_SOUND_EFFECTS);
        map.put(SettingsManager.KEY_VIDEO_EFFECT, SettingsManager.SETTING_VIDEO_EFFECT);
        map.put(SettingsManager.KEY_CAMERA_ID, SettingsManager.SETTING_CAMERA_ID);
        map.put(SettingsManager.KEY_CAMERA_HDR, SettingsManager.SETTING_CAMERA_HDR);
        map.put(SettingsManager.KEY_CAMERA_HDR_PLUS, SettingsManager.SETTING_CAMERA_HDR_PLUS);
        map.put(SettingsManager.KEY_CAMERA_FIRST_USE_HINT_SHOWN,
                SettingsManager.SETTING_CAMERA_FIRST_USE_HINT_SHOWN);
        map.put(SettingsManager.KEY_VIDEO_FIRST_USE_HINT_SHOWN,
                SettingsManager.SETTING_VIDEO_FIRST_USE_HINT_SHOWN);
        map.put(SettingsManager.KEY_PHOTOSPHERE_PICTURESIZE,
                SettingsManager.SETTING_PHOTOSPHERE_PICTURESIZE);
        map.put(SettingsManager.KEY_STARTUP_MODULE_INDEX,
                SettingsManager.SETTING_STARTUP_MODULE_INDEX);
        return map;
    }

    /**
     * Gets a pre-initialized Settings descriptor from the SettingsCache
     * if the same descriptor has already been used in this session.
     * Otherwise allocates a new Settings descriptor and caches it.
     */
    public Setting get(int id) {
        Setting setting = mCache.get(id);
        if (setting == null) {
            setting = settingFromId(id);
            mCache.put(id, setting);
        }
        return setting;
    }

    /**
     * Efficiently convert a SharedPreference String key to a Settings
     * descriptor id.
     */
    public Integer getId(String key) {
        return mKeyMap.get(key);
    }

    private Setting settingFromId(int id) {
        switch (id) {
        case SettingsManager.SETTING_VERSION:
            return new SettingsManager.VersionSetting();
        case SettingsManager.SETTING_LOCAL_VERSION:
            return new SettingsManager.LocalVersionSetting();
        case SettingsManager.SETTING_RECORD_LOCATION:
            return new SettingsManager.LocationSetting();
        case SettingsManager.SETTING_VIDEO_QUALITY:
            return new SettingsManager.VideoQualitySetting();
        case SettingsManager.SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL:
            return new SettingsManager.TimeLapseFrameIntervalSetting();
        case SettingsManager.SETTING_PICTURE_SIZE:
            return new SettingsManager.PictureSizeSetting();
        case SettingsManager.SETTING_JPEG_QUALITY:
            return new SettingsManager.JpegQualitySetting();
        case SettingsManager.SETTING_FOCUS_MODE:
            return new SettingsManager.FocusModeSetting();
        case SettingsManager.SETTING_FLASH_MODE:
            return new SettingsManager.FlashSetting();
        case SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE:
            return new SettingsManager.VideoFlashSetting();
        case SettingsManager.SETTING_WHITE_BALANCE:
            return new SettingsManager.WhiteBalanceSetting();
        case SettingsManager.SETTING_SCENE_MODE:
            return new SettingsManager.SceneModeSetting();
        case SettingsManager.SETTING_EXPOSURE:
            return new SettingsManager.ExposureSetting();
        case SettingsManager.SETTING_TIMER:
            return new SettingsManager.TimerSetting();
        case SettingsManager.SETTING_TIMER_SOUND_EFFECTS:
            return new SettingsManager.TimerSoundSetting();
        case SettingsManager.SETTING_VIDEO_EFFECT:
            return new SettingsManager.VideoEffectSetting();
        case SettingsManager.SETTING_CAMERA_ID:
            return new SettingsManager.DefaultCameraIdSetting();
        case SettingsManager.SETTING_CAMERA_HDR:
            return new SettingsManager.HdrSetting();
        case SettingsManager.SETTING_CAMERA_HDR_PLUS:
            return new SettingsManager.HdrPlusSetting();
        case SettingsManager.SETTING_CAMERA_FIRST_USE_HINT_SHOWN:
            return new SettingsManager.HintSetting();
        case SettingsManager.SETTING_VIDEO_FIRST_USE_HINT_SHOWN:
            return new SettingsManager.HintVideoSetting();
        case SettingsManager.SETTING_PHOTOSPHERE_PICTURESIZE:
            return new SettingsManager.PhotoSpherePictureSizeSetting();
        case SettingsManager.SETTING_STARTUP_MODULE_INDEX:
            return new SettingsManager.StartupModuleSetting();
        default:
            return null;
        }
    }
}
