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
#pragma once

#include "math.h"
#include <array>
#include <cassert>
#include <functional>
#include <memory>
#include <stdlib.h>
#include <vector>

/*
 * Provides a wrapper around libjpeg.
 */
namespace jpegutil {

class Transform;
class Plane;

inline int sgn(int val) { return (0 < val) - (val < 0); }

inline int min(int a, int b) { return a < b ? a : b; }

inline int max(int a, int b) { return a > b ? a : b; }

/**
 * Represents a combined cropping and rotation transformation.
 *
 * The transformation maps the coordinates (orig_x, orig_y) and (one_x, one_y)
 * in the input image to the origin and (output_width, output_height)
 * respectively.
 */
class Transform {
 public:
  Transform(int orig_x, int orig_y, int one_x, int one_y);

  static Transform ForCropFollowedByRotation(int cropLeft, int cropTop,
                                             int cropRight, int cropBottom,
                                             int rot90);

  inline int output_width() const { return output_width_; }

  inline int output_height() const { return output_height_; }

  bool operator==(const Transform& other) const;

  /**
   * Transforms the input coordinates.  Coordinates outside the cropped region
   * are clamped to valid values.
   */
  void Map(int x, int y, int* x_out, int* y_out) const;

 private:
  int output_width_;
  int output_height_;

  // The coordinates of the point to map the origin to.
  const int orig_x_, orig_y_;
  // The coordinates of the point to map the point (output_width(),
  // output_height()) to.
  const int one_x_, one_y_;

  // A matrix for the rotational component.
  int mat00_, mat01_;
  int mat10_, mat11_;
};

/**
 * Represents a model for accessing pixel data for a single plane of an image.
 * Note that the actual data is not owned by this class, and the underlying
 * data does not need to be stored in separate planes.
 */
struct Plane {
  // The dimensions of this plane of the image
  int width;
  int height;

  // A pointer to raw pixel data
  const unsigned char* data;
  // The difference in address between consecutive pixels in the same row
  int pixel_stride;
  // The difference in address between the start of consecutive rows
  int row_stride;
};

/**
 * Provides an interface for simultaneously reading a certain number of rows of
 * an image plane as contiguous arrays, suitable for use with libjpeg.
 */
template <unsigned int ROWS>
class RowIterator {
 public:
  /**
   * Creates a new RowIterator which will crop and rotate with the given
   * transform.
   *
   * @param plane the plane to iterate over
   * @param transform the transformation to map output values into the
   * coordinate space of the plane
   * @param row_length the length of the rows returned via LoadAt().  If this is
   * longer than the width of the output (after applying the transform), then
   * the right-most value is repeated.
   */
  inline RowIterator(Plane plane, Transform transform, int row_length);

  /**
   * Returns an array of pointers into consecutive rows of contiguous image
   * data starting at y.  That is, samples within each row are contiguous.
   * However, the individual arrays pointed-to may be separate.
   * When the end of the image is reached, the last row of the image is
   * repeated.
   * The returned pointers are valid until the next call to LoadAt().
   */
  inline const std::array<unsigned char*, ROWS> LoadAt(int y_base);

 private:
  Plane plane_;
  Transform transform_;
  // The length of a row, with padding to the next multiple of 64.
  int padded_row_length_;
  std::vector<unsigned char> buf_;
};

/**
 * Compresses an image from YUV 420p to JPEG. Output is buffered in outBuf until
 * capacity is reached, at which point flush(size_t) is called to write
 * out the specified number of bytes from outBuf.  Returns the number of bytes
 * written, or -1 in case of an error.
 */
int Compress(int img_width, int img_height, RowIterator<16>& y_row_generator,
             RowIterator<8>& cb_row_generator, RowIterator<8>& cr_row_generator,
             unsigned char* out_buf, size_t out_buf_capacity,
             std::function<void(size_t)> flush, int quality);

/**
 * Compresses an image from YUV 420p to JPEG.  Output is written into outBuf.
 * Returns the number of bytes written, or -1 in case of an error.
 */
int Compress(
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
    /** Rotation */
    int rot90);
}

template <unsigned int ROWS>
jpegutil::RowIterator<ROWS>::RowIterator(Plane plane, Transform transform,
                                         int row_length)
    : plane_(plane), transform_(transform) {
  padded_row_length_ = row_length;
  buf_ = std::vector<unsigned char>(row_length * ROWS);
}

template <unsigned int ROWS>
const std::array<unsigned char*, ROWS> jpegutil::RowIterator<ROWS>::LoadAt(
    int y_base) {
  std::array<unsigned char*, ROWS> buf_ptrs;
  for (int i = 0; i < ROWS; i++) {
    buf_ptrs[i] = &buf_[padded_row_length_ * i];
  }

  if (plane_.width == 0 || plane_.height == 0) {
    return buf_ptrs;
  }

  for (int i = 0; i < ROWS; i++) {
    int y = i + y_base;
    y = min(y, transform_.output_height() - 1);

    int output_width = padded_row_length_;
    output_width = min(output_width, transform_.output_width());
    output_width = min(output_width, plane_.width);

    // Each row in the output image will be copied into buf_ by gathering pixels
    // along an axis-aligned line in the plane.
    // The line is defined by (startX, startY) -> (endX, endY), computed via the
    // current Transform.
    int startX;
    int startY;
    transform_.Map(0, y, &startX, &startY);

    int endX;
    int endY;
    transform_.Map(output_width - 1, y, &endX, &endY);

    // Clamp (startX, startY) and (endX, endY) to the valid bounds of the plane.
    startX = min(startX, plane_.width - 1);
    startY = min(startY, plane_.height - 1);
    endX = min(endX, plane_.width - 1);
    endY = min(endY, plane_.height - 1);
    startX = max(startX, 0);
    startY = max(startY, 0);
    endX = max(endX, 0);
    endY = max(endY, 0);

    // To reduce work inside the copy-loop, precompute the start, end, and
    // stride relating the values to be gathered from plane_ into buf
    // for this particular scan-line.
    int dx = sgn(endX - startX);
    int dy = sgn(endY - startY);
    assert(dx == 0 || dy == 0);
    // The index into plane_.data of (startX, startY)
    int plane_start = startX * plane_.pixel_stride + startY * plane_.row_stride;
    // The index into plane_.data of (endX, endY)
    int plane_end = endX * plane_.pixel_stride + endY * plane_.row_stride;
    // The stride, in terms of indices in plane_data, required to enumerate the
    // samples between the start and end points.
    int stride = dx * plane_.pixel_stride + dy * plane_.row_stride;
    // In the degenerate-case of a 1x1 plane, startX and endX are equal, so
    // stride would be 0, resulting in an infinite-loop.  To avoid this case,
    // use a stride of at-least 1.
    if (stride == 0) {
      stride = 1;
    }

    int outX = 0;
    for (int idx = plane_start; idx >= min(plane_start, plane_end) &&
                                    idx <= max(plane_start, plane_end);
         idx += stride) {
      buf_ptrs[i][outX] = plane_.data[idx];
      outX++;
    }

    // Fill the remaining right-edge of the buffer by extending the last
    // value.
    unsigned char right_padding_value = buf_ptrs[i][outX - 1];
    // TODO OPTIMIZE Use memset instead.
    for (; outX < padded_row_length_; outX++) {
      buf_ptrs[i][outX] = right_padding_value;
    }
  }

  return buf_ptrs;
}
