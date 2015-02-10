/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <algorithm>
#include <array>
#include <fstream>
#include <functional>
#include <stdio.h>
#include <string>
#include <vector>

using namespace jpegutil;

class Image {
 public:
  Image(int width, int height, int channels)
      : width_(width),
        height_(height),
        channels_(channels),
        data_(width * height * channels) {}

  const unsigned char& operator()(int x, int y, int c) const {
    return data_[x + y * width_ + c * width_ * height_];
  }

  unsigned char& operator()(int x, int y, int c) {
    return data_[x + y * width_ + c * width_ * height_];
  }

  int width() const { return width_; }

  int height() const { return height_; }

  int channels() const { return channels_; }

  void fill(unsigned char value) {
    std::fill(data_.begin(), data_.end(), value);
  }

  const unsigned char* data() const { return data_.data(); }

  jpegutil::Plane getPlane(int channel, int factorX, int factorY) const {
    int imgWidth = width_;
    int imgHeight = height_;
    int planeWidth = width_ / factorX;
    int planeHeight = height_ / factorY;
    const unsigned char* data = data_.data() + (width_ * height_ * channel);
    int pixelStride = 1;
    int rowStride = width_;
    return jpegutil::Plane{planeWidth, planeHeight, data, pixelStride,
                           rowStride};
  }

 private:
  const int width_;
  const int height_;
  const int channels_;
  // Holds planar data without padding.
  std::vector<unsigned char> data_;
};

void generateTestBorder(Image& rgb) {
  // Draw a 1px border with decreasing brightness, starting from the top edge,
  // clockwise.
  for (int y = 0; y < rgb.height(); y++) {
    for (int x = 0; x < rgb.width(); x++) {
      for (int c = 0; c < rgb.channels(); c++) {
        if (y == 0) {
          rgb(x, y, c) = 255;
        } else if (y == rgb.height() - 1) {
          rgb(x, y, c) = 127;
        } else if (x == 0) {
          rgb(x, y, c) = 63;
        } else if (x == rgb.width() - 1) {
          rgb(x, y, c) = 191;
        }
      }
    }
  }
}

void compressJpegToFile(const std::string& fname, const jpegutil::Plane yPlane,
                        const jpegutil::Plane cbPlane,
                        const jpegutil::Plane crPlane) {
  std::ofstream f("output.jpg", std::ofstream::out | std::ofstream::binary |
                                    std::ofstream::trunc);

  std::vector<unsigned char> outBuf(10240000);

  auto flush = [&f, &outBuf](size_t numBytes) {
    printf("Flushing!\n");
    f.write((char*)outBuf.data(), numBytes);
  };

  int quality = 100;

  // printf("compressing (%d, %d)\n", yPlane.img_width, yPlane.img_height);
  // compress(yPlane, cbPlane, crPlane, outBuf.data(), outBuf.capacity(), flush,
  // quality);

  f.flush();
  f.close();
}

void compressJpegToFile(const std::string& fname, const Image& yuv) {
  compressJpegToFile(fname, yuv.getPlane(0, 1, 1), yuv.getPlane(1, 2, 2),
                     yuv.getPlane(2, 2, 2));
}

void printToPPM(const Image& rgb, const std::string& fname) {
  std::ofstream f(fname, std::ofstream::out | std::ofstream::trunc);
  f << "P3\n";
  f << rgb.width() << " " << rgb.height() << "\n";
  f << "255\n";
  for (int y = 0; y < rgb.height(); y++) {
    for (int x = 0; x < rgb.width(); x++) {
      f << (int)rgb(x, y, 0) << " " << (int)rgb(x, y, 1) << " "
        << (int)rgb(x, y, 2) << " ";
    }
    f << "\n";
  }
  f.flush();
  f.close();
}

void printVectorImage(int width, int height, std::vector<unsigned char> data) {
  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      printf("%d ", (int)data[x + y * width]);
    }
    printf("\n");
  }
}

template <unsigned int ROWS>
void printArrayImage(int width, std::array<unsigned char*, ROWS> data) {
  for (int y = 0; y < ROWS; y++) {
    for (int x = 0; x < width; x++) {
      printf("%d ", (int)data[y][x]);
    }
    printf("\n");
  }
}

class TransformTest : public ::testing::Test {};

TEST_F(TransformTest, testMapIdentity) {
  Transform transform(0, 0, 5, 5);
  int x, y;

  transform.Map(0, 0, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(0, y);

  transform.Map(1, 1, &x, &y);
  EXPECT_EQ(1, x);
  EXPECT_EQ(1, y);

  transform.Map(2, 3, &x, &y);
  EXPECT_EQ(2, x);
  EXPECT_EQ(3, y);

  transform.Map(0, 4, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(4, y);
}

TEST_F(TransformTest, testOutputSize) {
  Transform rot0(0, 0, 3, 5);
  EXPECT_EQ(3, rot0.output_width());
  EXPECT_EQ(5, rot0.output_height());

  Transform rot180(3, 5, 0, 0);
  EXPECT_EQ(3, rot180.output_width());
  EXPECT_EQ(5, rot180.output_height());

  Transform rot90(3, 0, 0, 5);
  EXPECT_EQ(5, rot90.output_width());
  EXPECT_EQ(3, rot90.output_height());

  Transform rot270(0, 5, 3, 0);
  EXPECT_EQ(5, rot270.output_width());
  EXPECT_EQ(3, rot270.output_height());
}

TEST_F(TransformTest, testMapIdentityClampsResults) {
  Transform transform(0, 0, 5, 5);
  int x, y;

  transform.Map(-1, -1, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(0, y);

  transform.Map(-1, 5, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(4, y);

  transform.Map(8, -3, &x, &y);
  EXPECT_EQ(4, x);
  EXPECT_EQ(0, y);
}

TEST_F(TransformTest, testMapCrop) {
  Transform transform(10, 10, 20, 20);
  int x, y;
  transform.Map(-3, -3, &x, &y);
  EXPECT_EQ(10, x);
  EXPECT_EQ(10, y);

  transform.Map(0, 0, &x, &y);
  EXPECT_EQ(10, x);
  EXPECT_EQ(10, y);

  transform.Map(5, 6, &x, &y);
  EXPECT_EQ(15, x);
  EXPECT_EQ(16, y);

  transform.Map(9, 9, &x, &y);
  EXPECT_EQ(19, x);
  EXPECT_EQ(19, y);

  transform.Map(43, 42, &x, &y);
  EXPECT_EQ(19, x);
  EXPECT_EQ(19, y);
}

TEST_F(TransformTest, testMapCropRotate180) {
  Transform transform(49, 49, -1, -1);
  int x, y;

  transform.Map(-3, -3, &x, &y);
  EXPECT_EQ(49, x);
  EXPECT_EQ(49, y);

  transform.Map(0, 0, &x, &y);
  EXPECT_EQ(49, x);
  EXPECT_EQ(49, y);

  transform.Map(5, 6, &x, &y);
  EXPECT_EQ(44, x);
  EXPECT_EQ(43, y);

  transform.Map(49, 49, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(0, y);

  transform.Map(142, 3243, &x, &y);
  EXPECT_EQ(0, x);
  EXPECT_EQ(0, y);
}

TEST_F(TransformTest, testMapCropRotate90) {
  // Crop to this rectangle, with origin at '@':
  // (50, 125)      (150, 125)
  //  +-------------+
  //  |             |
  //  +-------------@
  // (50, 75)      (150, 75)
  Transform transform(150, 75, 50, 125);

  EXPECT_EQ(50, transform.output_width());
  EXPECT_EQ(100, transform.output_height());

  int x, y;

  transform.Map(-3, -3, &x, &y);
  EXPECT_EQ(150, x);
  EXPECT_EQ(75, y);

  transform.Map(0, 0, &x, &y);
  EXPECT_EQ(150, x);
  EXPECT_EQ(75, y);

  transform.Map(5, 6, &x, &y);
  EXPECT_EQ(144, x);
  EXPECT_EQ(80, y);

  transform.Map(49, 99, &x, &y);
  EXPECT_EQ(51, x);
  EXPECT_EQ(124, y);

  transform.Map(242, 3243, &x, &y);
  EXPECT_EQ(51, x);
  EXPECT_EQ(124, y);
}

TEST_F(TransformTest, testMapCropRotate270) {
  // Crop to this rectangle, with origin at '@':
  // (50, 125)      (150, 125)
  //  @-------------+
  //  |             |
  //  +-------------+
  // (50, 75)      (150, 75)
  Transform transform(50, 125, 150, 75);

  EXPECT_EQ(50, transform.output_width());
  EXPECT_EQ(100, transform.output_height());

  int x, y;

  transform.Map(-3, -3, &x, &y);
  EXPECT_EQ(50, x);
  EXPECT_EQ(125, y);

  transform.Map(0, 0, &x, &y);
  EXPECT_EQ(50, x);
  EXPECT_EQ(125, y);

  transform.Map(5, 6, &x, &y);
  EXPECT_EQ(56, x);
  EXPECT_EQ(120, y);

  transform.Map(48, 98, &x, &y);
  EXPECT_EQ(148, x);
  EXPECT_EQ(77, y);

  transform.Map(242, 3243, &x, &y);
  EXPECT_EQ(149, x);
  EXPECT_EQ(76, y);
}

class RowIteratorTest : public ::testing::Test {
 protected:
  RowIteratorTest() {}

  virtual ~RowIteratorTest() {}

  virtual void SetUp() {}

  virtual void TearDown() {}
};

TEST_F(RowIteratorTest, testRowIteratorWithIdentityTransform) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6,   //
                                      7, 8, 9};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 3, input.data(), 1, 3};

  Transform identity(0, 0, 3, 3);

  RowIterator<3> iter(plane, identity, 3);

  std::array<unsigned char*, 3> output = iter.LoadAt(0);

  std::vector<unsigned char> expectedOutput = {1, 2, 3, 4, 5, 6, 7, 8, 9};

  printf("expected:\n");
  printVectorImage(3, 3, expectedOutput);
  printf("actual:\n");
  printArrayImage(3, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[0], 3));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[3], 3));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[6], 3));
}

TEST_F(RowIteratorTest, testRowIteratorWithIdentityOutputsLandscapeImage) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 2, input.data(), 1, 3};

  Transform identity(0, 0, 3, 2);

  RowIterator<4> iter(plane, identity, 4);

  std::array<unsigned char*, 4> output = iter.LoadAt(0);

  std::vector<unsigned char> expectedOutput = {1, 2, 3, 3,   //
                                               4, 5, 6, 6,   //
                                               4, 5, 6, 6,   //
                                               4, 5, 6, 6};  //

  printf("expected:\n");
  printVectorImage(4, 4, expectedOutput);
  printf("actual:\n");
  printArrayImage(4, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[4 * 0], 4));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[4 * 1], 4));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[4 * 2], 4));
  EXPECT_EQ(0, memcmp(output[3], &expectedOutput[4 * 3], 4));
}

TEST_F(RowIteratorTest,
       testRowIteratorWith90DegreeRotationOutputsLandscapeImage) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 2, input.data(), 1, 3};

  Transform identity =
      Transform::ForCropFollowedByRotation(0, 0, 3, 2, 90 / 90);

  RowIterator<4> iter(plane, identity, 4);

  std::array<unsigned char*, 4> output = iter.LoadAt(0);

  std::vector<unsigned char> expectedOutput = {3, 6, 6, 6,   //
                                               2, 5, 5, 5,   //
                                               1, 4, 4, 4,   //
                                               1, 4, 4, 4};  //
  printf("expected:\n");
  printVectorImage(4, 4, expectedOutput);
  printf("actual:\n");
  printArrayImage(4, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[4 * 0], 4));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[4 * 1], 4));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[4 * 2], 4));
  EXPECT_EQ(0, memcmp(output[3], &expectedOutput[4 * 3], 4));
}

TEST_F(RowIteratorTest, testRowIteratorWithIdentityOutputsPortraitImage) {
  std::vector<unsigned char> input = {1,  2,  3,    //
                                      4,  5,  6,    //
                                      7,  8,  9,    //
                                      10, 11, 12};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 4, input.data(), 1, 3};

  Transform identity(0, 0, 3, 4);

  RowIterator<5> iter(plane, identity, 5);

  std::array<unsigned char*, 5> output = iter.LoadAt(0);

  std::vector<unsigned char> expectedOutput = {1,  2,  3,  3,  3,    //
                                               4,  5,  6,  6,  6,    //
                                               7,  8,  9,  9,  9,    //
                                               10, 11, 12, 12, 12,   //
                                               10, 11, 12, 12, 12};  //

  printf("expected:\n");
  printVectorImage(5, 5, expectedOutput);
  printf("actual:\n");
  printArrayImage(5, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[5 * 0], 5));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[5 * 1], 5));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[5 * 2], 5));
  EXPECT_EQ(0, memcmp(output[3], &expectedOutput[5 * 3], 5));
  EXPECT_EQ(0, memcmp(output[4], &expectedOutput[5 * 4], 5));
}

TEST_F(RowIteratorTest, testRowIteratorWithIdentityTransformClampsToEdges) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6,   //
                                      7, 8, 9};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 3, input.data(), 1, 3};

  Transform identity(0, 0, 3, 3);

  RowIterator<3> iter(plane, identity, 3);

  std::array<unsigned char*, 3> output = iter.LoadAt(1);

  std::vector<unsigned char> expectedOutput = {4, 5, 6,   //
                                               7, 8, 9,   //
                                               7, 8, 9};  //

  printf("expected:\n");
  printVectorImage(3, 3, expectedOutput);
  printf("actual:\n");
  printArrayImage(3, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[0], 3));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[3], 3));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[6], 3));
}

TEST_F(RowIteratorTest,
       testRowIteratorWithIdentityTransformClampsToEdgesExtreme) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6,   //
                                      7, 8, 9};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 3, input.data(), 1, 3};

  Transform identity(0, 0, 3, 3);

  RowIterator<3> iter(plane, identity, 3);

  std::array<unsigned char*, 3> output = iter.LoadAt(10);

  std::vector<unsigned char> expectedOutput = {7, 8, 9,   //
                                               7, 8, 9,   //
                                               7, 8, 9};  //

  printf("expected:\n");
  printVectorImage(3, 3, expectedOutput);
  printf("actual:\n");
  printArrayImage(3, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[0], 3));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[3], 3));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[6], 3));
}

TEST_F(RowIteratorTest,
       testRowIteratorWithIdentityTransformFillsRowsWithPadding) {
  std::vector<unsigned char> input = {1, 2, 3,   //
                                      4, 5, 6,   //
                                      7, 8, 9};  //
  // {img_width, img_height, plane_width, plane_height, data, pixel_stride,
  //  row_stride}
  Plane plane{3, 3, input.data(), 1, 3};

  Transform identity(0, 0, 2, 2);

  RowIterator<4> iter(plane, identity, 5);

  std::array<unsigned char*, 4> output = iter.LoadAt(0);

  std::vector<unsigned char> expectedOutput = {1, 2, 2, 2, 2,   //
                                               4, 5, 5, 5, 5,   //
                                               4, 5, 5, 5, 5,   //
                                               4, 5, 5, 5, 5};  //

  printf("expected:\n");
  printVectorImage(5, 4, expectedOutput);
  printf("actual:\n");
  printArrayImage(5, output);

  EXPECT_EQ(0, memcmp(output[0], &expectedOutput[0], 5));
  EXPECT_EQ(0, memcmp(output[1], &expectedOutput[5], 5));
  EXPECT_EQ(0, memcmp(output[2], &expectedOutput[10], 5));
  EXPECT_EQ(0, memcmp(output[3], &expectedOutput[15], 5));
}

TEST_F(RowIteratorTest, testRowIteratorWithCropAndRotate180) {
  std::vector<unsigned char> input = {1,  2,  3,  4,    //
                                      5,  6,  7,  8,    //
                                      9,  10, 11, 12,   //
                                      13, 14, 15, 16};  //
  Transform transform(2, 2, 0, 0);
  Plane plane{4, 4, input.data(), 1, 4};

  RowIterator<4> iter(plane, transform, 4);
  std::vector<unsigned char> expected = {11, 10, 10, 10,  //
                                         7,  6,  6,  6,   //
                                         7,  6,  6,  6,   //
                                         7,  6,  6,  6};  //

  std::array<unsigned char*, 4> output = iter.LoadAt(0);

  printf("expected:\n");
  printVectorImage(4, 4, expected);
  printf("actual:\n");
  printArrayImage(4, output);

  EXPECT_EQ(0, memcmp(output[0], &expected[0], 4));
  EXPECT_EQ(0, memcmp(output[1], &expected[4 * 1], 4));
  EXPECT_EQ(0, memcmp(output[2], &expected[4 * 2], 4));
  EXPECT_EQ(0, memcmp(output[3], &expected[4 * 3], 4));
}

TEST_F(RowIteratorTest,
       testRowIteratorWithCropAndRotate180UsingStaticFactoryMethod) {
  std::vector<unsigned char> input = {1,  2,  3,  4,    //
                                      5,  6,  7,  8,    //
                                      9,  10, 11, 12,   //
                                      13, 14, 15, 16};  //
  Transform transform =
      Transform::ForCropFollowedByRotation(1, 1, 3, 3, 180 / 90);
  Plane plane{4, 4, input.data(), 1, 4};

  RowIterator<4> iter(plane, transform, 4);
  std::vector<unsigned char> expected = {11, 10, 10, 10,  //
                                         7,  6,  6,  6,   //
                                         7,  6,  6,  6,   //
                                         7,  6,  6,  6};  //

  std::array<unsigned char*, 4> output = iter.LoadAt(0);

  printf("expected:\n");
  printVectorImage(4, 4, expected);
  printf("actual:\n");
  printArrayImage(4, output);

  EXPECT_EQ(0, memcmp(output[0], &expected[0], 4));
  EXPECT_EQ(0, memcmp(output[1], &expected[4 * 1], 4));
  EXPECT_EQ(0, memcmp(output[2], &expected[4 * 2], 4));
  EXPECT_EQ(0, memcmp(output[3], &expected[4 * 3], 4));
}

TEST_F(RowIteratorTest,
       testRowIteratorWithCropAndRotate90UsingStaticFactoryMethod) {
  std::vector<unsigned char> input = {1,  2,  3,  4,    //
                                      5,  6,  7,  8,    //
                                      9,  10, 11, 12,   //
                                      13, 14, 15, 16};  //
  Transform transform =
      Transform::ForCropFollowedByRotation(1, 1, 4, 3, 90 / 90);
  Plane plane{4, 4, input.data(), 1, 4};

  RowIterator<3> iter(plane, transform, 2);
  std::vector<unsigned char> expected = {8, 12,   //
                                         7, 11,   //
                                         6, 10};  //

  std::array<unsigned char*, 3> output = iter.LoadAt(0);

  printf("expected:\n");
  printVectorImage(2, 3, expected);
  printf("actual:\n");
  printArrayImage(2, output);

  EXPECT_EQ(0, memcmp(output[0], &expected[0 * 2], 2));
  EXPECT_EQ(0, memcmp(output[1], &expected[1 * 2], 2));
  EXPECT_EQ(0, memcmp(output[2], &expected[2 * 2], 2));
}

TEST_F(RowIteratorTest,
       testRowIteratorWithCropAndRotate270UsingStaticFactoryMethod) {
  std::vector<unsigned char> input = {1,  2,  3,  4,    //
                                      5,  6,  7,  8,    //
                                      9,  10, 11, 12,   //
                                      13, 14, 15, 16};  //
  Transform transform =
      Transform::ForCropFollowedByRotation(1, 1, 4, 3, 270 / 90);
  Plane plane{4, 4, input.data(), 1, 4};

  RowIterator<3> iter(plane, transform, 2);
  std::vector<unsigned char> expected = {10, 6,   //
                                         11, 7,   //
                                         12, 8};  //

  std::array<unsigned char*, 3> output = iter.LoadAt(0);

  printf("expected:\n");
  printVectorImage(2, 3, expected);
  printf("actual:\n");
  printArrayImage(2, output);

  EXPECT_EQ(0, memcmp(output[0], &expected[0 * 2], 2));
  EXPECT_EQ(0, memcmp(output[1], &expected[1 * 2], 2));
  EXPECT_EQ(0, memcmp(output[2], &expected[2 * 2], 2));
}

class CompressTest : public ::testing::Test {
 protected:
  CompressTest() {}

  virtual ~CompressTest() {}

  virtual void SetUp() {}

  virtual void TearDown() {}
};

/**
 * Creates a test image with the specified dimensions and compresses it via
 * jpegutil::Compress, returning the result of Compress.
 */
int compressTestImage(int width, int height) {
  printf("Compressing test image of size %d x %d\n", width, height);
  std::vector<unsigned char> y(width * height);
  std::fill(y.begin(), y.end(), 128);

  int yPStride = 1;
  int yRStride = width;

  std::vector<unsigned char> cb((width / 2) * (height / 2));
  std::fill(cb.begin(), cb.end(), 0);

  int cbPStride = 1;
  int cbRStride = width / 2;

  std::vector<unsigned char> cr((width / 2) * (height / 2));
  std::fill(cr.begin(), cr.end(), 255);

  int crPStride = 1;
  int crRStride = width / 2;

  Plane yP = {width, height, y.data(), yPStride, yRStride};
  Plane cbP = {width / 2, height / 2, cb.data(), cbPStride, cbRStride};
  Plane crP = {width / 2, height / 2, cr.data(), crPStride, crRStride};

  // Identity transform

  // Round up to the nearest multiple of 64.
  int y_row_length = (width + 16 + 63) & ~63;
  int cb_row_length = (width / 2 + 16 + 63) & ~63;
  int cr_row_length = (width / 2 + 16 + 63) & ~63;

  RowIterator<16> yIter(yP, Transform(0, 0, width, height), y_row_length);
  RowIterator<8> cbIter(cbP, Transform(0, 0, width / 2, height / 2),
                        cb_row_length);
  RowIterator<8> crIter(crP, Transform(0, 0, width / 2, height / 2),
                        cr_row_length);

  std::vector<unsigned char> out(10240);
  int outBufCapacity = 10240;
  int quality = 100;

  auto flush = [](size_t numBytes) {
    // do nothing
  };

  return Compress(width, height, yIter, cbIter, crIter, out.data(),
                  outBufCapacity, flush, quality);
}

TEST_F(CompressTest, compressSquareImageProducesOutput) {
  EXPECT_LT(0, compressTestImage(1, 1));
  EXPECT_LT(0, compressTestImage(2, 2));
  EXPECT_LT(0, compressTestImage(3, 3));
  EXPECT_LT(0, compressTestImage(4, 4));
  EXPECT_LT(0, compressTestImage(5, 5));
  EXPECT_LT(0, compressTestImage(6, 6));
  EXPECT_LT(0, compressTestImage(7, 7));
  EXPECT_LT(0, compressTestImage(8, 8));
  EXPECT_LT(0, compressTestImage(9, 9));
  EXPECT_LT(0, compressTestImage(10, 10));
  EXPECT_LT(0, compressTestImage(11, 11));
  EXPECT_LT(0, compressTestImage(16, 16));
  EXPECT_LT(0, compressTestImage(17, 17));
  EXPECT_LT(0, compressTestImage(23, 23));
  EXPECT_LT(0, compressTestImage(256, 256));
}

TEST_F(CompressTest, compressNonSquareImageProducesOutput) {
  EXPECT_LT(0, compressTestImage(256, 1));
  EXPECT_LT(0, compressTestImage(256, 2));
  EXPECT_LT(0, compressTestImage(256, 3));
  EXPECT_LT(0, compressTestImage(256, 4));
  EXPECT_LT(0, compressTestImage(256, 5));
  EXPECT_LT(0, compressTestImage(256, 6));
  EXPECT_LT(0, compressTestImage(256, 7));
  EXPECT_LT(0, compressTestImage(256, 8));
  EXPECT_LT(0, compressTestImage(256, 9));
  EXPECT_LT(0, compressTestImage(256, 10));
  EXPECT_LT(0, compressTestImage(256, 11));
  EXPECT_LT(0, compressTestImage(256, 12));
  EXPECT_LT(0, compressTestImage(256, 13));
  EXPECT_LT(0, compressTestImage(256, 14));
  EXPECT_LT(0, compressTestImage(256, 15));
  EXPECT_LT(0, compressTestImage(256, 16));
  EXPECT_LT(0, compressTestImage(256, 17));

  EXPECT_LT(0, compressTestImage(1, 256));
  EXPECT_LT(0, compressTestImage(2, 256));
  EXPECT_LT(0, compressTestImage(3, 256));
  EXPECT_LT(0, compressTestImage(4, 256));
  EXPECT_LT(0, compressTestImage(5, 256));
  EXPECT_LT(0, compressTestImage(6, 256));
  EXPECT_LT(0, compressTestImage(7, 256));
  EXPECT_LT(0, compressTestImage(8, 256));
  EXPECT_LT(0, compressTestImage(9, 256));
  EXPECT_LT(0, compressTestImage(10, 256));
  EXPECT_LT(0, compressTestImage(11, 256));
  EXPECT_LT(0, compressTestImage(12, 256));
  EXPECT_LT(0, compressTestImage(13, 256));
  EXPECT_LT(0, compressTestImage(14, 256));
  EXPECT_LT(0, compressTestImage(15, 256));
  EXPECT_LT(0, compressTestImage(16, 256));
  EXPECT_LT(0, compressTestImage(17, 256));
}

int compressTestImage(int width, int height, int cropLeft, int cropTop,
                      int cropRight, int cropBottom, int rot90, int yPStride,
                      int yRStride, int cbPStride, int cbRStride, int crPStride,
                      int crRStride) {
  std::vector<unsigned char> yArr(yRStride * height);
  std::vector<unsigned char> cbArr(cbRStride * (height / 2));
  std::vector<unsigned char> crArr(crRStride * (height / 2));
  std::vector<unsigned char> outBuf(yArr.size());
  return Compress(width, height,                             //
                  yArr.data(), yPStride, yRStride,           //
                  cbArr.data(), cbPStride, cbRStride,        //
                  crArr.data(), crPStride, cbRStride,        //
                  outBuf.data(), (size_t)outBuf.size(),      //
                  100,                                       //
                  cropLeft, cropTop, cropRight, cropBottom,  //
                  rot90);
}

TEST_F(CompressTest, compressShouldProduceOutput) {
  EXPECT_LT(0, compressTestImage(4160, 3120, 0, 0, 4160, 3120, 1, 1, 4160, 2,
                                 4160, 2, 4160));

  EXPECT_LT(0, compressTestImage(4160, 3120, -10, -100, 3234, 3121, 0, 1, 4160,
                                 2, 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, -10, -100, 3234, 3121, 1, 1, 4160,
                                 2, 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, -10, -100, 3234, 3121, 2, 1, 4160,
                                 2, 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, -10, -100, 3234, 3121, 3, 1, 4160,
                                 2, 4160, 2, 4160));

  EXPECT_LT(0, compressTestImage(4160, 3120, 50, 50, 100, 100, 0, 1, 4160, 2,
                                 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, 50, 50, 100, 100, 1, 1, 4160, 2,
                                 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, 50, 50, 100, 100, 2, 1, 4160, 2,
                                 4160, 2, 4160));
  EXPECT_LT(0, compressTestImage(4160, 3120, 50, 50, 100, 100, 3, 1, 4160, 2,
                                 4160, 2, 4160));
}

void writeSampleImage() {
  // Output test jpeg
  int width = 512;
  int height = 255;
  std::vector<unsigned char> yData(width * height);
  std::vector<unsigned char> cbData((width / 2) * (height / 2));
  std::vector<unsigned char> crData((width / 2) * (height / 2));
  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      unsigned char red = 0, green = 0, blue = 0;

      if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
        red = 0;
        green = 0;
        blue = 0;
      } else {
        // red = (x + y) % 255;
        // green = (x + y) % 255;
        // blue = (x + y) % 255;
        red = 255;
        green = 255;
        blue = 255;
      }

      red = y * 1 + x * 1;
      green = y * 1 + x * 1;
      blue = y * 1 + x * 1;

      yData[x + y * width] =
          0.299 * (float)red + 0.587 * (float)green + 0.114 * (float)blue;
      int sx = x / 2;
      int sy = y / 2;
      if (sx < width / 2 && sy < height / 2) {
        cbData[sx + sy * (width / 2)] = 128 - 0.168736 * (float)red -
                                        0.331264 * (float)green +
                                        0.5 * (float)blue;
        crData[sx + sy * (width / 2)] = 128 + 0.5 * (float)red -
                                        0.418688 * (float)green -
                                        0.081321 * (float)blue;
      }
    }
  }

  std::vector<unsigned char> out(102400);
  int outBufCapacity = 102400;
  int quality = 100;

  std::ofstream f("output.jpg", std::ofstream::out | std::ofstream::binary |
                                    std::ofstream::trunc);

  int cropLeft = 0;
  int cropTop = 0;
  int cropRight = width;
  int cropBottom = height;

  int rot90 = 90 / 90;

  int fileSize = Compress(width, height, yData.data(), 1, width, cbData.data(),
                          1, width / 2, crData.data(), 1, width / 2, out.data(),
                          outBufCapacity, quality, cropLeft, cropTop, cropRight,
                          cropBottom, rot90);
  printf("Done Compress\n");
  f.write((char*)out.data(), fileSize);
  f.flush();
  f.close();
}

int main(int argc, char** argv) {
  if (false) {
    writeSampleImage();
    return 0;
  }
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
