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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

/**
 * {@code RecordLocationPreference} is used to keep the "store locaiton"
 * option in {@code SharedPreference}.
 */
public class RecordLocationPreference extends IconListPreference {

    public static final String VALUE_NONE = "none";
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    private final ContentResolver mResolver;

    public RecordLocationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResolver = context.getContentResolver();
    }

    public static boolean get(
            SharedPreferences pref, ContentResolver resolver) {
        String value = pref.getString(
                CameraSettings.KEY_RECORD_LOCATION, VALUE_NONE);
        return VALUE_ON.equals(value);
    }

    public static boolean isSet(SharedPreferences pref) {
        String value = pref.getString(
                CameraSettings.KEY_RECORD_LOCATION, VALUE_NONE);
        return !VALUE_NONE.equals(value);
    }

    @Override
    public String getValue() {
        return get(getSharedPreferences(), mResolver) ? VALUE_ON : VALUE_OFF;
    }
}
