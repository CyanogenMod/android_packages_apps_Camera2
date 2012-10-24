/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.exif;

import android.graphics.BitmapFactory;

import java.util.List;
import java.util.Map;

public class ExifReaderTest extends ExifXmlDataTestCase {
    private static final String TAG = "ExifReaderTest";

    private List<Map<Short, List<String>>> mGroundTruth;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mGroundTruth = ExifXmlReader.readXml(getXmlParser());
    }

    public ExifReaderTest(int imgRes, int xmlRes) {
        super(imgRes, xmlRes);
    }

    public ExifReaderTest(String imgPath, String xmlPath) {
        super(imgPath, xmlPath);
    }

    public void testRead() throws Exception {
        try {
            ExifReader reader = new ExifReader();
            ExifData exifData = reader.read(getImageInputStream());
            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                checkIfd(exifData.getIfdData(i), mGroundTruth.get(i));
            }
            checkThumbnail(exifData);
        } catch (Exception e) {
            throw new Exception(getImageTitle(), e);
        }
    }

    private void checkThumbnail(ExifData exifData) {
        Map<Short, List<String>> ifd1Truth = mGroundTruth.get(IfdId.TYPE_IFD_1);

        List<String> typeTagValue = ifd1Truth.get(ExifTag.TAG_COMPRESSION);
        if (typeTagValue == null) return;

        IfdData ifd1 = exifData.getIfdData(IfdId.TYPE_IFD_1);
        if (ifd1 == null) fail(getImageTitle() + ": failed to find IFD1");

        String typeTagTruth = typeTagValue.get(0);

        ExifTag typeTag = ifd1.getTag(ExifTag.TAG_COMPRESSION);
        int type = typeTag.getUnsignedShort(0);

        if (String.valueOf(ExifTag.Compression.JPEG).equals(typeTagTruth)) {
            assertTrue(getImageTitle(), type == ExifTag.Compression.JPEG);
            assertTrue(getImageTitle(), exifData.hasCompressedThumbnail());
            byte[] thumbnail = exifData.getCompressedThumbnail();
            assertTrue(getImageTitle(),
                    BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length) != null);
        } else if (String.valueOf(ExifTag.Compression.UNCOMPRESSION).equals(typeTagTruth)) {
            assertTrue(getImageTitle(), type == ExifTag.Compression.UNCOMPRESSION);
            // Try to check the strip count with the formula provided by EXIF spec.
            int planarType = ExifTag.PlanarConfiguration.CHUNKY;
            ExifTag planarTag = ifd1.getTag(ExifTag.TAG_PLANAR_CONFIGURATION);
            if (planarTag != null) {
                planarType = planarTag.getUnsignedShort(0);
            }

            if (!ifd1Truth.containsKey(ExifTag.TAG_IMAGE_LENGTH) ||
                    !ifd1Truth.containsKey(ExifTag.TAG_ROWS_PER_STRIP)) return;

            ExifTag heightTag = ifd1.getTag(ExifTag.TAG_IMAGE_LENGTH);
            ExifTag rowPerStripTag = ifd1.getTag(ExifTag.TAG_ROWS_PER_STRIP);

            // Fail the test if required tags are missing
            if (heightTag == null || rowPerStripTag == null) fail(getImageTitle());

            int imageLength = getUnsignedIntOrShort(heightTag);
            int rowsPerStrip = getUnsignedIntOrShort(rowPerStripTag);
            int stripCount = ifd1.getTag(
                    ExifTag.TAG_STRIP_OFFSETS).getComponentCount();

            if (planarType == ExifTag.PlanarConfiguration.CHUNKY) {
                assertTrue(getImageTitle(),
                        stripCount == (imageLength + rowsPerStrip - 1) / rowsPerStrip);
            } else {
                if (!ifd1Truth.containsKey(ExifTag.TAG_SAMPLES_PER_PIXEL)) return;
                ExifTag samplePerPixelTag = ifd1.getTag(ExifTag.TAG_SAMPLES_PER_PIXEL);
                int samplePerPixel = samplePerPixelTag.getUnsignedShort(0);
                assertTrue(getImageTitle(),
                        stripCount ==
                        (imageLength + rowsPerStrip - 1) / rowsPerStrip * samplePerPixel);
            }

            if (!ifd1Truth.containsKey(ExifTag.TAG_STRIP_BYTE_COUNTS)) return;
            ExifTag byteCountTag = ifd1.getTag(ExifTag.TAG_STRIP_BYTE_COUNTS);
            short byteCountDataType = byteCountTag.getDataType();
            for (int i = 0; i < stripCount; i++) {
                if (byteCountDataType == ExifTag.TYPE_UNSIGNED_SHORT) {
                    assertEquals(getImageTitle(),
                            byteCountTag.getUnsignedShort(i), exifData.getStrip(i).length);
                } else {
                    assertEquals(getImageTitle(),
                            byteCountTag.getUnsignedLong(i), exifData.getStrip(i).length);
                }
            }
        }
    }

    private int getUnsignedIntOrShort(ExifTag tag) {
        if (tag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT) {
            return tag.getUnsignedShort(0);
        } else {
            return (int) tag.getUnsignedLong(0);
        }
    }

    private void checkIfd(IfdData ifd, Map<Short, List<String>> ifdValue) {
        if (ifd == null) {
            assertEquals(getImageTitle(), 0 ,ifdValue.size());
            return;
        }
        ExifTag[] tags = ifd.getAllTags();
        for (ExifTag tag : tags) {
            List<String> truth = ifdValue.get(tag.getTagId());
            assertNotNull(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(), truth);
            if (truth.contains(null)) continue;
            assertTrue(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(),
                    truth.contains(Util.tagValueToString(tag).trim()));
        }
        assertEquals(getImageTitle(), ifdValue.size(), tags.length);
    }
}
