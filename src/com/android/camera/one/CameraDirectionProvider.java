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

package com.android.camera.one;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;

/**
 * Provides the camera orientation based on the given Camera2 characteristics.
 */
public class CameraDirectionProvider {
    private final CameraCharacteristics mCharacteristics;

    public CameraDirectionProvider(CameraCharacteristics characteristics) {
        mCharacteristics = characteristics;
    }

    public OneCamera.Facing getDirection() {
        switch (mCharacteristics.get(CameraCharacteristics.LENS_FACING)) {
            case CameraMetadata.LENS_FACING_BACK:
                return OneCamera.Facing.BACK;
            case CameraMetadata.LENS_FACING_FRONT:
                return OneCamera.Facing.FRONT;
        }
        return OneCamera.Facing.BACK;
    }
}
