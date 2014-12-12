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

import android.graphics.ImageFormat;

import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import java.util.concurrent.Executor;

/**
 *  Implements the conversion of a YUV_420_888 image to compressed JPEG byte array,
 *  as two separate tasks: the first to copy from the image to NV21 memory layout, and the second
 *  to convert the image into JPEG, using the built-in Android compressor.
 */
class TaskChainedCompressImageToJpeg extends TaskJpegEncode {
    private final static Log.Tag TAG = new Log.Tag("TaskChainJpg");

    TaskChainedCompressImageToJpeg(ImageProxy imageProxy, Executor executor,
            ImageBackend imageBackend) {
        super(imageProxy, executor, imageBackend, ProcessingPriority.SLOW);
    }

    private void logWrapper(String message) {
        // Do nothing.
    }


    @Override
    public void run() {
        ImageProxy img = mImageProxy;

        // TODO: Pass in the orientation for processing as well.
        final TaskImage inputImage = new TaskImage(OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(),img.getHeight(),img.getFormat());
        final TaskImage resultImage = new TaskImage(OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(),img.getHeight(),ImageFormat.JPEG);

        onStart(mId,inputImage,resultImage);

        int[] strides = new int[3];
        // Do the byte copy
        strides[0] = img.getPlanes()[0].getRowStride() / img.getPlanes()[0].getPixelStride();
        strides[1] = 2 * img.getPlanes()[1].getRowStride() / img.getPlanes()[1].getPixelStride();
        strides[2] = 2 * img.getPlanes()[2].getRowStride() / img.getPlanes()[2].getPixelStride();

        byte[] dataCopy = mImageBackend.getCache().cacheGet();
        if (dataCopy == null) {
            dataCopy = convertYUV420ImageToPackedNV21(img);
        } else {
            convertYUV420ImageToPackedNV21(img, dataCopy);
        }

        // Release the image now that you have a usable copy
        mImageBackend.releaseSemaphoreReference(img, mExecutor);

        final byte[] chainedDataCopy = dataCopy;
        final int[] chainedStrides = strides;

        // This task drops the image reference.
        TaskImageContainer chainedTask = new TaskJpegEncode(this, ProcessingPriority.SLOW) {

            @Override
            public void run() {
                // Image is closed by now. Do NOT reference image directly.
                byte[] compressedData = convertNv21toJpeg(chainedDataCopy,
                        resultImage.height, resultImage.width, chainedStrides);
                onJpegEncodeDone(mId, inputImage, resultImage, compressedData);
                mImageBackend.getCache().cacheSave(chainedDataCopy);
                logWrapper("Finished off a chained task now that image is released.");
            }
        };

        // Passed null, since the image has already been released.
        mImageBackend.appendTasks(null, chainedTask);
        logWrapper("Kicking off a chained task now that image is released.");

    }
}
