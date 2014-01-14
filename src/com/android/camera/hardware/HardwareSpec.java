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

package com.android.camera.hardware;

/**
 * HardwareSpec is a interface for specifying whether
 * high-level features are supported by the camera device
 * hardware limitations.
 */
public interface HardwareSpec {

    /**
     * Returns whether a front facing camera is available
     * on the current hardware.
     */
    public boolean isFrontCameraSupported();

    /**
     * Returns whether hdr scene mode is supported on the
     * current hardware.
     */
    public boolean isHdrSupported();

    /**
     * Returns whether hdr plus is supported on the current
     * hardware.
     */
    public boolean isHdrPlusSupported();

    /**
     * Returns whether flash is supported and has more than
     * one supported setting.  If flash is supported but is
     * always off, this method should return false.
     */
    public boolean isFlashSupported();
}