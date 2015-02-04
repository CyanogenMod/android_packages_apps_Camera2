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

import com.android.camera.app.LocationManager;
import com.android.camera.util.ApiHelper;
import com.android.camera2.R;

/**
 * Keys is a class for storing SharedPreferences keys and configuring
 * their defaults.
 *
 * For each key that has a default value and set of possible values, it
 * stores those defaults so they can be used by the SettingsManager
 * on lookup.  This step is optional, and it can be done anytime before
 * a setting is accessed by the SettingsManager API.
 */
public class Keys {

    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY_BACK = "pref_video_quality_back_key";
    public static final String KEY_VIDEO_QUALITY_FRONT = "pref_video_quality_front_key";
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
    public static final String KEY_HDR_SUPPORT_MODE_BACK_CAMERA =
            "pref_hdr_support_mode_back_camera";
    public static final String KEY_UPGRADE_VERSION = "pref_upgrade_version";
    public static final String KEY_REQUEST_RETURN_HDR_PLUS = "pref_request_return_hdr_plus";
    public static final String KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING =
            "pref_should_show_refocus_viewer_cling";
    public static final String KEY_EXPOSURE_COMPENSATION_ENABLED =
            "pref_camera_exposure_compensation_key";

    /**
     * Whether the user has chosen an aspect ratio on the first run dialog.
     */
    public static final String KEY_USER_SELECTED_ASPECT_RATIO = "pref_user_selected_aspect_ratio";

    public static final String KEY_COUNTDOWN_DURATION = "pref_camera_countdown_duration_key";
    public static final String KEY_HDR_PLUS_FLASH_MODE = "pref_hdr_plus_flash_mode";
    public static final String KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING =
            "pref_should_show_settings_button_cling";
    public static final String KEY_HAS_SEEN_PERMISSIONS_DIALOGS = "pref_has_seen_permissions_dialogs";
    public static final String KEY_POWER_SHUTTER = "pref_power_shutter";

    /**
     * Set some number of defaults for the defined keys.
     * It's not necessary to set all defaults.
     */
    public static void setDefaults(SettingsManager settingsManager, Context context) {
        settingsManager.setDefaults(KEY_COUNTDOWN_DURATION, 0,
            context.getResources().getIntArray(R.array.pref_countdown_duration));

        settingsManager.setDefaults(KEY_CAMERA_ID,
            context.getString(R.string.pref_camera_id_default),
            context.getResources().getStringArray(R.array.camera_id_entryvalues));

        settingsManager.setDefaults(KEY_SCENE_MODE,
            context.getString(R.string.pref_camera_scenemode_default),
            context.getResources().getStringArray(R.array.pref_camera_scenemode_entryvalues));

        settingsManager.setDefaults(KEY_FLASH_MODE,
            context.getString(R.string.pref_camera_flashmode_default),
            context.getResources().getStringArray(R.array.pref_camera_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_HDR_SUPPORT_MODE_BACK_CAMERA,
            context.getString(R.string.pref_camera_hdr_supportmode_none),
            context.getResources().getStringArray(R.array.pref_camera_hdr_supportmode_entryvalues));

        settingsManager.setDefaults(KEY_CAMERA_HDR, false);
        settingsManager.setDefaults(KEY_CAMERA_HDR_PLUS, false);

        settingsManager.setDefaults(KEY_CAMERA_FIRST_USE_HINT_SHOWN, true);

        settingsManager.setDefaults(KEY_FOCUS_MODE,
            context.getString(R.string.pref_camera_focusmode_default),
            context.getResources().getStringArray(R.array.pref_camera_focusmode_entryvalues));

        String videoQualityBackDefaultValue = context.getString(R.string.pref_video_quality_large);
        // TODO: We tweaked the default setting based on model string which is not ideal. Detecting
        // CamcorderProfile capability is a better way to get this job done. However,
        // |CamcorderProfile.hasProfile| needs camera id info. We need a way to provide camera id to
        // this method. b/17445274
        // Don't set the default resolution to be large if the device supports 4k video.
        if (ApiHelper.IS_NEXUS_6) {
            videoQualityBackDefaultValue = context.getString(R.string.pref_video_quality_medium);
        }
        settingsManager.setDefaults(
            KEY_VIDEO_QUALITY_BACK,
            videoQualityBackDefaultValue,
            context.getResources().getStringArray(R.array.pref_video_quality_entryvalues));
        if (!settingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_VIDEO_QUALITY_BACK)) {
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,
                                         Keys.KEY_VIDEO_QUALITY_BACK);
        }

        settingsManager.setDefaults(KEY_VIDEO_QUALITY_FRONT,
            context.getString(R.string.pref_video_quality_large),
            context.getResources().getStringArray(R.array.pref_video_quality_entryvalues));
        if (!settingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_VIDEO_QUALITY_FRONT)) {
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,
                                         Keys.KEY_VIDEO_QUALITY_FRONT);
        }

        settingsManager.setDefaults(KEY_JPEG_QUALITY,
            context.getString(R.string.pref_camera_jpeg_quality_normal),
            context.getResources().getStringArray(
                R.array.pref_camera_jpeg_quality_entryvalues));

        settingsManager.setDefaults(KEY_VIDEOCAMERA_FLASH_MODE,
            context.getString(R.string.pref_camera_video_flashmode_default),
            context.getResources().getStringArray(
                R.array.pref_camera_video_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_VIDEO_EFFECT,
            context.getString(R.string.pref_video_effect_default),
            context.getResources().getStringArray(R.array.pref_video_effect_entryvalues));

        settingsManager.setDefaults(KEY_VIDEO_FIRST_USE_HINT_SHOWN, true);

        settingsManager.setDefaults(KEY_STARTUP_MODULE_INDEX, 0,
            context.getResources().getIntArray(R.array.camera_modes));

        settingsManager.setDefaults(KEY_CAMERA_MODULE_LAST_USED,
            context.getResources().getInteger(R.integer.camera_mode_photo),
            context.getResources().getIntArray(R.array.camera_modes));

        settingsManager.setDefaults(KEY_CAMERA_PANO_ORIENTATION,
            context.getString(R.string.pano_orientation_horizontal),
            context.getResources().getStringArray(
                R.array.pref_camera_pano_orientation_entryvalues));

        settingsManager.setDefaults(KEY_CAMERA_GRID_LINES, false);

        settingsManager.setDefaults(KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING, true);

        settingsManager.setDefaults(KEY_HDR_PLUS_FLASH_MODE,
            context.getString(R.string.pref_camera_hdr_plus_flashmode_default),
            context.getResources().getStringArray(
                R.array.pref_camera_hdr_plus_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING, true);

    }

    /** Helper functions for some defined keys. */

    /**
     * Returns whether the camera has been set to back facing in settings.
     */
    public static boolean isCameraBackFacing(SettingsManager settingsManager,
                                             String moduleScope) {
        return settingsManager.isDefault(moduleScope, KEY_CAMERA_ID);
    }

    /**
     * Returns whether hdr plus mode is set on.
     */
    public static boolean isHdrPlusOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_HDR_PLUS);
    }

    /**
     * Returns whether hdr mode is set on.
     */
    public static boolean isHdrOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_HDR);
    }

    /**
     * Returns whether the app should return to hdr plus mode if possible.
     */
    public static boolean requestsReturnToHdrPlus(SettingsManager settingsManager,
                                                  String moduleScope) {
        return settingsManager.getBoolean(moduleScope, KEY_REQUEST_RETURN_HDR_PLUS);
    }

    /**
     * Returns whether grid lines are set on.
     */
    public static boolean areGridLinesOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_GRID_LINES);
    }

    /**
     * Returns whether power shutter is set on.
     */
    public static boolean isPowerShutterOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                KEY_POWER_SHUTTER);
    }
}

