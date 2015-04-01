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

import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.PreviewCommand;
import com.android.camera.one.v2.commands.ResettingRunnableCameraCommand;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Create a two stage frame server update command for ZSL implementations.
 */
public class ZslPreviewCommandFactory implements PreviewCommandFactory {
    /** Time to wait before attaching the ZSL stream to the preview. */
    private static final int ZSL_DELAY_MILLIS = 500;

    private final PreviewCommand mPreviewCommand;
    private final CameraCommandExecutor mCameraCommandExecutor;
    private final ScheduledExecutorService mScheduledExecutor;

    public ZslPreviewCommandFactory(
          PreviewCommand previewCommand,
          CameraCommandExecutor cameraCommandExecutor,
          ScheduledExecutorService scheduledExecutorService) {
        mPreviewCommand = previewCommand;
        mCameraCommandExecutor = cameraCommandExecutor;
        mScheduledExecutor = scheduledExecutorService;
    }

    @Override
    public Runnable get(PreviewCommand primaryCommand) {
        CameraCommand zslPreviewCommand = new ZslPreviewCommand(
              mPreviewCommand,
              primaryCommand,
              mCameraCommandExecutor);

        Runnable attachZslRunnable = new ResettingRunnableCameraCommand(mCameraCommandExecutor,
              zslPreviewCommand);

        // Delay attaching the ZSL stream to the repeating request until
        // after a fixed delay. Eventually, this should be replaced by
        // a callback that attaches the stream after the it is fully initialized.
        // TODO: Avoid doing work in the factory get methods.
        mScheduledExecutor.schedule(attachZslRunnable, ZSL_DELAY_MILLIS, TimeUnit.MILLISECONDS);

        return attachZslRunnable;
    }
}
