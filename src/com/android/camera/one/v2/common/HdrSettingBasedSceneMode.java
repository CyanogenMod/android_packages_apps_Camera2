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

import android.annotation.TargetApi;
import android.hardware.camera2.CaptureRequest;
import android.os.Build.VERSION_CODES;

import com.google.common.base.Supplier;

/**
 * Computes the current scene mode to use based on the current hdr setting.
 */
@TargetApi(VERSION_CODES.L)
public class HdrSettingBasedSceneMode implements Supplier<Integer> {
    // API 21 omitted this constant officially, but kept it around as a hidden constant
    // MR1 brings it back officially as the same int value.
    public static final int CONTROL_SCENE_MODE_HDR = 0x12;

    private final Supplier<Boolean> mHdrSetting;

    public HdrSettingBasedSceneMode(Supplier<Boolean> hdrSetting) {
        mHdrSetting = hdrSetting;
    }

    @Override
    public Integer get() {
        if (mHdrSetting.get()) {
            return CONTROL_SCENE_MODE_HDR;
        }
        return CaptureRequest.CONTROL_SCENE_MODE_DISABLED;
    }
}
