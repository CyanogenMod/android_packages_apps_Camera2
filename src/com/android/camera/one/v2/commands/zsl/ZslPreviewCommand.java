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

package com.android.camera.one.v2.commands.zsl;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delegate the first run of a frameserver command to a different
 * camera command than subsequent executions.
 */
public class ZslPreviewCommand implements CameraCommand {
    private final CameraCommandExecutor mExecutor;
    private final CameraCommand mPreviewOnlyCommand;
    private final CameraCommand mFullZslCommand;
    private final AtomicBoolean mIsFirstRun;

    public ZslPreviewCommand(CameraCommand previewOnlyCommand,
          CameraCommand fullZslCommand,
          CameraCommandExecutor commandExecutor) {
        mPreviewOnlyCommand = previewOnlyCommand;
        mFullZslCommand = fullZslCommand;
        mExecutor = commandExecutor;
        mIsFirstRun = new AtomicBoolean(true);
    }

    @Override
    public void run() throws InterruptedException, CameraAccessException,
          CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        if (mIsFirstRun.getAndSet(false)) {
            mExecutor.execute(mPreviewOnlyCommand);
        } else {
            mExecutor.execute(mFullZslCommand);
        }
    }
}
