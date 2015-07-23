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

import android.os.Process;

import com.android.camera.async.AndroidPriorityThread;
import com.android.camera.debug.Log;
import com.android.camera.processing.ProcessingTaskConsumer;
import com.android.camera.processing.memory.ByteBufferDirectPool;
import com.android.camera.processing.memory.LruResourcePool;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Size;
import com.google.common.base.Optional;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This ImageBackend is created for the purpose of creating a task-running
 * infrastructure that has two-level of priority and doing the book-keeping to
 * keep track of tasks that use Android Images. Android.media.images are
 * critical system resources that MUST be properly managed in order to maintain
 * camera application performance. Android.media.images are merely Java handles
 * to regions of physically contiguous memory used by the camera hardware as a
 * destination for imaging data. In general, this physically contiguous memory
 * is not counted as an application resource, but as a system resources held by
 * the application and does NOT count against the limits of application memory.
 * The performance pressures of both computing and memory resources must often
 * be prioritized in releasing Android.media.images in a timely manner. In order
 * to properly balance these concerns, most image processing requested should be
 * routed through this object. This object is also responsible for releasing
 * Android.media image as soon as possible, so as not to stall the camera
 * hardware subsystem. Image that reserve these images are a subclass of the
 * basic Java Runnable with a few conditions placed upon their run()
 * implementation:
 * <ol>
 * <li>The task will try to release the image as early as possible by calling
 * the releaseSemaphoreReference as soon as a reference to the original image is
 * no longer required.</li>
 * <li>A set of tasks that require ImageData must only happen on the first
 * receiveImage call. receiveImage must only be called once per image.</li>
 * <li>However, the submitted tasks may spawn new tasks via the appendTask with
 * any image that have had a task submitted, but NOT released via
 * releaseSemaphoreReference.</li>
 * <li>Computation that is dependent on multiple images should be written into
 * this task framework in a distributed manner where image task can be computed
 * independently and join their results to a common shared object.This style of
 * implementation allows for the earliest release of Android Images while
 * honoring the resources priorities set by this class. See the Lucky shot
 * implementation for a concrete example for this shared object and its
 * respective task {@link TaskLuckyShotSession} {@link LuckyShotSession}</li>
 * </ol>
 * To integrate with the ProcessingServiceManager, ImageBackend also signals to
 * the ProcessingServiceManager its processing state by enqueuing
 * ImageShadowTasks on each ImageBackend::receiveImage call. These ImageShadow
 * tasks have no implementation, but emulate the processing delay by blocking
 * until all tasks submitted and spawned by a particular receiveImage call have
 * completed their processing. This emulated functionality ensures that other
 * ProcessingTasks associated with Lens Blur and Panorama are not processing
 * while the ImageBackend is running. Unfairly, the ImageBackend proceeds with
 * its own processing regardless of the state of ImageShadowTask.
 * ImageShadowTasks that are associated with ImageBackend tasks that have
 * already been completed should return immediately on its process call.
 */
public class ImageBackend implements ImageConsumer, ImageTaskManager {
    private static final Log.Tag TAG = new Log.Tag("ImageBackend");

    protected static final int NUM_THREADS_FAST = 2;
    protected static final int NUM_THREADS_AVERAGE = 2;
    protected static final int NUM_THREADS_SLOW = 2;

    private static final int FAST_THREAD_PRIORITY = Process.THREAD_PRIORITY_DISPLAY;
    private static final int AVERAGE_THREAD_PRIORITY = Process.THREAD_PRIORITY_DEFAULT
            + Process.THREAD_PRIORITY_LESS_FAVORABLE;
    private static final int SLOW_THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND
            + Process.THREAD_PRIORITY_MORE_FAVORABLE;

    private static final int IMAGE_BACKEND_HARD_REF_POOL_SIZE = 2;

    protected final ProcessingTaskConsumer mProcessingTaskConsumer;

    /**
     * Map for TaskImageContainer and the release of ImageProxy Book-keeping
     */
    protected final Map<ImageToProcess, ImageReleaseProtocol> mImageSemaphoreMap;
    /**
     * Map for ImageShadowTask and release of blocking on
     * ImageShadowTask::process
     */
    protected final Map<CaptureSession, ImageShadowTask> mShadowTaskMap;

    // The available threadpools for scheduling
    protected final ExecutorService mThreadPoolFast;
    protected final ExecutorService mThreadPoolAverage;
    protected final ExecutorService mThreadPoolSlow;

    private final LruResourcePool<Integer, ByteBuffer> mByteBufferDirectPool;

    /**
     * Approximate viewable size (in pixels) for the fast thumbnail in the
     * current UX definition of the product. Note that these values will be the
     * minimum size of FAST_THUMBNAIL target for the CONVERT_TO_RGB_PREVIEW
     * task.
     */
    private final Size mTinyThumbnailTargetSize;

    /**
     * A standard viewable size (in pixels) for the filmstrip thumbnail in the
     * current UX definition of the product. Note that this size is the minimum
     * size for the Preview on the filmstrip associated with
     * COMPRESS_TO_JPEG_AND_WRITE_TO_DISK task.
     */
    private final static Size FILMSTRIP_THUMBNAIL_TARGET_SIZE = new Size(512, 384);

    // Some invariants to know that we're keeping track of everything
    // that reflect the state of mImageSemaphoreMap
    private int mOutstandingImageRefs = 0;

    private int mOutstandingImageOpened = 0;

    private int mOutstandingImageClosed = 0;

    // Objects that may be registered to this objects events.
    private ImageProcessorProxyListener mProxyListener = null;

    // Default constructor, values are conservatively targeted to the Nexus 6
    public ImageBackend(ProcessingTaskConsumer processingTaskConsumer, int tinyThumbnailSize) {
        mThreadPoolFast = Executors.newFixedThreadPool(NUM_THREADS_FAST, new FastThreadFactory());
        mThreadPoolAverage = Executors.newFixedThreadPool(NUM_THREADS_AVERAGE,
                new AverageThreadFactory());
        mThreadPoolSlow = Executors.newFixedThreadPool(NUM_THREADS_SLOW, new SlowThreadFactory());
        mByteBufferDirectPool = new ByteBufferDirectPool(IMAGE_BACKEND_HARD_REF_POOL_SIZE);
        mProxyListener = new ImageProcessorProxyListener();
        mImageSemaphoreMap = new HashMap<>();
        mShadowTaskMap = new HashMap<>();
        mProcessingTaskConsumer = processingTaskConsumer;
        mTinyThumbnailTargetSize = new Size(tinyThumbnailSize, tinyThumbnailSize);
    }

    /**
     * Direct Injection Constructor for Testing purposes.
     *
     * @param fastService Service where Tasks of FAST Priority are placed.
     * @param averageService Service where Tasks of AVERAGE Priority are placed.
     * @param slowService Service where Tasks of SLOW Priority are placed.
     * @param imageProcessorProxyListener iamge proxy listener to be used
     */
    public ImageBackend(ExecutorService fastService,
            ExecutorService averageService,
            ExecutorService slowService,
            LruResourcePool<Integer, ByteBuffer> byteBufferDirectPool,
            ImageProcessorProxyListener imageProcessorProxyListener,
            ProcessingTaskConsumer processingTaskConsumer,
            int tinyThumbnailSize) {
        mThreadPoolFast = fastService;
        mThreadPoolAverage = averageService;
        mThreadPoolSlow = slowService;
        mByteBufferDirectPool = byteBufferDirectPool;
        mProxyListener = imageProcessorProxyListener;
        mImageSemaphoreMap = new HashMap<>();
        mShadowTaskMap = new HashMap<>();
        mProcessingTaskConsumer = processingTaskConsumer;
        mTinyThumbnailTargetSize = new Size(tinyThumbnailSize, tinyThumbnailSize);
    }

    /**
     * Simple getter for the associated listener object associated with this
     * instantiation that handles registration of events listeners.
     *
     * @return listener proxy that handles events messaging for this object.
     */
    public ImageProcessorProxyListener getProxyListener() {
        return mProxyListener;
    }

    /**
     * Wrapper function for all log messages created by this object. Default
     * implementation is to send messages to the Android logger. For test
     * purposes, this method can be overridden to avoid "Stub!" Runtime
     * exceptions in Unit Tests.
     */
    public void logWrapper(String message) {
        Log.v(TAG, message);
    }

    /**
     * @return Number of Image references currently held by this instance
     */
    @Override
    public int getNumberOfReservedOpenImages() {
        synchronized (mImageSemaphoreMap) {
            // since mOutstandingImageOpened, mOutstandingImageClosed reflect
            // the historical state of mImageSemaphoreMap, we need to lock on
            // before we return a value.
            return mOutstandingImageOpened - mOutstandingImageClosed;
        }
    }

    /**
     * Returns of the number of receiveImage calls that are currently enqueued
     * and/or being processed.
     *
     * @return The number of receiveImage calls that are currently enqueued
     *         and/or being processed
     */
    @Override
    public int getNumberOfOutstandingCalls() {
        synchronized (mShadowTaskMap) {
            return mShadowTaskMap.size();
        }
    }

    /**
     * Signals the ImageBackend that a tasks has released a reference to the
     * image. Imagebackend determines whether all references have been released
     * and applies its specified release protocol of closing image and/or
     * unblocking the caller. Should ONLY be called by the tasks running on this
     * class.
     *
     * @param img the image to be released by the task.
     * @param executor the executor on which the image close is run. if null,
     *            image close is run by the calling thread (usually the main
     *            task thread).
     */
    @Override
    public void releaseSemaphoreReference(final ImageToProcess img, Executor executor) {
        synchronized (mImageSemaphoreMap) {
            ImageReleaseProtocol protocol = mImageSemaphoreMap.get(img);
            if (protocol == null || protocol.getCount() <= 0) {
                // That means task implementation has allowed an unbalanced
                // semaphore release.
                throw new RuntimeException(
                        "ERROR: Task implementation did NOT balance its release.");
            }

            // Normal operation from here.
            protocol.addCount(-1);
            mOutstandingImageRefs--;
            logWrapper("Ref release.  Total refs = " + mOutstandingImageRefs);
            if (protocol.getCount() == 0) {
                // Image is ready to be released
                // Remove the image from the map so that it may be submitted
                // again.
                mImageSemaphoreMap.remove(img);

                // Conditionally close the image, specified by initial
                // receiveImage call
                if (protocol.closeOnRelease) {
                    closeImageExecutorSafe(img, executor);
                    logWrapper("Ref release close.");
                }

                // Conditionally signal the blocking thread to go.
                if (protocol.blockUntilRelease) {
                    protocol.signal();
                }
            } else {
                // Image is still being held by other tasks.
                // Otherwise, update the semaphore
                mImageSemaphoreMap.put(img, protocol);
            }
        }
    }

    /**
     * Spawns dependent tasks from internal implementation of a set of tasks. If
     * a dependent task does NOT require the image reference, it should be
     * passed a null pointer as an image reference. In general, this method
     * should be called after the task has completed its own computations, but
     * before it has released its own image reference (via the
     * releaseSemaphoreReference call).
     *
     * @param tasks The set of tasks to be run
     * @return whether tasks are successfully submitted.
     */
    @Override
    public boolean appendTasks(ImageToProcess img, Set<TaskImageContainer> tasks) {
        // Make sure that referred images are all the same, if it exists.
        // And count how image references need to be kept track of.
        int countImageRefs = numPropagatedImageReferences(img, tasks);

        if (img != null) {
            // If you're still holding onto the reference, make sure you keep
            // count
            incrementSemaphoreReferenceCount(img, countImageRefs);
        }

        // Update the done count on the new tasks.
        incrementTaskDone(tasks);

        scheduleTasks(tasks);
        return true;
    }

    /**
     * Spawns a single dependent task from internal implementation of a task.
     *
     * @param task The task to be run
     * @return whether tasks are successfully submitted.
     */
    @Override
    public boolean appendTasks(ImageToProcess img, TaskImageContainer task) {
        Set<TaskImageContainer> tasks = new HashSet<TaskImageContainer>(1);
        tasks.add(task);
        return appendTasks(img, tasks);
    }

    /**
     * Implements that top-level image single task submission that is defined by
     * the ImageConsumer interface w/o Runnable to executed.
     *
     * @param img Image required by the task
     * @param task Task to be run
     * @param blockUntilImageRelease If true, call blocks until the object img
     *            is no longer referred by any task. If false, call is
     *            non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false, After an image
     *            is submitted, it should never be submitted again to the
     *            interface until all tasks and their spawned tasks are
     *            finished.
     * @return whether jobs were enqueued to the ImageBackend.
     */
    @Override
    public boolean receiveImage(ImageToProcess img, TaskImageContainer task,
            boolean blockUntilImageRelease, boolean closeOnImageRelease)
            throws InterruptedException {
        return receiveImage(img, task, blockUntilImageRelease, closeOnImageRelease,
                Optional.<Runnable> absent());
    }

    /**
     * Implements that top-level image single task submission that is defined by
     * the ImageConsumer interface.
     *
     * @param img Image required by the task
     * @param task Task to be run
     * @param blockUntilImageRelease If true, call blocks until the object img
     *            is no longer referred by any task. If false, call is
     *            non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false, After an image
     *            is submitted, it should never be submitted again to the
     *            interface until all tasks and their spawned tasks are
     *            finished.
     * @param runnableWhenDone Optional runnable to be executed when the set of
     *            tasks are done.
     * @return whether jobs were enqueued to the ImageBackend.
     */
    @Override
    public boolean receiveImage(ImageToProcess img, TaskImageContainer task,
            boolean blockUntilImageRelease, boolean closeOnImageRelease,
            Optional<Runnable> runnableWhenDone)
            throws InterruptedException {
        Set<TaskImageContainer> passTasks = new HashSet<TaskImageContainer>(1);
        passTasks.add(task);
        return receiveImage(img, passTasks, blockUntilImageRelease, closeOnImageRelease,
                runnableWhenDone);
    }

    /**
     * Returns an informational string about the current status of ImageBackend,
     * along with an approximate number of references being held.
     *
     * @return an informational string suitable to be dumped into logcat
     */
    @Override
    public String toString() {
        return "ImageBackend Status BEGIN:\n" +
                "Shadow Image Map Size = " + mShadowTaskMap.size() + "\n" +
                "Image Semaphore Map Size = " + mImageSemaphoreMap.size() + "\n" +
                "OutstandingImageRefs = " + mOutstandingImageRefs + "\n" +
                "Proxy Listener Map Size = " + mProxyListener.getMapSize() + "\n" +
                "Proxy Listener = " + mProxyListener.getNumRegisteredListeners() + "\n" +
                "ImageBackend Status END:\n";
    }

    /**
     * Implements that top-level image single task submission that is defined by
     * the ImageConsumer interface.
     *
     * @param img Image required by the task
     * @param tasks A set of Tasks to be run
     * @param blockUntilImageRelease If true, call blocks until the object img
     *            is no longer referred by any task. If false, call is
     *            non-blocking
     * @param closeOnImageRelease If true, images is closed when the object img
     *            is is no longer referred by any task. If false, After an image
     *            is submitted, it should never be submitted again to the
     *            interface until all tasks and their spawned tasks are
     *            finished.
     * @param runnableWhenDone Optional runnable to be executed when the set of
     *            tasks are done.
     * @return whether receiveImage succeeded. Generally, only happens when the
     *         image reference is null or the task set is empty.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    @Override
    public boolean receiveImage(ImageToProcess img, Set<TaskImageContainer> tasks,
            boolean blockUntilImageRelease, boolean closeOnImageRelease,
            Optional<Runnable> runnableWhenDone)
            throws InterruptedException {

        // Short circuit if no tasks submitted.
        if (tasks == null || tasks.size() <= 0) {
            return false;
        }

        if (img == null) {
            // TODO: Determine whether you need to be so strict at the top level
            throw new RuntimeException("ERROR: Initial call must reference valid Image!");
        }

        // Make sure that referred images are all the same, if it exists.
        // And count how image references need to be kept track of.
        int countImageRefs = numPropagatedImageReferences(img, tasks);

        // Initialize the counters for process-level tasks
        initializeTaskDone(tasks, runnableWhenDone);

        // Set the semaphore, given that the number of tasks that need to be
        // scheduled
        // and the boolean flags for imaging closing and thread blocking
        ImageReleaseProtocol protocol = setSemaphoreReferenceCount(img, countImageRefs,
                blockUntilImageRelease, closeOnImageRelease);

        // Put the tasks on their respective queues.
        scheduleTasks(tasks);

        // Implement blocking if required
        if (protocol.blockUntilRelease) {
            protocol.block();
        }

        return true;
    }

    /**
     * Implements that top-level image task submission short-cut that is defined
     * by the ImageConsumer interface.
     *
     * @param img Image required by the task
     * @param executor Executor to run events and image closes, in case of
     *            control leakage
     * @param processingFlags Magical bit vector that specifies jobs to be run
     *            After an image is submitted, it should never be submitted
     *            again to the interface until all tasks and their spawned tasks
     *            are finished.
     * @param imageProcessorListener Optional listener to automatically register
     *            at the job task and unregister after all tasks are done
     * @return whether receiveImage succeeded. Generally, only happens when the
     *         image reference is null or the task set is empty.
     * @throws InterruptedException occurs when call is set to be blocking and
     *             is interrupted.
     */
    @Override
    public boolean receiveImage(ImageToProcess img, Executor executor,
            Set<ImageTaskFlags> processingFlags, CaptureSession session,
            Optional<ImageProcessorListener> imageProcessorListener)
            throws InterruptedException {

        // Uncomment for occasional debugging
        // Log.v(TAG, toString());

        Set<TaskImageContainer> tasksToExecute = new HashSet<TaskImageContainer>();

        if (img == null) {
            // No data to process, just pure message.
            return true;
        }

        // Now add the pre-mixed versions of the tasks.

        if (processingFlags.contains(ImageTaskFlags.COMPRESS_TO_JPEG_AND_WRITE_TO_DISK)) {
            if (processingFlags.contains(ImageTaskFlags.CREATE_EARLY_FILMSTRIP_PREVIEW)) {
                // Request job that creates both filmstrip thumbnail from YUV,
                // JPEG compression of the YUV Image, and writes the result to
                // disk
                tasksToExecute.add(new TaskPreviewChainedJpeg(img, executor, this, session,
                        FILMSTRIP_THUMBNAIL_TARGET_SIZE, mByteBufferDirectPool));
            } else {
                // Request job that only does JPEG compression and writes the
                // result to disk
                tasksToExecute.add(new TaskCompressImageToJpeg(img, executor, this, session,
                      mByteBufferDirectPool));
            }
        }

        if (processingFlags.contains(ImageTaskFlags.CONVERT_TO_RGB_PREVIEW)) {
            // Add an additional type of task to the appropriate queue.
            tasksToExecute.add(new TaskConvertImageToRGBPreview(img, executor,
                    this, TaskImageContainer.ProcessingPriority.FAST, session,
                    mTinyThumbnailTargetSize,
                    TaskConvertImageToRGBPreview.ThumbnailShape.SQUARE_ASPECT_CIRCULAR_INSET));
        }

        // Wrap the listener in a runnable that will be fired when all tasks are
        // complete.
        final Optional<Runnable> runnableOptional;
        if (imageProcessorListener.isPresent()) {
            final ImageProcessorListener finalImageProcessorListener = imageProcessorListener.get();
            Runnable unregisterRunnable = new Runnable() {
                @Override
                public void run() {
                    getProxyListener().unregisterListener(finalImageProcessorListener);
                }
            };
            runnableOptional = Optional.of(unregisterRunnable);
        } else {
            runnableOptional = Optional.<Runnable> absent();
        }

        if (receiveImage(img, tasksToExecute,
                processingFlags.contains(ImageTaskFlags.BLOCK_UNTIL_ALL_TASKS_RELEASE),
                processingFlags.contains(ImageTaskFlags.CLOSE_ON_ALL_TASKS_RELEASE),
                runnableOptional)) {
            if (imageProcessorListener.isPresent()) {
                getProxyListener().registerListener(imageProcessorListener.get(), img.proxy);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Factory functions, in case, you want some shake and bake functionality.
     */
    public TaskConvertImageToRGBPreview createTaskConvertImageToRGBPreview(
            ImageToProcess image, Executor executor, ImageBackend imageBackend,
            CaptureSession session, Size targetSize,
            TaskConvertImageToRGBPreview.ThumbnailShape thumbnailShape) {
        return new TaskConvertImageToRGBPreview(image, executor, imageBackend,
                TaskImageContainer.ProcessingPriority.FAST, session,
                mTinyThumbnailTargetSize, thumbnailShape);
    }

    public TaskCompressImageToJpeg createTaskCompressImageToJpeg(ImageToProcess image,
            Executor executor, ImageBackend imageBackend, CaptureSession session) {
        return new TaskCompressImageToJpeg(image, executor, imageBackend, session,
              mByteBufferDirectPool);
    }

    /**
     * Blocks and waits for all tasks to complete.
     */
    @Override
    public void shutdown() {
        mThreadPoolSlow.shutdown();
        mThreadPoolFast.shutdown();
    }

    /**
     * For a given set of starting tasks, initialize the associated sessions
     * with a proper blocking semaphore and value of number of tasks to be run.
     * For each semaphore, a ImageShadowTask will be instantiated and enqueued
     * onto the selected ProcessingSerivceManager.
     *
     * @param tasks The set of ImageContainer tasks to be run on ImageBackend
     */
    protected void initializeTaskDone(Set<TaskImageContainer> tasks,
            Optional<Runnable> runnableWhenDone) {
        Set<CaptureSession> sessionSet = new HashSet<>();
        Map<CaptureSession, Integer> sessionTaskCount = new HashMap<>();

        // Create a set w/ no session duplicates and count them
        for (TaskImageContainer task : tasks) {
            sessionSet.add(task.mSession);
            Integer currentCount = sessionTaskCount.get(task.mSession);
            if (currentCount == null) {
                sessionTaskCount.put(task.mSession, 1);
            } else {
                sessionTaskCount.put(task.mSession, currentCount + 1);
            }
        }

        // Create a new blocking semaphore for each set of tasks on a given
        // session.
        synchronized (mShadowTaskMap) {
            for (CaptureSession captureSession : sessionSet) {
                BlockSignalProtocol protocol = new BlockSignalProtocol();
                protocol.setCount(sessionTaskCount.get(captureSession));
                final ImageShadowTask shadowTask;
                shadowTask = new ImageShadowTask(protocol, captureSession,
                            runnableWhenDone);
                mShadowTaskMap.put(captureSession, shadowTask);
                mProcessingTaskConsumer.enqueueTask(shadowTask);
            }
        }
    }

    /**
     * For ImageBackend tasks that spawn their own tasks, increase the semaphore
     * count to take into account the new tasks being spawned.
     *
     * @param tasks The set of tasks to be spawned.
     */
    protected void incrementTaskDone(Set<TaskImageContainer> tasks) throws RuntimeException {
        // TODO: Add invariant test so that all sessions are the same.
        synchronized (mShadowTaskMap) {
            for (TaskImageContainer task : tasks) {
                ImageShadowTask shadowTask = mShadowTaskMap.get(task.mSession);
                if (shadowTask == null) {
                    throw new RuntimeException(
                            "Session NOT previously registered."
                                    + " ImageShadowTask booking-keeping is incorrect.");
                }
                shadowTask.getProtocol().addCount(1);
            }
        }
    }

    /**
     * Decrement the semaphore count of the ImageShadowTask. Should be called
     * when a task completes its processing in ImageBackend.
     *
     * @param imageShadowTask The ImageShadow task that contains the blocking
     *            semaphore.
     * @return whether all the tasks associated with an ImageShadowTask are done
     */
    protected boolean decrementTaskDone(ImageShadowTask imageShadowTask) {
        synchronized (mShadowTaskMap) {
            int remainingTasks = imageShadowTask.getProtocol().addCount(-1);
            if (remainingTasks == 0) {
                mShadowTaskMap.remove(imageShadowTask.getSession());
                imageShadowTask.getProtocol().signal();
                return true;
            } else {
                return false;
            }
        }

    }

    /**
     * Puts the tasks on the specified queue. May be more complicated in the
     * future.
     *
     * @param tasks The set of tasks to be run
     */
    protected void scheduleTasks(Set<TaskImageContainer> tasks) {
        synchronized (mShadowTaskMap) {
            for (TaskImageContainer task : tasks) {
                ImageShadowTask shadowTask = mShadowTaskMap.get(task.mSession);
                if (shadowTask == null) {
                    throw new IllegalStateException("Scheduling a task with a unknown session.");
                }
                // Before scheduling, wrap TaskImageContainer inside of the
                // TaskDoneWrapper to add
                // instrumentation for managing ImageShadowTasks
                switch (task.getProcessingPriority()) {
                    case FAST:
                        mThreadPoolFast.execute(new TaskDoneWrapper(this, shadowTask, task));
                        break;
                    case AVERAGE:
                        mThreadPoolAverage.execute(new TaskDoneWrapper(this, shadowTask, task));
                        break;
                    case SLOW:
                        mThreadPoolSlow.execute(new TaskDoneWrapper(this, shadowTask, task));
                        break;
                    default:
                        mThreadPoolSlow.execute(new TaskDoneWrapper(this, shadowTask, task));
                        break;
                }
            }
        }
    }

    /**
     * Initializes the semaphore count for the image
     *
     * @return The protocol object that keeps tracks of the image reference
     *         count and actions to be taken on release.
     */
    protected ImageReleaseProtocol setSemaphoreReferenceCount(ImageToProcess img, int count,
            boolean blockUntilRelease, boolean closeOnRelease) throws RuntimeException {
        synchronized (mImageSemaphoreMap) {
            if (mImageSemaphoreMap.get(img) != null) {
                throw new RuntimeException(
                        "ERROR: Rewriting of Semaphore Lock."
                                + "  Image references may not freed properly");
            }

            // Create the new booking-keeping object.
            ImageReleaseProtocol protocol = new ImageReleaseProtocol(blockUntilRelease,
                    closeOnRelease);
            protocol.setCount(count);

            mImageSemaphoreMap.put(img, protocol);
            mOutstandingImageRefs += count;
            mOutstandingImageOpened++;
            logWrapper("Received an opened image: " + mOutstandingImageOpened + "/"
                    + mOutstandingImageClosed);
            logWrapper("Setting an image reference count of " + count + "   Total refs = "
                    + mOutstandingImageRefs);
            return protocol;
        }
    }

    /**
     * Increments the semaphore count for the image. Should ONLY be internally
     * via appendTasks by internal tasks. Otherwise, image references could get
     * out of whack.
     *
     * @param img The Image associated with the set of tasks running on it.
     * @param count The number of tasks to be added
     * @throws RuntimeException Indicates image Closing Bookkeeping is screwed
     *             up.
     */
    protected void incrementSemaphoreReferenceCount(ImageToProcess img, int count)
            throws RuntimeException {
        synchronized (mImageSemaphoreMap) {
            ImageReleaseProtocol protocol = mImageSemaphoreMap.get(img);
            if (mImageSemaphoreMap.get(img) == null) {
                throw new RuntimeException(
                        "Image Reference has already been released or has never been held.");
            }

            protocol.addCount(count);
            mImageSemaphoreMap.put(img, protocol);

            mOutstandingImageRefs += count;
        }
    }

    /**
     * Close an Image with a executor if it's available and does the proper
     * booking keeping on the object.
     *
     * @param img Image to be closed
     * @param executor Executor to be used, if executor is null, the close is
     *            run on the task thread
     */
    private void closeImageExecutorSafe(final ImageToProcess img, Executor executor) {
        Runnable closeTask = new Runnable() {
            @Override
            public void run() {
                img.proxy.close();
                mOutstandingImageClosed++;
                logWrapper("Release of image occurred.  Good fun. " + "Total Images Open/Closed = "
                        + mOutstandingImageOpened + "/" + mOutstandingImageClosed);
            }
        };
        if (executor == null) {
            // Just run it on the main thread.
            closeTask.run();
        } else {
            executor.execute(closeTask);
        }
    }

    /**
     * Calculates the number of new Image references in a set of dependent
     * tasks. Checks to make sure no new image references are being introduced.
     *
     * @param tasks The set of dependent tasks to be run
     */
    private int numPropagatedImageReferences(ImageToProcess img, Set<TaskImageContainer> tasks)
            throws RuntimeException {
        int countImageRefs = 0;
        for (TaskImageContainer task : tasks) {
            if (task.mImage != null && task.mImage != img) {
                throw new RuntimeException("ERROR:  Spawned tasks cannot reference new images!");
            }

            if (task.mImage != null) {
                countImageRefs++;
            }
        }

        return countImageRefs;
    }

    /**
     * Simple wrapper task to instrument when tasks ends so that ImageBackend
     * can fire events when set of tasks created by a ReceiveImage call have all
     * completed.
     */
    private class TaskDoneWrapper implements Runnable {
        private final ImageBackend mImageBackend;
        private final ImageShadowTask mImageShadowTask;
        private final TaskImageContainer mWrappedTask;

        /**
         * Constructor
         *
         * @param imageBackend ImageBackend that the task is running on
         * @param imageShadowTask ImageShadowTask that is blocking on the
         *            completion of the task
         * @param wrappedTask The task to be run w/o instrumentation
         */
        public TaskDoneWrapper(ImageBackend imageBackend, ImageShadowTask imageShadowTask,
                TaskImageContainer wrappedTask) {
            mImageBackend = imageBackend;
            mImageShadowTask = imageShadowTask;
            mWrappedTask = wrappedTask;
        }

        /**
         * Adds instrumentation that runs when a TaskImageContainer completes.
         */
        @Override
        public void run() {
            mWrappedTask.run();
            // Decrement count
            if (mImageBackend.decrementTaskDone(mImageShadowTask)) {
                // If you're the last one...
                Runnable doneRunnable = mImageShadowTask.getRunnableWhenDone();
                if (doneRunnable != null) {
                    if (mWrappedTask.mExecutor == null) {
                        doneRunnable.run();
                    } else {
                        mWrappedTask.mExecutor.execute(doneRunnable);
                    }
                }
            }
        }
    }

    /**
     * Encapsulates all synchronization for semaphore signaling and blocking.
     */
    static public class BlockSignalProtocol {
        private int count;

        private final ReentrantLock mLock = new ReentrantLock();

        private Condition mSignal;

        public void setCount(int value) {
            mLock.lock();
            count = value;
            mLock.unlock();
        }

        public int getCount() {
            int value;
            mLock.lock();
            value = count;
            mLock.unlock();
            return value;
        }

        public int addCount(int value) {
            mLock.lock();
            try {
                count += value;
                return count;
            } finally {
                mLock.unlock();
            }
        }

        BlockSignalProtocol() {
            count = 0;
            mSignal = mLock.newCondition();
        }

        public void block() throws InterruptedException {
            mLock.lock();
            try {
                while (count != 0) {
                    // Spin to deal with spurious signals.
                    mSignal.await();
                }
            } catch (InterruptedException e) {
                // TODO: on interruption, figure out what to do.
                throw (e);
            } finally {
                mLock.unlock();
            }
        }

        public void signal() {
            mLock.lock();
            mSignal.signal();
            mLock.unlock();
        }

    }

    /**
     * A simple tuple class to keep track of image reference, and whether to
     * block and/or close on final image release. Instantiated on every task
     * submission call.
     */
    static public class ImageReleaseProtocol extends BlockSignalProtocol {

        public final boolean blockUntilRelease;

        public final boolean closeOnRelease;

        ImageReleaseProtocol(boolean block, boolean close) {
            super();
            blockUntilRelease = block;
            closeOnRelease = close;
        }

    }

    // Thread factories for a default constructor
    private class FastThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new AndroidPriorityThread(FAST_THREAD_PRIORITY, r);
            return t;
        }
    }

    private class AverageThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new AndroidPriorityThread(AVERAGE_THREAD_PRIORITY, r);
            return t;
        }
    }

    private class SlowThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new AndroidPriorityThread(SLOW_THREAD_PRIORITY, r);
            return t;
        }
    }

}
