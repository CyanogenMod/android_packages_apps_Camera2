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

package com.android.camera.one.v2.autofocus;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.ResettingDelayedExecutor;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

/**
 * Performs a full Auto Focus scan, holds for a period of time, and then resets
 * the preview.
 */
class AFScanHoldResetCommand implements CameraCommand {
    private final CameraCommand mAFScanCommand;
    private final ResettingDelayedExecutor mDelayedExecutor;
    private final Runnable mPreviewRunnable;
    private final Updatable<MeteringParameters> mMeteringParametersUpdatable;

    public AFScanHoldResetCommand(CameraCommand afScanCommand,
            ResettingDelayedExecutor delayedExecutor,
            Runnable previewRunnable,
            Updatable<MeteringParameters> meteringParametersUpdatable) {
        mAFScanCommand = afScanCommand;
        mDelayedExecutor = delayedExecutor;
        mPreviewRunnable = previewRunnable;
        mMeteringParametersUpdatable = meteringParametersUpdatable;
    }

    @Override
    public void run() throws CameraAccessException, InterruptedException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        // Reset any delayed preview-restart which may be pending execution as a
        // result of a previous tap-to-focus.
        mDelayedExecutor.reset();
        mAFScanCommand.run();
        mDelayedExecutor.execute(new Runnable() {
            public void run() {
                // Reset metering regions and restart the preview.
                mMeteringParametersUpdatable.update(GlobalMeteringParameters.create());
                mPreviewRunnable.run();
            }
        });
    }
}
