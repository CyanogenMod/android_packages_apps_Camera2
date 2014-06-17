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

import android.hardware.Camera.CameraInfo;

import com.android.ex.camera2.portability.CameraAgent.CameraProxy;

/**
 * The class is kept to make sure the tests can build.
 */
@Deprecated
public class CameraTestDevice {

    public static void injectMockCamera(CameraInfo[] info, CameraProxy[] camera) {
    }

    private CameraTestDevice() {
    }
}
