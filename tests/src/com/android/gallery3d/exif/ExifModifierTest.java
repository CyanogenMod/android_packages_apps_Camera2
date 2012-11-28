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

    // TYPE_UNDEFINED with 4 components
    private static final ExifTag sVersionTag = ExifTag.buildTag(ExifTag.TAG_EXIF_VERSION);
    // TYPE_UNSIGNED_BYTE with 4 components
    private static final ExifTag sGpsVersionTag = ExifTag.buildTag(ExifTag.TAG_GPS_VERSION_ID);
    // TYPE ASCII with arbitary length
    private static final ExifTag sModelTag = ExifTag.buildTag(ExifTag.TAG_MODEL);
    // TYPE_ASCII with 20 components
    private static final ExifTag sDateTimeTag = ExifTag.buildTag(ExifTag.TAG_DATE_TIME);
    // TYPE_UNSIGNED_SHORT with 1 components
    private static final ExifTag sCompressionTag = ExifTag.buildTag(ExifTag.TAG_COMPRESSION);
    // TYPE_UNSIGNED_LONG with 1 components
    private static final ExifTag sThumbnailFormatTag =
            ExifTag.buildTag(ExifTag.TAG_JPEG_INTERCHANGE_FORMAT);
    // TYPE_UNSIGNED_RATIONAL with 3 components
    private static final ExifTag sLongitudeTag = ExifTag.buildTag(ExifTag.TAG_GPS_LONGITUDE);
    // TYPE_RATIONAL with 1 components
    private static final ExifTag sShutterTag = ExifTag.buildTag(ExifTag.TAG_SHUTTER_SPEED_VALUE);

    private static final Map<Short, ExifTag> sTestTags = new HashMap<Short, ExifTag>();

    static {
        sVersionTag.setValue(new byte[] {1, 2, 3, 4});
        sGpsVersionTag.setValue(new byte[] {4, 3, 2, 1});
        sModelTag.setValue("end-of-the-world");
        sDateTimeTag.setValue("2012:12:31 23:59:59");
        sCompressionTag.setValue(100);
        sThumbnailFormatTag.setValue(100);
        sLongitudeTag.setValue(new Rational[] {new Rational(1, 1), new Rational(10, 10),
                new Rational(100, 100)});
        sShutterTag.setValue(new Rational(1, 1));

        sTestTags.put(sVersionTag.getTagId(), sVersionTag);
        sTestTags.put(sGpsVersionTag.getTagId(), sGpsVersionTag);
        sTestTags.put(sModelTag.getTagId(), sModelTag);
        sTestTags.put(sDateTimeTag.getTagId(), sDateTimeTag);
        sTestTags.put(sCompressionTag.getTagId(), sCompressionTag);
        sTestTags.put(sThumbnailFormatTag.getTagId(), sThumbnailFormatTag);
        sTestTags.put(sLongitudeTag.getTagId(), sLongitudeTag);
        sTestTags.put(sShutterTag.getTagId(), sShutterTag);
    }

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
    }

    public ExifModifierTest(int imageRes, int xmlRes) {
        super(imageRes, xmlRes);
    }

    public ExifModifierTest(String imagePath, String xmlPath) {
        super(imagePath, xmlPath);
    }

    public void testModify() throws Exception {
        Map<Short, Boolean> results = new HashMap<Short, Boolean>();

        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(mTmpFile, "rw");
            MappedByteBuffer buf = file.getChannel().map(MapMode.READ_WRITE, 0, file.length());
            for (ExifTag tag: sTestTags.values()) {
                ExifModifier modifier = new ExifModifier(buf);
                modifier.modifyTag(tag);
                boolean result = modifier.commit();
                results.put(tag.getTagId(), result);
                buf.force();
                buf.position(0);

                if (!result) {
                    List<String> value = mGroundTruth.get(tag.getIfd()).get(tag.getTagId());
                    assertTrue (String.format("Tag %x, ", tag.getTagId()) + getImageTitle(),
                            value == null || tag.getTagId() == ExifTag.TAG_MODEL);
                }
            }
        } finally {
            Util.closeSilently(file);
        }

        // Parse the new file and check the result
        InputStream is = null;
        try {
            is = new FileInputStream(mTmpFile);
            ExifData data = new ExifReader().read(is);
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
            assertEquals(getImageTitle(), 0 ,ifdValue.size());
            return;
        }
        ExifTag[] tags = ifd.getAllTags();
        for (ExifTag tag : tags) {
            List<String> truth = ifdValue.get(tag.getTagId());
            assertNotNull(String.format("Tag %x, ", tag.getTagId()) + getImageTitle(), truth);
            if (truth.contains(null)) continue;

            ExifTag newTag = sTestTags.get(tag.getTagId());
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
