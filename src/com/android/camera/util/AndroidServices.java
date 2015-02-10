/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build.VERSION_CODES;
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;

/**
 * Initializable singleton set of android service providers.
 * Some services may be null
 */
public class AndroidServices {
    private static Tag TAG = new Tag("AndroidServices");
    /** Log all requests; otherwise will only log long requests. */
    private static final boolean LOG_ALL_REQUESTS = false;
    /** Log requests if this threshold is exceeded. */
    private static final int LOG_THRESHOLD_MILLIS = 10;

    private static class Singleton {
        private static final AndroidServices INSTANCE =
              new AndroidServices(AndroidContext.instance().get());
    }

    public static AndroidServices instance() {
        return Singleton.INSTANCE;
    }

    private final Context mContext;
    private AndroidServices(Context context) {
        mContext = context;
    }

    public ActivityManager provideActivityManager() {
        return (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    }

    public AudioManager provideAudioManager() {
        return (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    public AccessibilityManager provideAccessibilityManager() {
        return (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    public CameraManager provideCameraManager() {
        CameraManager cameraManager = null;

        try {
            Object service = ApiHelper.HAS_CAMERA_2_API ? getSystemService(Context.CAMERA_SERVICE)
                  : null;
            cameraManager = (CameraManager) service;
        } catch (IllegalStateException ex) {
            Log.e(TAG, "Could not get the CAMERA_SERVICE");
            cameraManager = null;
        }

        return cameraManager;
    }

    public DevicePolicyManager provideDevicePolicyManager() {
        return (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public DisplayManager provideDisplayManager() {
        return (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
    }

    public KeyguardManager provideKeyguardManager() {
        return (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    public LayoutInflater provideLayoutInflater() {
        return (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public LocationManager provideLocationManager() {
        return (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    public NotificationManager provideNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public PowerManager providePowerManager() {
        return (PowerManager) getSystemService(Context.POWER_SERVICE);
    }

    public SensorManager provideSensorManager() {
        return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    public Vibrator provideVibrator() {
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    public WindowManager provideWindowManager() {
        return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    private Object getSystemService(String service) {
        try {
            long start = System.currentTimeMillis();
            Object result = mContext.getSystemService(service);
            long duration = System.currentTimeMillis() - start;
            if (duration > LOG_THRESHOLD_MILLIS) {
                Log.w(TAG, "Warning: providing system service " + service + " took " +
                        duration + "ms");
            } else if (LOG_ALL_REQUESTS) {
                Log.v(TAG, "Provided system service " + service + " in " + duration + "ms");
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
