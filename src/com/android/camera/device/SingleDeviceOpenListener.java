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

package com.android.camera.device;

/**
 * Listener for camera opening lifecycle events.
 */
public interface SingleDeviceOpenListener<TDevice> {
    /**
     * Executed when a device is successfully opened.
     * @param device the open device.
     */
    public void onDeviceOpened(TDevice device);

    /**
     * Executed when an exception occurs opening the device.
     */
    public void onDeviceOpenException(Throwable throwable);

    /**
     * Executed when an exception occurs opening the device
     * and the actual device object is provided.
     */
    public void onDeviceOpenException(TDevice device);
}
