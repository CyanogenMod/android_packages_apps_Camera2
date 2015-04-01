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

import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.PreviewCommand;
import com.android.camera.one.v2.commands.ResettingRunnableCameraCommand;

/**
 * Simple frame server command runnable.
 */
public class BasicPreviewCommandFactory implements PreviewCommandFactory {
    private final CameraCommandExecutor mCameraCommandExecutor;

    public BasicPreviewCommandFactory(CameraCommandExecutor cameraCommandExecutor) {
        mCameraCommandExecutor = cameraCommandExecutor;
    }

    @Override
    public Runnable get(PreviewCommand primaryCommand) {
        return new ResettingRunnableCameraCommand(mCameraCommandExecutor,
              primaryCommand);
    }
}
