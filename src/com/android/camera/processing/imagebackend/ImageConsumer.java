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

import com.google.common.base.Optional;

import com.android.camera.session.CaptureSession;

import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Defines interface between an ImageBackend object and a simplified camera
 * object that merely delivers images to the backend. After the tasks and
 * Android image is submitted to the ImageConsumer, the responsibility to close
 * on the Android image object as early as possible is transferred to the
 * implementation. Whether an image can be submitted again for process is up to
 * the implementation of the consumer. For integration of Camera Application, we
 * now pass in the CaptureSession in order to properly update filmstrip and UI.
 * More generalized versions of functions of this interface allow a Runnable to
 * be executed when the set of events and any spawned events have completed
 * processing.
 */
public interface ImageConsumer {

    /**
     * ImageTaskFlags specifies the current tasks that will be run with an
     * image.
     * <ol>
     * <li>CREATE_EARLY_FILMSTRIP_PREVIEW: Subsamples a YUV Image and converts
     * it to an ARGB Image with nearly similar aspect ratio. ONLY Valid when
     * specified with COMPRESS_TO_JPEG_AND_WRITE_TO_DISK. Otherwise, ignored.</li>
     * <li>COMPRESS_TO_JPEG_AND_WRITE_TO_DISK: Compresses an YUV/JPEG Image to
     * JPEG (when necessary), delivers the compressed artifact via the listener,
     * and writes it to disk.</li>
     * <li>CONVERT_TO_RGB_PREVIEW: Subsamples a YUV Image and converts the
     * uncompressed output to ARGB image inset within a circle</li>
     * <li>BLOCK_UNTIL_ALL_TASKS_RELEASE: Block on ReceiveImage call until image
     * is released.</li>
     * <li>CLOSE_ON_ALL_TASKS_RELEASE: Close the ImageProxy on ReceiveImage Call
     * when all tasks release their image</li>
     * </ol>
     */
    public enum ImageTaskFlags {
        CREATE_EARLY_FILMSTRIP_PREVIEW,
        COMPRESS_TO_JPEG_AND_WRITE_TO_DISK,
        CONVERT_TO_RGB_PREVIEW,
        BLOCK_UNTIL_ALL_TASKS_RELEASE,
        CLOSE_ON_ALL_TASKS_RELEASE
    }

    /**
     * Provides the basic functionality of camera processing via an easy-to-use
     * method call.  Feel free to expand this implementation by increasing the
     * types of common tasks in the enum ImageTaskFlags.  This version of the call
     * also automatically registers at time of task submission and unregisters
     * the listener at the time when all tasks and associated spawned tasks have
     * completed their processing.
     *
     * @param img image to be processed
     * @param executor The executor on which to execute events and image close
     * @param processingFlags {@see ImageTaskFlags}
     * @param imageProcessorListener Optional listener to automatically register
     *            at task submission and unregister after all tasks are done
     * @return Whether any tasks were actually added.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    public boolean receiveImage(ImageToProcess img, Executor executor,
            Set<ImageTaskFlags> processingFlags, CaptureSession captureSession,
            Optional<ImageProcessorListener> imageProcessorListener)
            throws InterruptedException;

    /**
     * Provides the basic functionality of camera processing via an easy-to-use
     * method call w/o a listener to be released.
     *
     * @param img image to be processed
     * @param executor executor on which to execute events and image close
     * @param processingFlags {@see ImageTaskFlags}
     * @return Whether any tasks were actually added.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    /*
    public boolean receiveImage(ImageToProcess img, Executor executor,
            Set<ImageTaskFlags> processingFlags, CaptureSession captureSession)
            throws InterruptedException;
            */

    /**
     * Provides the basic functionality of camera processing via a more general-
     * purpose method call. Tasks can be extended off of the TaskImageContainer,
     * or created from factory method provided by implementation.
     *
     * @param img image to be processed
     * @param sharedTask a single task to be run
     * @param blockOnImageRelease If true, call blocks until the object img is
     *            no longer referred by any task. If false, call is non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false,
     * @param runnableWhenDone Optional runnable to be executed when the task is
     *            done.
     * @return Whether the blocking completed properly. If false, there may be a
     *         need to clean up image closes manually.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */

    public boolean receiveImage(ImageToProcess img, TaskImageContainer sharedTask,
            boolean blockOnImageRelease, boolean closeOnImageRelease,
            Optional<Runnable> runnableWhenDone)
            throws InterruptedException;

    /**
     * Provides the basic functionality of camera processing via a more general-
     * purpose method call w/o a Runnable to be executed when the task is done.
     * Tasks can be extended off of the TaskImageContainer, or created from
     * factory method provided by implementation.
     *
     * @param img image to be processed.
     * @param sharedTask a single task to be run.
     * @param blockOnImageRelease If true, call blocks until the object img is
     *            no longer referred by any task. If false, call is non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false,
     * @return Whether the blocking completed properly. If false, there may be a
     *         need to clean up image closes manually.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    @Deprecated
    public boolean receiveImage(ImageToProcess img, TaskImageContainer sharedTask,
            boolean blockOnImageRelease, boolean closeOnImageRelease)
            throws InterruptedException;

    /**
     * Provides the basic functionality of camera processing via the most
     * general- purpose method call. Tasks can be extended off of the
     * TaskImageContainer, or created from factory method provided by the
     * implementation.
     *
     * @param img image to be processed.
     * @param sharedTasks Set of tasks to be run on the given image.
     * @param blockOnImageRelease If true, call blocks until the object img is
     *            no longer referred by any task. If false, call is non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false, close is not
     *            called on release.
     * @param runnableWhenDone optional runnable to be executed when the set of
     *            tasks are done.
     * @return Whether the blocking completed properly. If false, there may be a
     *         need to clean up image closes manually.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    public boolean receiveImage(ImageToProcess img, Set<TaskImageContainer> sharedTasks,
            boolean blockOnImageRelease, boolean closeOnImageRelease,
            Optional<Runnable> runnableWhenDone)
            throws InterruptedException;

    /**
     * Returns the number of images that are currently being referred by the
     * consumer component.
     *
     * @return Number of images that are currently being referred by the
     *         consumer
     */
    public int getNumberOfReservedOpenImages();

    /**
     * Returns the number of currently outstanding receiveImage calls that are
     * processing and/or enqueued.
     *
     * @return the number of receiveImage calls still running or queued in the
     *         ImageBackend
     */
    public int getNumberOfOutstandingCalls();

    /**
     * Shutdown all tasks by blocking on tasks to be completed.
     */
    public void shutdown();

    /**
     * Getter to the object that manages the ListenerEvents. Register listeners
     * to this object.
     */
    public ImageProcessorProxyListener getProxyListener();

}
