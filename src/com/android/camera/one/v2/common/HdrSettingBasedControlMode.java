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

import android.hardware.camera2.CaptureRequest;

import com.android.camera.one.OneCameraCharacteristics.SupportedHardwareLevel;
import com.google.common.base.Supplier;

/**
 * Computes the current control mode to use based on the current hdr setting
 * and supported hardware level for the device.
 */
public class HdrSettingBasedControlMode implements Supplier<Integer> {
    private final Supplier<Boolean> mHdrSetting;
    private final SupportedHardwareLevel mSupportedHardwareLevel;
    private final Integer mDefaultControlMode;

    public HdrSettingBasedControlMode(Supplier<Boolean> hdrSetting,
          SupportedHardwareLevel supportedHardwareLevel,
          Integer defaultControlMode) {
        mHdrSetting = hdrSetting;
        mSupportedHardwareLevel = supportedHardwareLevel;
        mDefaultControlMode = defaultControlMode;
    }

    @Override
    public Integer get() {
        if (mSupportedHardwareLevel == SupportedHardwareLevel.LEGACY) {
            if (mHdrSetting.get()) {
                return CaptureRequest.CONTROL_MODE_USE_SCENE_MODE;
            }
        }

        return mDefaultControlMode;
    }
}
