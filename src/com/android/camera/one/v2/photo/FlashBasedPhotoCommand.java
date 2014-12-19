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

package com.android.camera.one.v2.photo;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureResult;

import com.android.camera.async.Pollable;
import com.android.camera.one.OneCamera.PhotoCaptureParameters;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

/**
 * Combines a flash-enabled command and a non-flash command into a single
 * command which fires depending on the current flash setting and AE metadata.
 */
class FlashBasedPhotoCommand implements CameraCommand {
    private final CameraCommand mFlashPhotoCommand;
    private final CameraCommand mNoFlashPhotoCommand;
    private final Pollable<Integer> mAEState;
    private final PhotoCaptureParameters.Flash mFlashState;

    public FlashBasedPhotoCommand(
            CameraCommand flashPhotoCommand,
            CameraCommand noFlashPhotoCommand,
            Pollable<Integer> aeState,
            PhotoCaptureParameters.Flash flashState) {
        mFlashPhotoCommand = flashPhotoCommand;
        mNoFlashPhotoCommand = noFlashPhotoCommand;
        mAEState = aeState;
        mFlashState = flashState;
    }

    @Override
    public void run() throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        boolean needsFlash = false;
        if (mFlashState == PhotoCaptureParameters.Flash.ON) {
            needsFlash = true;
        } else {
            Integer mostRecentAEState = mAEState.get(CaptureResult.CONTROL_AE_STATE_INACTIVE);
            if (mostRecentAEState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                needsFlash = true;
            }
        }

        if (needsFlash) {
            mFlashPhotoCommand.run();
        } else {
            mNoFlashPhotoCommand.run();
        }
    }
}
