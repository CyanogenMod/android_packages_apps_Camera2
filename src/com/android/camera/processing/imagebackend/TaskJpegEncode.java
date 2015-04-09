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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * TaskJpegEncode are the base class of tasks that wish to do JPEG
 * encoding/decoding. Various helper functions are held in this class.
 */
public abstract class TaskJpegEncode extends TaskImageContainer {

    protected final static Log.Tag TAG = new Log.Tag("TaskJpegEnc");

    /**
     * Constructor to use for NOT passing the image reference forward.
     *
     * @param otherTask Parent task that is spawning this task
     * @param processingPriority Preferred processing priority for this task
     */
    public TaskJpegEncode(TaskImageContainer otherTask, ProcessingPriority processingPriority) {
        super(otherTask, processingPriority);
    }

    /**
     * Constructor to use for initial task definition or complex shared state
     * sharing.
     *
     * @param image Image reference that is required for computation
     * @param executor Executor to avoid thread control leakage
     * @param imageTaskManager ImageBackend associated with
     * @param preferredLane Preferred processing priority for this task
     * @param captureSession Session associated for UI handling
     */
    public TaskJpegEncode(ImageToProcess image, Executor executor,
            ImageTaskManager imageTaskManager,
            TaskImageContainer.ProcessingPriority preferredLane, CaptureSession captureSession) {
        super(image, executor, imageTaskManager, preferredLane, captureSession);
    }

    /**
     * Converts the YUV420_888 Image into a packed NV21 of a single byte array,
     * suitable for JPEG compression by the method convertNv21toJpeg. This
     * version will allocate its own byte buffer memory.
     *
     * @param img image to be converted
     * @return byte array of NV21 packed image
     */
    public byte[] convertYUV420ImageToPackedNV21(ImageProxy img) {
        final List<ImageProxy.Plane> planeList = img.getPlanes();

        ByteBuffer y_buffer = planeList.get(0).getBuffer();
        ByteBuffer u_buffer = planeList.get(1).getBuffer();
        ByteBuffer v_buffer = planeList.get(2).getBuffer();
        byte[] dataCopy = new byte[y_buffer.capacity() + u_buffer.capacity() + v_buffer.capacity()];

        return convertYUV420ImageToPackedNV21(img, dataCopy);
    }

    /**
     * Converts the YUV420_888 Image into a packed NV21 of a single byte array,
     * suitable for JPEG compression by the method convertNv21toJpeg. Creates a
     * memory block with the y component at the head and interleaves the u,v
     * components following the y component. Caller is responsible to allocate a
     * large enough buffer for results.
     *
     * @param img image to be converted
     * @param dataCopy buffer to write NV21 packed image
     * @return byte array of NV21 packed image
     */
    public byte[] convertYUV420ImageToPackedNV21(ImageProxy img, byte[] dataCopy) {
        // Get all the relevant information and then release the image.
        final int w = img.getWidth();
        final int h = img.getHeight();
        final List<ImageProxy.Plane> planeList = img.getPlanes();

        ByteBuffer y_buffer = planeList.get(0).getBuffer();
        ByteBuffer u_buffer = planeList.get(1).getBuffer();
        ByteBuffer v_buffer = planeList.get(2).getBuffer();
        final int color_pixel_stride = planeList.get(1).getPixelStride();
        final int y_size = y_buffer.capacity();
        final int u_size = u_buffer.capacity();
        final int data_offset = w * h;

        for (int i = 0; i < y_size; i++) {
            dataCopy[i] = (byte) (y_buffer.get(i) & 255);
        }

        for (int i = 0; i < u_size / color_pixel_stride; i++) {
            dataCopy[data_offset + 2 * i] = v_buffer.get(i * color_pixel_stride);
            dataCopy[data_offset + 2 * i + 1] = u_buffer.get(i * color_pixel_stride);
        }

        return dataCopy;
    }

    /**
     * Creates a dummy shaded image for testing in packed NV21 format.
     *
     * @param dataCopy Buffer to contained shaded test image
     * @param w Width of image
     * @param h Height of Image
     */
    public void dummyConvertYUV420ImageToPackedNV21(byte[] dataCopy,
            final int w, final int h) {
        final int y_size = w * h;
        final int data_offset = w * h;

        for (int i = 0; i < y_size; i++) {
            dataCopy[i] = (byte) ((((i % w) * 255) / w) & 255);
            dataCopy[i] = 0;
        }

        for (int i = 0; i < h / 2; i++) {
            for (int j = 0; j < w / 2; j++) {
                int offset = data_offset + w * i + j * 2;
                dataCopy[offset] = (byte) ((255 * i) / (h / 2) & 255);
                dataCopy[offset + 1] = (byte) ((255 * j) / (w / 2) & 255);
            }
        }
    }

    /**
     * Wraps the Android built-in YUV to Jpeg conversion routine. Pass in a
     * valid NV21 image and get back a compressed JPEG buffer. A good default
     * JPEG compression implementation that should be supported on all
     * platforms.
     *
     * @param data_copy byte buffer that contains the NV21 image
     * @param w width of NV21 image
     * @param h height of N21 image
     * @return byte array of compressed JPEG image
     */
    public byte[] convertNv21toJpeg(byte[] data_copy, int w, int h, int[] strides) {
        Log.e(TAG, "TIMER_BEGIN NV21 to Jpeg Conversion.");
        YuvImage yuvImage = new YuvImage(data_copy, ImageFormat.NV21, w, h, strides);

        ByteArrayOutputStream postViewBytes = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(new Rect(0, 0, w, h), 90, postViewBytes);
        try {
            postViewBytes.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.e(TAG, "TIMER_END NV21 to Jpeg Conversion.");
        return postViewBytes.toByteArray();
    }

    /**
     * Implement cropping through the decompression and re-compression of the JPEG using
     * the built-in Android bitmap utilities.
     *
     * @param jpegData Compressed Image to be cropped
     * @param crop Crop to be applied
     * @param recompressionQuality Recompression quality value for cropped JPEG Image
     * @return JPEG compressed byte array representing the cropped image
     */
    public byte[] decompressCropAndRecompressJpegData(final byte[] jpegData, Rect crop,
            int recompressionQuality) {
        Bitmap original = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        final Bitmap croppedResult = Bitmap.createBitmap(original, crop.left, crop.top,
                crop.width(), crop.height());;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        croppedResult.compress(Bitmap.CompressFormat.JPEG, recompressionQuality, stream);
        return stream.toByteArray();
    }

    /**
     * Wraps the onResultCompressed listener for ease of use.
     *
     * @param id Unique content id
     * @param input Specification of image input size
     * @param result Specification of resultant input size
     * @param data Container for uncompressed data that represents image
     */
    public void onJpegEncodeDone(long id, TaskImage input, TaskImage result, byte[] data,
            TaskInfo.Destination aDestination) {
        TaskInfo job = new TaskInfo(id, input, result, aDestination);
        final ImageProcessorListener listener = mImageTaskManager.getProxyListener();
        listener.onResultCompressed(job, new CompressedPayload(data));
    }

    /**
     * Wraps the onResultUri listener for ease of use.
     *
     * @param id Unique content id
     * @param input Specification of image input size
     * @param result Specification of resultant input size
     * @param imageUri URI of the saved image.
     * @param destination Specifies the purpose of the image artifact
     */
    public void onUriResolved(long id, TaskImage input, TaskImage result, final Uri imageUri,
            TaskInfo.Destination destination) {
        final TaskInfo job = new TaskInfo(id, input, result, destination);
        final ImageProcessorListener listener = mImageTaskManager.getProxyListener();
        listener.onResultUri(job, imageUri);
    }
}
