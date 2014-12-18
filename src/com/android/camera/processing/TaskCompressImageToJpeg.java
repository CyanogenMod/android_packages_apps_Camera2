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
 *  as a single two-step task: the first to copy from the image to NV21 memory layout,
 *  and the second to convert the image into JPEG, using the built-in Android compressor.
 */
public class TaskCompressImageToJpeg extends TaskJpegEncode {

    TaskCompressImageToJpeg(ImageProxy imageProxy, Executor executor, ImageBackend imageBackend) {
        super(imageProxy, executor, imageBackend, ProcessingPriority.SLOW);
    }

    private void logWrapper(String message) {
        // Do nothing.
    }

    @Override
    public void run() {
        ImageProxy img = mImageProxy;

        // TODO: Pass in the orientation for processing as well.
        final TaskImage inputImage = new TaskImage(
                OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(),img.getHeight(),img.getFormat());
        final TaskImage resultImage = new TaskImage(
                OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(),img.getHeight(),ImageFormat.JPEG);

        onStart(mId,inputImage,resultImage);

        logWrapper("TIMER_END Full-size YUV buffer available, w=" + img.getWidth() + " h="
                + img.getHeight() + " of format " + img.getFormat() + " (35==YUV_420_888)");
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

        byte[] compressedData = convertNv21toJpeg(dataCopy, inputImage.width, inputImage.height,
                strides);
        mImageBackend.getCache().cacheSave(dataCopy);
        onJpegEncodeDone(mId,inputImage,resultImage,compressedData);

    }
}
