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

import com.android.camera.ui.SettingsView;
import com.android.camera.SettingsManager.LocationSetting;

public class SettingsController implements SettingsView.SettingsListener {
    private static final String TAG = "CAM_SettingsController";

    private CameraActivity mActivity;
    private SettingsManager mSettingsManager;

    SettingsController(CameraActivity activity, SettingsManager manager) {
        mActivity = activity;
        mSettingsManager = manager;
    }

    @Override
    public void setLocation(boolean on) {
        if (!mActivity.isPaused()) {
            LocationSetting locationPreference = mSettingsManager.getLocationSetting();
            locationPreference.set(on ? SettingsManager.VALUE_ON : SettingsManager.VALUE_OFF);

            LocationManager locationManager = mActivity.getLocationManager();
            locationManager.recordLocation(on);
        }
    }

    @Override
    public void setPictureSize(int size) {
    }

    @Override
    public void setVideoResolution(int resolution) {
    }

    @Override
    public void setDefaultCamera(int id) {
    }
}