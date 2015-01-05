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

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The interface by which Task derived off of TaskImageContainer can describe
 * its task dependencies and manage its image references.
 *
 */
public interface ImageTaskManager {

    /**
     * Spawns dependent tasks from internal implementation of set of tasks. If a
     * dependent task does NOT require the image reference, it should be passed
     * a null pointer as an image reference. In general, this method should be
     * called after the task has completed its own computations, but before it
     * has released its own image reference (via the releaseSemaphoreReference
     * call).
     *
     * @param tasks The set of tasks to be run
     * @return whether tasks are successfully submitted.
     */
    public boolean appendTasks(ImageToProcess img, Set<TaskImageContainer> tasks);

    /**
     * Spawns a single dependent task from internal implementation of a task.
     *
     * @param task The task to be run
     * @return whether tasks are successfully submitted.
     */
    public boolean appendTasks(ImageToProcess img, TaskImageContainer task);

    /**
     * Signals the ImageTaskManager that a task has released a reference to the
     * image. ImageTaskManager determines whether all references have been
     * released and applies its specified release protocol of closing image
     * and/or unblocking the caller. Should ONLY be called by the tasks running
     * on this manager.
     *
     * @param img the image to be released by the task.
     * @param executor the executor on which the image close is run. if null,
     *            image close is run by the calling thread (usually the main
     *            task thread).
     */
    public void releaseSemaphoreReference(final ImageToProcess img, Executor executor);

    /**
     * Simple getter for the associated listener object associated with this
     * instance that handles registration of event listeners.
     *
     * @return listener proxy that handles events messaging for this object.
     */
    public ImageProcessorProxyListener getProxyListener();
}
