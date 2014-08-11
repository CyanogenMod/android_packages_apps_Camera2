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

jpegutil::Plane::RowIterator::RowIterator(const Plane* plane) : plane_(plane) {
  // We must be able to supply up to 8 * 2 lines at a time to libjpeg.
  // 8 = vertical size of blocks transformed with DCT.
  // 2 = scaling factor for Y vs UV planes.
  bufRowCount_ = 16;

  // Rows must be padded to the next multiple of 16
  // TODO OPTIMIZE Cb and Cr components only need to be padded to a multiple of
  // 8.
  rowPadding_ = (16 - (plane_->planeWidth_ % 16)) % 16;
  bufRowStride_ = plane_->planeWidth_ + rowPadding_;

  // Round up to the nearest multiple of 64 for cache alignment
  bufRowStride_ = (bufRowStride_ + 63) & ~63;

  // Allocate an extra 64 bytes to allow for cache alignment
  size_t bufSize = bufRowStride_ * bufRowCount_ + 64;

  // TODO OPTIMIZE if the underlying data has a pixel-stride of 1, and an image
  // width which is a multiple of 16, we can avoid this allocation and simply
  // return pointers into the underlying data in operator()(int) instead of
  // copying the data.
  buffer_ = unique_ptr<unsigned char[]>(new unsigned char[bufSize]);

  // Find the start of the 64-byte aligned buffer we allocated.
  size_t bufStart = reinterpret_cast<size_t>(&buffer_[0]);
  size_t alignedBufStart = (bufStart + 63) & ~63;
  alignedBuffer_ = reinterpret_cast<unsigned char*>(alignedBufStart);

  bufCurRow_ = 0;
}

unsigned char* jpegutil::Plane::RowIterator::operator()(int y) {
  unsigned char* bufCurRowPtr = alignedBuffer_ + bufRowStride_ * bufCurRow_;

  unsigned char* srcPtr = &plane_->data_[y * plane_->rowStride_];
  unsigned char* dstPtr = bufCurRowPtr;

  // Use memcpy when possible.
  if (plane_->pixelStride_ == 1) {
    memcpy(dstPtr, srcPtr, plane_->planeWidth_);
  } else {
    int pixelStride = plane_->pixelStride_;

    for (int i = 0; i < plane_->planeWidth_; i++) {
      *dstPtr = *srcPtr;

      srcPtr += pixelStride;
      dstPtr++;
    }
  }

  // Add padding to the right side by replicating the rightmost column of
  // (actual) image values into the padding bytes.
  memset(&bufCurRowPtr[plane_->planeWidth_],
         bufCurRowPtr[plane_->planeWidth_ - 1], rowPadding_);

  bufCurRow_++;
  // Wrap within ring buffer.
  bufCurRow_ %= bufRowCount_;

  return bufCurRowPtr;
}

jpegutil::Plane::Plane(int imgWidth, int imgHeight, int planeWidth,
                       int planeHeight, unsigned char* data, int pixelStride,
                       int rowStride)
    : imgWidth_(imgWidth),
      imgHeight_(imgHeight),
      planeWidth_(planeWidth),
      planeHeight_(planeHeight),
      data_(data),
      rowStride_(rowStride),
      pixelStride_(pixelStride) {}

int jpegutil::compress(const Plane& yPlane, const Plane& cbPlane,
                       const Plane& crPlane, unsigned char* outBuf,
                       size_t outBufCapacity, std::function<void(size_t)> flush,
                       int quality) {
  int imgWidth = yPlane.imgWidth();
  int imgHeight = yPlane.imgHeight();

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

  Plane::RowIterator* volatile yRowGenerator = nullptr;
  Plane::RowIterator* volatile cbRowGenerator = nullptr;
  Plane::RowIterator* volatile crRowGenerator = nullptr;

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
    safeDelete(yRowGenerator);
    safeDelete(cbRowGenerator);
    safeDelete(crRowGenerator);

    return -1;
  }

  // Create jpeg compression context
  jpeg_create_compress(&cinfo);

  // Stores data needed by our c-style callbacks into libjpeg
  struct ClientData {
    unsigned char* outBuf;
    size_t outBufCapacity;
    std::function<void(size_t)> flush;
    int totalOutputBytes;
  } clientData{outBuf, outBufCapacity, flush, 0};

  cinfo.client_data = &clientData;

  // Initialize destination manager
  jpeg_destination_mgr dest;

  dest.init_destination = [](j_compress_ptr cinfo) {
    ClientData& cdata = *reinterpret_cast<ClientData*>(cinfo->client_data);

    cinfo->dest->next_output_byte = cdata.outBuf;
    cinfo->dest->free_in_buffer = cdata.outBufCapacity;
  };

  dest.empty_output_buffer = [](j_compress_ptr cinfo) -> boolean {
    ClientData& cdata = *reinterpret_cast<ClientData*>(cinfo->client_data);

    size_t numBytesInBuffer = cdata.outBufCapacity;
    cdata.flush(numBytesInBuffer);
    cdata.totalOutputBytes += numBytesInBuffer;

    // Reset the buffer
    cinfo->dest->next_output_byte = cdata.outBuf;
    cinfo->dest->free_in_buffer = cdata.outBufCapacity;

    return true;
  };

  dest.term_destination = [](j_compress_ptr cinfo) {
    // do nothing to terminate the output buffer
  };

  cinfo.dest = &dest;

  // Set jpeg parameters
  cinfo.image_width = imgWidth;
  cinfo.image_height = imgHeight;
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

  yRowGenerator = new Plane::RowIterator(&yPlane);
  cbRowGenerator = new Plane::RowIterator(&cbPlane);
  crRowGenerator = new Plane::RowIterator(&crPlane);

  Plane::RowIterator& yRG = *const_cast<Plane::RowIterator*>(yRowGenerator);
  Plane::RowIterator& cbRG = *const_cast<Plane::RowIterator*>(cbRowGenerator);
  Plane::RowIterator& crRG = *const_cast<Plane::RowIterator*>(crRowGenerator);

  for (int y = 0; y < imgHeight; y += DCTSIZE * 2) {
    for (int row = 0; row < DCTSIZE * 2; row++) {
      yArr[row] = yRG(y + row);
    }

    for (int row = 0; row < DCTSIZE; row++) {
      // The y-index within the subsampled chroma planes to send to libjpeg.
      const int chY = y / 2 + row;

      if (chY < imgHeight / 2) {
        cbArr[row] = cbRG(chY);
        crArr[row] = crRG(chY);
      } else {
        // When we have run out of rows in the chroma planes to compress, send
        // the last row as padding.
        cbArr[row] = cbRG(imgHeight / 2 - 1);
        crArr[row] = crRG(imgHeight / 2 - 1);
      }
    }

    jpeg_write_raw_data(&cinfo, imgArr, DCTSIZE * 2);
  }

  jpeg_finish_compress(&cinfo);

  int numBytesInBuffer = cinfo.dest->next_output_byte - outBuf;

  flush(numBytesInBuffer);

  clientData.totalOutputBytes += numBytesInBuffer;

  safeDeleteArray(yArr);
  safeDeleteArray(cbArr);
  safeDeleteArray(crArr);
  safeDelete(yRowGenerator);
  safeDelete(cbRowGenerator);
  safeDelete(crRowGenerator);

  return clientData.totalOutputBytes;
}
