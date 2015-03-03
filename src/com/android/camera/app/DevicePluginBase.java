/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.camera.app;

import android.content.Context;

import com.android.ex.camera2.portability.CameraAgent;

/**
 * The interface defining device-specific application hooks
 */
public class DevicePluginBase {
    public void onCreate(Context context) {
    }

    public void onDestroy() {
    }

    public void onCameraOpened(CameraAgent.CameraProxy camera) {
    }
}
