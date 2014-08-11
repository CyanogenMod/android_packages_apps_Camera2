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

/**
 * @param env the JNI environment
 * @param yBuf the buffer containing the Y component of the image
 * @param yPStride the stride between adjacent pixels in the same row in yBuf
 * @param yRStride the stride between adjacent rows in yBuf
 * @param cbBuf the buffer containing the Cb component of the image
 * @param cbPStride the stride between adjacent pixels in the same row in cbBuf
 * @param cbRStride the stride between adjacent rows in cbBuf
 * @param crBuf the buffer containing the Cr component of the image
 * @param crPStride the stride between adjacent pixels in the same row in crBuf
 * @param crRStride the stride between adjacent rows in crBuf
 */
extern "C" JNIEXPORT jint JNICALL
    Java_com_android_camera_util_JpegUtilNative_compressJpegFromYUV420pNative(
        JNIEnv* env, jclass clazz, jint width, jint height, jobject yBuf,
        jint yPStride, jint yRStride, jobject cbBuf, jint cbPStride,
        jint cbRStride, jobject crBuf, jint crPStride, jint crRStride,
        jobject outBuf, jint outBufCapacity, jint quality) {
  jbyte* y = (jbyte*)env->GetDirectBufferAddress(yBuf);
  jbyte* cb = (jbyte*)env->GetDirectBufferAddress(cbBuf);
  jbyte* cr = (jbyte*)env->GetDirectBufferAddress(crBuf);
  jbyte* out = (jbyte*)env->GetDirectBufferAddress(outBuf);

  jpegutil::Plane yP(width, height, width, height, (unsigned char*)y, yPStride,
                     yRStride);
  jpegutil::Plane cbP(width, height, width / 2, height / 2, (unsigned char*)cb,
                      cbPStride, cbRStride);
  jpegutil::Plane crP(width, height, width / 2, height / 2, (unsigned char*)cr,
                      crPStride, crRStride);

  auto flush = [](size_t numBytes) {
    // do nothing
  };

  return jpegutil::compress(yP, cbP, crP, (unsigned char*)out, outBufCapacity,
                            flush, quality);
}
