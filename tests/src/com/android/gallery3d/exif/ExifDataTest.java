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

import junit.framework.TestCase;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExifDataTest extends TestCase {
    Map<Integer, ExifTag> mTestTags;
    ExifInterface mInterface;
    private ExifTag mVersionTag;
    private ExifTag mGpsVersionTag;
    private ExifTag mModelTag;
    private ExifTag mDateTimeTag;
    private ExifTag mCompressionTag;
    private ExifTag mThumbnailFormatTag;
    private ExifTag mLongitudeTag;
    private ExifTag mShutterTag;
    private ExifTag mInteropIndex;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mInterface = new ExifInterface();

        // TYPE_UNDEFINED with 4 components
        mVersionTag = mInterface.buildTag(ExifInterface.TAG_EXIF_VERSION, new byte[] {
                5, 4, 3, 2
        });
        // TYPE_UNSIGNED_BYTE with 4 components
        mGpsVersionTag = mInterface.buildTag(ExifInterface.TAG_GPS_VERSION_ID, new byte[] {
                6, 7, 8, 9
        });
        // TYPE ASCII with arbitrary length
        mModelTag = mInterface.buildTag(ExifInterface.TAG_MODEL, "helloworld");
        // TYPE_ASCII with 20 components
        mDateTimeTag = mInterface.buildTag(ExifInterface.TAG_DATE_TIME, "2013:02:11 20:20:20");
        // TYPE_UNSIGNED_SHORT with 1 components
        mCompressionTag = mInterface.buildTag(ExifInterface.TAG_COMPRESSION, 100);
        // TYPE_UNSIGNED_LONG with 1 components
        mThumbnailFormatTag =
                mInterface.buildTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, 100);
        // TYPE_UNSIGNED_RATIONAL with 3 components
        mLongitudeTag = mInterface.buildTag(ExifInterface.TAG_GPS_LONGITUDE, new Rational[] {
                new Rational(2, 2), new Rational(11, 11),
                new Rational(102, 102)
        });
        // TYPE_RATIONAL with 1 components
        mShutterTag = mInterface
                .buildTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, new Rational(4, 6));
        // TYPE_ASCII with arbitrary length
        mInteropIndex = mInterface.buildTag(ExifInterface.TAG_INTEROPERABILITY_INDEX, "foo");

        mTestTags = new HashMap<Integer, ExifTag>();

        mTestTags.put(ExifInterface.TAG_EXIF_VERSION, mVersionTag);
        mTestTags.put(ExifInterface.TAG_GPS_VERSION_ID, mGpsVersionTag);
        mTestTags.put(ExifInterface.TAG_MODEL, mModelTag);
        mTestTags.put(ExifInterface.TAG_DATE_TIME, mDateTimeTag);
        mTestTags.put(ExifInterface.TAG_COMPRESSION, mCompressionTag);
        mTestTags.put(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, mThumbnailFormatTag);
        mTestTags.put(ExifInterface.TAG_GPS_LONGITUDE, mLongitudeTag);
        mTestTags.put(ExifInterface.TAG_SHUTTER_SPEED_VALUE, mShutterTag);
        mTestTags.put(ExifInterface.TAG_INTEROPERABILITY_INDEX, mInteropIndex);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mInterface = null;
        mTestTags = null;
    }

    public void testAddTag() {
        ExifData exifData = new ExifData(ByteOrder.BIG_ENDIAN);

        // Add all test tags
        for (ExifTag t : mTestTags.values()) {
            assertTrue(exifData.addTag(t) == null);
        }

        // Make sure no initial thumbnails
        assertFalse(exifData.hasCompressedThumbnail());
        assertFalse(exifData.hasUncompressedStrip());

        // Check that we can set thumbnails
        exifData.setStripBytes(3, new byte[] {
                1, 2, 3, 4, 5
        });
        assertTrue(exifData.hasUncompressedStrip());
        exifData.setCompressedThumbnail(new byte[] {
            1
        });
        assertTrue(exifData.hasCompressedThumbnail());

        // Check that we can clear thumbnails
        exifData.clearThumbnailAndStrips();
        assertFalse(exifData.hasCompressedThumbnail());
        assertFalse(exifData.hasUncompressedStrip());

        // Make sure ifds exist
        for (int i : IfdData.getIfds()) {
            assertTrue(exifData.getIfdData(i) != null);
        }

        // Get all test tags
        List<ExifTag> allTags = exifData.getAllTags();
        assertTrue(allTags != null);

        // Make sure all test tags are in data
        for (ExifTag t : mTestTags.values()) {
            boolean check = false;
            for (ExifTag i : allTags) {
                if (t.equals(i)) {
                    check = true;
                    break;
                }
            }
            assertTrue(check);
        }

        // Check if getting tags for a tid works
        List<ExifTag> tidTags = exifData.getAllTagsForTagId(ExifInterface
                .getTrueTagKey(ExifInterface.TAG_SHUTTER_SPEED_VALUE));
        assertTrue(tidTags.size() == 1);
        assertTrue(tidTags.get(0).equals(mShutterTag));

        // Check if getting tags for an ifd works
        List<ExifTag> ifdTags = exifData.getAllTagsForIfd(IfdId.TYPE_IFD_INTEROPERABILITY);
        assertTrue(ifdTags.size() == 1);
        assertTrue(ifdTags.get(0).equals(mInteropIndex));

    }
}
