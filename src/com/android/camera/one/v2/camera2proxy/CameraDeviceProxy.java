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

package com.android.camera.one.v2.camera2proxy;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.async.SafeCloseable;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Interface for {@link android.hardware.camera2.CameraDevice}.
 */
@ParametersAreNonnullByDefault
public interface CameraDeviceProxy extends SafeCloseable {
    public String getId();

    public void createCaptureSession(List<Surface> list,
            CameraCaptureSessionProxy.StateCallback stateCallback, @Nullable Handler handler)
            throws CameraAccessException;

    public CaptureRequest.Builder createCaptureRequest(int i) throws CameraAccessException;

    @Override
    public void close();
}
