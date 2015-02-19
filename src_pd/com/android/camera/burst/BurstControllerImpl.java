/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.SurfaceTexture;

import com.android.camera.burst.BurstResultsListener;
import com.android.camera.burst.EvictionHandler;
import com.android.camera.burst.BurstController.ImageStreamProperties;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.imagesaver.MetadataImage;
import com.android.camera.session.CaptureSession;

import java.util.List;

/**
 * Stub implementation for burst controller.
 */
class BurstControllerImpl implements BurstController {

    public BurstControllerImpl(Context context) {}
    /**
     * Returns true if burst mode is supported by camera.
     */
    public static boolean isBurstModeSupported(ContentResolver contentResolver) {
        return false;
    }

    @Override
    public EvictionHandler startBurst(SurfaceTexture surfaceTexture,
            ImageStreamProperties imageStreamProperties,
            BurstResultsListener resultsListener,
            CaptureSession captureSession) {
        return null;
    }

    @Override
    public void processBurstResults(List<MetadataImage> capturedImages) {
        // no op
    }
}
