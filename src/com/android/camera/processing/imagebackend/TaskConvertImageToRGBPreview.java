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
import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.Size;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to subsampled image targeted
 * toward a given resolution. The task automatically calculates the largest
 * integer sub-sample factor that is greater than the target resolution. There
 * are four different thumbnail types:
 * <ol>
 * <li>DEBUG_SQUARE_ASPECT_CIRCULAR_INSET: a center-weighted circularly cropped
 * gradient image</li>
 * <li>SQUARE_ASPECT_CIRCULAR_INSET: a center-weighted circularly cropped
 * sub-sampled image</li>
 * <li>SQUARE_ASPECT_NO_INSET: a center-weighted square cropped sub-sampled
 * image</li>
 * <li>MAINTAIN_ASPECT_NO_INSET: a sub-sampled image without cropping (except to
 * maintain even values of width and height for the image</li>
 * </ol>
 * This task does NOT implement rotation at the byte-level, since it is best
 * implemented when displayed at the view level.
 */
public class TaskConvertImageToRGBPreview extends TaskImageContainer {
    public enum ThumbnailShape {
        DEBUG_SQUARE_ASPECT_CIRCULAR_INSET,
        SQUARE_ASPECT_CIRCULAR_INSET,
        SQUARE_ASPECT_NO_INSET,
        MAINTAIN_ASPECT_NO_INSET,
    }

    // 24 bit-vector to be written for images that are out of bounds.
    public final static int OUT_OF_BOUNDS_COLOR = 0x00000000;

    /**
     * Quick n' Dirty YUV to RGB conversion
     * <ol>
     * <li>R = Y + 1.402V'</li>
     * <li>G = Y - 0.344U'- 0.714V'</li>
     * <li>B = Y + 1.770U'</li>
     * </ol>
     * to be calculated at compile time.
     */
    public final static int SHIFT_APPROXIMATION = 8;
    public final static double SHIFTED_BITS_AS_VALUE = (double) (1 << SHIFT_APPROXIMATION);
    public final static int V_FACTOR_FOR_R = (int) (1.402 * SHIFTED_BITS_AS_VALUE);
    public final static int U_FACTOR_FOR_G = (int) (-0.344 * SHIFTED_BITS_AS_VALUE);
    public final static int V_FACTOR_FOR_G = (int) (-0.714 * SHIFTED_BITS_AS_VALUE);
    public final static int U_FACTOR_FOR_B = (int) (1.772 * SHIFTED_BITS_AS_VALUE);

    protected final static Log.Tag TAG = new Log.Tag("TaskRGBPreview");

    protected final ThumbnailShape mThumbnailShape;
    protected final Size mTargetSize;

    /**
     * Constructor
     *
     * @param image Image that the computation is dependent on
     * @param executor Executor to fire off an events
     * @param imageTaskManager Image task manager that allows reference counting
     *            and task spawning
     * @param captureSession Capture session that bound to this image
     * @param targetSize Approximate viewable pixel dimensions of the desired
     *            preview Image (Resultant image may NOT be of this width)
     * @param thumbnailShape the desired thumbnail shape for resultant image
     *            artifact
     */
    TaskConvertImageToRGBPreview(ImageToProcess image, Executor executor,
            ImageTaskManager imageTaskManager, ProcessingPriority processingPriority,
            CaptureSession captureSession, Size targetSize, ThumbnailShape thumbnailShape) {
        super(image, executor, imageTaskManager, processingPriority, captureSession);
        mTargetSize = targetSize;
        mThumbnailShape = thumbnailShape;
    }

    public void logWrapper(String message) {
        Log.v(TAG, message);
    }

    /**
     * Return the closest minimal value of the parameter that is evenly divisible by two.
     */
    private static int quantizeBy2(int value) {
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
     * @param imageSize Dimensions of the original image
     * @param targetSize Target dimensions of the resultant image
     * @return inscribed image as ARGB_8888
     */
    protected int calculateBestSubsampleFactor(Size imageSize, Size targetSize) {
        int maxSubsample = Math.min(imageSize.getWidth() / targetSize.getWidth(),
                imageSize.getHeight() / targetSize.getHeight());
        if (maxSubsample < 1) {
            return 1;
        }

        // Make sure the resultant image width/height is divisible by 2 to
        // account
        // for chroma subsampled images such as YUV
        for (int i = maxSubsample; i >= 1; i--) {
            if (((imageSize.getWidth() % (2 * i) == 0)
            && (imageSize.getHeight() % (2 * i) == 0))) {
                return i;
            }
        }

        return 1; // If all fails, don't do the subsample.
    }

    /**
     * Calculates the memory offset of a YUV 420 plane, given the parameters of
     * the separate YUV color planes and the fact that UV components may be
     * subsampled by a factor of 2.
     *
     * @param inscribedXMin X location that you want to start sampling on the
     *            input image in terms of input pixels
     * @param inscribedYMin Y location that you want to start sampling on the
     *            input image in terms of input pixels
     * @param subsample Subsample factor applied to the input image
     * @param colorSubsample Color subsample due to the YUV color space (In YUV,
     *            it's 1 for Y, 2 for UV)
     * @param rowStride Row Stride of the color plane in terms of bytes
     * @param pixelStride Pixel Stride of the color plane in terms of bytes
     * @param inputHorizontalOffset Horizontal Input Offset for sampling that
     *            you wish to add in terms of input pixels
     * @param inputVerticalOffset Vertical Input Offset for sampling that you
     *            wish to add in terms of input pixels
     * @return value of the corresponding memory offset.
     */
    protected static int calculateMemoryOffsetFromPixelOffsets(int inscribedXMin,
            int inscribedYMin, int subsample, int colorSubsample,
            int rowStride, int pixelStride, int inputHorizontalOffset, int inputVerticalOffset) {
        return inputVerticalOffset * (rowStride / subsample)
                + inputHorizontalOffset * (pixelStride / subsample)
                + (inscribedYMin / colorSubsample) * rowStride
                + (inscribedXMin / colorSubsample) * pixelStride;
    }

    /**
     * Converts an Android Image to a inscribed circle bitmap of ARGB_8888 in a
     * super-optimized loop unroll. Guarantees only one subsampled pass over the
     * YUV data. This version of the function should be used in production and
     * also feathers the edges with 50% alpha on its edges. <br>
     * NOTE: To get the size of the resultant bitmap, you need to call
     * inscribedCircleRadius(w, h) outside of this function. Runs in ~10-15ms
     * for 4K image with a subsample of 13. <br>
     * <p>
     * <b>Crop Treatment: </b>Since this class does a lot of memory offset
     * calculation, it is critical that it doesn't poke strange memory locations on
     * strange crop values. Crop is always applied before any rotation. Out-of-bound
     * crop boundaries are accepted, but treated mathematically as intersection with
     * the Image rectangle. If this intersection is null, the result is minimal 2x2
     * images.
     * <p>
     * <b>Known Image Artifacts</b> Since this class produces bitmaps that are
     * transient on the screen, the implementation is geared toward efficiency
     * rather than image quality. The image created is a straight, dumb integer
     * subsample of the YUV space with an acceptable color conversion, but w/o any
     * sort of re-sampling. So, expect the usual aliasing noise. Furthermore, when a
     * subsample factor of n is chosen, the resultant UV pixels will have the same
     * subsampling, even though the RGBA artifact produces could produce an
     * effective resample of (n/2) in the U,V color space. For cases where subsample
     * is odd-valued, there will be pixel-to-pixel color bleeding, which may be
     * apparent in sharp color edges.  But since our eyes are pretty bad at color
     * edges anyway, it may be an acceptable trade-off for run-time efficiency on an
     * image artifact that has a short lifetime on the screen.
     * </p>
     * TODO: Implement horizontal alpha feathering of the edge of the image.
     *
     * @param img YUV420_888 Image to convert
     * @param subsample width/height subsample factor
     * @return inscribed image as ARGB_8888
     */
    protected int[] colorInscribedDataCircleFromYuvImage(ImageProxy img, int subsample) {
        Rect defaultCrop = new Rect(0, 0, img.getWidth(), img.getHeight());

        return colorInscribedDataCircleFromYuvImage(img, defaultCrop, subsample);
    }

    protected int[] colorInscribedDataCircleFromYuvImage(ImageProxy img, Rect crop, int subsample) {
        crop = guaranteedSafeCrop(img, crop);
        final List<ImageProxy.Plane> planeList = img.getPlanes();
        if (planeList.size() != 3) {
            throw new IllegalArgumentException("Incorrect number planes (" + planeList.size()
                    + ") in YUV Image Object");
        }

        int inputWidth = crop.width();
        int inputHeight = crop.height();
        int outputWidth = inputWidth / subsample;
        int outputHeight = inputHeight / subsample;
        int w = outputWidth;
        int h = outputHeight;
        int r = inscribedCircleRadius(w, h);

        final int inscribedXMin;
        final int inscribedXMax;
        final int inscribedYMin;
        final int inscribedYMax;
        // To minimize color bleeding, always quantize the start coordinates by 2.
        final int inputVerticalOffset = quantizeBy2(crop.top);
        final int inputHorizontalOffset = quantizeBy2(crop.left);

        // Set up input read boundaries.
        if (w > h) {
            inscribedYMin = 0;
            inscribedYMax = h;
            // since we're 2x2 blocks we need to quantize these values by 2
            inscribedXMin = quantizeBy2(w / 2 - r);
            inscribedXMax = quantizeBy2(w / 2 + r);
        } else {
            inscribedXMin = 0;
            inscribedXMax = w;
            // since we're 2x2 blocks we need to quantize these values by 2
            inscribedYMin = quantizeBy2(h / 2 - r);
            inscribedYMax = quantizeBy2(h / 2 + r);
        }

        ByteBuffer buf0 = planeList.get(0).getBuffer();
        ByteBuffer bufU = planeList.get(1).getBuffer(); // Downsampled by 2
        ByteBuffer bufV = planeList.get(2).getBuffer(); // Downsampled by 2
        int yByteStride = planeList.get(0).getRowStride() * subsample;
        int uByteStride = planeList.get(1).getRowStride() * subsample;
        int vByteStride = planeList.get(2).getRowStride() * subsample;
        int yPixelStride = planeList.get(0).getPixelStride() * subsample;
        int uPixelStride = planeList.get(1).getPixelStride() * subsample;
        int vPixelStride = planeList.get(2).getPixelStride() * subsample;
        int outputPixelStride = r * 2;
        int centerY = h / 2;
        int centerX = w / 2;

        int len = r * r * 4;
        int[] colors = new int[len];
        int alpha = 255 << 24;

        logWrapper("TIMER_BEGIN Starting Native Java YUV420-to-RGB Circular Conversion");
        logWrapper("\t Y-Plane Size=" + w + "x" + h);
        logWrapper("\t U-Plane Size=" + planeList.get(1).getRowStride() + " Pixel Stride="
                + planeList.get(1).getPixelStride());
        logWrapper("\t V-Plane Size=" + planeList.get(2).getRowStride() + " Pixel Stride="
                + planeList.get(2).getPixelStride());
        // Take in vertical lines by factor of two because of the u/v component
        // subsample
        for (int j = inscribedYMin; j < inscribedYMax; j += 2) {
            int offsetColor = (j - inscribedYMin) * (outputPixelStride);
            int offsetY = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    1 /* YComponent */, yByteStride, yPixelStride, inputHorizontalOffset,
                    inputVerticalOffset);
            int offsetU = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    2 /* U Component downsampled by 2 */, uByteStride, uPixelStride,
                    inputHorizontalOffset / 2, inputVerticalOffset / 2);
            int offsetV = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    2 /* v Component downsampled by 2 */, vByteStride, vPixelStride,
                    inputHorizontalOffset / 2, inputVerticalOffset / 2);

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
                int redDiff = (v * V_FACTOR_FOR_R) >> SHIFT_APPROXIMATION;
                int greenDiff =
                        ((u * U_FACTOR_FOR_G + v * V_FACTOR_FOR_G) >> SHIFT_APPROXIMATION);
                int blueDiff = (u * U_FACTOR_FOR_B) >> SHIFT_APPROXIMATION;

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
        logWrapper("TIMER_END Starting Native Java YUV420-to-RGB Circular Conversion");

        return colors;
    }

    /**
     * Converts an Android Image to a subsampled image of ARGB_8888 data in a
     * super-optimized loop unroll. Guarantees only one subsampled pass over the
     * YUV data.  No crop is applied.
     *
     * @param img YUV420_888 Image to convert
     * @param subsample width/height subsample factor
     * @param enableSquareInscribe true, output is an cropped square output;
     *            false, output maintains aspect ratio of input image
     * @return inscribed image as ARGB_8888
     */
    protected int[] colorSubSampleFromYuvImage(ImageProxy img, int subsample,
            boolean enableSquareInscribe) {
        Rect defaultCrop = new Rect(0, 0, img.getWidth(), img.getHeight());

        return colorSubSampleFromYuvImage(img, defaultCrop, subsample, enableSquareInscribe);
    }

    /**
     * Converts an Android Image to a subsampled image of ARGB_8888 data in a
     * super-optimized loop unroll. Guarantees only one subsampled pass over the
     * YUV data.
     * <p>
     * <b>Crop Treatment: </b>Since this class does a lot of memory offset
     * calculation, it is critical that it doesn't poke strange memory locations on
     * strange crop values. Crop is always applied before any rotation. Out-of-bound
     * crop boundaries are accepted, but treated mathematically as intersection with
     * the Image rectangle. If this intersection is null, the result is minimal 2x2
     * images.
     * <p>
     * <b>Known Image Artifacts</b> Since this class produces bitmaps that are
     * transient on the screen, the implementation is geared toward efficiency
     * rather than image quality. The image created is a straight, dumb integer
     * subsample of the YUV space with an acceptable color conversion, but w/o any
     * sort of re-sampling. So, expect the usual aliasing noise. Furthermore, when a
     * subsample factor of n is chosen, the resultant UV pixels will have the same
     * subsampling, even though the RGBA artifact produces could produce an
     * effective resample of (n/2) in the U,V color space. For cases where subsample
     * is odd-valued, there will be pixel-to-pixel color bleeding, which may be
     * apparent in sharp color edges.  But since our eyes are pretty bad at color
     * edges anyway, it may be an acceptable trade-off for run-time efficiency on an
     * image artifact that has a short lifetime on the screen.
     * </p>
     *
     * @param img YUV420_888 Image to convert
     * @param crop crop to be applied.
     * @param subsample width/height subsample factor
     * @param enableSquareInscribe true, output is an cropped square output;
     *            false, output maintains aspect ratio of input image
     * @return inscribed image as ARGB_8888
     */
    protected int[] colorSubSampleFromYuvImage(ImageProxy img, Rect crop, int subsample,
            boolean enableSquareInscribe) {
        crop = guaranteedSafeCrop(img, crop);
        final List<ImageProxy.Plane> planeList = img.getPlanes();
        if (planeList.size() != 3) {
            throw new IllegalArgumentException("Incorrect number planes (" + planeList.size()
                    + ") in YUV Image Object");
        }

        int inputWidth = crop.width();
        int inputHeight = crop.height();
        int outputWidth = inputWidth / subsample;
        int outputHeight = inputHeight / subsample;

        // Set up input read boundaries.

        ByteBuffer bufY = planeList.get(0).getBuffer();
        ByteBuffer bufU = planeList.get(1).getBuffer(); // Downsampled by 2
        ByteBuffer bufV = planeList.get(2).getBuffer(); // Downsampled by 2
        int yByteStride = planeList.get(0).getRowStride() * subsample;
        int uByteStride = planeList.get(1).getRowStride() * subsample;
        int vByteStride = planeList.get(2).getRowStride() * subsample;
        int yPixelStride = planeList.get(0).getPixelStride() * subsample;
        int uPixelStride = planeList.get(1).getPixelStride() * subsample;
        int vPixelStride = planeList.get(2).getPixelStride() * subsample;


        // Set up default input read boundaries.
        final int outputPixelStride;
        final int len;
        final int inscribedXMin;
        final int inscribedXMax;
        final int inscribedYMin;
        final int inscribedYMax;
        final int inputVerticalOffset = quantizeBy2(crop.top);
        final int inputHorizontalOffset = quantizeBy2(crop.left);

        if (enableSquareInscribe) {
            int r = inscribedCircleRadius(outputWidth, outputHeight);
            len = r * r * 4;
            outputPixelStride = r * 2;

            if (outputWidth > outputHeight) {
                // since we're 2x2 blocks we need to quantize these values by 2
                inscribedXMin = quantizeBy2(outputWidth / 2 - r);
                inscribedXMax = quantizeBy2(outputWidth / 2 + r);
                inscribedYMin = 0;
                inscribedYMax = outputHeight;
            } else {
                inscribedXMin = 0;
                inscribedXMax = outputWidth;
                // since we're 2x2 blocks we need to quantize these values by 2
                inscribedYMin = quantizeBy2(outputHeight / 2 - r);
                inscribedYMax = quantizeBy2(outputHeight / 2 + r);
            }
        } else {
            outputPixelStride = outputWidth;
            len = outputWidth * outputHeight;
            inscribedXMin = 0;
            inscribedXMax = quantizeBy2(outputWidth);
            inscribedYMin = 0;
            inscribedYMax = quantizeBy2(outputHeight);
        }

        int[] colors = new int[len];
        int alpha = 255 << 24;

        logWrapper("TIMER_BEGIN Starting Native Java YUV420-to-RGB Rectangular Conversion");
        logWrapper("\t Y-Plane Size=" + outputWidth + "x" + outputHeight);
        logWrapper("\t U-Plane Size=" + planeList.get(1).getRowStride() + " Pixel Stride="
                + planeList.get(1).getPixelStride());
        logWrapper("\t V-Plane Size=" + planeList.get(2).getRowStride() + " Pixel Stride="
                + planeList.get(2).getPixelStride());
        // Take in vertical lines by factor of two because of the u/v component
        // subsample
        for (int j = inscribedYMin; j < inscribedYMax; j += 2) {
            int offsetColor = (j - inscribedYMin) * (outputPixelStride);
            int offsetY = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    1 /* YComponent */, yByteStride, yPixelStride, inputHorizontalOffset,
                    inputVerticalOffset);
            int offsetU = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    2 /* U Component downsampled by 2 */, uByteStride, uPixelStride,
                    inputHorizontalOffset / 2, inputVerticalOffset / 2);
            int offsetV = calculateMemoryOffsetFromPixelOffsets(inscribedXMin, j, subsample,
                    2 /* v Component downsampled by 2 */, vByteStride, vPixelStride,
                    inputHorizontalOffset / 2, inputVerticalOffset / 2);

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

                // calculate the RGB component of the u/v channels and use it
                // for all pixels in the 2x2 block
                int u = (int) (bufU.get(offsetU) & 255) - 128;
                int v = (int) (bufV.get(offsetV) & 255) - 128;
                int redDiff = (v * V_FACTOR_FOR_R) >> SHIFT_APPROXIMATION;
                int greenDiff = ((u * U_FACTOR_FOR_G + v * V_FACTOR_FOR_G) >> SHIFT_APPROXIMATION);
                int blueDiff = (u * U_FACTOR_FOR_B) >> SHIFT_APPROXIMATION;

                // Do a little alpha feathering on the edges
                int alpha00 = (255 << 24);

                int y00 = (int) (bufY.get(offsetY) & 255);

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

                int alpha01 = (255 << 24);
                int y01 = (int) (bufY.get(offsetY + yPixelStride) & 255);
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

                int alpha10 = (255 << 24);
                int y10 = (int) (bufY.get(offsetY + yByteStride) & 255);
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

                int alpha11 = (255 << 24);
                int y11 = (int) (bufY.get(offsetY + yByteStride + yPixelStride) & 255);
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
        logWrapper("TIMER_END Starting Native Java YUV420-to-RGB Rectangular Conversion");

        return colors;
    }

    /**
     * DEBUG IMAGE FUNCTION Converts an Android Image to a inscribed circle
     * bitmap, currently wired to the test pattern. Will subsample and optimize
     * the image given a target resolution.
     *
     * @param img YUV420_888 Image to convert
     * @param subsample width/height subsample factor
     * @return inscribed image as ARGB_8888
     */
    protected int[] dummyColorInscribedDataCircleFromYuvImage(ImageProxy img, int subsample) {
        logWrapper("RUNNING DUMMY dummyColorInscribedDataCircleFromYuvImage");
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

    /**
     * Calculates the input Task Image specification an ImageProxy
     *
     * @param img Specified ImageToProcess
     * @return Calculated specification
     */
    protected TaskImage calculateInputImage(ImageToProcess img, Rect cropApplied) {
        return new TaskImage(img.rotation, img.proxy.getWidth(), img.proxy.getHeight(),
                img.proxy.getFormat(), cropApplied);
    }

    /**
     * Calculates the resultant Task Image specification, given the shape
     * selected at the time of task construction
     *
     * @param img Specified image to process
     * @param subsample Amount of subsampling to be applied
     * @return Calculated Specification
     */
    protected TaskImage calculateResultImage(ImageToProcess img, int subsample) {
        final Rect safeCrop = guaranteedSafeCrop(img.proxy, img.crop);
        int resultWidth, resultHeight;

        if (mThumbnailShape == ThumbnailShape.MAINTAIN_ASPECT_NO_INSET) {
            resultWidth = safeCrop.width() / subsample;
            resultHeight = safeCrop.height() / subsample;
        } else {
            final int radius = inscribedCircleRadius(safeCrop.width() / subsample, safeCrop.height()
                    / subsample);
            resultWidth = 2 * radius;
            resultHeight = 2 * radius;
        }

        return new TaskImage(img.rotation, resultWidth, resultHeight,
                TaskImage.EXTRA_USER_DEFINED_FORMAT_ARGB_8888,
                null /* Crop already applied */);

    }

    /**
     * Runs the correct image conversion routine, based upon the selected
     * thumbnail shape.
     *
     * @param img Image to be converted
     * @param subsample Amount of image subsampling
     * @return an ARGB_888 packed array ready for Bitmap conversion
     */
    protected int[] runSelectedConversion(ImageProxy img, Rect crop, int subsample) {
        switch (mThumbnailShape) {
            case DEBUG_SQUARE_ASPECT_CIRCULAR_INSET:
                return dummyColorInscribedDataCircleFromYuvImage(img, subsample);
            case SQUARE_ASPECT_CIRCULAR_INSET:
                return colorInscribedDataCircleFromYuvImage(img, crop, subsample);
            case SQUARE_ASPECT_NO_INSET:
                return colorSubSampleFromYuvImage(img, crop, subsample, true);
            case MAINTAIN_ASPECT_NO_INSET:
                return colorSubSampleFromYuvImage(img, crop, subsample, false);
            default:
                return null;
        }
    }

    /**
     * Runnable implementation
     */
    @Override
    public void run() {
        ImageToProcess img = mImage;
        Rect safeCrop = guaranteedSafeCrop(img.proxy, img.crop);

        final TaskImage inputImage = calculateInputImage(img, safeCrop);
        final int subsample = calculateBestSubsampleFactor(
                new Size(safeCrop.width(), safeCrop.height()),
                mTargetSize);
        final TaskImage resultImage = calculateResultImage(img, subsample);
        final int[] convertedImage;

        try {
            onStart(mId, inputImage, resultImage, TaskInfo.Destination.FAST_THUMBNAIL);

            logWrapper("TIMER_END Rendering preview YUV buffer available, w="
                    + img.proxy.getWidth()
                    / subsample + " h=" + img.proxy.getHeight() / subsample + " of subsample "
                    + subsample);

            convertedImage = runSelectedConversion(img.proxy, safeCrop, subsample);
        } finally {
            // Signal backend that reference has been released
            mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
        }
        onPreviewDone(resultImage, inputImage, convertedImage, TaskInfo.Destination.FAST_THUMBNAIL);
    }

    /**
     * Wraps the onResultUncompressed listener function
     *
     * @param resultImage Image specification of result image
     * @param inputImage Image specification of the input image
     * @param colors Uncompressed data buffer
     * @param destination Specifies the purpose of this image processing
     *            artifact
     */
    public void onPreviewDone(TaskImage resultImage, TaskImage inputImage, int[] colors,
            TaskInfo.Destination destination) {
        TaskInfo job = new TaskInfo(mId, inputImage, resultImage, destination);
        final ImageProcessorListener listener = mImageTaskManager.getProxyListener();

        listener.onResultUncompressed(job, new UncompressedPayload(colors));
    }

}
