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

package com.android.camera.processing.imagebackend;

import com.android.camera.session.CaptureSession;

import java.util.concurrent.Executor;

/**
 *  Placeholder for writing an image to disk.
 */
public class TaskWriteImageToDisk extends TaskImageContainer {

    public TaskWriteImageToDisk(ImageToProcess image, Executor executor,
                                ImageBackend imageBackend, CaptureSession captureSession) {
        super(image, executor, imageBackend, ProcessingPriority.SLOW, captureSession);
    }

    @Override
    public void run() {
        // TODO: Make a dependency on JPEG_COMPRESSION

    }
}
