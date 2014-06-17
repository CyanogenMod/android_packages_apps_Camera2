/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera;

import com.android.camera.debug.Log;
import com.android.ex.camera2.portability.CameraAgent;

public class CameraErrorCallback
        implements CameraAgent.CameraErrorCallback {
    private static final Log.Tag TAG = new Log.Tag("CamErrCallback");

    @Override
    public void onError(int error, CameraAgent.CameraProxy camera) {
        Log.e(TAG, "Got camera error callback. error=" + error);
        if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
            // We are not sure about the current state of the app (in preview or
            // snapshot or recording). Closing the app is better than creating a
            // new Camera object.
            throw new RuntimeException("Media server died.");
        }
    }
}
