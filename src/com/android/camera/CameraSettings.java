/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;

import com.android.camera.app.AppController;
import com.android.camera.debug.Log;
import com.android.camera2.R;

/**
 *  Provides utilities and keys for Camera settings.
 *  TODO: deprecate once all modules are using SettingsManager.
 */
@Deprecated
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
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

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    /**
     * This is used to indicate the current version of the preferences file.
     * We should track this number to decide if we should change the file
     * content once we move on to a new preferences file format.
     */
    public static final int CURRENT_VERSION = 1;

    private static final Log.Tag TAG = new Log.Tag("CameraSettings");

    private final AppController mActivityController;

    public CameraSettings(AppController app, Parameters parameters,
                          int cameraId, CameraInfo[] cameraInfo) {
        mActivityController = app;
    }

    public static int getMaxVideoDuration(Context context) {
        int duration = 0;  // in milliseconds, 0 means unlimited.
        try {
            duration = context.getResources().getInteger(R.integer.max_video_recording_length);
        } catch (Resources.NotFoundException ex) {
        }
        return duration;
    }

    /**
     * Upgrade the preferences file from previous older versions to the one we
     * are using. No-op if the versions match.
     *
     * @param pref The preferences file.
     * @param numberOfCameras The number of cameras available on this device.
     */
    public static void upgradeGlobalPreferences(SharedPreferences pref, int numberOfCameras) {
        upgradeOldVersion(pref);
        upgradeCameraId(pref, numberOfCameras);
    }

    private static void upgradeOldVersion(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_VERSION) {
            return;
        }

        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.apply();
    }

    /**
     * Updates the preferred camera ID to make sure it's a valid one on the
     * current device. This solves the situation when the user backup all the
     * data and moved to a new device.
     *
     * @param pref The preferences file.
     * @param numberOfCameras The number of cameras available on this device.
     */
    private static void upgradeCameraId(SharedPreferences pref, int numberOfCameras) {
        // The id stored in the preference may be out of range if we are running
        // inside the emulator and a webcam is removed.
        // Note: This method accesses the global preferences directly, not the
        // combo preferences.
        int cameraId = readPreferredCameraId(pref);
        if (cameraId == 0) {
            return; // fast path
        }

        if (cameraId < 0 || cameraId >= numberOfCameras) {
            writePreferredCameraId(pref, 0);
        }
    }

    public static int readPreferredCameraId(SharedPreferences pref) {
        return Integer.parseInt(pref.getString(KEY_CAMERA_ID, "0"));
    }

    public static void writePreferredCameraId(SharedPreferences pref,
            int cameraId) {
        Editor editor = pref.edit();
        editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
        editor.apply();
    }
}
