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

package com.android.camera.one.v2.face;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;

import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraCharacteristics.FaceDetectMode;

import java.util.List;

/**
 * Compute face detect modes.
 */
@TargetApi(VERSION_CODES.LOLLIPOP)
public class FaceDetect {
    public static FaceDetectMode getHighestFaceDetectMode(OneCameraCharacteristics characteristics) {
        List<FaceDetectMode> faceDetectModes = characteristics.getSupportedFaceDetectModes();

        if (faceDetectModes.contains(FaceDetectMode.FULL)) {
            return FaceDetectMode.FULL;
        } else if (faceDetectModes.contains(FaceDetectMode.SIMPLE)) {
            return FaceDetectMode.SIMPLE;
        } else {
            return FaceDetectMode.NONE;
        }
    }
}
