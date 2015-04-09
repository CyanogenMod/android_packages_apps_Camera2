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

import android.graphics.Rect;
import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * TaskImageContainer are the base class of tasks that wish to run with the
 * ImageBackend class. It contains the basic information required to interact
 * with the ImageBackend class and the ability to identify itself to the UI
 * backend for updates on its progress.
 */
public abstract class TaskImageContainer implements Runnable {

    /**
     * Simple helper class to encapsulate uncompressed payloads. Could be more
     * complex in the future.
     */
    static public class UncompressedPayload {
        final public int[] data;

        UncompressedPayload(int[] passData) {
            data = passData;
        }
    }

    /**
     * Simple helper class to encapsulate compressed payloads. Could be more
     * complex in the future.
     */
    static public class CompressedPayload {
        final public byte[] data;

        CompressedPayload(byte[] passData) {
            data = passData;
        }
    }

    /**
     * Simple helper class to encapsulate all necessary image information that
     * is carried with the data to processing, so that tasks derived off of
     * TaskImageContainer can properly coordinate and optimize its computation.
     */
    static public class TaskImage {
        // Addendum to Android-defined image-format
        public final static int EXTRA_USER_DEFINED_FORMAT_ARGB_8888 = -1;

        // Minimal required knowledge for the image specification.
        public final OrientationManager.DeviceOrientation orientation;

        public final int height;
        public final int width;
        public final int format;
        public final Rect cropApplied;

        TaskImage(OrientationManager.DeviceOrientation anOrientation, int aWidth, int aHeight,
                int aFormat, Rect crop) {
            orientation = anOrientation;
            height = aHeight;
            width = aWidth;
            format = aFormat;
            cropApplied = crop;
        }

    }

    /**
     * Simple helper class to encapsulate input and resultant image
     * specification. TasksImageContainer classes can be uniquely identified by
     * triplet of its content (currently, the global timestamp of when the
     * object was taken), the image specification of the input and the desired
     * output image specification. Added a field to specify the destination of
     * the image artifact, since spawn tasks may created multiple un/compressed
     * artifacts of different size that need to be routed to different
     * components.
     */
    static public class TaskInfo {

        /**
         * A single task graph can often create multiple imaging processing
         * artifacts and the listener needs to distinguish an uncompressed image
         * meant for image destinations. The different destinations are as
         * follows:
         * <ul>
         * <li>FAST_THUMBNAIL: Small image required as soon as possible</li>
         * <li>INTERMEDIATE_THUMBNAIL: Mid-sized image required for filmstrips
         * at approximately 100-500ms latency</li>
         * <li>FINAL_IMAGE: Full-resolution image artifact where latency > 500
         * ms</li>
         * </ul>
         */
        public enum Destination {
            FAST_THUMBNAIL,
            INTERMEDIATE_THUMBNAIL,
            FINAL_IMAGE
        }

        public final Destination destination;
        // The unique Id of the image being processed.
        public final long contentId;

        public final TaskImage input;

        public final TaskImage result;

        TaskInfo(long aContentId, TaskImage inputSpec, TaskImage outputSpec,
                Destination aDestination) {
            contentId = aContentId;
            input = inputSpec;
            result = outputSpec;
            destination = aDestination;
        }

    }

    public enum ProcessingPriority {
        FAST, AVERAGE, SLOW
    }

    protected final static Log.Tag TAG = new Log.Tag("TaskImgContain");

    final protected ImageTaskManager mImageTaskManager;

    final protected Executor mExecutor;

    final protected long mId;

    final protected ProcessingPriority mProcessingPriority;

    final protected ImageToProcess mImage;

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
        mImageTaskManager = otherTask.mImageTaskManager;
        mProcessingPriority = processingPriority;
        mSession = otherTask.mSession;
        mImage = null;
    }

    /**
     * Constructor to use when keeping the image reference.
     *
     * @param image Image reference that needs to be released.
     * @param Executor Executor to run the event handling, if required.
     * @param imageTaskManager a reference to the ImageBackend, in case, you
     *            need to spawn other tasks
     * @param preferredLane Priority that the derived task will run at
     * @param captureSession Session that handles image processing events
     */
    public TaskImageContainer(ImageToProcess image, @Nullable Executor Executor,
            ImageTaskManager imageTaskManager,
            ProcessingPriority preferredLane, CaptureSession captureSession) {
        mImage = image;
        mId = mImage.proxy.getTimestamp();
        mExecutor = Executor;
        mImageTaskManager = imageTaskManager;
        mProcessingPriority = preferredLane;
        mSession = captureSession;
    }

    /**
     * Returns rotated crop rectangle in terms of absolute sensor crop
     *
     */
    protected Rect rotateBoundingBox(Rect box, OrientationManager.DeviceOrientation orientation) {
        if(orientation == OrientationManager.DeviceOrientation.CLOCKWISE_0 ||
                orientation == OrientationManager.DeviceOrientation.CLOCKWISE_180) {
            return new Rect(box);
        } else {
            // Switch x/y coordinates.
            return new Rect(box.top, box.left, box.bottom, box.right);
        }
    }

    protected OrientationManager.DeviceOrientation addOrientation(
            OrientationManager.DeviceOrientation orientation1,
            OrientationManager.DeviceOrientation orientation2) {
        return OrientationManager.DeviceOrientation.from(orientation1.getDegrees()
                + orientation2.getDegrees());
    }

    /**
     * Returns a crop rectangle whose points are a strict subset of the points
     * specified by image rectangle. A Null Intersection returns
     * Rectangle(0,0,0,0).
     *
     * @param image image to be cropped
     * @param crop an arbitrary crop rectangle; if null, the crop is assumed to
     *            be set of all points.
     * @return the rectangle produced by the intersection of the image rectangle
     *         with passed-in crop rectangle; a null intersection returns
     *         Rect(0,0,0,0)
     */
    public Rect guaranteedSafeCrop(ImageProxy image, @Nullable Rect crop) {
        return guaranteedSafeCrop(image.getWidth(), image.getHeight(), crop);
    }

    /**
     * Returns a crop rectangle whose points are a strict subset of the points
     * specified by image rectangle. A Null Intersection returns Rectangle(0,0,0,0).
     * Since sometimes the ImageProxy doesn't take into account rotation.  The Image
     * is assumed to have its top-left corner at (0,0).
     *
     * @param width image width
     * @param height image height
     * @param crop an arbitrary crop rectangle; if null, the crop is assumed to
     *            be set of all points.
     * @return the rectangle produced by the intersection of the image rectangle
     *         with passed-in crop rectangle; a null intersection returns
     *         Rect(0,0,0,0)
     */

    public Rect guaranteedSafeCrop(int width, int height, @Nullable Rect crop) {
        if (crop == null) {
            return new Rect(0, 0, width, height);
        }
        Rect safeCrop = new Rect(crop);
        if (crop.top > crop.bottom || crop.left > crop.right || crop.width() <= 0
                || crop.height() <= 0) {
            return new Rect(0, 0, 0, 0);
        }

        safeCrop.left = Math.max(safeCrop.left, 0);
        safeCrop.top = Math.max(safeCrop.top, 0);
        safeCrop.right = Math.max(Math.min(safeCrop.right, width), safeCrop.left);
        safeCrop.bottom = Math.max(Math.min(safeCrop.bottom, height), safeCrop.top);

        if (safeCrop.width() <= 0 || safeCrop.height() <= 0) {
            return new Rect(0, 0, 0, 0);
        }

        return safeCrop;
    }

    /**
     * Returns whether the crop operation is required.
     *
     * @param image Image to be cropped
     * @param crop Crop region
     * @return whether the image needs any more processing to be cropped
     *         properly.
     */
    public boolean requiresCropOperation(ImageProxy image, @Nullable Rect crop) {
        if (crop == null) {
            return false;
        }

        return !(crop.equals(new Rect(0, 0, image.getWidth(), image.getHeight())));
    }

    /**
     * Basic listener function to signal ImageBackend that task has started.
     *
     * @param id Id for image content
     * @param input Image specification for task input
     * @param result Image specification for task result
     * @param aDestination Purpose of image processing artifact
     */
    public void onStart(long id, TaskImage input, TaskImage result,
            TaskInfo.Destination aDestination) {
        TaskInfo job = new TaskInfo(id, input, result, aDestination);
        final ImageProcessorListener listener = mImageTaskManager.getProxyListener();
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
