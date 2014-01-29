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

package com.android.camera;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.CamcorderProfile;

import com.android.camera.app.CameraManager;
import com.android.camera.app.LocationManager;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsManager.SettingsCapabilities;
import com.android.camera.ui.SettingsView;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.List;

public class SettingsController implements SettingsView.SettingsViewController {
    private static final String TAG = "SettingsController";

    private final CameraActivity mActivity;
    private final SettingsManager mSettingsManager;
    private final LocationManager mLocationManager;

    public SettingsController(CameraActivity activity) {
        mActivity = activity;
        mSettingsManager = activity.getSettingsManager();
        mLocationManager = activity.getLocationManager();
    }

    public void syncLocationManager() {
        String value = mSettingsManager.get(SettingsManager.SETTING_RECORD_LOCATION);
        mLocationManager.recordLocation(value.equals(SettingsManager.VALUE_ON));
    }

    @Override
    public void setLocation(boolean on) {
        if (!mActivity.isPaused()) {
            mSettingsManager.set(SettingsManager.SETTING_RECORD_LOCATION,
                (on ? SettingsManager.VALUE_ON : SettingsManager.VALUE_OFF));
            LocationManager locationManager = mActivity.getLocationManager();
            locationManager.recordLocation(on);
        }
    }

    @Override
    public void setPictureSize(String size) {
        if (!mActivity.isPaused()) {
            mSettingsManager.set(SettingsManager.SETTING_PICTURE_SIZE, size);
        }
    }

    @Override
    public String[] getSupportedVideoQualityEntries() {
        ArrayList<String> supported = new ArrayList<String>();
        int cameraId = mSettingsManager.getRegisteredCameraId();

        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
            String entry = mActivity.getString(R.string.pref_video_quality_entry_1080p);
            supported.add(entry);
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
            String entry = mActivity.getString(R.string.pref_video_quality_entry_720p);
            supported.add(entry);
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
            String entry = mActivity.getString(R.string.pref_video_quality_entry_480p);
            supported.add(entry);
        }
        return supported.toArray(new String[supported.size()]);
    }

    @Override
    public void setVideoQuality(String quality) {
        if (!mActivity.isPaused()) {
            mSettingsManager.set(SettingsManager.SETTING_VIDEO_QUALITY, quality);
        }
    }

    @Override
    public String[] getSupportedDefaultCameras() {
        List<String> supported = mActivity.getSupportedModeNames();
        return supported.toArray(new String[supported.size()]);
    }

    @Override
    public void setDefaultCamera(int id) {
        mSettingsManager.setInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX, id);
    }

    public static SettingsCapabilities
            getSettingsCapabilities(CameraManager.CameraProxy camera) {
        final Parameters parameters = camera.getParameters();
        return (new SettingsCapabilities() {
                @Override
                public String[] getSupportedExposureValues() {
                    int max = parameters.getMaxExposureCompensation();
                    int min = parameters.getMinExposureCompensation();
                    float step = parameters.getExposureCompensationStep();
                    int maxValue = Math.min(3, (int) Math.floor(max * step));
                    int minValue = Math.max(-3, (int) Math.ceil(min * step));
                    String[] entryValues = new String[maxValue - minValue + 1];
                    for (int i = minValue; i <= maxValue; ++i) {
                        entryValues[i - minValue] = Integer.toString(Math.round(i / step));
                    }
                    return entryValues;
                }

                @Override
                public String[] getSupportedCameraIds() {
                    int numberOfCameras = Camera.getNumberOfCameras();
                    String[] cameraIds = new String[numberOfCameras];
                    for (int i = 0; i < numberOfCameras; i++) {
                        cameraIds[i] = "" + i;
                    }
                    return cameraIds;
                }
            });
    }
}
