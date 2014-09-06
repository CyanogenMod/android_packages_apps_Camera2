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

package com.android.camera.one.v2;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.util.DisplayMetrics;

import com.android.camera.SoundPlayer;
import com.android.camera.one.OneCamera;
import com.android.camera.util.Size;

public class OneCameraCreator {
    public static OneCamera create(Context context, boolean useHdr, CameraDevice device,
            CameraCharacteristics characteristics, Size pictureSize, int maxMemoryMB,
            DisplayMetrics displayMetrics, SoundPlayer soundPlayer) {
        // TODO: Might want to switch current camera to vendor HDR.
        return new OneCameraImpl(device, characteristics, pictureSize);
    }
}
