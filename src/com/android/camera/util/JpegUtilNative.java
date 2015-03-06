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
import android.graphics.Rect;

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Provides direct access to libjpeg-turbo via the NDK.
 */
public class JpegUtilNative {
    static {
        System.loadLibrary("jni_jpegutil");
    }

    public static final int ERROR_OUT_BUF_TOO_SMALL = -1;
    private static final Log.Tag TAG = new Log.Tag("JpegUtilNative");

    /**
     * Compresses a YCbCr image to jpeg, applying a crop and rotation.
     * <p>
     * The input is defined as a set of 3 planes of 8-bit samples, one plane for
     * each channel of Y, Cb, Cr.<br>
     * The Y plane is assumed to have the same width and height of the entire
     * image.<br>
     * The Cb and Cr planes are assumed to be downsampled by a factor of 2, to
     * have dimensions (floor(width / 2), floor(height / 2)).<br>
     * Each plane is specified by a direct java.nio.ByteBuffer, a pixel-stride,
     * and a row-stride. So, the sample at coordinate (x, y) can be retrieved
     * from byteBuffer[x * pixel_stride + y * row_stride].
     * <p>
     * The pre-compression transformation is applied as follows:
     * <ol>
     * <li>The image is cropped to the rectangle from (cropLeft, cropTop) to
     * (cropRight - 1, cropBottom - 1). So, a cropping-rectangle of (0, 0) -
     * (width, height) is a no-op.</li>
     * <li>The rotation is applied counter-clockwise relative to the coordinate
     * space of the image, so a CCW rotation will appear CW when the image is
     * rendered in scanline order. Only rotations which are multiples of
     * 90-degrees are suppored, so the parameter 'rot90' specifies which
     * multiple of 90 to rotate the image.</li>
     * </ol>
     *
     * @param width the width of the image to compress
     * @param height the height of the image to compress
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
     * @param outBuf a direct java.nio.ByteBuffer to hold the compressed jpeg.
     *            This must have enough capacity to store the result, or an
     *            error code will be returned.
     * @param outBufCapacity the capacity of outBuf
     * @param quality the jpeg-quality (1-100) to use
     * @param cropLeft left-edge of the bounds of the image to crop to before
     *            rotation
     * @param cropTop top-edge of the bounds of the image to crop to before
     *            rotation
     * @param cropRight right-edge of the bounds of the image to crop to before
     *            rotation
     * @param cropBottom bottom-edge of the bounds of the image to crop to
     *            before rotation
     * @param rot90 the multiple of 90 to rotate the image CCW (after cropping)
     */
    private static native int compressJpegFromYUV420pNative(
            int width, int height,
            Object yBuf, int yPStride, int yRStride,
            Object cbBuf, int cbPStride, int cbRStride,
            Object crBuf, int crPStride, int crRStride,
            Object outBuf, int outBufCapacity,
            int quality,
            int cropLeft, int cropTop, int cropRight, int cropBottom,
            int rot90);

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
     * @param rot90 the multiple of 90 degrees to rotate counterclockwise, one
     *            of {0, 1, 2, 3}.
     */
    private static native void copyImagePlaneToBitmap(int width, int height, Object planeBuf,
            int pStride, int rStride, Object outBitmap, int rot90);

    public static void copyImagePlaneToBitmap(ImageProxy.Plane plane, Bitmap bitmap, int rot90) {
        if (bitmap.getConfig() != Bitmap.Config.ALPHA_8) {
            throw new RuntimeException("Unsupported bitmap format");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        copyImagePlaneToBitmap(width, height, plane.getBuffer(), plane.getPixelStride(),
                plane.getRowStride(), bitmap, rot90);
    }

    /**
     * @see JpegUtilNative#compressJpegFromYUV420pNative(int, int, Object, int,
     *      int, Object, int, int, Object, int, int, Object, int, int, int, int,
     *      int, int, int)
     */
    public static int compressJpegFromYUV420p(
            int width, int height,
            ByteBuffer yBuf, int yPStride, int yRStride,
            ByteBuffer cbBuf, int cbPStride, int cbRStride,
            ByteBuffer crBuf, int crPStride, int crRStride,
            ByteBuffer outBuf, int quality,
            int cropLeft, int cropTop, int cropRight, int cropBottom, int rot90) {
        Log.i(TAG, String.format(
                "Compressing jpeg with size = (%d, %d); " +
                        "y-channel pixel stride = %d; " +
                        "y-channel row stride =  %d; " +
                        "cb-channel pixel stride = %d; " +
                        "cb-channel row stride =  %d; " +
                        "cr-channel pixel stride = %d; " +
                        "cr-channel row stride =  %d; " +
                        "crop = [(%d, %d) - (%d, %d)]; " +
                        "rotation = %d * 90 deg. ",
                width, height, yPStride, yRStride, cbPStride, cbRStride, crPStride, crRStride,
                cropLeft, cropTop, cropRight, cropBottom, rot90));
        return compressJpegFromYUV420pNative(width, height, yBuf, yPStride, yRStride, cbBuf,
                cbPStride, cbRStride, crBuf, crPStride, crRStride, outBuf, outBuf.capacity(),
                quality, cropLeft, cropTop, cropRight, cropBottom, rot90);
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
    public static int compressJpegFromYUV420Image(ImageProxy img, ByteBuffer outBuf, int quality) {
        return compressJpegFromYUV420Image(img, outBuf, quality, 0);
    }

    /**
     * Compresses the given image to jpeg. Note that only
     * ImageFormat.YUV_420_888 is currently supported. Furthermore, all planes
     * must use direct byte buffers.<br>
     *
     * @param img the image to compress
     * @param outBuf a direct byte buffer to hold the output jpeg.
     * @param quality the jpeg encoder quality (0 to 100)
     * @param degrees the amount to rotate the image clockwise, in degrees.
     * @return The number of bytes written to outBuf
     */
    public static int compressJpegFromYUV420Image(ImageProxy img, ByteBuffer outBuf, int quality,
            int degrees) {
        return compressJpegFromYUV420Image(img, outBuf, quality, new Rect(0, 0, img.getWidth(),
                img.getHeight()), degrees);
    }

    /**
     * Compresses the given image to jpeg. Note that only
     * ImageFormat.YUV_420_888 is currently supported. Furthermore, all planes
     * must use direct byte buffers.
     *
     * @param img the image to compress
     * @param outBuf a direct byte buffer to hold the output jpeg.
     * @param quality the jpeg encoder quality (0 to 100)
     * @param crop The crop rectangle to apply *before* rotation.
     * @param degrees The number of degrees to rotate the image *after*
     *            cropping. This must be a multiple of 90. Note that this
     *            represents a clockwise rotation in the space of the image
     *            plane, which appears as a counter-clockwise rotation when the
     *            image is displayed in raster-order.
     * @return The number of bytes written to outBuf
     */
    public static int compressJpegFromYUV420Image(ImageProxy img, ByteBuffer outBuf, int quality,
            Rect crop, int degrees) {
        Preconditions.checkState((degrees % 90) == 0, "Rotation must be a multiple of 90 degrees," +
                " was " + degrees);
        // Handle negative angles by converting to positive.
        degrees = ((degrees % 360) + (360 * 2)) % 360;
        Preconditions.checkState(outBuf.isDirect(), "Output buffer must be direct");
        Preconditions.checkState(crop.left < crop.right, "Invalid crop rectangle: " +
                crop.toString());
        Preconditions.checkState(crop.top < crop.bottom, "Invalid crop rectangle: " +
                crop.toString());
        final int NUM_PLANES = 3;
        Preconditions.checkState(img.getFormat() == ImageFormat.YUV_420_888, "Only " +
                "ImageFormat.YUV_420_888 is supported, found " + img.getFormat());
        final List<ImageProxy.Plane> planeList = img.getPlanes();
        Preconditions.checkState(planeList.size() == NUM_PLANES);

        ByteBuffer[] planeBuf = new ByteBuffer[NUM_PLANES];
        int[] pixelStride = new int[NUM_PLANES];
        int[] rowStride = new int[NUM_PLANES];

        for (int i = 0; i < NUM_PLANES; i++) {
            ImageProxy.Plane plane = planeList.get(i);

            Preconditions.checkState(plane.getBuffer().isDirect());

            planeBuf[i] = plane.getBuffer();
            pixelStride[i] = plane.getPixelStride();
            rowStride[i] = plane.getRowStride();
        }

        outBuf.clear();

        int cropLeft = crop.left;
        cropLeft = Math.max(cropLeft, 0);
        cropLeft = Math.min(cropLeft, img.getWidth() - 1);

        int cropRight = crop.right;
        cropRight = Math.max(cropRight, 0);
        cropRight = Math.min(cropRight, img.getWidth());

        int cropTop = crop.top;
        cropTop = Math.max(cropTop, 0);
        cropTop = Math.min(cropTop, img.getHeight() - 1);

        int cropBot = crop.bottom;
        cropBot = Math.max(cropBot, 0);
        cropBot = Math.min(cropBot, img.getHeight());

        degrees = degrees % 360;
        // Convert from clockwise to counter-clockwise.
        int rot90 = (360 - degrees) / 90;

        int numBytesWritten = compressJpegFromYUV420p(
                img.getWidth(), img.getHeight(),
                planeBuf[0], pixelStride[0], rowStride[0],
                planeBuf[1], pixelStride[1], rowStride[1],
                planeBuf[2], pixelStride[2], rowStride[2],
                outBuf, quality, cropLeft, cropTop, cropRight, cropBot,
                rot90);

        outBuf.limit(numBytesWritten);

        return numBytesWritten;
    }
}
