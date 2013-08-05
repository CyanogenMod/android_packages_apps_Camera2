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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExifModifierTest extends ExifXmlDataTestCase {

    private File mTmpFile;
    private List<Map<Short, List<String>>> mGroundTruth;
    private ExifInterface mInterface;
    private Map<Short, ExifTag> mTestTags;
    ExifTag mVersionTag;
    ExifTag mGpsVersionTag;
    ExifTag mModelTag;
    ExifTag mDateTimeTag;
    ExifTag mCompressionTag;
    ExifTag mThumbnailFormatTag;
    ExifTag mLongitudeTag;
    ExifTag mShutterTag;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mGroundTruth = ExifXmlReader.readXml(getXmlParser());
        mTmpFile = File.createTempFile("exif_test", ".jpg");
        FileOutputStream os = null;
        InputStream is = getImageInputStream();
        try {
            os = new FileOutputStream(mTmpFile);
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } finally {
            Util.closeSilently(os);
        }

        // TYPE_UNDEFINED with 4 components
        mVersionTag = mInterface.buildTag(ExifInterface.TAG_EXIF_VERSION, new byte[] {
                1, 2, 3, 4
        });
        // TYPE_UNSIGNED_BYTE with 4 components
        mGpsVersionTag = mInterface.buildTag(ExifInterface.TAG_GPS_VERSION_ID, new byte[] {
                4, 3, 2, 1
        });
        // TYPE ASCII with arbitary length
        mModelTag = mInterface.buildTag(ExifInterface.TAG_MODEL, "end-of-the-world");
        // TYPE_ASCII with 20 components
        mDateTimeTag = mInterface.buildTag(ExifInterface.TAG_DATE_TIME, "2012:12:31 23:59:59");
        // TYPE_UNSIGNED_SHORT with 1 components
        mCompressionTag = mInterface.buildTag(ExifInterface.TAG_COMPRESSION, 100);
        // TYPE_UNSIGNED_LONG with 1 components
        mThumbnailFormatTag =
                mInterface.buildTag(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, 100);
        // TYPE_UNSIGNED_RATIONAL with 3 components
        mLongitudeTag = mInterface.buildTag(ExifInterface.TAG_GPS_LONGITUDE, new Rational[] {
                new Rational(1, 1), new Rational(10, 10),
                new Rational(100, 100)
        });
        // TYPE_RATIONAL with 1 components
        mShutterTag = mInterface
                .buildTag(ExifInterface.TAG_SHUTTER_SPEED_VALUE, new Rational(1, 1));

        mTestTags = new HashMap<Short, ExifTag>();

        mTestTags.put(mVersionTag.getTagId(), mVersionTag);
        mTestTags.put(mGpsVersionTag.getTagId(), mGpsVersionTag);
        mTestTags.put(mModelTag.getTagId(), mModelTag);
        mTestTags.put(mDateTimeTag.getTagId(), mDateTimeTag);
        mTestTags.put(mCompressionTag.getTagId(), mCompressionTag);
        mTestTags.put(mThumbnailFormatTag.getTagId(), mThumbnailFormatTag);
        mTestTags.put(mLongitudeTag.getTagId(), mLongitudeTag);
        mTestTags.put(mShutterTag.getTagId(), mShutterTag);
    }

    public ExifModifierTest(int imageRes, int xmlRes) {
        super(imageRes, xmlRes);
        mInterface = new ExifInterface();
    }

    public ExifModifierTest(String imagePath, String xmlPath) {
        super(imagePath, xmlPath);
        mInterface = new ExifInterface();
    }

    public void testModify() throws Exception {
        Map<Short, Boolean> results = new HashMap<Short, Boolean>();

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(mTmpFile, "rw");
            MappedByteBuffer buf = file.getChannel().map(MapMode.READ_WRITE, 0, file.length());
            for (ExifTag tag : mTestTags.values()) {
                ExifModifier modifier = new ExifModifier(buf, mInterface);
                modifier.modifyTag(tag);
                boolean result = modifier.commit();
                results.put(tag.getTagId(), result);
                buf.force();
                buf.position(0);

                if (!result) {
                    List<String> value = mGroundTruth.get(tag.getIfd()).get(tag.getTagId());
                    assertTrue(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(),
                            value == null || tag.getTagId() == ExifInterface.TAG_MODEL);
                }
            }
        } finally {
            Util.closeSilently(file);
        }

        // Parse the new file and check the result
        InputStream is = null;
        try {
            is = new FileInputStream(mTmpFile);
            ExifData data = new ExifReader(mInterface).read(is);
            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                checkIfd(data.getIfdData(i), mGroundTruth.get(i), results);
            }
        } finally {
            Util.closeSilently(is);
        }

    }

    private void checkIfd(IfdData ifd, Map<Short, List<String>> ifdValue,
            Map<Short, Boolean> results) {
        if (ifd == null) {
            assertEquals(getImageTitle(), 0, ifdValue.size());
            return;
        }
        ExifTag[] tags = ifd.getAllTags();
        for (ExifTag tag : tags) {
            List<String> truth = ifdValue.get(tag.getTagId());
            assertNotNull(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(), truth);
            if (truth.contains(null)) {
                continue;
            }

            ExifTag newTag = mTestTags.get(tag.getTagId());
            if (newTag != null
                    && results.get(tag.getTagId())) {
                assertEquals(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(),
                        Util.tagValueToString(newTag), Util.tagValueToString(tag));
            } else {
                assertTrue(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(),
                        truth.contains(Util.tagValueToString(tag).trim()));
            }
        }
        assertEquals(getImageTitle(), ifdValue.size(), tags.length);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mTmpFile.delete();
    }
}
