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

package com.android.camera.one.v2.initialization;

import android.view.Surface;

import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.photo.PictureTaker;

/**
 * Starts a camera after initialization (e.g. of the preview Surface and
 * CameraCaptureSession) is already done.
 * <p>
 * Device-specific features should typically be wired together here.
 */
public interface CameraStarter {
    public static class CameraControls {
        private final PictureTaker mPictureTaker;
        private final ManualAutoFocus mManualAutoFocus;

        public CameraControls(PictureTaker pictureTaker, ManualAutoFocus manualAutoFocus) {
            mPictureTaker = pictureTaker;
            mManualAutoFocus = manualAutoFocus;
        }

        public PictureTaker getPictureTaker() {
            return mPictureTaker;
        }

        public ManualAutoFocus getManualAutoFocus() {
            return mManualAutoFocus;
        }
    }

    /**
     * Implementations should start a preview and return controls for taking
     * pictures and triggering focus.
     * <p>
     * They should also react to changes to zoom and invoke callbacks for the
     * auto focus state, ready state, and preview-start success state.
     *
     * @param cameraLifetime The lifetime of all resources to be created.
     */
    public CameraControls startCamera(
            Lifetime cameraLifetime,
            CameraCaptureSessionProxy cameraCaptureSession,
            Surface previewSurface,
            Observable<Float> zoomState,
            Updatable<TotalCaptureResultProxy> metadataCallback,
            Updatable<Boolean> readyStateCallback);
}
