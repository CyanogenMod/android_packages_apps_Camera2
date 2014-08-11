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

#include <memory>
#include <functional>

/*
 * Provides a wrapper around libjpeg.
 */
namespace jpegutil {

/**
 * Represents a model for accessing pixel data for a single plane of an image.
 * Note that the actual data is not owned by this class, and the underlying
 * data does not need to be stored in separate planes.
 */
class Plane {
 public:
  /**
   * Provides access to several rows of planar data at a time, copied into an
   * intermediate buffer with pixel data packed in contiguous rows which can be
   * passed to libjpeg.
   */
  class RowIterator {
   public:
    RowIterator(const Plane* plane);

    /**
     * Retrieves the y-th row, copying it into a buffer with as much padding
     * as is necessary for use with libjpeg.
     */
    unsigned char* operator()(int y);

   private:
    const Plane* plane_;

    // Stores a ring-buffer of cache-aligned buffers for storing contiguous
    // pixel data for rows of the image to be sent to libjpeg.
    std::unique_ptr<unsigned char[]> buffer_;
    // The cache-aligned start index of buffer_
    unsigned char* alignedBuffer_;
    // The total number of rows in the ring-buffer
    int bufRowCount_;
    // The current ring-buffer row being used
    int bufCurRow_;
    // The number of bytes between consecutive rows in the buffer
    int bufRowStride_;

    // The number of bytes of padding-pixels which must be appended to each row
    // to reach the multiple of 16-bytes required by libjpeg.
    int rowPadding_;
  };

  Plane(int imgWidth, int imgHeight, int planeWidth, int planeHeight,
        unsigned char* data, int pixelStride, int rowStride);

  int imgWidth() const { return imgWidth_; }
  int imgHeight() const { return imgHeight_; }

 private:
  // The dimensions of the entire image
  int imgWidth_;
  int imgHeight_;
  // The dimensions of this plane of the image
  int planeWidth_;
  int planeHeight_;

  // A pointer to raw pixel data
  unsigned char* data_;
  // The difference in address between the start of consecutive rows
  int rowStride_;
  // The difference in address between consecutive pixels in the same row
  int pixelStride_;
};

/**
 * Compresses an image from YUV 420p to JPEG. Output is buffered in outBuf until
 * capacity is reached, at which point flush(size_t) is called to write
 * out the specified number of bytes from outBuf.  Returns the number of bytes
 * written, or -1 in case of an error.
 */
int compress(const Plane& yPlane, const Plane& cbPlane, const Plane& crPlane,
             unsigned char* outBuf, size_t outBufCapacity,
             std::function<void(size_t)> flush, int quality);
}
