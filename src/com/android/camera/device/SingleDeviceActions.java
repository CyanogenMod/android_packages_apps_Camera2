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

import com.android.camera.async.Lifetime;

/**
 * Device specific actions for opening and closing a device.
 */
public interface SingleDeviceActions<TDevice> {

    /**
     * Open the device represented by this instance. This should only
     * be called if there is a reasonable expectation that the device is
     * available and openable.
     *
     * It is possible for this to throw if there is a problem with the
     * parameters or if the camera device determined to be un-openable.
     */
    public void executeOpen(SingleDeviceOpenListener<TDevice> openListener,
          Lifetime deviceLifetime) throws UnsupportedOperationException;
    /**
     * Close the device represented by this instance.
     *
     * It is possible for this to throw if there is a problem with the
     * parameters used to create these actions.
     */
    public void executeClose(SingleDeviceCloseListener closeListener, TDevice device)
          throws UnsupportedOperationException;
}
