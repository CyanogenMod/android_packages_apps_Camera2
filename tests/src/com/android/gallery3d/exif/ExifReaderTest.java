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

import android.content.res.XmlResourceParser;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class ExifReaderTest extends ExifXmlDataTestCase {
    private static final String TAG = "ExifReaderTest";

    private final HashMap<Short, String> mIfd0Value = new HashMap<Short, String>();
    private final HashMap<Short, String> mIfd1Value = new HashMap<Short, String>();
    private final HashMap<Short, String> mExifIfdValue = new HashMap<Short, String>();
    private final HashMap<Short, String> mInteroperabilityIfdValue = new HashMap<Short, String>();

    private InputStream mImageInputStream;

    public ExifReaderTest(int imageResourceId, int xmlResourceId) {
        super(imageResourceId, xmlResourceId);
    }

    @Override
    public void setUp() throws Exception {
        mImageInputStream = getInstrumentation()
                .getContext().getResources().openRawResource(mImageResourceId);

        XmlResourceParser parser =
                getInstrumentation().getContext().getResources().getXml(mXmlResourceId);

        ExifXmlReader.readXml(parser, mIfd0Value, mIfd1Value, mExifIfdValue
                , mInteroperabilityIfdValue);
        parser.close();
    }

    public void testRead() throws ExifInvalidFormatException, IOException {
        ExifReader reader = new ExifReader();
        ExifData exifData = reader.read(mImageInputStream);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_0), mIfd0Value);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_1), mIfd1Value);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_EXIF), mExifIfdValue);
        checkIfd(exifData.getIfdData(IfdId.TYPE_IFD_INTEROPERABILITY),
                mInteroperabilityIfdValue);
        checkThumbnail(exifData);
    }

    private void checkThumbnail(ExifData exifData) {
        IfdData ifd1 = exifData.getIfdData(IfdId.TYPE_IFD_1);
        if (ifd1 != null) {
            if (ifd1.getTag(ExifTag.TAG_COMPRESSION).getUnsignedShort(0) ==
                    ExifTag.Compression.JPEG) {
                assertTrue(exifData.hasCompressedThumbnail());
                byte[] thumbnail = exifData.getCompressedThumbnail();
                assertTrue(BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length) != null);
            } else {
                // Try to check the strip count with the formula provided by EXIF spec.
                int planarType = ExifTag.PlanarConfiguration.CHUNKY;
                ExifTag planarTag = ifd1.getTag(ExifTag.TAG_PLANAR_CONFIGURATION);
                if (planarTag != null) {
                    planarType = planarTag.getUnsignedShort(0);
                }

                ExifTag heightTag = ifd1.getTag(ExifTag.TAG_IMAGE_LENGTH);
                ExifTag rowPerStripTag = ifd1.getTag(ExifTag.TAG_ROWS_PER_STRIP);

                int imageLength = getUnsignedIntOrShort(heightTag);
                int rowsPerStrip = getUnsignedIntOrShort(rowPerStripTag);
                int stripCount = ifd1.getTag(
                        ExifTag.TAG_STRIP_OFFSETS).getComponentCount();

                if (planarType == ExifTag.PlanarConfiguration.CHUNKY) {
                    assertTrue(stripCount == (imageLength + rowsPerStrip - 1) / rowsPerStrip);
                } else {
                    ExifTag samplePerPixelTag = ifd1.getTag(ExifTag.TAG_SAMPLES_PER_PIXEL);
                    int samplePerPixel = samplePerPixelTag.getUnsignedShort(0);
                    assertTrue(stripCount ==
                            (imageLength + rowsPerStrip - 1) / rowsPerStrip * samplePerPixel);
                }

                for (int i = 0; i < stripCount; i++) {
                    ExifTag byteCountTag = ifd1.getTag(ExifTag.TAG_STRIP_BYTE_COUNTS);
                    if (byteCountTag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT) {
                        assertEquals(byteCountTag.getUnsignedShort(i), exifData.getStrip(i).length);
                    } else {
                        assertEquals(
                                byteCountTag.getUnsignedLong(i), exifData.getStrip(i).length);
                    }
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

    private void checkIfd(IfdData ifd, HashMap<Short, String> ifdValue) {
        if (ifd == null) {
            assertEquals(0 ,ifdValue.size());
            return;
        }
        ExifTag[] tags = ifd.getAllTags();
        for (ExifTag tag : tags) {
            assertEquals(ifdValue.get(tag.getTagId()), tag.valueToString().trim());
        }
        assertEquals(ifdValue.size(), tags.length);
    }

    @Override
    public void tearDown() throws Exception {
        mImageInputStream.close();
    }
}
