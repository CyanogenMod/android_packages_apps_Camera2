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

import android.content.ContentResolver;
import android.content.Context;
import android.location.Location;
import android.util.Log;

public class SettingsManager {
    private static final String TAG = "CAM_SettingsManager";

    private Context mContext;
    // TODO(edahlgren): plumb this into this class.
    private ComboPreferences mPreferences;

    // List settings here.
    private LocationSetting mLocationSetting;

    public SettingsManager(Context context) {
        mContext = context;

        mPreferences = new ComboPreferences(context);
        // Local preferences must be non-null to edit preferences.
        int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
        mPreferences.setLocalId(context, cameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        init();
    }

    private void init() {
        mLocationSetting = new LocationSetting();
        // initialize other settings here.
    }

    // There's global and camera dependent preferences.
    // We need to distinguish between the two.
    public static final String VALUE_GLOBAL = "global";
    public static final String VALUE_CAMERA = "camera";

    public static final String VALUE_NONE = "none";
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    public class LocationSetting {
        public String isGlobal() {
            return VALUE_GLOBAL;
        }

        public String get() {
            return mPreferences.getString(CameraSettings.KEY_RECORD_LOCATION,
                VALUE_NONE);
        }

        public void set(String value) {
            mPreferences.edit()
                .putString(CameraSettings.KEY_RECORD_LOCATION, value)
                .apply();
        }
    }

    public LocationSetting getLocationSetting() {
        return mLocationSetting;
    }
}
