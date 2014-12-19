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

package com.android.camera.processing;

import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Defines interface between an ImageBackend object and a simplified camera
 * object that merely delivers images to the backend. After the tasks and
 * Android image is submitted to the ImageConsumer, the responsibility to close
 * on the Android image object as early as possible is transferred to the
 * implementation.  Whether an image can be submitted again for process is up to
 * the implementation of the consumer.  For integration of Camera Application,
 * we now pass in the CaptureSession in order to properly update filmstrip and UI.
 */
public interface ImageConsumer {

    /**
     * Provides the basic functionality of camera processing via an easy-to-use
     * method call.
     *
     * @param img The Image to be Processed
     * @param executor The executor on which to execute events and image close
     * @param processingFlags Bit vector comprised of logically ORed TASK_FLAG*
     *            constants
     */
    public boolean receiveImage(ImageProxy img, Executor executor,
            Set<ImageTaskFlags> processingFlags, CaptureSession captureSession)
            throws InterruptedException;

    /**
     * Provides the basic functionality of camera processing via a more general-
     * purpose method call. Tasks can be extended off of the TaskImageContainer,
     * or created from factory method provided by implementor.
     *
     * @param img The Image to be Processed
     * @param sharedTask Set of Tasks to be run on the given image
     * @param blockOnImageRelease If true, call blocks until the object img is
     *            no longer referred by any task. If false, call is non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false,
     * @return Whether the blocking completed properly. If false, there may be a
     *         need to clean up image closes manually.
     */
    public boolean receiveImage(ImageProxy img, TaskImageContainer sharedTask,
            boolean blockOnImageRelease, boolean closeOnImageRelease,
            CaptureSession captureSession)
            throws InterruptedException;

    /**
     * Provides the basic functionality of camera processing via a more general-
     * purpose method call. Tasks can be extended off of the TaskImageContainer,
     * or created from factory method provided by implementor.
     *
     * @param img The Image to be Processed
     * @param sharedTasks Set of tasks to be run on the given image
     * @param blockOnImageRelease If true, call blocks until the object img is
     *            no longer referred by any task. If false, call is non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false, close is not
     *            called on release
     * @return Whether the blocking completed properly. If false, there may be a
     *         need to clean up image closes manually.
     */
    public boolean receiveImage(ImageProxy img, Set<TaskImageContainer> sharedTasks,
            boolean blockOnImageRelease, boolean closeOnImageRelease,
            CaptureSession captureSession)
            throws InterruptedException;

    /**
     * Returns the number of images that are currently being referred by the
     * consumer component.
     *
     * @return Number of images that are currently being referred by the
     *         consumer
     */
    public int numberOfReservedOpenImages();

    /**
     * Shutdown all tasks by blocking on tasks to be completed.
     */
    public void shutdown();

    /**
     * Getter to the object that manages the ListenerEvents. Reigster listeners
     * to this object.
     */
    public ImageProcessorProxyListener getProxyListener();

    // Current jobs that should be able to be tagged to an image.
    public enum ImageTaskFlags {
        COMPRESS_IMAGE_TO_JPEG,
        CONVERT_IMAGE_TO_RGB_PREVIEW,
        WRITE_IMAGE_TO_DISK,
        BLOCK_UNTIL_IMAGE_RELEASE,
        CLOSE_IMAGE_ON_RELEASE
    }
}
