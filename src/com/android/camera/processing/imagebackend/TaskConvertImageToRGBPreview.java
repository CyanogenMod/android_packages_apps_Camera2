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
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to subsampled image
 * inscribed in a circle.
 */
public class TaskConvertImageToRGBPreview extends TaskImageContainer {
    // 24 bit-vector to be written for images that are out of bounds.
    public final static int OUT_OF_BOUNDS_COLOR = 0x00000000;

    protected final static Log.Tag TAG = new Log.Tag("TaskRGBPreview");

    private int mTargetHeight;

    private int mTargetWidth;

    TaskConvertImageToRGBPreview(ImageToProcess image, Executor executor,
            ImageBackend imageBackend, CaptureSession captureSession, int targetWidth,
            int targetHeight) {
        super(image, executor, imageBackend, ProcessingPriority.FAST, captureSession);
        mTargetWidth = targetWidth;
        mTargetHeight = targetHeight;
    }

    private void logWrapper(String message) {
        // Do nothing.
    }

    /**
     * Simple helper function
     */
    private int quantizeBy2(int value) {
        return (value / 2) * 2;
    }

    /**
     * Way to calculate the resultant image sizes of inscribed circles:
     * colorInscribedDataCircleFromYuvImage,
     * dummyColorInscribedDataCircleFromYuvImage, colorDataCircleFromYuvImage
     *
     * @param height height of the input image
     * @param width width of the input image
     * @return height/width of the resultant square image TODO: Refactor
     *         functions in question to return the image size as a tuple for
     *         these functions, or re-use an general purpose holder object.
     */
    protected int inscribedCircleRadius(int width, int height) {
        return (Math.min(height, width) / 2) + 1;
    }

    /**
     * Calculates the best integer subsample from a given height and width to a
     * target width and height It is assumed that the exact scaling will be done
     * with the Android Bitmap framework; this subsample value is to best
     * convert raw images into the lowest resolution raw images in visually
     * lossless manner without changing the aspect ratio or creating subsample
     * artifacts.
     *
     * @param height height of the input image
     * @param width width of the input image
     * @param targetWidth width of the image as it will be seen on the screen in
     *            raw pixels
     * @param targetHeight height of the image as it will be seen on the screen
     *            in raw pixels
     * @returns inscribed image as ARGB_8888
     */
    protected int calculateBestSubsampleFactor(int height, int width, int targetWidth,
            int targetHeight) {
        int maxSubsample = Math.min(height / targetHeight, width / targetWidth);
        if (maxSubsample < 1) {
            return 1;
        }

        // Make sure the resultant image width/height is divisible by 2 to
        // account
        // for chroma subsampled images such as YUV
        for (int i = maxSubsample; i >= 1; i--) {
            if (((height % (2 * i) == 0) && (width % (2 * i) == 0))) {
                return i;
            }
        }

        return 1; // If all fails, don't do the subsample.
    }

    /**
     * Converts an Android Image to a inscribed circle bitmap of ARGB_8888 in a
     * super-optimized loop unroll. Guarantees only one subsampled pass over the
     * YUV data. This version of the function should be used in production and
     * also feathers the edges with 50% alpha on its edges. NOTE: To get the
     * size of the resultant bitmap, you need to call inscribedCircleRadius(w,
     * h) outside of this function. Runs in ~10-15ms for 4K image with a
     * subsample of 13. TODO: Implement horizontal alpha feathering of the edge
     * of the image, if necessary.
     *
     * @param img YUV420_888 Image to convert
     * @param subsample width/height subsample factor
     * @returns inscribed image as ARGB_8888
     */
    protected int[] colorInscribedDataCircleFromYuvImage(ImageProxy img, int subsample) {
        int w = img.getWidth() / subsample;
        int h = img.getHeight() / subsample;
        int r = inscribedCircleRadius(w, h);

        int inscribedXMin;
        int inscribedXMax;
        int inscribedYMin;
        int inscribedYMax;

        // Set up input read boundaries.
        if (w > h) {
            // since we're 2x2 blocks we need to quantize these values by 2
            inscribedXMin = quantizeBy2(w / 2 - r);
            inscribedXMax = quantizeBy2(w / 2 + r);
            inscribedYMin = 0;
            inscribedYMax = h;
        } else {
            inscribedXMin = 0;
            inscribedXMax = w;
            // since we're 2x2 blocks we need to quantize these values by 2
            inscribedYMin = quantizeBy2(h / 2 - r);
            inscribedYMax = quantizeBy2(h / 2 + r);
        }

        ByteBuffer buf0 = img.getPlanes()[0].getBuffer();
        ByteBuffer bufU = img.getPlanes()[1].getBuffer(); // Downsampled by 2
        ByteBuffer bufV = img.getPlanes()[2].getBuffer(); // Downsampled by 2
        int yByteStride = img.getPlanes()[0].getRowStride() * subsample;
        int uByteStride = img.getPlanes()[1].getRowStride() * subsample;
        int vByteStride = img.getPlanes()[2].getRowStride() * subsample;
        int yPixelStride = img.getPlanes()[0].getPixelStride() * subsample;
        int uPixelStride = img.getPlanes()[1].getPixelStride() * subsample;
        int vPixelStride = img.getPlanes()[2].getPixelStride() * subsample;
        int outputPixelStride = r * 2;
        int centerY = h / 2;
        int centerX = w / 2;

        int len = r * r * 4;
        int[] colors = new int[len];
        int alpha = 255 << 24;

        /*
         * Quick n' Dirty YUV to RGB conversion R = Y + 1.403V' G = Y - 0.344U'
         * - 0.714V' B = Y + 1.770U'
         */

        Log.v(TAG, "TIMER_BEGIN Starting Native Java YUV420-to-RGB Quick n' Dirty Conversion 4");
        Log.v(TAG, "\t Y-Plane Size=" + w + "x" + h);
        Log.v(TAG,
                "\t U-Plane Size=" + img.getPlanes()[1].getRowStride() + " Pixel Stride="
                        + img.getPlanes()[1].getPixelStride());
        Log.v(TAG,
                "\t V-Plane Size=" + img.getPlanes()[2].getRowStride() + " Pixel Stride="
                        + img.getPlanes()[2].getPixelStride());
        // Take in vertical lines by factor of two because of the u/v component
        // subsample
        for (int j = inscribedYMin; j < inscribedYMax; j += 2) {
            int offsetY = j * yByteStride + inscribedXMin;
            int offsetColor = (j - inscribedYMin) * (outputPixelStride);
            int offsetU = (j / 2) * (uByteStride) + (inscribedXMin);
            int offsetV = (j / 2) * (vByteStride) + (inscribedXMin);
            // Parametrize the circle boundaries w.r.t. the y component.
            // Find the subsequence of pixels we need for each horizontal raster
            // line.
            int circleHalfWidth0 =
                    (int) (Math.sqrt((float) (r * r - (j - centerY) * (j - centerY))) + 0.5f);
            int circleMin0 = centerX - (circleHalfWidth0);
            int circleMax0 = centerX + circleHalfWidth0;
            int circleHalfWidth1 = (int) (Math.sqrt((float) (r * r - (j + 1 - centerY)
                    * (j + 1 - centerY))) + 0.5f);
            int circleMin1 = centerX - (circleHalfWidth1);
            int circleMax1 = centerX + circleHalfWidth1;

            // Take in horizontal lines by factor of two because of the u/v
            // component subsample
            // and everything as 2x2 blocks.
            for (int i = inscribedXMin; i < inscribedXMax; i += 2, offsetY += 2 * yPixelStride,
                    offsetColor += 2, offsetU += uPixelStride, offsetV += vPixelStride) {
                // Note i and j are in terms of pixels of the subsampled image
                // offsetY, offsetU, and offsetV are in terms of bytes of the
                // image
                // offsetColor, output_pixel stride are in terms of the packed
                // output image
                if ((i > circleMax0 && i > circleMax1) || (i + 1 < circleMin0 && i < circleMin1)) {
                    colors[offsetColor] = OUT_OF_BOUNDS_COLOR;
                    colors[offsetColor + 1] = OUT_OF_BOUNDS_COLOR;
                    colors[offsetColor + outputPixelStride] = OUT_OF_BOUNDS_COLOR;
                    colors[offsetColor + outputPixelStride + 1] = OUT_OF_BOUNDS_COLOR;
                    continue;
                }

                // calculate the RGB component of the u/v channels and use it
                // for all pixels in the 2x2 block
                int u = (int) (bufU.get(offsetU) & 255) - 128;
                int v = (int) (bufV.get(offsetV) & 255) - 128;
                int redDiff = (v * 45) >> 4;
                int greenDiff = 0 - ((u * 11 + v * 22) >> 4);
                int blueDiff = (u * 58) >> 4;

                if (i > circleMax0 || i < circleMin0) {
                    colors[offsetColor] = OUT_OF_BOUNDS_COLOR;
                } else {
                    // Do a little alpha feathering on the edges
                    int alpha00 = (i == circleMax0 || i == circleMin0) ? (128 << 24) : (255 << 24);

                    int y00 = (int) (buf0.get(offsetY) & 255);

                    int green00 = y00 + greenDiff;
                    int blue00 = y00 + blueDiff;
                    int red00 = y00 + redDiff;

                    // Get the railing correct
                    if (green00 < 0) {
                        green00 = 0;
                    }
                    if (red00 < 0) {
                        red00 = 0;
                    }
                    if (blue00 < 0) {
                        blue00 = 0;
                    }

                    if (green00 > 255) {
                        green00 = 255;
                    }
                    if (red00 > 255) {
                        red00 = 255;
                    }
                    if (blue00 > 255) {
                        blue00 = 255;
                    }

                    colors[offsetColor] = (red00 & 255) << 16 | (green00 & 255) << 8
                            | (blue00 & 255) | alpha00;
                }

                if (i + 1 > circleMax0 || i + 1 < circleMin0) {
                    colors[offsetColor + 1] = OUT_OF_BOUNDS_COLOR;
                } else {
                    int alpha01 = ((i + 1) == circleMax0 || (i + 1) == circleMin0) ? (128 << 24)
                            : (255 << 24);
                    int y01 = (int) (buf0.get(offsetY + yPixelStride) & 255);
                    int green01 = y01 + greenDiff;
                    int blue01 = y01 + blueDiff;
                    int red01 = y01 + redDiff;

                    // Get the railing correct
                    if (green01 < 0) {
                        green01 = 0;
                    }
                    if (red01 < 0) {
                        red01 = 0;
                    }
                    if (blue01 < 0) {
                        blue01 = 0;
                    }

                    if (green01 > 255) {
                        green01 = 255;
                    }
                    if (red01 > 255) {
                        red01 = 255;
                    }
                    if (blue01 > 255) {
                        blue01 = 255;
                    }
                    colors[offsetColor + 1] = (red01 & 255) << 16 | (green01 & 255) << 8
                            | (blue01 & 255) | alpha01;
                }

                if (i > circleMax1 || i < circleMin1) {
                    colors[offsetColor + outputPixelStride] = OUT_OF_BOUNDS_COLOR;
                } else {
                    int alpha10 = (i == circleMax1 || i == circleMin1) ? (128 << 24) : (255 << 24);
                    int y10 = (int) (buf0.get(offsetY + yByteStride) & 255);
                    int green10 = y10 + greenDiff;
                    int blue10 = y10 + blueDiff;
                    int red10 = y10 + redDiff;

                    // Get the railing correct
                    if (green10 < 0) {
                        green10 = 0;
                    }
                    if (red10 < 0) {
                        red10 = 0;
                    }
                    if (blue10 < 0) {
                        blue10 = 0;
                    }
                    if (green10 > 255) {
                        green10 = 255;
                    }
                    if (red10 > 255) {
                        red10 = 255;
                    }
                    if (blue10 > 255) {
                        blue10 = 255;
                    }

                    colors[offsetColor + outputPixelStride] = (red10 & 255) << 16
                            | (green10 & 255) << 8 | (blue10 & 255) | alpha10;
                }

                if (i + 1 > circleMax1 || i + 1 < circleMin1) {
                    colors[offsetColor + outputPixelStride + 1] = OUT_OF_BOUNDS_COLOR;
                } else {
                    int alpha11 = ((i + 1) == circleMax1 || (i + 1) == circleMin1) ? (128 << 24)
                            : (255 << 24);
                    int y11 = (int) (buf0.get(offsetY + yByteStride + yPixelStride) & 255);
                    int green11 = y11 + greenDiff;
                    int blue11 = y11 + blueDiff;
                    int red11 = y11 + redDiff;

                    // Get the railing correct
                    if (green11 < 0) {
                        green11 = 0;
                    }
                    if (red11 < 0) {
                        red11 = 0;
                    }
                    if (blue11 < 0) {
                        blue11 = 0;
                    }

                    if (green11 > 255) {
                        green11 = 255;
                    }

                    if (red11 > 255) {
                        red11 = 255;
                    }
                    if (blue11 > 255) {
                        blue11 = 255;
                    }
                    colors[offsetColor + outputPixelStride + 1] = (red11 & 255) << 16
                            | (green11 & 255) << 8 | (blue11 & 255) | alpha11;
                }

            }
        }
        Log.v(TAG, "TIMER_END Starting Native Java YUV420-to-RGB Quick n' Dirty Conversion 4");

        return colors;
    }

    /**
     * DEBUG IMAGE FUNCTION Converts an Android Image to a inscribed circle
     * bitmap, currently wired to the test pattern. Will subsample and optimize
     * the image given a target resolution.
     *
     * @param img YUV420_888 Image to convert
     * @param subsample width/height subsample factor
     * @returns inscribed image as ARGB_8888
     */
    protected int[] dummyColorInscribedDataCircleFromYuvImage(ImageProxy img, int subsample) {
        Log.e(TAG, "RUNNING DUMMY dummyColorInscribedDataCircleFromYuvImage");
        int w = img.getWidth() / subsample;
        int h = img.getHeight() / subsample;
        int r = inscribedCircleRadius(w, h);
        int len = r * r * 4;
        int[] colors = new int[len];

        // Make a fun test pattern.
        for (int i = 0; i < len; i++) {
            int x = i % (2 * r);
            int y = i / (2 * r);
            colors[i] = (255 << 24) | ((x & 255) << 16) | ((y & 255) << 8);
        }

        return colors;
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;

        final TaskImage inputImage = new TaskImage(img.rotation, img.proxy.getWidth(),
                img.proxy.getHeight(), img.proxy.getFormat());
        final int subsample = calculateBestSubsampleFactor(inputImage.width, inputImage.height,
                mTargetWidth, mTargetHeight);
        final int radius = inscribedCircleRadius(inputImage.width / subsample, inputImage.height
                / subsample);

        final TaskImage resultImage = new TaskImage(img.rotation, radius * 2, radius * 2,
                TaskImage.EXTRA_USER_DEFINED_FORMAT_ARGB_8888);

        onStart(mId, inputImage, resultImage);

        logWrapper("TIMER_END Rendering preview YUV buffer available, w=" + img.proxy.getWidth()
                / subsample + " h=" + img.proxy.getHeight() / subsample + " of subsample "
                + subsample);

        // For dummy version, use
        // dummyColorInscribedDataCircleFromYuvImage
        final int[] convertedImage = colorInscribedDataCircleFromYuvImage(img.proxy, subsample);
        // Signal backend that reference has been released
        mImageBackend.releaseSemaphoreReference(img, mExecutor);

        onPreviewDone(resultImage, inputImage, convertedImage);
    }

    /**
     * Wraps the onResultUncompressed listener function
     *
     * @param resultImage Image specification of result image
     * @param inputImage Image specification of the input image
     * @param colors Uncompressed data buffer
     */
    public void onPreviewDone(TaskImage resultImage, TaskImage inputImage, int[] colors) {
        TaskInfo job = new TaskInfo(mId, inputImage, resultImage);
        final ImageProcessorListener listener = mImageBackend.getProxyListener();

        listener.onResultUncompressed(job, new UncompressedPayload(colors));
    }

}
