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

package com.android.camera.one.v2.common;

import android.hardware.camera2.CameraMetadata;

import com.android.camera.one.OneCameraCharacteristics.FaceDetectMode;
import com.google.common.base.Supplier;

/**
 * Supply the face detection mode based on the OneCamera face detect
 * mode.
 */
public class StatisticsFaceDetectMode implements Supplier<Integer> {
    private final Supplier<FaceDetectMode> mFaceDetectMode;

    public StatisticsFaceDetectMode(Supplier<FaceDetectMode> faceDetectMode) {
        mFaceDetectMode = faceDetectMode;
    }

    @Override
    public Integer get() {
        if (mFaceDetectMode.get() == FaceDetectMode.FULL) {
            return CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL;
        }

        if (mFaceDetectMode.get() == FaceDetectMode.SIMPLE) {
            return CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE;
        }

        return CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF;
    }
}
