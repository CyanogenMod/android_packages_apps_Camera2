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
#include "jpegutil.h"
#include <memory.h>
#include <array>
#include <vector>
#include <cstring>
#include <cstdio>

#include <setjmp.h>

extern "C" {
#include "jpeglib.h"
}

using namespace std;
using namespace jpegutil;

template <typename T>
void safeDelete(T& t) {
  if (t != nullptr) {
    delete t;
    t = nullptr;
  }
}

template <typename T>
void safeDeleteArray(T& t) {
  if (t != nullptr) {
    delete[] t;
    t = nullptr;
  }
}

jpegutil::Transform::Transform(int orig_x, int orig_y, int one_x, int one_y)
    : orig_x_(orig_x), orig_y_(orig_y), one_x_(one_x), one_y_(one_y) {
  if (orig_x == one_x || orig_y == one_y) {
    // Handle the degenerate case of cropping to a 0x0 rectangle.
    mat00_ = 0;
    mat01_ = 0;
    mat10_ = 0;
    mat11_ = 0;
    return;
  }

  if (one_x > orig_x && one_y > orig_y) {
    // 0-degree rotation
    mat00_ = 1;
    mat01_ = 0;
    mat10_ = 0;
    mat11_ = 1;
    output_width_ = abs(one_x - orig_x);
    output_height_ = abs(one_y - orig_y);
  } else if (one_x < orig_x && one_y > orig_y) {
    // 90-degree CCW rotation
    mat00_ = 0;
    mat01_ = -1;
    mat10_ = 1;
    mat11_ = 0;
    output_width_ = abs(one_y - orig_y);
    output_height_ = abs(one_x - orig_x);
  } else if (one_x > orig_x && one_y < orig_y) {
    // 270-degree CCW rotation
    mat00_ = 0;
    mat01_ = 1;
    mat10_ = -1;
    mat11_ = 0;
    output_width_ = abs(one_y - orig_y);
    output_height_ = abs(one_x - orig_x);
  } else if (one_x < orig_x && one_y < orig_y) {
    // 180-degree CCW rotation
    mat00_ = -1;
    mat01_ = 0;
    mat10_ = 0;
    mat11_ = -1;
    output_width_ = abs(one_x - orig_x);
    output_height_ = abs(one_y - orig_y);
  }
}

jpegutil::Transform jpegutil::Transform::ForCropFollowedByRotation(
    int cropLeft, int cropTop, int cropRight, int cropBottom, int rot90) {
  // The input crop-region excludes cropRight and cropBottom, so transform the
  // crop rect such that it defines the entire valid region of pixels
  // inclusively.
  cropRight -= 1;
  cropBottom -= 1;

  int cropXLow = min(cropLeft, cropRight);
  int cropYLow = min(cropTop, cropBottom);
  int cropXHigh = max(cropLeft, cropRight);
  int cropYHigh = max(cropTop, cropBottom);
  rot90 %= 4;
  if (rot90 == 0) {
    return Transform(cropXLow, cropYLow, cropXHigh + 1, cropYHigh + 1);
  } else if (rot90 == 1) {
    return Transform(cropXHigh, cropYLow, cropXLow - 1, cropYHigh + 1);
  } else if (rot90 == 2) {
    return Transform(cropXHigh, cropYHigh, cropXLow - 1, cropYLow - 1);
  } else if (rot90 == 3) {
    return Transform(cropXLow, cropYHigh, cropXHigh + 1, cropYLow - 1);
  }
  // Impossible case.
  return Transform(cropXLow, cropYLow, cropXHigh + 1, cropYHigh + 1);
}

bool jpegutil::Transform::operator==(const Transform& other) const {
  return other.orig_x_ == orig_x_ &&  //
         other.orig_y_ == orig_y_ &&  //
         other.one_x_ == one_x_ &&    //
         other.one_y_ == one_y_;
}

/**
 * Transforms the input coordinates.  Coordinates outside the cropped region
 * are clamped to valid values.
 */
void jpegutil::Transform::Map(int x, int y, int* x_out, int* y_out) const {
  x = max(x, 0);
  y = max(y, 0);
  x = min(x, output_width() - 1);
  y = min(y, output_height() - 1);
  *x_out = x * mat00_ + y * mat01_ + orig_x_;
  *y_out = x * mat10_ + y * mat11_ + orig_y_;
}

int jpegutil::Compress(int img_width, int img_height,
                       jpegutil::RowIterator<16>& y_row_generator,
                       jpegutil::RowIterator<8>& cb_row_generator,
                       jpegutil::RowIterator<8>& cr_row_generator,
                       unsigned char* out_buf, size_t out_buf_capacity,
                       std::function<void(size_t)> flush, int quality) {
  // libjpeg requires the use of setjmp/longjmp to recover from errors.  Since
  // this doesn't play well with RAII, we must use pointers and manually call
  // delete. See POSIX documentation for longjmp() for details on why the
  // volatile keyword is necessary.
  volatile jpeg_compress_struct cinfov;

  jpeg_compress_struct& cinfo =
      *const_cast<struct jpeg_compress_struct*>(&cinfov);

  JSAMPROW* volatile yArr = nullptr;
  JSAMPROW* volatile cbArr = nullptr;
  JSAMPROW* volatile crArr = nullptr;

  JSAMPARRAY imgArr[3];

  // Error handling

  struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
  } err;

  cinfo.err = jpeg_std_error(&err.pub);

  // Default error_exit will call exit(), so override
  // to return control via setjmp/longjmp.
  err.pub.error_exit = [](j_common_ptr cinfo) {
    my_error_mgr* myerr = reinterpret_cast<my_error_mgr*>(cinfo->err);

    (*cinfo->err->output_message)(cinfo);

    // Return control to the setjmp point (see call to setjmp()).
    longjmp(myerr->setjmp_buffer, 1);
  };

  cinfo.err = (struct jpeg_error_mgr*)&err;

  // Set the setjmp point to return to in case of error.
  if (setjmp(err.setjmp_buffer)) {
    // If libjpeg hits an error, control will jump to this point (see call to
    // longjmp()).
    jpeg_destroy_compress(&cinfo);

    safeDeleteArray(yArr);
    safeDeleteArray(cbArr);
    safeDeleteArray(crArr);

    return -1;
  }

  // Create jpeg compression context
  jpeg_create_compress(&cinfo);

  // Stores data needed by our c-style callbacks into libjpeg
  struct ClientData {
    unsigned char* out_buf;
    size_t out_buf_capacity;
    std::function<void(size_t)> flush;
    int totalOutputBytes;
  } clientData{out_buf, out_buf_capacity, flush, 0};

  cinfo.client_data = &clientData;

  // Initialize destination manager
  jpeg_destination_mgr dest;

  dest.init_destination = [](j_compress_ptr cinfo) {
    ClientData& cdata = *reinterpret_cast<ClientData*>(cinfo->client_data);

    cinfo->dest->next_output_byte = cdata.out_buf;
    cinfo->dest->free_in_buffer = cdata.out_buf_capacity;
  };

  dest.empty_output_buffer = [](j_compress_ptr cinfo) -> boolean {
    ClientData& cdata = *reinterpret_cast<ClientData*>(cinfo->client_data);

    size_t numBytesInBuffer = cdata.out_buf_capacity;
    cdata.flush(numBytesInBuffer);
    cdata.totalOutputBytes += numBytesInBuffer;

    // Reset the buffer
    cinfo->dest->next_output_byte = cdata.out_buf;
    cinfo->dest->free_in_buffer = cdata.out_buf_capacity;

    return true;
  };

  dest.term_destination = [](j_compress_ptr cinfo) {
    // do nothing to terminate the output buffer
  };

  cinfo.dest = &dest;

  // Set jpeg parameters
  cinfo.image_width = img_width;
  cinfo.image_height = img_height;
  cinfo.input_components = 3;

  // Set defaults based on the above values
  jpeg_set_defaults(&cinfo);

  jpeg_set_quality(&cinfo, quality, true);

  cinfo.dct_method = JDCT_IFAST;

  cinfo.raw_data_in = true;

  jpeg_set_colorspace(&cinfo, JCS_YCbCr);

  cinfo.comp_info[0].h_samp_factor = 2;
  cinfo.comp_info[0].v_samp_factor = 2;
  cinfo.comp_info[1].h_samp_factor = 1;
  cinfo.comp_info[1].v_samp_factor = 1;
  cinfo.comp_info[2].h_samp_factor = 1;
  cinfo.comp_info[2].v_samp_factor = 1;

  jpeg_start_compress(&cinfo, true);

  yArr = new JSAMPROW[cinfo.comp_info[0].v_samp_factor * DCTSIZE];
  cbArr = new JSAMPROW[cinfo.comp_info[1].v_samp_factor * DCTSIZE];
  crArr = new JSAMPROW[cinfo.comp_info[2].v_samp_factor * DCTSIZE];

  imgArr[0] = const_cast<JSAMPARRAY>(yArr);
  imgArr[1] = const_cast<JSAMPARRAY>(cbArr);
  imgArr[2] = const_cast<JSAMPARRAY>(crArr);

  for (int y = 0; y < img_height; y += DCTSIZE * 2) {
    std::array<unsigned char*, 16> yData = y_row_generator.LoadAt(y);
    std::array<unsigned char*, 8> cbData = cb_row_generator.LoadAt(y / 2);
    std::array<unsigned char*, 8> crData = cr_row_generator.LoadAt(y / 2);

    for (int row = 0; row < DCTSIZE * 2; row++) {
      yArr[row] = yData[row];
    }
    for (int row = 0; row < DCTSIZE; row++) {
      cbArr[row] = cbData[row];
      crArr[row] = crData[row];
    }

    jpeg_write_raw_data(&cinfo, imgArr, DCTSIZE * 2);
  }

  jpeg_finish_compress(&cinfo);

  int numBytesInBuffer = cinfo.dest->next_output_byte - out_buf;

  flush(numBytesInBuffer);

  clientData.totalOutputBytes += numBytesInBuffer;

  safeDeleteArray(yArr);
  safeDeleteArray(cbArr);
  safeDeleteArray(crArr);

  jpeg_destroy_compress(&cinfo);

  return clientData.totalOutputBytes;
}

int jpegutil::Compress(
    /** Input image dimensions */
    int width, int height,
    /** Y Plane */
    unsigned char* yBuf, int yPStride, int yRStride,
    /** Cb Plane */
    unsigned char* cbBuf, int cbPStride, int cbRStride,
    /** Cr Plane */
    unsigned char* crBuf, int crPStride, int crRStride,
    /** Output */
    unsigned char* outBuf, size_t outBufCapacity,
    /** Jpeg compression parameters */
    int quality,
    /** Crop */
    int cropLeft, int cropTop, int cropRight, int cropBottom,
    /** Rotation (multiple of 90).  For example, rot90 = 1 implies a 90 degree
     * rotation. */
    int rot90) {
  int finalWidth;
  int finalHeight;
  finalWidth = cropRight - cropLeft;
  finalHeight = cropBottom - cropTop;

  rot90 %= 4;
  // for 90 and 270-degree rotations, flip the final width and height
  if (rot90 == 1) {
    finalWidth = cropBottom - cropTop;
    finalHeight = cropRight - cropLeft;
  } else if (rot90 == 3) {
    finalWidth = cropBottom - cropTop;
    finalHeight = cropRight - cropLeft;
  }

  const Plane yP = {width, height, yBuf, yPStride, yRStride};
  const Plane cbP = {width / 2, height / 2, cbBuf, cbPStride, cbRStride};
  const Plane crP = {width / 2, height / 2, crBuf, crPStride, crRStride};

  auto flush = [](size_t numBytes) {
    // do nothing
  };

  // Round up to the nearest multiple of 64.
  int y_row_length = (finalWidth + 16 + 63) & ~63;
  int cb_row_length = (finalWidth / 2 + 16 + 63) & ~63;
  int cr_row_length = (finalWidth / 2 + 16 + 63) & ~63;

  Transform yTrans = Transform::ForCropFollowedByRotation(
      cropLeft, cropTop, cropRight, cropBottom, rot90);

  Transform chromaTrans = Transform::ForCropFollowedByRotation(
      cropLeft / 2, cropTop / 2, cropRight / 2, cropBottom / 2, rot90);

  RowIterator<16> yIter(yP, yTrans, y_row_length);
  RowIterator<8> cbIter(cbP, chromaTrans, cb_row_length);
  RowIterator<8> crIter(crP, chromaTrans, cr_row_length);

  return Compress(finalWidth, finalHeight, yIter, cbIter, crIter, outBuf,
                  outBufCapacity, flush, quality);
}
