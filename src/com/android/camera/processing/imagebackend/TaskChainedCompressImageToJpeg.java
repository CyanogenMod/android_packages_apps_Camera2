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

import android.graphics.ImageFormat;

import android.graphics.Rect;
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to compressed JPEG byte
 * array, as two separate tasks: the first to copy from the image to NV21 memory
 * layout, and the second to convert the image into JPEG, using the built-in
 * Android compressor.
 *
 * TODO: Implement cropping, if required.
 */
class TaskChainedCompressImageToJpeg extends TaskJpegEncode {
    private final static Log.Tag TAG = new Log.Tag("TaskChainJpg");

    TaskChainedCompressImageToJpeg(ImageToProcess image, Executor executor,
            ImageTaskManager imageTaskManager, CaptureSession captureSession) {
        super(image, executor, imageTaskManager, ProcessingPriority.SLOW, captureSession);
    }

    private void logWrapper(String message) {
        // Do nothing.
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;
        Rect safeCrop = guaranteedSafeCrop(img.proxy, img.crop);
        final List<ImageProxy.Plane> planeList = img.proxy.getPlanes();

        final TaskImage inputImage = new TaskImage(mImage.rotation, img.proxy.getWidth(),
                img.proxy.getHeight(), img.proxy.getFormat(), safeCrop);
        final TaskImage resultImage = new TaskImage(mImage.rotation, img.proxy.getWidth(),
                img.proxy.getHeight(), ImageFormat.JPEG , safeCrop);
        byte[] dataCopy;
        int[] strides = new int[3];

        try {
            onStart(mId, inputImage, resultImage, TaskInfo.Destination.FINAL_IMAGE);

            // Do the byte copy
            strides[0] = planeList.get(0).getRowStride()
                    / planeList.get(0).getPixelStride();
            strides[1] = planeList.get(1).getRowStride()
                    / planeList.get(1).getPixelStride();
            strides[2] = 2 * planeList.get(2).getRowStride()
                    / planeList.get(2).getPixelStride();

            // TODO: For performance, use a cache subsystem for buffer reuse.
            dataCopy = convertYUV420ImageToPackedNV21(img.proxy);
        } finally {
            // Release the image now that you have a usable copy
            mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
        }

        final byte[] chainedDataCopy = dataCopy;
        final int[] chainedStrides = strides;

        // This task drops the image reference.
        TaskImageContainer chainedTask = new TaskJpegEncode(this, ProcessingPriority.SLOW) {

            @Override
            public void run() {
                // Image is closed by now. Do NOT reference image directly.
                byte[] compressedData = convertNv21toJpeg(chainedDataCopy,
                        resultImage.height, resultImage.width, chainedStrides);
                onJpegEncodeDone(mId, inputImage, resultImage, compressedData,
                        TaskInfo.Destination.FINAL_IMAGE);
                logWrapper("Finished off a chained task now that image is released.");
            }
        };

        // Passed null, since the image has already been released.
        mImageTaskManager.appendTasks(null, chainedTask);
        logWrapper("Kicking off a chained task now that image is released.");
    }
}
