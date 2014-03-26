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
import android.hardware.Camera;

public class UsageStatistics {
    public static void initialize(Context context) {}
    public static void photoInteraction(String fileNameHash, int interactionType, int cause) {}
    public static void foregrounded(int source) {}
    public static void captureEvent(int mode, String fileNameHash,
                                    Camera.Parameters parameters, Float duration) {}
    public static void changePreference(String preference, String newValue, String oldValue) {}
    public static void cameraFailure(int cause) {}
    public static void tapToFocus() {}
    public static void changeScreen(int newScreen, Integer interactionCause) {}

    public static String hashFileName(String fileName) {
        return "";
    }
}
