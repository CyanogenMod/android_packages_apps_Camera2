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

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a OneCamera device which is on top of camera2 API. This is essential a wrapper
 * for #{link android.hardware.camera2.CameraCharacteristics}.
 */
public class OneCameraCharacteristicsImpl implements OneCameraCharacteristics {
    private final CameraCharacteristics mCameraCharacteristics;

    public OneCameraCharacteristicsImpl(CameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    @Override
    public List<Size> getSupportedPictureSizes(int imageFormat) {
        StreamConfigurationMap configMap =
                mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        ArrayList<Size> supportedPictureSizes = new ArrayList<>();
        for (android.util.Size androidSize : configMap.getOutputSizes(imageFormat)) {
            supportedPictureSizes.add(new Size(androidSize));
        }
        return supportedPictureSizes;
    }
}
