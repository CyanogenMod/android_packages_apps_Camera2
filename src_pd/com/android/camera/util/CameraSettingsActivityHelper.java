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

package com.android.camera.util;

import android.content.Context;
import android.preference.PreferenceFragment;

import com.android.camera.settings.ListPreferenceFiller;
import com.android.camera.settings.SettingsManager;

import java.util.List;

public class CameraSettingsActivityHelper {
    public static void addAdditionalPreferences(PreferenceFragment fragment, Context context) {
    }

    public static void onSizesLoaded(PreferenceFragment fragment,
            List<Size> backCameraSizes, ListPreferenceFiller cameraSizesFiller) {
    }

    public static void verifyDefaults(SettingsManager settingsManager, Context context) {
    }
}
