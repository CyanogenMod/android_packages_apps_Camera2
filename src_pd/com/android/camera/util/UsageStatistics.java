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
import com.android.camera.exif.ExifInterface;

public class UsageStatistics {
    private static UsageStatistics sInstance;

    public static UsageStatistics instance() {
        if (sInstance == null) {
            sInstance = new UsageStatistics();
        }
        return sInstance;
    }

    public void initialize(Context context) {
    }

    public void mediaInteraction(String ref, int interactionType, int cause) {
    }

    public void foregrounded(int source, int mode) {
    }

    public void videoCaptureDoneEvent(String ref, long durationMsec, boolean front, int width,
                                      int height, long size) {
    }

    public void photoCaptureDoneEvent(int mode, String fileRef, ExifInterface exifRef,
                                      boolean front, boolean isHDR, float zoom) {
    }

    public void cameraFailure(int cause) {
    }

    public void changeScreen(int newScreen, Integer interactionCause) {
    }

    public void controlUsed(int control) {
    }
}
