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

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.util.Log;

import com.android.camera.app.CameraManager;
import com.android.camera.ui.SettingsView;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsManager.SettingsCapabilities;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.List;

public class SettingsController implements SettingsView.SettingsViewListener {
    private static final String TAG = "CAM_SettingsController";

    private CameraActivity mActivity;
    private SettingsManager mSettingsManager;
    private LocationManager mLocationManager;

    public SettingsController(CameraActivity activity, SettingsManager settingsManager,
            LocationManager locationManager) {
        mActivity = activity;
        mSettingsManager = settingsManager;
        mLocationManager = locationManager;
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
    public String[] getSupportedPictureSizeEntries() {
        ArrayList<String> supported = new ArrayList<String>();
        List<Size> sizes = mSettingsManager.getSupportedPictureSizes();
        String[] entries = mActivity.getResources().getStringArray(
            R.array.pref_camera_picturesize_entries);
        String[] values = mActivity.getResources().getStringArray(
            R.array.pref_camera_picturesize_entryvalues);

        if (entries.length != values.length) {
            return supported.toArray(new String[0]);
        }

        int i = 0;
        for (String value : values) {
            int index = value.indexOf('x');
            if (index >= 0) {
                int width = Integer.parseInt(value.substring(0, index));
                int height = Integer.parseInt(value.substring(index + 1));
                for (Size size : sizes) {
                    if (size.width == width && size.height == height) {
                        supported.add(entries[i]);
                    }
                }
            }
            i++;
        }
        return supported.toArray(new String[supported.size()]);
    }

    @Override
    public void setPictureSize(String size) {
        if (!mActivity.isPaused()) {
            mSettingsManager.set(SettingsManager.SETTING_PICTURE_SIZE, size);
        }
    }

    @Override
    public String[] getSupportedVideoQualityEntries() {
        ArrayList<String> supported = new ArrayList();
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
    public void setDefaultCamera(int id) {
        mSettingsManager.setInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX, id);
    }

    public static SettingsCapabilities
            getSettingsCapabilities(CameraManager.CameraProxy camera) {
        Parameters parameters = camera.getParameters();
        final List<Size> sizes = parameters.getSupportedPictureSizes();
        return (new SettingsCapabilities() {
                @Override
                public List<Size> getSupportedPictureSizes() {
                    return sizes;
                }
            });
    }
}
