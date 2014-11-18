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

package com.android.camera.util;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.Image.Plane;

import java.nio.ByteBuffer;

/**
 * Provides direct access to libjpeg-turbo via the NDK.
 */
public class JpegUtilNative {
    static {
        System.loadLibrary("jni_jpegutil");
    }

    public static final int ERROR_OUT_BUF_TOO_SMALL = -1;

    /**
     * Compresses an image from YUV422 format to jpeg.
     *
     * @param yBuf the buffer containing the Y component of the image
     * @param yPStride the stride between adjacent pixels in the same row in
     *            yBuf
     * @param yRStride the stride between adjacent rows in yBuf
     * @param cbBuf the buffer containing the Cb component of the image
     * @param cbPStride the stride between adjacent pixels in the same row in
     *            cbBuf
     * @param cbRStride the stride between adjacent rows in cbBuf
     * @param crBuf the buffer containing the Cr component of the image
     * @param crPStride the stride between adjacent pixels in the same row in
     *            crBuf
     * @param crRStride the stride between adjacent rows in crBuf
     * @param quality the quality level (0 to 100) to use
     * @return The number of bytes written, or a negative value indicating an
     *         error
     */
    private static native int compressJpegFromYUV420pNative(
            int width, int height,
            Object yBuf, int yPStride, int yRStride,
            Object cbBuf, int cbPStride, int cbRStride,
            Object crBuf, int crPStride, int crRStride,
            Object outBuf, int outBufCapacity, int quality);

    /**
     * Copies the Image.Plane specified by planeBuf, pStride, and rStride to the
     * Bitmap.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @param planeBuf the native ByteBuffer containing the image plane data
     * @param pStride the stride between adjacent pixels in the same row of
     *            planeBuf
     * @param rStride the stride between adjacent rows in planeBuf
     * @param outBitmap the output bitmap object
     * @param rot90 the multiple of 90 degrees to rotate counterclockwise, one of {0, 1, 2, 3}.
     */
    private static native void copyImagePlaneToBitmap(int width, int height, Object planeBuf,
            int pStride, int rStride, Object outBitmap, int rot90);

    public static void copyImagePlaneToBitmap(Image.Plane plane, Bitmap bitmap, int rot90) {
        if (bitmap.getConfig() != Bitmap.Config.ALPHA_8) {
            throw new RuntimeException("Unsupported bitmap format");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        copyImagePlaneToBitmap(width, height, plane.getBuffer(), plane.getPixelStride(),
                plane.getRowStride(), bitmap, rot90);
    }

    /**
     * @see JpegUtilNative#compressJpegFromYUV420pNative(int, int,
     *      java.lang.Object, int, int, java.lang.Object, int, int,
     *      java.lang.Object, int, int, java.lang.Object, int, int)
     */
    public static int compressJpegFromYUV420p(
            int width, int height,
            ByteBuffer yBuf, int yPStride, int yRStride,
            ByteBuffer cbBuf, int cbPStride, int cbRStride,
            ByteBuffer crBuf, int crPStride, int crRStride,
            ByteBuffer outBuf, int quality) {
        return compressJpegFromYUV420pNative(width, height, yBuf, yPStride, yRStride, cbBuf,
                cbPStride, cbRStride, crBuf, crPStride, crRStride, outBuf, outBuf.capacity(),
                quality);
    }

    /**
     * Compresses the given image to jpeg. Note that only
     * ImageFormat.YUV_420_888 is currently supported. Furthermore, all planes
     * must use direct byte buffers.
     *
     * @param img the image to compress
     * @param outBuf a direct byte buffer to hold the output jpeg.
     * @param quality the jpeg encoder quality (0 to 100)
     * @return The number of bytes written to outBuf
     */
    public static int compressJpegFromYUV420Image(Image img, ByteBuffer outBuf, int quality) {
        if (img.getFormat() != ImageFormat.YUV_420_888) {
            throw new RuntimeException("Unsupported Image Format.");
        }

        final int NUM_PLANES = 3;

        if (img.getPlanes().length != NUM_PLANES) {
            throw new RuntimeException("Output buffer must be direct.");
        }

        if (!outBuf.isDirect()) {
            throw new RuntimeException("Output buffer must be direct.");
        }

        ByteBuffer[] planeBuf = new ByteBuffer[NUM_PLANES];
        int[] pixelStride = new int[NUM_PLANES];
        int[] rowStride = new int[NUM_PLANES];

        for (int i = 0; i < NUM_PLANES; i++) {
            Plane plane = img.getPlanes()[i];

            if (!plane.getBuffer().isDirect()) {
                return -1;
            }

            planeBuf[i] = plane.getBuffer();
            pixelStride[i] = plane.getPixelStride();
            rowStride[i] = plane.getRowStride();
        }

        outBuf.clear();

        int numBytesWritten = compressJpegFromYUV420p(
                img.getWidth(), img.getHeight(),
                planeBuf[0], pixelStride[0], rowStride[0],
                planeBuf[1], pixelStride[1], rowStride[1],
                planeBuf[2], pixelStride[2], rowStride[2],
                outBuf, quality);

        outBuf.limit(numBytesWritten);

        return numBytesWritten;
    }

    /**
     * Compresses the given image to jpeg. Note that only
     * ImageFormat.YUV_420_888 is currently supported. Furthermore, all planes
     * must use direct byte buffers.<br>
     * FIXME TODO OPTIMIZE This method is *incredibly* inefficient.
     *
     * @param img the image to compress
     * @param outBuf a direct byte buffer to hold the output jpeg.
     * @param quality the jpeg encoder quality (0 to 100)
     * @param degrees the amount to rotate the image clockwise, in degrees.
     * @return The number of bytes written to outBuf
     */
    public static int compressJpegFromYUV420Image(Image img, ByteBuffer outBuf, int quality,
            int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new RuntimeException("Unsupported rotation angle");
        }

        if (degrees == 0) {
            return compressJpegFromYUV420Image(img, outBuf, quality);
        }

        if (img.getFormat() != ImageFormat.YUV_420_888) {
            throw new RuntimeException("Unsupported Image Format.");
        }

        final int NUM_PLANES = 3;

        if (img.getPlanes().length != NUM_PLANES) {
            throw new RuntimeException("Output buffer must be direct.");
        }

        if (!outBuf.isDirect()) {
            throw new RuntimeException("Output buffer must be direct.");
        }

        ByteBuffer[] planeBuf = new ByteBuffer[NUM_PLANES];
        int[] pixelStride = new int[NUM_PLANES];
        int[] rowStride = new int[NUM_PLANES];

        for (int i = 0; i < NUM_PLANES; i++) {
            Plane plane = img.getPlanes()[i];

            if (!plane.getBuffer().isDirect()) {
                return -1;
            }

            int width = img.getWidth();
            int height = img.getHeight();

            if (i > 0) {
                // The image plane for the Cb and Cr channels is downsampled.
                width /= 2;
                height /= 2;
            }

            if (degrees == 90 || degrees == 270) {
                int tmp = width;
                width = height;
                height = tmp;
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);

            // TODO: Make copyImagePlaneToBitmap take clockwise angle to rotate the bitmap.
            int counterClockwiseDegrees = (360 - degrees) % 360;
            copyImagePlaneToBitmap(plane, bitmap, counterClockwiseDegrees / 90);

            Bitmap rotatedBitmap = bitmap;

            ByteBuffer rotatedBitmapBuffer = ByteBuffer.allocateDirect(
                    rotatedBitmap.getWidth() * rotatedBitmap.getHeight());

            rotatedBitmap.copyPixelsToBuffer(rotatedBitmapBuffer);

            planeBuf[i] = rotatedBitmapBuffer;
            pixelStride[i] = 1;
            rowStride[i] = rotatedBitmap.getWidth();
        }

        outBuf.clear();

        int width = img.getWidth();
        int height = img.getHeight();
        if (degrees == 90 || degrees == 270) {
            int tmp = width;
            width = height;
            height = tmp;
        }

        int numBytesWritten = compressJpegFromYUV420p(
                width, height,
                planeBuf[0], pixelStride[0], rowStride[0],
                planeBuf[1], pixelStride[1], rowStride[1],
                planeBuf[2], pixelStride[2], rowStride[2],
                outBuf, quality);

        outBuf.limit(numBytesWritten);

        return numBytesWritten;
    }
}
