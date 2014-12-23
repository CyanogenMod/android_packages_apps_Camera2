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

import android.net.Uri;

/**
 * Defines the interactions between the Tasks that are running on ImageBackend and other subsystems
 * (such as UI) which need to update and listen for said event (such as preview completition).
 */
public interface ImageProcessorListener {

    /*
     * !!!!PLACEHOLDER IMPLEMENTATION!!!! Unclear what the best pattern for listeners, given the
     * types of tasks and the return types For now, I've gone with a minimal interface that is not
     * currently plumbed for error handling.
     */

    /**
     * Called when a task starts running.
     *
     * @param task Task specification that includes unique content id
     */
    public void onStart(TaskImageContainer.TaskInfo task);

    /**
     * Called when compressed image is complete and is ready for further processing
     *
     * @param task Task specification that includes unique content id
     * @param payload Byte array that contains the compressed image data
     */
    public void onResultCompressed(TaskImageContainer.TaskInfo task,
            TaskImageContainer.CompressedPayload payload);

    /**
     * Called when uncompressed image conversion is done and is ready for further processing
     *
     * @param task Task specification that includes unique content id
     * @param payload 32-bit Integer array that contains the uncompressed image data
     */
    public void onResultUncompressed(TaskImageContainer.TaskInfo task,
            TaskImageContainer.UncompressedPayload payload);

    /**
     * Called when image has been written to disk and ready for further processing via uri.
     *
     * @param task Task specification that includes unique content id
     * @param uri Uri that serves as handle to image written to disk
     */
    public void onResultUri(TaskImageContainer.TaskInfo task, Uri uri);

    // TODO: Figure out how to best error handling interface
    // public void onError(TaskImageContainer.JobInfo task);
}
