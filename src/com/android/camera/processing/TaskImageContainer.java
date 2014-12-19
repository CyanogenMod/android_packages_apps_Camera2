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

import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.util.concurrent.Executor;

/**
 * TaskImageContainer are the base class of tasks that wish to run with the ImageBackend class. It
 * contains the basic information required to interact with the ImageBackend class and the ability
 * to identify itself to the UI backend for updates on its progress.
 */
public abstract class TaskImageContainer implements Runnable {


    /**
     * Simple helper class to encapsulate uncompressed payloads.  Could be more complex in
     * the future.
     */
    static public class UncompressedPayload {
        final public int [] data;
        UncompressedPayload(int [] passData) {
            data = passData;
        }
    }


    /**
     * Simple helper class to encapsulate compressed payloads.  Could be more complex in
     * the future.
     */
    static public class CompressedPayload {
        final public byte [] data;
        CompressedPayload(byte [] passData) {
            data = passData;
        }
    }

    /**
     * Simple helper class to encapsulate all necessary image information that is carried with the
     * data to processing, so that tasks derived off of TaskImageContainer can properly coordinate
     * and optimize its computation.
     */
    static public class TaskImage {
        // Addendum to Android-defined image-format
        public final static int EXTRA_USER_DEFINED_FORMAT_ARGB_8888 = -1;

        // Minimal required knowledge for the image specification.
        public final OrientationManager.DeviceOrientation orientation;

        public final int height;

        public final int width;

        public final int format;

        TaskImage(OrientationManager.DeviceOrientation anOrientation, int aWidth, int aHeight,
                int aFormat) {
            orientation = anOrientation;
            height = aWidth;
            width = aHeight;
            format = aFormat;
        }

    }

    /**
     * Simple helper class to encapsulate input and resultant image specification.
     * TasksImageContainer classes can be uniquely identified by triplet of its content (currently,
     * the global timestamp of when the object was taken), the image specification of the input and
     * the desired output image specification.
     */
    static public class TaskInfo {
        // The unique Id of the image being processed.
        public final long contentId;

        public final TaskImage input;

        public final TaskImage result;

        TaskInfo(long aContentId, TaskImage inputSpec, TaskImage outputSpec) {
            contentId = aContentId;
            input = inputSpec;
            result = outputSpec;
        }

    }

    public enum ProcessingPriority {
        FAST, SLOW
    }

    protected final static Log.Tag TAG = new Log.Tag("TaskImgContain");

    final protected ImageBackend mImageBackend;

    final protected Executor mExecutor;

    final protected long mId;

    final protected ProcessingPriority mProcessingPriority;

    final protected ImageProxy mImageProxy;

    final protected CaptureSession mSession;

    /**
     * Constructor when releasing the image reference.
     *
     * @param otherTask the original task that is spawning this task.
     * @param processingPriority Priority that the derived task will run at.
     */
    public TaskImageContainer(TaskImageContainer otherTask, ProcessingPriority processingPriority) {
        mId = otherTask.mId;
        mExecutor = otherTask.mExecutor;
        mImageBackend = otherTask.mImageBackend;
        mProcessingPriority = processingPriority;
        mSession = otherTask.mSession;
        mImageProxy = null;
    }

    /**
     * Constructor to use when keeping the image reference.
     *
     * @param image Image reference that needs to be released.
     * @param Executor Executor to run the event handling
     * @param imageBackend a reference to the ImageBackend, in case, you need to spawn other tasks
     * @param preferredLane Priority that the derived task will run at
     * @param captureSession Session that handles image processing events
     */
    public TaskImageContainer(ImageProxy image, Executor Executor, ImageBackend imageBackend,
            ProcessingPriority preferredLane, CaptureSession captureSession) {
        mImageProxy = image;
        mId = image.getTimestamp();
        mExecutor = Executor;
        mImageBackend = imageBackend;
        mProcessingPriority = preferredLane;
        mSession = captureSession;
    }

    /**
     * Basic listener function to signal ImageBackend that task has started.
     *
     * @param id Id for image content
     * @param input Image specification for task input
     * @param result Image specification for task result
     */
    public void onStart(long id, TaskImage input, TaskImage result) {
        TaskInfo job = new TaskInfo(id, input, result);
        final ImageProcessorListener listener = mImageBackend.getProxyListener();
        listener.onStart(job);
    }

    /**
     * Getter for Processing Priority
     *
     * @return Processing Priority associated with the task.
     */
    public ProcessingPriority getProcessingPriority() {
        return mProcessingPriority;
    }
}
