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

package com.android.camera.one.v2.common;

import android.hardware.camera2.CaptureRequest;

import com.android.camera.one.OneCamera;
import com.google.common.base.Supplier;

/**
 * Computes the current AE Mode to use based on the current flash state.
 */
public class FlashBasedAEMode implements Supplier<Integer> {
    private final Supplier<OneCamera.PhotoCaptureParameters.Flash> mFlash;
    private final Supplier<Boolean> mHdrSceneMode;

    public FlashBasedAEMode(
          Supplier<OneCamera.PhotoCaptureParameters.Flash> flash,
          Supplier<Boolean> hdrSceneMode) {
        mFlash = flash;
        mHdrSceneMode = hdrSceneMode;
    }

    @Override
    public Integer get() {
        // In the case that hdr scene mode is on, disable flash.
        if (mHdrSceneMode.get()) {
            return CaptureRequest.CONTROL_AE_MODE_ON;
        }

        switch (mFlash.get()) {
            case AUTO:
                return CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
            case ON:
                return CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
            case OFF:
                return CaptureRequest.CONTROL_AE_MODE_ON;
            default:
                return CaptureRequest.CONTROL_AE_MODE_ON;
        }
    }
}
