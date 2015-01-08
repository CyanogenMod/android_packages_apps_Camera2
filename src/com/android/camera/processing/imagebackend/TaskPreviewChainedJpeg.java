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

import com.android.camera.debug.Log;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Size;

import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to subsampled image
 * inscribed in a circle.
 */
public class TaskPreviewChainedJpeg extends TaskConvertImageToRGBPreview {
    protected final static Log.Tag TAG = new Log.Tag("TaskPreviewChainedJpeg");

    /**
     * Constructor
     *
     * @param image Image that the computation is dependent on
     * @param executor Executor to fire off an events
     * @param imageTaskManager Image task manager that allows reference counting
     *            and task spawning
     * @param captureSession Capture session that bound to this image
     * @param targetSize Approximate viewable pixel demensions of the desired
     *            preview Image     */
    TaskPreviewChainedJpeg(ImageToProcess image, Executor executor,
            ImageTaskManager imageTaskManager, CaptureSession captureSession, Size targetSize) {
        super(image, executor, imageTaskManager, ProcessingPriority.SLOW, captureSession,
                targetSize , ThumbnailShape.MAINTAIN_ASPECT_NO_INSET);
    }

    public void logWrapper(String message) {
        Log.v(TAG, message);
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;

        final TaskImage inputImage = calculateInputImage(img);
        final int subsample = calculateBestSubsampleFactor(
                new Size(inputImage.width, inputImage.height),
                mTargetSize);
        final TaskImage resultImage = calculateResultImage(img, subsample);

        onStart(mId, inputImage, resultImage, TaskInfo.Destination.INTERMEDIATE_THUMBNAIL);

        logWrapper("TIMER_END Rendering preview YUV buffer available, w=" + img.proxy.getWidth()
                / subsample + " h=" + img.proxy.getHeight() / subsample + " of subsample "
                + subsample);

        final int[] convertedImage = runSelectedConversion(img.proxy,subsample);

        // Chain JPEG task
        TaskImageContainer jpegTask = new TaskCompressImageToJpeg(img, mExecutor,
                mImageTaskManager, mSession);
        mImageTaskManager.appendTasks(img, jpegTask);

        // Signal backend that reference has been released
        mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
        onPreviewDone(resultImage, inputImage, convertedImage,
                TaskInfo.Destination.INTERMEDIATE_THUMBNAIL);
    }


}
