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

#include <jni.h>
#include <math.h>
#include <android/bitmap.h>

#include "jpegutil.h"

using namespace jpegutil;

/**
 * Compresses a YCbCr image to jpeg, applying a crop and rotation.
 *
 * The input is defined as a set of 3 planes of 8-bit samples, one plane for
   each channel of Y, Cb, Cr.
 * The Y plane is assumed to have the same width and height of the entire image.
 * The Cb and Cr planes are assumed to be downsampled by a factor of 2, to have
 * dimensions (floor(width / 2), floor(height / 2)).
 * Each plane is specified by a direct java.nio.ByteBuffer, a pixel-stride, and
 * a row-stride.  So, the sample at coordinate (x, y) can be retrieved from
 * byteBuffer[x * pixel_stride + y * row_stride].
 *
 * The pre-compression transformation is applied as follows:
 *  1. The image is cropped to the rectangle from (cropLeft, cropTop) to
 *  (cropRight - 1, cropBottom - 1).  So, a cropping-rectangle of (0, 0) -
 *  (width, height) is a no-op.
 *  2. The rotation is applied counter-clockwise relative to the coordinate
 *  space of the image, so a CCW rotation will appear CW when the image is
 *  rendered in scanline order.  Only rotations which are multiples of
 *  90-degrees are suppored, so the parameter 'rot90' specifies which multiple
 *  of 90 to rotate the image.
 *
 * @param env the JNI environment
 * @param width the width of the image to compress
 * @param height the height of the image to compress
 * @param yBuf the buffer containing the Y component of the image
 * @param yPStride the stride between adjacent pixels in the same row in yBuf
 * @param yRStride the stride between adjacent rows in yBuf
 * @param cbBuf the buffer containing the Cb component of the image
 * @param cbPStride the stride between adjacent pixels in the same row in cbBuf
 * @param cbRStride the stride between adjacent rows in cbBuf
 * @param crBuf the buffer containing the Cr component of the image
 * @param crPStride the stride between adjacent pixels in the same row in crBuf
 * @param crRStride the stride between adjacent rows in crBuf
 * @param outBuf a direct java.nio.ByteBuffer to hold the compressed jpeg.  This
 * must have enough capacity to store the result, or an error code will be
 * returned.
 * @param outBufCapacity the capacity of outBuf
 * @param quality the jpeg-quality (1-100) to use
 * @param crop[Left|Top|Right|Bottom] the bounds of the image to crop to before
 * rotation
 * @param rot90 the multiple of 90 to rotate by
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_android_camera_util_JpegUtilNative_compressJpegFromYUV420pNative(
    JNIEnv* env, jclass clazz,
    /** Input image dimensions */
    jint width, jint height,
    /** Y Plane */
    jobject yBuf, jint yPStride, jint yRStride,
    /** Cb Plane */
    jobject cbBuf, jint cbPStride, jint cbRStride,
    /** Cr Plane */
    jobject crBuf, jint crPStride, jint crRStride,
    /** Output */
    jobject outBuf, jint outBufCapacity,
    /** Jpeg compression parameters */
    jint quality,
    /** Crop */
    jint cropLeft, jint cropTop, jint cropRight, jint cropBottom,
    /** Rotation (multiple of 90).  For example, rot90 = 1 implies a 90 degree
     * rotation. */
    jint rot90) {
  jbyte* y = (jbyte*)env->GetDirectBufferAddress(yBuf);
  jbyte* cb = (jbyte*)env->GetDirectBufferAddress(cbBuf);
  jbyte* cr = (jbyte*)env->GetDirectBufferAddress(crBuf);
  jbyte* out = (jbyte*)env->GetDirectBufferAddress(outBuf);

  return Compress(width, height,                                //
                  (unsigned char*)y, yPStride, yRStride,        //
                  (unsigned char*)cb, cbPStride, cbRStride,     //
                  (unsigned char*)cr, crPStride, crRStride,     //
                  (unsigned char*)out, (size_t)outBufCapacity,  //
                  quality,                                      //
                  cropLeft, cropTop, cropRight, cropBottom,     //
                  rot90);
}

/**
 * Copies the Image.Plane specified by planeBuf, pStride, and rStride to the
 * Bitmap.
 *
 * @param env the JNI environment
 * @param clazz the java class
 * @param width the width of the output image
 * @param height the height of the output image
 * @param planeBuf the native ByteBuffer containing the image plane data
 * @param pStride the stride between adjacent pixels in the same row of
 *planeBuf
 * @param rStride the stride between adjacent rows in planeBuf
 * @param rot90 the multiple of 90 degrees to rotate, one of {0, 1, 2, 3}.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_android_camera_util_JpegUtilNative_copyImagePlaneToBitmap(
    JNIEnv* env, jclass clazz, jint width, jint height, jobject planeBuf,
    jint pStride, jint rStride, jobject outBitmap, jint rot90) {
  jbyte* src = (jbyte*)env->GetDirectBufferAddress(planeBuf);

  char* dst = 0;
  AndroidBitmap_lockPixels(env, outBitmap, (void**)&dst);

  if (rot90 == 0) {
    // No rotation
    for (int y = 0; y < height; y++) {
      char* srcPtr = reinterpret_cast<char*>(&src[y * rStride]);
      char* dstPtr = &dst[y * width];
      for (int x = 0; x < width; x++) {
        *dstPtr = *srcPtr;
        srcPtr += pStride;
        dstPtr++;
      }
    }
  } else if (rot90 == 1) {
    // 90-degree rotation
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int srcX = height - 1 - y;
        int srcY = x;
        dst[y * width + x] = src[srcX * pStride + rStride * srcY];
      }
    }
  } else if (rot90 == 2) {
    // 180-degree rotation
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int srcX = width - 1 - x;
        int srcY = height - 1 - y;
        dst[y * width + x] = src[srcX * pStride + rStride * srcY];
      }
    }
  } else if (rot90 == 3) {
    // 270-degree rotation
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int srcX = y;
        int srcY = width - 1 - x;
        dst[y * width + x] = src[srcX * pStride + rStride * srcY];
      }
    }
  }

  AndroidBitmap_unlockPixels(env, outBitmap);
}
